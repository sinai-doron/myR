package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.neonsheep.myreceipts.data.TotalSource
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AmountEntryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val receipt = CapturedReceipt(
        link = "https://wee.ai/r/test",
        senderLabel = "IKEA",
        timestamp = 1728900000000L,
        clientId = "test-id-2",
    )

    @Test
    fun noAmount_addAmount_savesWhatWasTyped() {
        val saved = mutableListOf<Double?>()
        composeTestRule.setContent {
            MyReceiptsTheme { ReceiptDetailScreen(receipt = receipt, onBack = {}, onSetAmount = { _, a -> saved += a }) }
        }
        composeTestRule.onNodeWithText("Add amount").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("amount_field").performTextReplacement("12.3.4")
        composeTestRule.onNodeWithTag("amount_save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("amount_field").performTextReplacement("89,80")
        composeTestRule.onNodeWithTag("amount_save").performClick()
        composeTestRule.waitUntil(5_000) { saved.isNotEmpty() }
        assertEquals(listOf<Double?>(89.8), saved)
    }

    @Test
    fun aReadAmount_isShown_withWhereItCameFrom() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = receipt.copy(totalAmount = 730.0, totalSource = TotalSource.PAGE),
                    onBack = {},
                    onSetAmount = { _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithTag("amount").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read from the receipt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Change amount").assertIsDisplayed()
    }
}
