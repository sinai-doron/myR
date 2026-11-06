package com.neonsheep.myreceipts.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** An update must keep the owner's receipts: v3 → v4 adds pdfStatus, v4 → v5 totalSource, and neither loses anything. */
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
            .addMigrations(*MIGRATIONS)
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

    @Test
    fun v4ToV5_keepsAmounts_andTheUsersAmountIsNeverReplaced() = runBlocking {
        context.deleteDatabase(name)
        // The v4 schema exactly as Room created it.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE `captured_receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`link` TEXT NOT NULL, `senderLabel` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "`clientId` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `totalAmount` REAL, `pdfPath` TEXT, " +
                    "`pdfStatus` TEXT)",
            )
            db.execSQL("CREATE UNIQUE INDEX `index_captured_receipts_clientId` ON `captured_receipts` (`clientId`)")
            db.execSQL(
                "INSERT INTO captured_receipts (link, senderLabel, timestamp, clientId, capturedAt, pdfPath) " +
                    "VALUES ('https://wee.ai/r/a', 'IKEA', 1, 'c-1', 2, '/data/x.pdf'), " +
                    "('https://wee.ai/r/b', 'Jumbo', 3, 'c-2', 4, NULL)",
            )
            db.version = 4
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*MIGRATIONS)
            .build() // no destructive fallback: a failing migration must fail this test
        try {
            val dao = room.receiptDao()
            assertEquals(2, dao.getAllOnce().size)
            assertEquals(listOf("c-1"), dao.withoutTotalLookedFor().map { it.clientId })

            // Nothing in the PDF: marked, so it isn't read again; a page amount still comes in.
            dao.setPdfTotal("c-1", null, TotalSource.NONE)
            assertEquals(emptyList<String>(), dao.withoutTotalLookedFor().map { it.clientId })
            dao.setPageTotal("c-1", 730.0)
            assertEquals(730.0, dao.getByClientId("c-1")!!.totalAmount!!, 0.001)
            assertEquals(TotalSource.PAGE, dao.getByClientId("c-1")!!.totalSource)

            // The PDF never replaces a known amount.
            dao.setPdfTotal("c-1", 12.0, TotalSource.PDF)
            assertEquals(730.0, dao.getByClientId("c-1")!!.totalAmount!!, 0.001)

            // The user's amount is never replaced, by the page or the PDF.
            dao.setUserTotal("c-1", 700.0)
            dao.setPageTotal("c-1", 730.0)
            dao.setPdfTotal("c-1", 12.0, TotalSource.PDF)
            assertEquals(700.0, dao.getByClientId("c-1")!!.totalAmount!!, 0.001)
            assertEquals(TotalSource.USER, dao.getByClientId("c-1")!!.totalSource)
        } finally {
            room.close()
        }
    }

    @Test
    fun v5ToV6_keepsReceipts_andAddsCategoriesAndBudgets() = runBlocking {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE `captured_receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`link` TEXT NOT NULL, `senderLabel` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "`clientId` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `totalAmount` REAL, `pdfPath` TEXT, " +
                    "`pdfStatus` TEXT, `totalSource` TEXT)",
            )
            db.execSQL("CREATE UNIQUE INDEX `index_captured_receipts_clientId` ON `captured_receipts` (`clientId`)")
            db.execSQL(
                "INSERT INTO captured_receipts (link, senderLabel, timestamp, clientId, capturedAt, totalAmount, totalSource) " +
                    "VALUES ('https://wee.ai/r/a', 'IKEA', 1, 'c-1', 2, 730.0, 'page')",
            )
            db.version = 5
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*MIGRATIONS)
            .build() // no destructive fallback: a failing migration must fail this test
        try {
            val r = room.receiptDao().getAllOnce().single()
            assertEquals(730.0, r.totalAmount!!, 0.001)
            assertEquals(TotalSource.PAGE, r.totalSource)

            val spending = room.spendingDao()
            spending.setStoreCategory(StoreCategoryEntity("IKEA", Category.LEISURE.name))
            spending.setStoreCategory(StoreCategoryEntity("IKEA", Category.HOME.name))
            spending.setBudget(BudgetEntity(BudgetEntity.ALL, 3000.0))
            spending.setBudget(BudgetEntity(Category.HOME.name, 500.0))
            spending.removeBudget(Category.HOME.name)
            assertEquals(listOf(StoreCategoryEntity("IKEA", "HOME")), spending.storeCategories().first())
            assertEquals(listOf(BudgetEntity(BudgetEntity.ALL, 3000.0)), spending.budgets().first())
        } finally {
            room.close()
        }
    }

    @Test
    fun v6ToV7_keepsEverything_andTheUsersCorrectionsWin() = runBlocking {
        context.deleteDatabase(name)
        // The v6 schema exactly as Room created it.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE `captured_receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`link` TEXT NOT NULL, `senderLabel` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "`clientId` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `totalAmount` REAL, `pdfPath` TEXT, " +
                    "`pdfStatus` TEXT, `totalSource` TEXT)",
            )
            db.execSQL("CREATE UNIQUE INDEX `index_captured_receipts_clientId` ON `captured_receipts` (`clientId`)")
            db.execSQL(
                "CREATE TABLE `store_categories` (`senderLabel` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "PRIMARY KEY(`senderLabel`))",
            )
            db.execSQL("CREATE TABLE `budgets` (`category` TEXT NOT NULL, `monthlyLimit` REAL NOT NULL, PRIMARY KEY(`category`))")
            db.execSQL(
                "INSERT INTO captured_receipts (link, senderLabel, timestamp, clientId, capturedAt, totalAmount, pdfPath, totalSource) " +
                    "VALUES ('https://wee.ai/r/a', 'IKEA', 1000, 'c-1', 2, 730.0, '/data/x.pdf', 'page')",
            )
            db.execSQL("INSERT INTO store_categories VALUES ('IKEA', 'HOME')")
            db.execSQL("INSERT INTO budgets VALUES ('ALL', 3000.0)")
            db.version = 6
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*MIGRATIONS)
            .build() // no destructive fallback: a failing migration must fail this test
        try {
            val dao = room.receiptDao()
            val r = dao.getAllOnce().single()
            assertEquals(730.0, r.totalAmount!!, 0.001)
            assertEquals("/data/x.pdf", r.pdfPath)
            assertEquals("IKEA", r.store)
            assertEquals(1000L, r.boughtAt)
            assertNull(r.category)
            assertEquals(false, r.notDuplicate)
            assertEquals(listOf(StoreCategoryEntity("IKEA", "HOME")), room.spendingDao().storeCategories().first())
            assertEquals(listOf(BudgetEntity(BudgetEntity.ALL, 3000.0)), room.spendingDao().budgets().first())

            dao.setStoreName("c-1", "IKEA Rishon")
            dao.setPurchasedAt("c-1", 500L)
            dao.setCategory("c-1", Category.LEISURE.name)
            dao.setDeadlines("c-1", returnBy = 5000L, warrantyUntil = 9000L)
            dao.markNotDuplicate(listOf("c-1"))
            val edited = dao.getByClientId("c-1")!!
            assertEquals("IKEA Rishon", edited.store)
            assertEquals(500L, edited.boughtAt)
            assertEquals("LEISURE", edited.category)
            assertEquals(true, edited.notDuplicate)
            assertEquals(listOf("c-1"), dao.withDeadlinesAfter(6000L).map { it.clientId })
            assertEquals(emptyList<String>(), dao.withDeadlinesAfter(9001L).map { it.clientId })

            // Clearing a correction falls back to what the text said.
            dao.setStoreName("c-1", null)
            dao.setPurchasedAt("c-1", null)
            assertEquals("IKEA", dao.getByClientId("c-1")!!.store)
            assertEquals(1000L, dao.getByClientId("c-1")!!.boughtAt)
        } finally {
            room.close()
        }
    }
}
