package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.performScrollTo
import com.neonsheep.myreceipts.data.ItemUnit
import com.neonsheep.myreceipts.data.ReceiptArchive
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
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
    fun nothingIsSent_untilTheUserAgrees_thenOnlyWhatIsTickedIsSaved() {
        val reads = mutableListOf<String>()
        val applied = mutableListOf<AiDetails>()
        val receipt = scan().copy(totalAmount = 50.0)
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = receipt,
                    onBack = {},
                    onSetAmount = { _, _ -> },
                    onReadWithAi = { r ->
                        reads += r.clientId
                        AiRead.Read(
                            AiDetails(store = "Aroma", amount = 41.9, invoiceNumber = "7734-21", businessId = "512345678"),
                            listOf(R.string.ai_near_limit),
                        )
                    },
                    onApplyAi = { _, d -> applied += d },
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
        composeTestRule.onNodeWithText("What the AI read").assertIsDisplayed()
        // The amount would replace one the receipt has: shown with it, and not ticked.
        composeTestRule.onNodeWithText("Now: 50.00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_keep_amount").assertIsOff()
        composeTestRule.onNodeWithTag("ai_keep_invoice").assertIsOn()
        // The user corrects the invoice number and leaves out the tax ID.
        composeTestRule.onNodeWithTag("ai_value_invoice").performTextReplacement("7734-22")
        composeTestRule.onNodeWithTag("ai_keep_business_id").performClick()
        composeTestRule.onNodeWithTag("ai_save").performClick()
        composeTestRule.waitUntil(5_000) { applied.isNotEmpty() }
        assertEquals(listOf(AiDetails(store = "Aroma", invoiceNumber = "7734-22")), applied)
        composeTestRule.onNodeWithText("You've used most of this month's AI allowance.").assertIsDisplayed()
    }

    @Test
    fun aFileJustAdded_asksFirst_andNotNow_opensTheEditScreen_withNothingSent() {
        val reads = mutableListOf<String>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan(),
                    onBack = {},
                    askAiFirst = true,
                    onEdit = { _, _ -> },
                    onReadWithAi = { r -> reads += r.clientId; AiRead.NotFound(emptyList()) },
                    onApplyAi = { _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithText("Read this receipt with AI?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_not_now").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("edit_store").assertIsDisplayed()
        assertEquals(emptyList<String>(), reads)
    }

    @Test
    fun aFileJustAdded_isReadOnlyOnceTheUserAgrees() {
        val applied = mutableListOf<AiDetails>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan(),
                    onBack = {},
                    askAiFirst = true,
                    onEdit = { _, _ -> },
                    onReadWithAi = { AiRead.Read(AiDetails(store = "Aroma", amount = 41.9), emptyList()) },
                    onApplyAi = { _, d -> applied += d },
                )
            }
        }
        composeTestRule.onNodeWithTag("ai_confirm").performClick()
        composeTestRule.onNodeWithText("What the AI read").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_save").performClick()
        composeTestRule.waitUntil(5_000) { applied.isNotEmpty() }
        assertEquals(listOf(AiDetails(store = "Aroma", amount = 41.9)), applied)
        composeTestRule.onNodeWithTag("edit_store").assertDoesNotExist()
    }

    @Test
    fun withoutAi_aFileJustAdded_opensTheEditScreen_asBefore() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(receipt = scan(), onBack = {}, askAiFirst = true, onEdit = { _, _ -> })
            }
        }
        composeTestRule.onNodeWithTag("edit_store").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read this receipt with AI?").assertDoesNotExist()
    }

    @Test
    fun aReceiptFromAbroad_isOfferedInItsCurrency_andKeptWithItsShekels() {
        val applied = mutableListOf<AiDetails>()
        val read = AiDetails(
            store = "Żabka",
            amount = 98.37,
            currency = "PLN",
            originalTotal = 123.45,
            ilsRate = 0.796877,
            rateDate = "2026-11-02",
            vat = 23.08,
        )
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan(),
                    onBack = {},
                    onReadWithAi = { AiRead.Read(read, emptyList()) },
                    onApplyAi = { _, d -> applied += d },
                )
            }
        }
        composeTestRule.onNodeWithTag("read_with_ai").performClick()
        composeTestRule.onNodeWithTag("ai_confirm").performClick()
        composeTestRule.onNodeWithText("Amount paid (PLN)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_note_amount").assertIsDisplayed()
        composeTestRule.onNodeWithText("VAT (PLN)").assertIsDisplayed()
        // The user corrects the printed total: its shekels follow at the same rate.
        composeTestRule.onNodeWithTag("ai_value_amount").performTextReplacement("100")
        composeTestRule.onNodeWithTag("ai_save").performClick()
        composeTestRule.waitUntil(5_000) { applied.isNotEmpty() }
        assertEquals(
            listOf(
                AiDetails(
                    store = "Żabka",
                    amount = 79.69,
                    currency = "PLN",
                    originalTotal = 100.0,
                    ilsRate = 0.796877,
                    rateDate = "2026-11-02",
                    vat = 23.08,
                ),
            ),
            applied,
        )
    }

    @Test
    fun aReceiptFromAbroad_showsItsOwnCurrency_andItsShekelsBelow() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan().copy(
                        totalAmount = 98.37,
                        currency = "PLN",
                        originalTotal = 123.45,
                        ilsRate = 0.796877,
                        rateDate = "2026-11-02",
                        vatAmount = 23.08,
                    ),
                    onBack = {},
                    items = listOf(ReceiptArchive.Item("Chleb", quantity = 2.0, unitPrice = 4.5, price = 9.0)),
                )
            }
        }
        val amount = composeTestRule.onNodeWithTag("amount").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assert(amount.contains("123.45") && !amount.contains("₪")) { amount }
        composeTestRule.onNodeWithTag("amount_ils").assertIsDisplayed()
        val note = composeTestRule.onNodeWithTag("amount_ils").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assert(note.contains("98.37") && note.contains("ECB")) { note }
        composeTestRule.onNodeWithTag("items_toggle").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Chleb").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("item_row").fetchSemanticsNodes().forEach { node ->
            val text = node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString()
            assert(!text.contains("₪")) { text }
        }
    }

    @Test
    fun theBranchAndItems_areOffered_andKept() {
        val applied = mutableListOf<AiDetails>()
        val items = listOf(
            ReceiptArchive.Item("צלי כתף טרי", quantity = 2.126, unit = ItemUnit.KG, unitPrice = 99.9, price = 212.39, discount = 21.26),
            ReceiptArchive.Item("מולר חלבון קפה", price = 11.9),
        )
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan(),
                    onBack = {},
                    onReadWithAi = {
                        AiRead.Read(AiDetails(branch = "249", storePhone = "09-8357522", items = items), emptyList())
                    },
                    onApplyAi = { _, d -> applied += d },
                )
            }
        }
        composeTestRule.onNodeWithTag("read_with_ai").performClick()
        composeTestRule.onNodeWithTag("ai_confirm").performClick()
        composeTestRule.onNodeWithText("2 items").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_keep_items").assertIsOn()
        composeTestRule.onNodeWithTag("ai_save").performClick()
        composeTestRule.waitUntil(5_000) { applied.isNotEmpty() }
        assertEquals(listOf(AiDetails(branch = "249", storePhone = "09-8357522", items = items)), applied)
    }

    @Test
    fun keptItems_showFolded_untilOpened() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan().copy(branch = "249", storePhone = "09-8357522"),
                    onBack = {},
                    items = listOf(
                        ReceiptArchive.Item("צלי כתף טרי", quantity = 2.126, unit = ItemUnit.KG, unitPrice = 99.9, price = 212.39, discount = 21.26),
                    ),
                )
            }
        }
        composeTestRule.onNodeWithTag("detail_phone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_row").assertDoesNotExist()
        composeTestRule.onNodeWithTag("items_toggle").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("צלי כתף טרי").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2.126 kg × ₪99.90").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nothingNew_isSaid_andNoDialogOpens() {
        val receipt = scan().copy(invoiceNumber = "7734-21")
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = receipt,
                    onBack = {},
                    onReadWithAi = { AiRead.Read(AiDetails(invoiceNumber = "7734-21"), emptyList()) },
                    onApplyAi = { _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithTag("read_with_ai").performClick()
        composeTestRule.onNodeWithTag("ai_confirm").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTag("ai_message").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The AI found nothing new on this receipt.").assertIsDisplayed()
        composeTestRule.onNodeWithText("What the AI read").assertDoesNotExist()
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
                    onApplyAi = { _, _ -> },
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
    fun notOffered_withoutAPdf() {
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(
                    receipt = scan().copy(pdfPath = null),
                    onBack = {},
                    onSetAmount = { _, _ -> },
                    onReadWithAi = { AiRead.Failed(emptyList()) },
                    onApplyAi = { _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithTag("read_with_ai").assertDoesNotExist()
    }
}
