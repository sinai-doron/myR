package com.doron.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.doron.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Rule
import org.junit.Test

class CapturedListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_showsNoReceiptsMessage() {
        composeTestRule.setContent {
            MyReceiptsTheme(dynamicColor = false) {
                CapturedListScreen(receipts = emptyList())
            }
        }

        composeTestRule.onNodeWithText("No receipts captured yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Receipt SMS will appear here automatically").assertIsDisplayed()
    }

    @Test
    fun withReceipts_showsReceiptCards() {
        val receipts = listOf(
            CapturedReceipt(
                link = "https://shufersal.co.il/receipt/123",
                senderLabel = "Shufersal",
                timestamp = 1728900000000L,
                clientId = "test-id-1",
            ),
            CapturedReceipt(
                link = "https://pairzon.com/r/456",
                senderLabel = "OSHERAD",
                timestamp = 1728903600000L,
                clientId = "test-id-2",
            ),
        )

        composeTestRule.setContent {
            MyReceiptsTheme(dynamicColor = false) {
                CapturedListScreen(receipts = receipts)
            }
        }

        composeTestRule.onNodeWithText("Shufersal").assertIsDisplayed()
        composeTestRule.onNodeWithText("OSHERAD").assertIsDisplayed()
        composeTestRule.onNodeWithText("shufersal.co.il/receipt/123", substring = true).assertIsDisplayed()
    }
}
