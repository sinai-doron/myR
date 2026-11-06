package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignInAndPdfScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val receipt = CapturedReceipt(
        link = "https://www.shufersal.co.il/r/test",
        senderLabel = "Shufersal",
        timestamp = 1728900000000L,
        clientId = "test-id-1",
    )

    @Test
    fun signIn_buttonCallsBack_andShowsError() {
        var clicks = 0
        composeTestRule.setContent {
            MyReceiptsTheme {
                SignInScreen(signingIn = false, error = "This Google account isn't allowed to use My Receipts.") { clicks++ }
            }
        }

        composeTestRule.onNodeWithText("Sign in with Google").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
        composeTestRule.onNodeWithTag("sign_in_error").assertIsDisplayed()
    }

    @Test
    fun signIn_disabledWhileSigningIn() {
        composeTestRule.setContent {
            MyReceiptsTheme { SignInScreen(signingIn = true, error = null) {} }
        }
        composeTestRule.onNodeWithTag("sign_in").assertIsNotEnabled()
    }

    @Test
    fun receiptWithoutPdf_getPdfAsksTheBackend_andShowsItsError() {
        val answer = CompletableDeferred<String?>()
        val asked = mutableListOf<String>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(receipt = receipt, onBack = {}, onGetPdf = { r ->
                    asked += r.clientId
                    answer.await()
                })
            }
        }

        composeTestRule.onNodeWithTag("get_pdf").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("test-id-1"), asked)
        composeTestRule.onNodeWithTag("get_pdf").assertIsNotEnabled()

        answer.complete("The server couldn't make a PDF of this receipt.")
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCount("The server couldn't make a PDF of this receipt.") > 0
        }
        composeTestRule.onNodeWithText("Open in Browser").assertIsDisplayed()
    }

    @Test
    fun receiptWithPdf_canBeFetchedAgain() {
        val pdf = java.io.File(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "again.pdf",
        ).apply { writeText("%PDF-1.7 test") }
        val asked = mutableListOf<String>()
        composeTestRule.setContent {
            MyReceiptsTheme {
                ReceiptDetailScreen(receipt = receipt.copy(pdfPath = pdf.absolutePath), onBack = {}, onGetPdf = { r ->
                    asked += r.clientId
                    null
                })
            }
        }

        composeTestRule.onNodeWithText("Open PDF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("get_pdf_again").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("test-id-1"), asked)
        pdf.delete()
    }

    @Test
    fun home_showsAccountBadge_andSignOutAsks() {
        var signedOut = false
        composeTestRule.setContent {
            MyReceiptsTheme {
                TestHome(receipts = listOf(receipt), accountEmail = "doron@example.com", onSignOut = { signedOut = true })
            }
        }

        composeTestRule.onNodeWithTag("account").performClick()
        composeTestRule.onNodeWithText("doron@example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign out").performClick()
        assertTrue(signedOut)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
