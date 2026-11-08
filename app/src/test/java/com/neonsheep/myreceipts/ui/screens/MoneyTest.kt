package com.neonsheep.myreceipts.ui.screens

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MoneyTest {
    private val saved = Locale.getDefault()

    @Before
    fun english() = Locale.setDefault(Locale.US)

    @After
    fun restore() = Locale.setDefault(saved)

    @Test
    fun `an amount shows in the receipt's own currency, shekels when none`() {
        assertEquals(formatAmount(12.5), formatMoney(12.5, null))
        assertEquals(formatAmount(12.5), formatMoney(12.5, "ILS"))
        assertTrue(formatMoney(123.45, "PLN").contains("123.45"))
        assertTrue(formatMoney(123.45, "PLN").contains("PLN"))
        assertEquals("€9.99", formatMoney(9.99, "EUR"))
    }

    @Test
    fun `a code the phone doesn't know is written out`() {
        assertEquals("5.00 XQQ", formatMoney(5.0, "XQQ"))
    }

    @Test
    fun `conversion rounds to the agora`() {
        assertEquals(98.37, toShekels(123.45, 0.796877), 0.0)
        assertEquals(0.0, toShekels(0.0, 3.5), 0.0)
    }
}
