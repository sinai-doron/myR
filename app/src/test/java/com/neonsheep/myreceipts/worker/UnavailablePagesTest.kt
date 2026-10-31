package com.neonsheep.myreceipts.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnavailablePagesTest {
    @Test
    fun `Shufersal's expired-link page is recognised, as WebView returns it`() {
        assertTrue(isUnavailablePage("\"לקוח יקר,\\nהחשבונית בעיבוד,\\nתהיה זמינה עוד דקה...\""))
        assertTrue(isUnavailablePage("\"\\u05d4\\u05d7\\u05e9\\u05d1\\u05d5\\u05e0\\u05d9\\u05ea \\u05d1\\u05e2\\u05d9\\u05d1\\u05d5\\u05d3\""))
    }

    @Test
    fun `Pairzon's expired-token page is recognised`() {
        assertTrue(isUnavailablePage("\"תאריך סכום לצפייה תוקף הכניסה שלך פג OK לכניסה מחדש\""))
    }

    @Test
    fun `a real receipt or an empty answer is not`() {
        assertFalse(isUnavailablePage("\"שופרסל בע\\\"מ\\nחשבונית / קבלה\\nלתשלום 121.81\""))
        assertFalse(isUnavailablePage("null"))
        assertFalse(isUnavailablePage(null))
    }
}
