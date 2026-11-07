package com.neonsheep.myreceipts.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A receipt's items go with it however it's deleted, and stay when it's re-saved with INSERT OR REPLACE. */
class ReceiptItemsTest {
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) = db.execSQL(ITEMS_GO_WITH_RECEIPT)
                },
            )
            .build()
    }

    @After
    fun close() = db.close()

    private fun receipt(id: String) = CapturedReceiptEntity(link = "https://x/$id", senderLabel = "S", timestamp = 1, clientId = id, capturedAt = 1)

    private fun items(id: String) = listOf(
        ReceiptItemEntity(receiptClientId = id, position = 0, name = "a"),
        ReceiptItemEntity(receiptClientId = id, position = 0, name = "b"),
    )

    @Test
    fun itemsStayThroughAReplace_andGoWithTheReceipt() = runBlocking {
        val dao = db.receiptDao()
        val itemDao = db.itemDao()
        dao.insert(receipt("one"))
        dao.insert(receipt("two"))
        itemDao.replace("one", items("one"))
        itemDao.replace("two", items("two"))
        assertEquals(listOf("a", "b"), itemDao.itemsForOnce("one").map { it.name })
        assertEquals(listOf(0, 1), itemDao.itemsForOnce("one").map { it.position })

        dao.getByClientId("one")?.let { dao.update(it.copy(totalAmount = 9.0)) }
        assertEquals(2, itemDao.itemsForOnce("one").size)

        dao.deleteByClientId("one")
        assertEquals(0, itemDao.itemsForOnce("one").size)
        dao.deleteByClientIds(listOf("two"))
        assertEquals(0, itemDao.itemsForOnce("two").size)
    }
}
