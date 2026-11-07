package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RemindersTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // Bought 6 Nov 2026 at 19:27: 14 days to return, 2 years of warranty.
    private val bought = at(2026, 11, 6, 19, 27)
    private val returnBy = ReceiptDates.returnBy(bought, 14, zone)
    private val warrantyUntil = ReceiptDates.warrantyUntil(bought, 2, zone)

    @Test
    fun returnReminder_twoDaysBeforeItsLastDay_atTen() {
        assertEquals(at(2026, 11, 18, 10), Reminders.remindAt(Deadline.RETURN, returnBy, now = bought, zone))
    }

    @Test
    fun warrantyReminder_aMonthBefore_atTen() {
        assertEquals(at(2028, 10, 7, 10), Reminders.remindAt(Deadline.WARRANTY, warrantyUntil, now = bought, zone))
    }

    @Test
    fun noReminder_onceItsMomentHasPassed() {
        // A deadline set on the 18th at 10:01: the user is looking at it now.
        assertNull(Reminders.remindAt(Deadline.RETURN, returnBy, now = at(2026, 11, 18, 10, 1), zone))
        assertEquals(
            at(2026, 11, 18, 10),
            Reminders.remindAt(Deadline.RETURN, returnBy, now = at(2026, 11, 18, 9, 59), zone),
        )
    }

    @Test
    fun plan_hasEachDeadline_andNullForOneNotSet() {
        val receipt = CapturedReceiptEntity(
            link = "https://example.test/r", senderLabel = "IKEA", timestamp = bought, clientId = "c1",
            capturedAt = bought, returnBy = returnBy,
        )
        assertEquals(
            mapOf(Deadline.RETURN to at(2026, 11, 18, 10), Deadline.WARRANTY to null),
            Reminders.plan(receipt, now = bought, zone),
        )
    }
}
