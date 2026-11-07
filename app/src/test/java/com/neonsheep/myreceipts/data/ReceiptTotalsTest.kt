package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Made-up receipts, laid out the way Android reads each store's PDF. */
class ReceiptTotalsTest {
    private fun total(vararg lines: String) = ReceiptTotals.fromText(lines.joinToString("\n"))

    @Test
    fun weezmoPharmacy_totalAfterTheLabel_notTheColumnHeader() {
        assertEquals(
            89.80,
            total(
                "תיאור פריט מק\"ט מחיר ליחידה כמות לתשלום",
                "מברשת שיניים 7290000000001 ₪59.90 1 ₪59.90",
                "משחת שיניים 7290000000002 ₪29.90 1 ₪29.90",
                "אמצעי תשלום כרטיס אשראי - ₪89.8",
                "סה\"כ ללא מע\"מ ₪76.10",
                "סה\"כ לתשלום ₪89.80",
            )!!,
            0.001,
        )
    }

    @Test
    fun weezmoSupermarket_totalWithIlsBeforeTheNumber() {
        assertEquals(
            164.73,
            total("1 7290000000003 פריכיות 12.9 1 ILS12.9", "סה\"כ: ILS164.73", "סה\"כ ללא מע\"מ: ILS140.32")!!,
            0.001,
        )
    }

    @Test
    fun shekelSignAfterTheNumber_andShekelWordBeforeIt() {
        assertEquals(149.0, total("סכום לתשלום 149.00 ₪")!!, 0.001)
        assertEquals(730.0, total("סה\"כ תשלום ש\"ח 730.00 ")!!, 0.001)
    }

    @Test
    fun tabit_andAThousandsSeparator() {
        assertEquals(50.0, total("סלט 38.00", "סה\"כ לתשלום 50.00", "שולם באשראי 50.00")!!, 0.001)
        assertEquals(1428.0, total("סה\"כ לתשלום ₪1,428.00")!!, 0.001)
    }

    @Test
    fun aLabelWithAColon() {
        assertEquals(124.40, total("לתשלום : 124.40 ", "סה\"כ 4 פריטים,ו 4 שורות מוצרים.")!!, 0.001)
    }

    @Test
    fun directionMarksAndGershayimAreIgnored() {
        assertEquals(18.0, total("‏סה״כ לתשלום‎ 18.00")!!, 0.001)
    }

    @Test
    fun theStrongestLabelWins() {
        assertEquals(185.10, total("סה\"כ: ₪999.00", "סכום לתשלום ₪185.10")!!, 0.001)
    }

    @Test
    fun noAmount_whenLabelsDisagree_orThereIsNone() {
        assertNull(total("סה\"כ לתשלום ₪47.80", "סה\"כ לתשלום ₪50.00"))
        assertNull(total("כרטיס רגיל 48.5 ש\"ח (0) כרטיס אשראי"))
        assertNull(total("שם פריט סה\"כ", "סה\"כ 4 פריטים"))
        assertNull(total(""))
    }

    // A furniture order the way Android reads it: the labels in one column, the amounts in another.
    private val orderInColumns = arrayOf(
        "1 1234 שולחן אוכל ₪ 1,990 20 ₪ 2,490 1",
        "2 5678 הובלה והרכבה 250 ש\"ח 250 ₪ 250 ₪",
        "הנחה % 0.00 0 ₪",
        "סה\"כ לתשלום",
        "סה\"כ אחרי הנחה",
        "₪ 2,240",
        "מע\"מ % 18 342 ₪",
        "₪ 1,898",
    )

    @Test
    fun amountsInTheirOwnColumn_theOneThatIsAnAmountPlusItsVat() {
        // 1,898 + 18% VAT (342) = 2,240; the 1,898 after the other label adds up to nothing.
        assertEquals(2240.0, total(*orderInColumns)!!, 0.001)
    }

    @Test
    fun amountsInTheirOwnColumn_atLastYearsVat() {
        assertEquals(117.0, total("סכום לתשלום", "₪ 117", "₪ 100", "מע\"מ 17% ₪ 17")!!, 0.001)
    }

    @Test
    fun amountsInTheirOwnColumn_nothingWhenTheyDontAddUp() {
        // No VAT line: the column could be in either order.
        assertNull(total("סה\"כ לתשלום", "סה\"כ אחרי הנחה", "₪ 2,240", "₪ 1,898"))
        // The VAT is there, but not on an amount that makes the one after the label.
        assertNull(total("סה\"כ לתשלום", "₪ 500", "מע\"מ ₪ 90", "₪ 400"))
        // Too far from the label.
        assertNull(total("סה\"כ לתשלום", "א", "ב", "ג", "ד", "₪ 2,240", "₪ 1,898", "₪ 342"))
    }
}
