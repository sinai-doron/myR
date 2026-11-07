package com.neonsheep.myreceipts.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neonsheep.myreceipts.auth.Backend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The spec's pass check: receipts survive export, a fresh install, and import. */
@RunWith(AndroidJUnit4::class)
class ReceiptBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbs = mutableListOf<AppDatabase>()
    private val made = mutableListOf<File>()
    private val pdfBytes = "%PDF-1.7\nthe scanned receipt\n%%EOF".toByteArray()

    private fun freshDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { dbs += it }

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
        made.forEach { it.delete() }
    }

    @Test
    fun receipts_survive_exportReinstallAndImport() = runBlocking {
        val old = freshDb()
        val scanId = "file-00112233445566778899aabbccddeeff"
        val scanPdf = Backend.pdfFile(context, scanId).apply { parentFile?.mkdirs(); writeBytes(pdfBytes) }
        made += scanPdf
        old.receiptDao().insert(
            CapturedReceiptEntity(
                link = "https://wee.ai/r/abc", senderLabel = "IKEA", timestamp = 1_000, clientId = "sms-1",
                capturedAt = 1_000, totalAmount = 730.0, totalSource = TotalSource.PAGE, storeName = "IKEA Rishon",
                returnBy = System.currentTimeMillis() + 10L * 86_400_000,
            ),
        )
        old.receiptDao().insert(
            CapturedReceiptEntity(
                link = "", senderLabel = "Scanned receipt", timestamp = 2_000, clientId = scanId, capturedAt = 2_000,
                pdfPath = scanPdf.absolutePath, totalAmount = 89.8, totalSource = TotalSource.USER, origin = Origin.SCAN,
            ),
        )
        old.receiptDao().insertAll(SampleReceipts.build())
        old.spendingDao().setStoreCategory(StoreCategoryEntity("IKEA", Category.HOME.name))
        old.spendingDao().setBudget(BudgetEntity(BudgetEntity.ALL, 3000.0))

        val file = File(context.cacheDir, "export.zip").also { made += it }
        val exported = ReceiptBackup.export(context, old, Uri.fromFile(file))
        assertEquals("the samples stay behind", 2, exported)

        // A reinstall: the database and the PDFs are gone.
        scanPdf.delete()
        val fresh = freshDb()

        val result = ReceiptBackup.import(context, fresh, Uri.fromFile(file))

        assertEquals(ReceiptBackup.Imported(added = 2, alreadyHere = 0), result)
        val sms = fresh.receiptDao().getByClientId("sms-1")!!
        assertEquals("IKEA Rishon", sms.store)
        assertEquals(730.0, sms.totalAmount!!, 0.001)
        assertEquals(TotalSource.PAGE, sms.totalSource)
        assertTrue(sms.returnBy != null)
        val scan = fresh.receiptDao().getByClientId(scanId)!!
        assertEquals(Origin.SCAN, scan.origin)
        assertEquals(TotalSource.USER, scan.totalSource)
        assertTrue(pdfBytes.contentEquals(File(scan.pdfPath!!).readBytes()))
        assertNull(fresh.receiptDao().getAllOnce().firstOrNull { SampleReceipts.isSample(it.clientId) })
        assertEquals(listOf(StoreCategoryEntity("IKEA", Category.HOME.name)), fresh.spendingDao().storeCategories().first())
        assertEquals(listOf(BudgetEntity(BudgetEntity.ALL, 3000.0)), fresh.spendingDao().budgets().first())
    }

    @Test
    fun importingAgain_addsNothing_andChangesNothingTheUserSet() = runBlocking {
        val db = freshDb()
        db.receiptDao().insert(
            CapturedReceiptEntity(link = "https://wee.ai/r/a", senderLabel = "IKEA", timestamp = 1, clientId = "sms-1", capturedAt = 1),
        )
        db.spendingDao().setBudget(BudgetEntity(BudgetEntity.ALL, 3000.0))
        val file = File(context.cacheDir, "export2.zip").also { made += it }
        ReceiptBackup.export(context, db, Uri.fromFile(file))

        // Since the export, the user renamed the store and changed the budget.
        db.receiptDao().setStoreName("sms-1", "IKEA Netanya")
        db.spendingDao().setBudget(BudgetEntity(BudgetEntity.ALL, 2500.0))

        val result = ReceiptBackup.import(context, db, Uri.fromFile(file))

        assertEquals(ReceiptBackup.Imported(added = 0, alreadyHere = 1), result)
        assertEquals("IKEA Netanya", db.receiptDao().getByClientId("sms-1")!!.store)
        assertEquals(listOf(BudgetEntity(BudgetEntity.ALL, 2500.0)), db.spendingDao().budgets().first())
    }
}
