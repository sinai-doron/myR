package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Rule
import org.junit.Test

class CapturedListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_showsNoReceiptsMessage() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                VendorListScreen(receipts = emptyList())
            }
        }

        composeTestRule.onNodeWithText("No receipts captured yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Receipt SMS will appear here automatically").assertIsDisplayed()
    }

    @Test
    fun withReceipts_showsOneRowPerVendor() {
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
            MyReceiptsTheme {
                VendorListScreen(receipts = receipts)
            }
        }

        composeTestRule.onNodeWithText("Shufersal").assertIsDisplayed()
        composeTestRule.onNodeWithText("OSHERAD").assertIsDisplayed()
    }
}
