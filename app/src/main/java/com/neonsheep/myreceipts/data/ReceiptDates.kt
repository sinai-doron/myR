package com.neonsheep.myreceipts.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Dates the user sets on a receipt: the purchase date and the return and warranty deadlines. */
object ReceiptDates {
    /** Return windows offered, in days. 14 is the Israeli consumer-protection default for most goods. */
    val RETURN_DAYS = listOf(14, 30)

    /** Warranty lengths offered, in years. */
    val WARRANTY_YEARS = listOf(1, 2, 3)

    /** The last moment of the day [days] after [boughtAt]: a deadline lasts the whole of its last day. */
    fun returnBy(boughtAt: Long, days: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        endOfDay(day(boughtAt, zone).plusDays(days.toLong()), zone)

    fun warrantyUntil(boughtAt: Long, years: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        endOfDay(day(boughtAt, zone).plusYears(years.toLong()), zone)

    /** Which preset [deadline] matches, if any, so the screen can show it selected. */
    fun returnPreset(boughtAt: Long, deadline: Long?, zone: ZoneId = ZoneId.systemDefault()): Int? =
        deadline?.let { d -> RETURN_DAYS.firstOrNull { returnBy(boughtAt, it, zone) == d } }

    fun warrantyPreset(boughtAt: Long, deadline: Long?, zone: ZoneId = ZoneId.systemDefault()): Int? =
        deadline?.let { d -> WARRANTY_YEARS.firstOrNull { warrantyUntil(boughtAt, it, zone) == d } }

    /**
     * [original] moved to the day a date picker chose, keeping its time of day. A Material date picker
     * reports the chosen day as midnight UTC.
     */
    fun onPickedDay(original: Long, pickedUtcMidnight: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val day = Instant.ofEpochMilli(pickedUtcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(original).atZone(zone).toLocalTime()
        return day.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** [at]'s day as a date picker wants it: midnight UTC of the local day. */
    fun asPickerDay(at: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        day(at, zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    private fun endOfDay(day: LocalDate, zone: ZoneId): Long =
        day.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
}
