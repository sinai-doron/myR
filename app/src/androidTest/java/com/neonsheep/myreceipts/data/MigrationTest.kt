package com.neonsheep.myreceipts.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** An update must keep the owner's receipts: v3 → v4 adds pdfStatus and loses nothing. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun v3ToV4_keepsReceipts_andAddsAnEmptyStatus() = runBlocking {
        context.deleteDatabase(name)
        // The v3 schema exactly as Room created it.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE `captured_receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`link` TEXT NOT NULL, `senderLabel` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "`clientId` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `totalAmount` REAL, `pdfPath` TEXT)",
            )
            db.execSQL("CREATE UNIQUE INDEX `index_captured_receipts_clientId` ON `captured_receipts` (`clientId`)")
            db.execSQL(
                "INSERT INTO captured_receipts (link, senderLabel, timestamp, clientId, capturedAt, totalAmount, pdfPath) " +
                    "VALUES ('https://wee.ai/r/test', 'IKEA', 1, 'c-1', 2, 74.3, '/data/x.pdf')",
            )
            db.version = 3
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_3_4)
            .build() // no destructive fallback: a failing migration must fail this test
        try {
            val all = room.receiptDao().getAllOnce()
            assertEquals(1, all.size)
            val r = all.single()
            assertEquals("c-1", r.clientId)
            assertEquals("/data/x.pdf", r.pdfPath)
            assertEquals(74.3, r.totalAmount!!, 0.001)
            assertNull(r.pdfStatus)

            room.receiptDao().updatePdfStatus("c-1", PdfStatus.UNSUPPORTED)
            assertEquals(PdfStatus.UNSUPPORTED, room.receiptDao().getByClientId("c-1")?.pdfStatus)
            room.receiptDao().updatePdfPath("c-1", "/data/y.pdf")
            assertNull("a PDF clears the status", room.receiptDao().getByClientId("c-1")?.pdfStatus)
        } finally {
            room.close()
        }
    }
}
