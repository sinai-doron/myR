package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class SpendingTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private val receipts = listOf(
        Spend("Shufersal", at(2026, 11, 1), 113.58),
        Spend("Shufersal", at(2026, 11, 4), 322.62),
        Spend("SuperPharm", at(2026, 11, 2), 25.90),
        Spend("IKEA", at(2026, 11, 3), null),
        Spend("Delek", at(2026, 10, 31, 23), 299.00), // October, by Israel's clock
        Spend("Delek", at(2026, 11, 1, 0), 500.06),
    )

    @Test
    fun aMonthsTotals_byCategoryAndStore_withoutUnknownAmounts() {
        val s = Spending.forMonth(YearMonth.of(2026, 11), receipts, Category::guessFor, zone)
        assertEquals(962.16, s.total, 0.001)
        assertEquals(5, s.receipts)
        assertEquals(1, s.withoutAmount)
        assertEquals(436.20, s.byCategory[Category.GROCERIES]!!, 0.001)
        assertEquals(500.06, s.byCategory[Category.TRANSPORT]!!, 0.001)
        assertEquals(listOf("Delek", "Shufersal", "SuperPharm"), s.byStore.map { it.store })
        assertEquals(2, s.byStore.first { it.store == "Shufersal" }.receipts)
    }

    @Test
    fun theUsersCategoryForAStore_wins() {
        val s = Spending.forMonth(YearMonth.of(2026, 11), receipts, { if (it == "SuperPharm") Category.HOME else Category.guessFor(it) }, zone)
        assertEquals(25.90, s.byCategory[Category.HOME]!!, 0.001)
        assertEquals(null, s.byCategory[Category.HEALTH])
    }

    @Test
    fun guessesFromSenderNames() {
        assertEquals(Category.GROCERIES, Category.guessFor("Shufersal"))
        assertEquals(Category.HEALTH, Category.guessFor("SuperPharm"))
        assertEquals(Category.TRANSPORT, Category.guessFor("DorAlon"))
        assertEquals(Category.HOME, Category.guessFor("IKEA"))
        assertEquals(Category.CLOTHING, Category.guessFor("Decathlon"))
        assertEquals(Category.LEISURE, Category.guessFor("MOVIELAND"))
        assertEquals(Category.OTHER, Category.guessFor("TSfarim"))
    }

    @Test
    fun budgetState_okNearOver() {
        assertEquals(BudgetStatus.State.OK, BudgetStatus(700.0, 1000.0).state)
        assertEquals(BudgetStatus.State.NEAR, BudgetStatus(800.0, 1000.0).state)
        assertEquals(BudgetStatus.State.NEAR, BudgetStatus(1000.0, 1000.0).state)
        assertEquals(BudgetStatus.State.OVER, BudgetStatus(1000.01, 1000.0).state)
        assertEquals(-0.01, BudgetStatus(1000.01, 1000.0).left, 0.001)
    }
}
