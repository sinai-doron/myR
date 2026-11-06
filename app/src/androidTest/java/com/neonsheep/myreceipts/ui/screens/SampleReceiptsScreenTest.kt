package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.neonsheep.myreceipts.data.SampleReceipts
import com.neonsheep.myreceipts.toUiModel
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The empty home screen offers sample receipts and the month; samples say so and can be removed. */
class SampleReceiptsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyHome_showsTheMonth_andOffersSamples() {
        var added = 0
        var opened = 0
        composeTestRule.setContent {
            MyReceiptsTheme {
                TestHome(receipts = emptyList(), onAddSamples = { added++ }, onOpenSpending = { opened++ })
            }
        }
        composeTestRule.onNodeWithTag("month_card").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("add_samples").performScrollTo().performClick()
        assertEquals(1, opened)
        assertEquals(1, added)
    }

    @Test
    fun withSamples_aBannerSaysSo_andRemovesThem() {
        var removed = 0
        val samples = SampleReceipts.build().map { it.toUiModel() }
        composeTestRule.setContent {
            MyReceiptsTheme { TestHome(receipts = samples, onRemoveSamples = { removed++ }) }
        }
        composeTestRule.onNodeWithTag("samples_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("remove_samples").performClick()
        assertEquals(1, removed)
    }

    @Test
    fun aSampleReceipt_saysItIsMadeUp_andOffersNoPdf() {
        val sample = SampleReceipts.build().first().toUiModel()
        composeTestRule.setContent {
            MyReceiptsTheme { ReceiptDetailScreen(receipt = sample, onBack = {}, onGetPdf = { null }) }
        }
        composeTestRule.onNodeWithTag("sample_note").assertIsDisplayed()
        composeTestRule.onNodeWithTag("get_pdf").assertDoesNotExist()
    }
}
