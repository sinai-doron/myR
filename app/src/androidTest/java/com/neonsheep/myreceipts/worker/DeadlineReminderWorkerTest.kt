package com.neonsheep.myreceipts.worker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.neonsheep.myreceipts.data.AppDatabase
import com.neonsheep.myreceipts.data.CapturedReceiptEntity
import com.neonsheep.myreceipts.data.Deadline
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeadlineReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = AppDatabase.getInstance(context).receiptDao()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val clientId = "reminder-test"
    private val day = TimeUnit.DAYS.toMillis(1)
    private val returnBy = System.currentTimeMillis() + 10 * day

    private fun receipt(returnBy: Long?) = CapturedReceiptEntity(
        link = "https://example.test/r", senderLabel = "IKEA", timestamp = 0, clientId = clientId,
        capturedAt = 0, storeName = "IKEA Rishon", returnBy = returnBy,
    )

    @Before
    fun setUp() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        dao.deleteByClientId(clientId)
        notifications.cancelAll()
    }

    @After
    fun tearDown() = runBlocking {
        DeadlineReminderWorker.cancel(context, clientId)
        dao.deleteByClientId(clientId)
        notifications.cancelAll()
    }

    private fun run(kind: Deadline, deadline: Long): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<DeadlineReminderWorker>(context)
            .setInputData(DeadlineReminderWorker.input(clientId, kind, deadline))
            .build()
            .doWork()
    }

    private fun shown() = notifications.activeNotifications
        .firstOrNull { it.id == DeadlineReminderWorker.notificationId(clientId, Deadline.RETURN) }

    @Test
    fun theDeadlineStillSet_showsTheReminder_withTheStoresName() {
        runBlocking { dao.insert(receipt(returnBy)) }
        assertEquals(ListenableWorker.Result.success(), run(Deadline.RETURN, returnBy))
        val title = shown()?.notification?.extras?.getCharSequence("android.title")?.toString()
        assertEquals("Last days to return: IKEA Rishon", title)
    }

    @Test
    fun theDeadlineMovedOrTheReceiptGone_showsNothing() {
        runBlocking { dao.insert(receipt(returnBy + day)) }
        run(Deadline.RETURN, returnBy)
        assertNull(shown())
        runBlocking { dao.deleteByClientId(clientId) }
        run(Deadline.RETURN, returnBy)
        assertNull(shown())
    }

    @Test
    fun schedule_queuesEachDeadlineSet_andClearingOneCancelsIt() {
        val work = WorkManager.getInstance(context)
        fun state(kind: Deadline) =
            work.getWorkInfosForUniqueWork(DeadlineReminderWorker.uniqueName(clientId, kind)).get().lastOrNull()?.state

        DeadlineReminderWorker.schedule(context, receipt(returnBy))
        assertEquals(WorkInfo.State.ENQUEUED, state(Deadline.RETURN))
        assertNull(state(Deadline.WARRANTY))

        DeadlineReminderWorker.schedule(context, receipt(returnBy = null))
        assertEquals(WorkInfo.State.CANCELLED, state(Deadline.RETURN))
    }
}
