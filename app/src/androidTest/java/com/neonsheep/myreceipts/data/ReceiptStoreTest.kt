package com.neonsheep.myreceipts.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptStoreTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ReceiptDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.receiptDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_and_retrieve() = runBlocking {
        val entity = CapturedReceiptEntity(
            link = "https://shufersal.co.il/receipt/123",
            senderLabel = "Shufersal",
            timestamp = 1728900000000L,
            clientId = "test-client-id",
            capturedAt = 1728900001000L,
        )

        dao.insert(entity)
        val all = dao.getAllOnce()

        assertEquals(1, all.size)
        assertEquals("https://shufersal.co.il/receipt/123", all[0].link)
        assertEquals("Shufersal", all[0].senderLabel)
        assertEquals(1728900000000L, all[0].timestamp)
        assertEquals("test-client-id", all[0].clientId)
    }

    @Test
    fun duplicateClientId_isIgnored() = runBlocking {
        val entity1 = CapturedReceiptEntity(
            link = "https://shufersal.co.il/receipt/123",
            senderLabel = "Shufersal",
            timestamp = 1728900000000L,
            clientId = "same-id",
            capturedAt = 1728900001000L,
        )
        val entity2 = CapturedReceiptEntity(
            link = "https://shufersal.co.il/receipt/123",
            senderLabel = "Shufersal",
            timestamp = 1728900000000L,
            clientId = "same-id",
            capturedAt = 1728900002000L,
        )

        dao.insert(entity1)
        dao.insert(entity2)
        val all = dao.getAllOnce()

        assertEquals(1, all.size)
    }

    @Test
    fun getAllOnce_orderedByTimestampDesc() = runBlocking {
        dao.insert(CapturedReceiptEntity(
            link = "https://shufersal.co.il/old",
            senderLabel = "Shufersal",
            timestamp = 1000L,
            clientId = "id-old",
            capturedAt = 2000L,
        ))
        dao.insert(CapturedReceiptEntity(
            link = "https://shufersal.co.il/new",
            senderLabel = "Shufersal",
            timestamp = 3000L,
            clientId = "id-new",
            capturedAt = 4000L,
        ))

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        assertTrue(all[0].timestamp > all[1].timestamp)
    }
}
