package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class SampleReceiptsTest {
    private val now = java.time.LocalDate.of(2026, 11, 20).atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun samples_areMarked_haveAmounts_andFetchNothing() {
        val samples = SampleReceipts.build(now)
        assertEquals(10, samples.size)
        samples.forEach {
            assertTrue(SampleReceipts.isSample(it.clientId))
            assertTrue(it.totalAmount!! > 0)
            assertEquals(TotalSource.SAMPLE, it.totalSource)
            assertEquals(PdfStatus.SAMPLE, it.pdfStatus)
            assertTrue(it.link.startsWith("https://sample.invalid/"))
        }
        assertEquals(samples.size, samples.map { it.clientId }.toSet().size)
    }

    @Test
    fun samples_fillThisMonthsSpending_andLastMonths() {
        val samples = SampleReceipts.build(now).map { Spend(it.senderLabel, it.timestamp, it.totalAmount) }
        val thisMonth = Spending.forMonth(YearMonth.of(2026, 11), samples, Category::guessFor)
        assertEquals(0, thisMonth.withoutAmount)
        assertTrue(thisMonth.byCategory.size >= 5)
        assertTrue(Spending.forMonth(YearMonth.of(2026, 10), samples, Category::guessFor).total > 0)
    }

    @Test
    fun aRealReceipt_isNeverASample() {
        assertTrue(!SampleReceipts.isSample("3f2a8c1e-0000-4000-8000-000000000000"))
    }
}
