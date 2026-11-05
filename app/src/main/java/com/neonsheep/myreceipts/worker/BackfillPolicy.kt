package com.neonsheep.myreceipts.worker

import com.neonsheep.myreceipts.net.BackendException
import java.time.LocalDate

/**
 * How the background catch-up of old receipts paces itself, so a new install with hundreds of
 * old receipt texts never floods the backend. Pure rules, no Android, so they can be tested.
 *
 * - At most [BATCH] receipts per run, [GAP_SECONDS] apart, then [NEXT_BATCH_MINUTES] of rest.
 * - At most [DAILY_CAP] a day on this phone.
 * - Backs off when the backend says it's busy (429) or can't be reached, and stops when signed out.
 */
object BackfillPolicy {
    const val BATCH = 10
    const val GAP_SECONDS = 15L
    const val NEXT_BATCH_MINUTES = 15L
    const val DAILY_CAP = 60
    const val BUSY_MIN_MINUTES = 15L
    const val DOWN_MINUTES = 60L

    /** What the run does after an attempt. */
    sealed interface Next {
        /** Carry on with the next receipt in this run. */
        data object Continue : Next

        /** End this run and come back after [minutes]. */
        data class Pause(val minutes: Long) : Next

        /** End and don't reschedule: signed out. Signing in starts it again. */
        data object Stop : Next
    }

    /** The receipt's verdict to store, or null to leave it untouched (it will be tried again). */
    fun verdictFor(error: Throwable?): String? = when (error) {
        null -> null
        is BackendException.Http -> when {
            error.code == 410 -> com.neonsheep.myreceipts.data.PdfStatus.GONE
            error.code == 400 -> com.neonsheep.myreceipts.data.PdfStatus.UNSUPPORTED
            error.code in 500..599 -> com.neonsheep.myreceipts.data.PdfStatus.FAILED
            else -> null
        }
        else -> null
    }

    fun nextAfter(error: Throwable?): Next = when (error) {
        null -> Next.Continue
        is BackendException.Unauthorized -> Next.Stop
        is BackendException.RateLimited -> Next.Pause(maxOf(BUSY_MIN_MINUTES, (error.retryAfterSeconds + 59) / 60))
        is BackendException.Network -> Next.Pause(DOWN_MINUTES)
        is BackendException.Http -> when (error.code) {
            // The backend is in trouble: one receipt is marked failed, and the rest wait.
            502, 503, 504 -> Next.Pause(DOWN_MINUTES)
            else -> Next.Continue
        }
        else -> Next.Pause(DOWN_MINUTES)
    }

    /** Today's count, reset when the day changes. */
    data class Budget(val day: LocalDate, val used: Int) {
        fun forToday(today: LocalDate): Budget = if (today == day) this else Budget(today, 0)
        val remaining: Int get() = (DAILY_CAP - used).coerceAtLeast(0)
        fun spend(): Budget = copy(used = used + 1)
    }

    /** Minutes until tomorrow's budget, rounded up, when today's is spent. */
    fun minutesUntilTomorrow(nowMinuteOfDay: Int): Long = (24 * 60 - nowMinuteOfDay).toLong().coerceAtLeast(1)
}
