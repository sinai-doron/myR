package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.ReceiptDates
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditReceiptScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val receipt = CapturedReceipt(
        link = "https://wee.ai/r/1",
        senderLabel = "IKEA",
        timestamp = 1_762_450_000_000L,
        clientId = "c-1",
        totalAmount = 730.0,
        totalSource = "page",
    )

    private fun show(r: CapturedReceipt, saved: MutableList<ReceiptEdit>) = composeTestRule.setContent {
        MyReceiptsTheme {
            ReceiptDetailScreen(
                receipt = r,
                onBack = {},
                storeCategory = Category.HOME,
                onEdit = { _, e -> saved += e },
            )
        }
    }

    @Test
    fun savingWithoutChanges_keepsTheReadAmount_andSetsNothing() {
        val saved = mutableListOf<ReceiptEdit>()
        show(receipt, saved)
        composeTestRule.onNodeWithTag("edit_receipt").performClick()
        composeTestRule.onNodeWithTag("category_store").assertIsSelected()
        composeTestRule.onNodeWithTag("edit_save").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val e = saved.single()
        assertFalse("an untouched amount stays read, not typed", e.amountChanged)
        assertNull(e.storeName)
        assertNull(e.purchasedAt)
        assertNull(e.category)
        assertNull(e.returnBy)
        assertNull(e.warrantyUntil)
    }

    @Test
    fun theUsersStoreCategoryAmountAndDeadlines_areSaved() {
        val saved = mutableListOf<ReceiptEdit>()
        show(receipt, saved)
        composeTestRule.onNodeWithTag("edit_receipt").performClick()
        composeTestRule.onNodeWithTag("edit_store").performTextInput("IKEA Rishon")
        composeTestRule.onNodeWithTag("edit_amount_field").performTextClearance()
        composeTestRule.onNodeWithTag("edit_amount_field").performTextInput("700")
        composeTestRule.onNodeWithTag("category_LEISURE").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("return_14").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("warranty_2").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("edit_save").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val e = saved.single()
        assertEquals("IKEA Rishon", e.storeName)
        assertTrue(e.amountChanged)
        assertEquals(700.0, e.amount!!, 0.001)
        assertEquals("LEISURE", e.category)
        assertEquals(ReceiptDates.returnBy(receipt.timestamp, 14), e.returnBy)
        assertEquals(ReceiptDates.warrantyUntil(receipt.timestamp, 2), e.warrantyUntil)
    }

    @Test
    fun aReceiptWithDeadlines_showsThem_andTheEditorOpensOnThem() {
        val withDeadlines = receipt.copy(
            returnBy = ReceiptDates.returnBy(receipt.timestamp, 30),
            warrantyUntil = ReceiptDates.warrantyUntil(receipt.timestamp, 1),
        )
        val saved = mutableListOf<ReceiptEdit>()
        show(withDeadlines, saved)
        assertNotNull(composeTestRule.onNodeWithTag("detail_return").performScrollTo())
        composeTestRule.onNodeWithTag("detail_warranty").performScrollTo()
        composeTestRule.onNodeWithTag("edit_receipt").performClick()
        composeTestRule.onNodeWithTag("return_30").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithTag("warranty_1").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithTag("return_none").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("edit_save").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertNull("None clears the return deadline", saved.single().returnBy)
        assertEquals(withDeadlines.warrantyUntil, saved.single().warrantyUntil)
    }
}
