package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FailureReportTest {
    private fun crash(): Throwable = try {
        throw IllegalStateException(
            "couldn't fetch https://wee.ai/r/canary-777 for 0501234567, ₪412.50",
            java.io.IOException("canary-888"),
        )
    } catch (e: Throwable) {
        e
    }

    @Test
    fun `a crash keeps its classes and code locations, never its message`() {
        val report = FailureReport.crashOf(crash(), "0.2.0", 37, "Pixel 10")
        assertEquals("java.lang.IllegalStateException", report.exception)
        assertEquals("java.io.IOException", report.cause)
        assertTrue(report.frames.isNotEmpty())
        assertTrue(report.frames.size <= FailureReport.FRAMES_MAX)
        assertTrue(report.frames.first(), report.frames.first().startsWith("com.neonsheep.myreceipts.data.FailureReportTest.crash(FailureReportTest.kt:"))
        val json = FailureReport.batch(listOf(report))
        for (canary in listOf("canary", "wee.ai", "0501234567", "412.50", "couldn't")) {
            assertFalse("$canary in $json", json.contains(canary))
        }
    }

    @Test
    fun `odd model and version names are trimmed to what the server takes`() {
        val report = FailureReport.crashOf(crash(), "0.2.0 (debug) / beta", 37, "Pixel 10 Pro XL™ <x>")
        assertEquals("0.2.0debugbeta", report.appVersion)
        assertEquals("Pixel 10 Pro XL x", report.model)
    }

    @Test
    fun `a site is its lower-case hostname, never an address or a path`() {
        assertEquals("public.pairzon.com", FailureReport.domainOf("https://Public.Pairzon.com/1267/42fVA?x=1"))
        assertEquals("tbit.be", FailureReport.domainOf("https://tbit.be/q9tv8X"))
        assertNull(FailureReport.domainOf("https://10.0.0.1/r/1"))
        assertNull(FailureReport.domainOf("not a link"))
        assertNull(FailureReport.domainOf(""))
    }

    @Test
    fun `a sender keeps its name, and only its name`() {
        assertEquals("KINGKONG", FailureReport.senderOf("KINGKONG"))
        assertEquals("קינג קונג", FailureReport.senderOf("קינג קונג"))
        assertEquals("+972501234567", FailureReport.senderOf("+972501234567"))
        assertEquals("unknown", FailureReport.senderOf("///"))
        assertEquals(20, FailureReport.senderOf("A".repeat(40)).length)
    }

    @Test
    fun `a batch is what the server reads, at most 20`() {
        val reports = List(25) { FailureReport.TotalNotFound("tbit.be") }
        val json = FailureReport.batch(reports)
        assertTrue(json.startsWith("""{"reports":[{"kind":"total_not_found","domain":"tbit.be"}"""))
        assertEquals(20, Regex("total_not_found").findAll(json).count())
        // An absent cause is left out, not sent as null.
        assertFalse(FailureReport.batch(listOf(FailureReport.crashOf(IllegalStateException(), "1", 37, "x"))).contains("null"))

        // A sample of every kind, for checking against the backend's own schema.
        File("build/failure-report-sample.json").writeText(
            FailureReport.batch(
                listOf(
                    FailureReport.crashOf(crash(), "0.2.0", 37, "Pixel 10"),
                    FailureReport.UnsupportedSite("receipts.newstore.co.il", FailureReport.senderOf("קינג קונג")),
                    FailureReport.TotalNotFound("public.pairzon.com"),
                ),
            ),
        )
    }
}
