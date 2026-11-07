package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReceiptDatesTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private val bought = at(2026, 11, 6, 19, 27)

    @Test
    fun `a 14-day return window lasts until the end of its last day`() {
        val d = ReceiptDates.returnBy(bought, 14, zone)
        assertEquals(at(2026, 11, 21) - 1, d) // 20 Nov, 23:59:59.999
    }

    @Test
    fun `a warranty runs whole years from the purchase day`() {
        val d = ReceiptDates.warrantyUntil(bought, 2, zone)
        assertEquals(at(2028, 11, 7) - 1, d)
    }

    @Test
    fun `a deadline set from a preset shows as that preset, and others as none`() {
        assertEquals(14, ReceiptDates.returnPreset(bought, ReceiptDates.returnBy(bought, 14, zone), zone))
        assertEquals(2, ReceiptDates.warrantyPreset(bought, ReceiptDates.warrantyUntil(bought, 2, zone), zone))
        assertNull(ReceiptDates.returnPreset(bought, at(2026, 12, 1), zone))
        assertNull(ReceiptDates.warrantyPreset(bought, null, zone))
    }

    @Test
    fun `picking another day keeps the time of day, across a clock change`() {
        // 25 Oct 2026 is the end of summer time in Israel; the purchase was in November.
        val picked = LocalDateTime.of(2026, 10, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(at(2026, 10, 1, 19, 27), ReceiptDates.onPickedDay(bought, picked, zone))
    }

    @Test
    fun `the picker opens on the receipt's local day`() {
        // 01:30 on 7 Nov in Israel is still 6 Nov in UTC: the picker must show the 7th.
        val late = at(2026, 11, 7, 1, 30)
        assertEquals(LocalDateTime.of(2026, 11, 7, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), ReceiptDates.asPickerDay(late, zone))
    }
}
