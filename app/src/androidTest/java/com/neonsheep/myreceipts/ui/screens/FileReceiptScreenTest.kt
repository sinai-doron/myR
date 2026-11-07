package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.neonsheep.myreceipts.data.TotalSource
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A receipt added as a file or scan: no link to show or open, and a request for what couldn't be read. */
class FileReceiptScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val scan = CapturedReceipt(
        link = "",
        senderLabel = "Scanned receipt",
        timestamp = 1_790_000_000_000L,
        clientId = "file-0123456789abcdef0123456789abcdef",
        totalSource = TotalSource.NONE,
    )

    @Test
    fun anUnreadScan_asksForTheAmount_andHasNoLink() {
        composeTestRule.setContent {
            MyReceiptsTheme { ReceiptDetailScreen(receipt = scan, onBack = {}, onSetAmount = { _, _ -> }) }
        }
        composeTestRule.onNodeWithTag("amount_note").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add amount").assertIsDisplayed()
        composeTestRule.onNodeWithText("Added").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Link").assertDoesNotExist()
        composeTestRule.onNodeWithText("Open in Browser").assertDoesNotExist()
        composeTestRule.onNodeWithTag("get_pdf").assertDoesNotExist()
    }

    @Test
    fun aFileJustAdded_opensOnTheEditScreen_forItsStore() {
        val edits = mutableListOf<ReceiptEdit>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(receipt = scan, onBack = {}, startEditing = true, onEdit = { _, e -> edits += e })
            }
        }
        composeTestRule.onNodeWithTag("edit_store").assertIsDisplayed()
        composeTestRule.onNodeWithText("Edit receipt").assertIsDisplayed()
        assertEquals(0, edits.size)
    }

    @Test
    fun theAddSheet_offersTheScannerAndAFile() {
        val picked = mutableListOf<String>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                AddReceiptSheet(onScan = { picked += "scan" }, onPickFile = { picked += "file" }, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("add_scan").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("add_file").assertIsDisplayed().performClick()
        assertEquals(listOf("scan", "file"), picked)
    }
}
