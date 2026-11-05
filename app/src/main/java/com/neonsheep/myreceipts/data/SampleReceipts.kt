package com.neonsheep.myreceipts.data

import java.util.concurrent.TimeUnit

/**
 * Made-up receipts anyone can add from the empty home screen, to see amounts, spending and the
 * budget before a real receipt arrives (and for Google's reviewer, who gets no Israeli receipt
 * texts). They are marked as samples everywhere, fetch nothing, and "Remove" deletes them all.
 */
object SampleReceipts {
    const val CLIENT_ID_PREFIX = "sample-"

    // A host that can never resolve (RFC 2606), so a sample link can't reach anyone.
    private const val LINK_PREFIX = "https://sample.invalid/receipt/"

    fun isSample(clientId: String): Boolean = clientId.startsWith(CLIENT_ID_PREFIX)

    private data class Sample(val store: String, val daysAgo: Long, val amount: Double)

    private val SAMPLES = listOf(
        Sample("Shufersal", 0, 286.40),
        Sample("SuperPharm", 1, 74.80),
        Sample("Delek", 2, 312.15),
        Sample("NONOMIMI", 3, 96.00),
        Sample("Shufersal", 6, 412.90),
        Sample("IKEA", 9, 549.00),
        Sample("Decathlon", 15, 189.90),
        Sample("Yochananof", 20, 238.35),
        Sample("Delek", 33, 298.60),
        Sample("Shufersal", 38, 377.25),
    )

    fun build(now: Long = System.currentTimeMillis()): List<CapturedReceiptEntity> =
        SAMPLES.mapIndexed { i, s ->
            val at = now - TimeUnit.DAYS.toMillis(s.daysAgo) - TimeUnit.HOURS.toMillis(i.toLong())
            CapturedReceiptEntity(
                link = "$LINK_PREFIX${i + 1}",
                senderLabel = s.store,
                timestamp = at,
                clientId = "$CLIENT_ID_PREFIX${i + 1}",
                capturedAt = at,
                totalAmount = s.amount,
                // Not a real receipt: nothing to fetch, so the catch-up never picks it up.
                pdfStatus = PdfStatus.SAMPLE,
                totalSource = TotalSource.SAMPLE,
            )
        }
}
