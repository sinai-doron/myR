package com.neonsheep.myreceipts.worker

import com.neonsheep.myreceipts.data.PdfStatus
import com.neonsheep.myreceipts.net.BackendException
import com.neonsheep.myreceipts.worker.BackfillPolicy.Next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class BackfillPolicyTest {

    @Test
    fun `the daily budget caps a phone and resets the next day`() {
        val day = LocalDate.of(2026, 10, 31)
        var budget = BackfillPolicy.Budget(day, 0)
        repeat(BackfillPolicy.DAILY_CAP) { budget = budget.spend() }
        assertEquals(0, budget.remaining)
        assertEquals(0, budget.forToday(day).remaining)
        assertEquals(BackfillPolicy.DAILY_CAP, budget.forToday(day.plusDays(1)).remaining)
    }

    @Test
    fun `success and a refused receipt carry on with the next one`() {
        assertEquals(Next.Continue, BackfillPolicy.nextAfter(null))
        assertEquals(Next.Continue, BackfillPolicy.nextAfter(BackendException.Http(410)))
        assertEquals(Next.Continue, BackfillPolicy.nextAfter(BackendException.Http(400)))
    }

    @Test
    fun `a busy backend is left alone for at least 15 minutes, or as long as it asks`() {
        assertEquals(Next.Pause(15), BackfillPolicy.nextAfter(BackendException.RateLimited(30)))
        assertEquals(Next.Pause(60), BackfillPolicy.nextAfter(BackendException.RateLimited(3600)))
    }

    @Test
    fun `an unreachable or failing backend pauses an hour, and signing out stops`() {
        assertEquals(Next.Pause(60), BackfillPolicy.nextAfter(BackendException.Network(IOException("x"))))
        assertEquals(Next.Pause(60), BackfillPolicy.nextAfter(BackendException.Http(502)))
        assertEquals(Next.Stop, BackfillPolicy.nextAfter(BackendException.Unauthorized))
    }

    @Test
    fun `verdicts stop a receipt being retried on its own, except for transient trouble`() {
        assertEquals(PdfStatus.GONE, BackfillPolicy.verdictFor(BackendException.Http(410)))
        assertEquals(PdfStatus.UNSUPPORTED, BackfillPolicy.verdictFor(BackendException.Http(400)))
        assertEquals(PdfStatus.FAILED, BackfillPolicy.verdictFor(BackendException.Http(502)))
        assertNull(BackfillPolicy.verdictFor(BackendException.RateLimited(60)))
        assertNull(BackfillPolicy.verdictFor(BackendException.Network(IOException("x"))))
        assertNull(BackfillPolicy.verdictFor(null))
    }
}
