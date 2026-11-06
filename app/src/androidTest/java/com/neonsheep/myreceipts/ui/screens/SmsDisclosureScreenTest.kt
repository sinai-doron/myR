package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SmsDisclosureScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun disclosure_saysWhatIsReadKeptAndSent_andBothButtonsCallBack() {
        var continued = 0
        var declined = 0
        composeTestRule.setContent {
            MyReceiptsTheme { SmsDisclosureScreen(onContinue = { continued++ }, onNotNow = { declined++ }) }
        }

        for (heading in listOf("What it reads", "What it keeps", "What it ignores", "What it sends", "It never sends texts")) {
            composeTestRule.onNodeWithText(heading).performScrollTo().assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("sms_continue").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("sms_not_now").performScrollTo().performClick()
        assertEquals(1, continued)
        assertEquals(1, declined)
    }

    @Test
    fun receiptList_showsCaptureOffBanner_onlyWhileCaptureIsOff() {
        var turnOn = 0
        composeTestRule.setContent {
            MyReceiptsTheme { TestHome(receipts = emptyList(), captureOff = true, onTurnOnCapture = { turnOn++ }) }
        }
        composeTestRule.onNodeWithTag("capture_turn_on").performClick()
        assertEquals(1, turnOn)
    }

    @Test
    fun receiptList_hasNoBanner_whenCaptureIsOn() {
        composeTestRule.setContent {
            MyReceiptsTheme { TestHome(receipts = emptyList(), captureOff = false) }
        }
        composeTestRule.onNodeWithTag("capture_off").assertDoesNotExist()
    }
}
