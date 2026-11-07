package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.data.Origin
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReadWithAiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun scan(): CapturedReceipt {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdf = File(context.cacheDir, "ai-test.pdf").apply { writeBytes("%PDF-1.7\n%%EOF".toByteArray()) }
        return CapturedReceipt(
            link = "",
            senderLabel = "Scanned receipt",
            timestamp = 1728900000000L,
            clientId = "file-ai-test",
            origin = Origin.SCAN,
            pdfPath = pdf.absolutePath,
        )
    }

    @Test
    fun nothingIsSent_untilTheUserAgrees_thenTheReadAmountIsOfferedToCheck() {
        val reads = mutableListOf<String>()
        val saved = mutableListOf<Double?>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan(),
                    onBack = {},
                    onSetAmount = { _, a -> saved += a },
                    onReadWithAi = { r ->
                        reads += r.clientId
                        AiRead.Found(41.9, listOf(R.string.ai_near_limit))
                    },
                )
            }
        }
        composeTestRule.onNodeWithTag("read_with_ai").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Read this receipt with AI?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), reads)

        composeTestRule.onNodeWithTag("read_with_ai").performClick()
        composeTestRule.onNodeWithTag("ai_confirm").performClick()
        composeTestRule.waitUntil(5_000) { reads.isNotEmpty() }
        composeTestRule.onNodeWithTag("amount_ai_note").assertIsDisplayed()
        composeTestRule.onNodeWithText("41.90").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_save").performClick()
        composeTestRule.waitUntil(5_000) { saved.isNotEmpty() }
        assertEquals(listOf<Double?>(41.9), saved)
        composeTestRule.onNodeWithText("You've used most of this month's AI allowance.").assertIsDisplayed()
    }

    @Test
    fun aRefusal_isSaidPlainly_andNoAmountIsOffered() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan(),
                    onBack = {},
                    onSetAmount = { _, _ -> },
                    onReadWithAi = { AiRead.Failed(listOf(R.string.ai_budget_used)) },
                )
            }
        }
        composeTestRule.onNodeWithTag("read_with_ai").performClick()
        composeTestRule.onNodeWithTag("ai_confirm").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTag("ai_message").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("You've used this month's AI allowance. It renews on the 1st.").assertIsDisplayed()
    }

    @Test
    fun notOffered_whenThereIsAnAmountOrNoPdf() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan().copy(totalAmount = 10.0),
                    onBack = {},
                    onSetAmount = { _, _ -> },
                    onReadWithAi = { AiRead.Failed(emptyList()) },
                )
            }
        }
        composeTestRule.onNodeWithTag("read_with_ai").assertDoesNotExist()
    }
}
