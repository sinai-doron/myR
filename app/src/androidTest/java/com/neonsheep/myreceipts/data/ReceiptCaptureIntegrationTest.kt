package com.neonsheep.myreceipts.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neonsheep.myreceipts.receiver.ReceiptCaptureHandler
import com.neonsheep.myreceipts.receiver.buildFilter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptCaptureIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ReceiptDao
    private lateinit var config: MerchantConfig
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.receiptDao()
        config = MerchantConfig(
            merchants = listOf(
                MerchantConfig.Merchant(
                    name = "Shufersal",
                    domains = listOf("shufersal.co.il"),
                ),
                MerchantConfig.Merchant(
                    name = "Pairzon",
                    domains = listOf("pairzon.com"),
                ),
            ),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun filterAndProcess(body: String, sender: String, timestamp: Long) = runBlocking {
        val filter = buildFilter(config)
        val result = filter.filter(body, sender)
        if (result.matchedLinks.isNotEmpty()) {
            ReceiptCaptureHandler.processMessage(
                context = context,
                matchedLinks = result.matchedLinks,
                senderLabel = result.senderLabel,
                timestamp = timestamp,
                dao = dao,
            )
        }
    }

    @Test
    fun receiptSms_storedInDatabase() {
        filterAndProcess("הקבלה שלך: https://shufersal.co.il/receipt/123", "Shufersal", 1728900000000L)

        val receipts = runBlocking { dao.getAllOnce() }
        assertEquals(1, receipts.size)
        assertEquals("https://shufersal.co.il/receipt/123", receipts[0].link)
        assertEquals("Shufersal", receipts[0].senderLabel)
    }

    @Test
    fun nonReceiptSms_notStored() {
        filterAndProcess("Your verification code is 123456", "Google", 1728900000000L)

        val receipts = runBlocking { dao.getAllOnce() }
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun duplicateMessage_storedOnce() {
        repeat(2) {
            filterAndProcess("הקבלה שלך: https://shufersal.co.il/receipt/123", "Shufersal", 1728900000000L)
        }

        val receipts = runBlocking { dao.getAllOnce() }
        assertEquals(1, receipts.size)
    }

    @Test
    fun mixedLinks_onlyOnListStored() {
        filterAndProcess("הקבלה שלך: https://pairzon.com/r/456 לפרטים: https://bit.ly/xyz", "OSHERAD", 1728900000000L)

        val receipts = runBlocking { dao.getAllOnce() }
        assertEquals(1, receipts.size)
        assertEquals("https://pairzon.com/r/456", receipts[0].link)
    }
}
