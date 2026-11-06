package com.neonsheep.myreceipts.worker

import android.os.Build
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download a receipt SMS starts. Ordinary work waited until the app was next opened on a phone
 * where Android had put the app in its "rare" standby bucket (52 minutes to 2 hours, seen on a
 * Pixel 10), so on Android 12+ it is expedited.
 */
class ReceiptDownloadRequestTest {

    @Test
    fun `runs right away on Android 12 and later`() {
        val spec = ReceiptDownloadWorker.request("https://example.com/r/1", "id-1", sdk = Build.VERSION_CODES.S).workSpec
        assertTrue(spec.expedited)
        // Out of quota it still runs, as ordinary work, rather than being dropped.
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, spec.outOfQuotaPolicy)
    }

    @Test
    fun `is ordinary work before Android 12, which would need a foreground notification`() {
        val spec = ReceiptDownloadWorker.request("https://example.com/r/1", "id-1", sdk = Build.VERSION_CODES.R).workSpec
        assertFalse(spec.expedited)
    }

    @Test
    fun `still waits for a network and carries the receipt`() {
        val spec = ReceiptDownloadWorker.request("https://example.com/r/1", "id-1", sdk = Build.VERSION_CODES.S).workSpec
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals("id-1", spec.input.getString("clientId"))
    }
}
