package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.neonsheep.myreceipts.data.BudgetEntity
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.Spend
import com.neonsheep.myreceipts.data.Spending
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Made-up receipts: the month's total, the budget, categories, and changing a store's category. */
class SpendingScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val month = YearMonth.of(2026, 11)
    private fun day(d: Int) = LocalDate.of(2026, 11, d).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val spending = Spending.forMonth(
        month,
        listOf(
            Spend("Shufersal", day(1), 2400.0),
            Spend("SuperPharm", day(2), 89.80),
            Spend("IKEA", day(3), null),
        ),
        Category::guessFor,
    )

    @Test
    fun overTheBudget_saysByHowMuch_andCountsUnknownAmountsApart() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                SpendingScreen(
                    spending = spending,
                    budgets = mapOf(BudgetEntity.ALL to 2000.0, Category.HEALTH.name to 100.0),
                    isCurrentMonth = true,
                    onMonth = {},
                    onBack = {},
                    onSetBudget = { _, _ -> },
                    onSetCategory = { _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithTag("budget_OVER").assertIsDisplayed()
        composeTestRule.onNodeWithTag("budget_NEAR").assertIsDisplayed() // health: 89.80 of 100
        composeTestRule.onNodeWithTag("without_amount").assertIsDisplayed()
        composeTestRule.onNodeWithTag("next_month").assertIsNotEnabled()
    }

    @Test
    fun setABudget_andMoveAStoreToAnotherCategory() {
        val budgets = mutableListOf<Pair<String, Double?>>()
        val moved = mutableListOf<Pair<String, Category>>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                SpendingScreen(
                    spending = spending,
                    budgets = emptyMap(),
                    isCurrentMonth = false,
                    onMonth = {},
                    onBack = {},
                    onSetBudget = { c, l -> budgets += c to l },
                    onSetCategory = { s, c -> moved += s to c },
                )
            }
        }
        composeTestRule.onNodeWithTag("set_budget").performClick()
        composeTestRule.onNodeWithTag("budget_field").performTextReplacement("3000")
        composeTestRule.onNodeWithTag("budget_save").performClick()
        composeTestRule.waitUntil(5_000) { budgets.isNotEmpty() }
        assertEquals(listOf<Pair<String, Double?>>(BudgetEntity.ALL to 3000.0), budgets)

        composeTestRule.onNodeWithTag("store_SuperPharm").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("category_HOME").performClick()
        composeTestRule.waitUntil(5_000) { moved.isNotEmpty() }
        assertEquals(listOf("SuperPharm" to Category.HOME), moved)
    }
}
