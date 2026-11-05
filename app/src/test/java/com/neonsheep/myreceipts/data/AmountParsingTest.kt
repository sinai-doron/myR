package com.neonsheep.myreceipts.data

import com.neonsheep.myreceipts.net.BackendApi
import com.neonsheep.myreceipts.ui.screens.parseTypedAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountParsingTest {
    @Test
    fun theBackendsHeader_onlyAPlainPositiveAmount() {
        assertEquals(113.58, BackendApi.Totals.parse("113.58")!!, 0.001)
        assertEquals(18.0, BackendApi.Totals.parse(" 18.00 ")!!, 0.001)
        assertNull(BackendApi.Totals.parse(null))
        assertNull(BackendApi.Totals.parse(""))
        assertNull(BackendApi.Totals.parse("0.00"))
        assertNull(BackendApi.Totals.parse("-5.00"))
        assertNull(BackendApi.Totals.parse("1,428.00"))
        assertNull(BackendApi.Totals.parse("NaN"))
    }

    @Test
    fun aTypedAmount_dotOrComma_shekelSignAllowed_emptyMeansNone() {
        assertEquals(89.8, parseTypedAmount("89.80").getOrThrow()!!, 0.001)
        assertEquals(89.8, parseTypedAmount("89,8").getOrThrow()!!, 0.001)
        assertEquals(12.0, parseTypedAmount("₪ 12").getOrThrow()!!, 0.001)
        assertNull(parseTypedAmount("  ").getOrThrow())
        assertTrue(parseTypedAmount("12.345").isFailure)
        assertTrue(parseTypedAmount("abc").isFailure)
    }
}
