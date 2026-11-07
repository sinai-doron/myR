package com.neonsheep.myreceipts.data

import java.time.Instant
import java.time.ZoneId

/** A deadline set on a receipt, and how long before it ends its reminder comes. */
enum class Deadline(val daysBefore: Long) {
    /** Two days, to get back to the store. */
    RETURN(2),

    /** A month, to notice a fault and claim it. */
    WARRANTY(30),
    ;

    fun of(receipt: CapturedReceiptEntity): Long? = when (this) {
        RETURN -> receipt.returnBy
        WARRANTY -> receipt.warrantyUntil
    }
}

/** When a deadline's reminder comes. Worked out on the phone; nothing is sent anywhere. */
object Reminders {
    /** The hour of the day reminders come at: a time to act on them, not to be woken by them. */
    const val HOUR = 10

    /**
     * The moment to remind of [deadline]: [Deadline.daysBefore] days before its last day, at [HOUR].
     * Null when that moment has passed: the user is setting or looking at the date right now.
     */
    fun remindAt(kind: Deadline, deadline: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val day = Instant.ofEpochMilli(deadline).atZone(zone).toLocalDate().minusDays(kind.daysBefore)
        val at = day.atTime(HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        return at.takeIf { it > now }
    }

    /** Each deadline of [receipt] with the moment to remind of it, or null for none (cleared or passed). */
    fun plan(receipt: CapturedReceiptEntity, now: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Deadline, Long?> =
        Deadline.entries.associateWith { kind -> kind.of(receipt)?.let { remindAt(kind, it, now, zone) } }
}
