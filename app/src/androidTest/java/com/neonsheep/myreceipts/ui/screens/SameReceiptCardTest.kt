package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.Spend
import com.neonsheep.myreceipts.data.Spending
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class SameReceiptCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 6, h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun r(id: String, at: Long) = CapturedReceipt(
        link = "https://example.test/$id", senderLabel = "Delek", timestamp = at, clientId = id, totalAmount = 312.15,
    )
    private val group = listOf(r("a", at(12, 40)), r("b", at(12, 41)))

    private fun show(duplicates: List<List<CapturedReceipt>>, notSame: MutableList<List<String>>, merged: MutableList<List<String>>) =
        composeTestRule.setContent {
            MyReceiptsTheme {
                HomeScreen(
                    receipts = group,
                    spending = Spending.forMonth(YearMonth.now(), group.map { Spend(it.senderLabel, it.timestamp, it.totalAmount) }, Category::guessFor),
                    budgets = emptyMap(),
                    categoryOf = Category::guessFor,
                    duplicates = duplicates,
                    onNotSame = { g -> notSame += g.map { it.clientId } },
                    onMerge = { g -> merged += g.map { it.clientId } },
                )
            }
        }

    @Test
    fun theCardNamesTheStoreAndAmount_andEachAnswerGivesTheGroup() {
        val notSame = mutableListOf<List<String>>()
        val merged = mutableListOf<List<String>>()
        show(listOf(group), notSame, merged)
        composeTestRule.onNodeWithText("Same receipt?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delek, ₪312.15, came in more than once: 6 Nov, 12:40 · 12:41.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("same_receipt_merge").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("same_receipt_no").performClick()
        assertEquals(listOf(listOf("a", "b")), merged)
        assertEquals(listOf(listOf("a", "b")), notSame)
    }

    @Test
    fun noCard_withoutDuplicates() {
        show(emptyList(), mutableListOf(), mutableListOf())
        composeTestRule.onNodeWithTag("same_receipt").assertDoesNotExist()
    }
}
