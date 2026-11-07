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

    @Test
    fun applyEdit_keepsAReadAmountUnlessChanged_andSavesTheRest() = runBlocking {
        dao.insert(CapturedReceiptEntity(link = "https://wee.ai/r/1", senderLabel = "IKEA", timestamp = 1000L, clientId = "c-1", capturedAt = 1L))
        dao.setPageTotal("c-1", 730.0)

        dao.applyEdit("c-1", "IKEA Rishon", 500L, "LEISURE", 2000L, 3000L, amount = null, amountChanged = false)
        val e = dao.getByClientId("c-1")!!
        assertEquals("IKEA Rishon", e.store)
        assertEquals(500L, e.boughtAt)
        assertEquals("LEISURE", e.category)
        assertEquals(2000L, e.returnBy)
        assertEquals(3000L, e.warrantyUntil)
        assertEquals(730.0, e.totalAmount!!, 0.001)
        assertEquals(TotalSource.PAGE, e.totalSource)

        dao.applyEdit("c-1", null, null, null, null, null, amount = 700.0, amountChanged = true)
        val back = dao.getByClientId("c-1")!!
        assertEquals("IKEA", back.store)
        assertEquals(1000L, back.boughtAt)
        assertEquals(null, back.category)
        assertEquals(700.0, back.totalAmount!!, 0.001)
        assertEquals(TotalSource.USER, back.totalSource)
    }

    @Test
    fun merge_savesTheKeptReceipt_andDeletesTheOthers_together() = runBlocking {
        fun r(id: String, at: Long) = CapturedReceiptEntity(
            link = "https://example.test/$id", senderLabel = "Delek", timestamp = at, clientId = id, capturedAt = at,
            totalAmount = 312.15, totalSource = "page",
        )
        dao.insert(r("a", 0).copy(pdfPath = "/files/a.pdf"))
        dao.insert(r("b", 60_000).copy(category = "CAR", returnBy = 5L))
        dao.insert(r("other", 0))

        val (kept, removed) = Duplicates.merge(listOfNotNull(dao.getByClientId("a"), dao.getByClientId("b")))
        dao.merge(kept, removed.map { it.clientId })

        assertEquals(listOf("a", "other"), dao.getAllOnce().map { it.clientId }.sorted())
        val a = dao.getByClientId("a")!!
        assertEquals("CAR", a.category)
        assertEquals(5L, a.returnBy)
        assertEquals("/files/a.pdf", a.pdfPath)
    }
}
