package com.neonsheep.myreceipts.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The reports kept on the phone until sent: what's kept, how many, and that the switch stops it all. */
@RunWith(AndroidJUnit4::class)
class FailureReportsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        FailureReports.init(context)
        // Off then on: an empty queue, reports on.
        FailureReports.setEnabled(context, false)
        FailureReports.setEnabled(context, true)
    }

    @After
    fun tearDown() {
        FailureReports.setEnabled(context, false)
        FailureReports.setEnabled(context, true)
    }

    @Test
    fun eachKind_isKept_withoutTheLinkOrText() {
        FailureReports.unsupportedSite("https://receipts.newstore.co.il/r/canary-1?t=2", "NEWSTORE")
        FailureReports.totalNotFound("https://public.pairzon.com/1267/canary-2")
        FailureReports.recordCrash(IllegalStateException("canary-3 https://wee.ai/r/x"))

        val queued = FailureReports.queued(context)
        assertEquals(
            listOf(
                FailureReport.UnsupportedSite("receipts.newstore.co.il", "NEWSTORE"),
                FailureReport.TotalNotFound("public.pairzon.com"),
            ),
            queued.take(2),
        )
        val crash = queued[2] as FailureReport.Crash
        assertEquals("java.lang.IllegalStateException", crash.exception)
        assertEquals(android.os.Build.VERSION.SDK_INT, crash.androidVersion)
        val file = java.io.File(context.filesDir, "failure-reports.jsonl").readText()
        assertTrue(!file.contains("canary"))
    }

    @Test
    fun onlyTheNewest50_areKept_andSentOnesAreDropped() {
        repeat(60) { i -> FailureReports.totalNotFound("https://site$i.co.il/r") }
        val queued = FailureReports.queued(context)
        assertEquals(FailureReports.QUEUE_MAX, queued.size)
        assertEquals(FailureReport.TotalNotFound("site10.co.il"), queued.first())

        FailureReports.drop(context, 20)
        assertEquals(FailureReport.TotalNotFound("site30.co.il"), FailureReports.queued(context).first())
    }

    @Test
    fun turnedOff_nothingIsKept_andWhatWaitedIsForgotten() {
        FailureReports.totalNotFound("https://tbit.be/x")
        FailureReports.setEnabled(context, false)
        assertTrue(FailureReports.queued(context).isEmpty())

        FailureReports.totalNotFound("https://tbit.be/y")
        FailureReports.recordCrash(IllegalStateException())
        assertTrue(FailureReports.queued(context).isEmpty())
    }

    @Test
    fun aFileOrScan_hasNoSite_soIsNotReported() {
        FailureReports.totalNotFound("")
        FailureReports.unsupportedSite("https://10.0.0.1/r", "X")
        assertTrue(FailureReports.queued(context).isEmpty())
    }
}
