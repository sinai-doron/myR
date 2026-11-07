package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesTest {
    private val min = 60_000L
    private fun c(id: String, store: String, amount: Double?, at: Long, notDuplicate: Boolean = false) =
        Duplicates.Candidate(id, store, amount, at, notDuplicate)

    @Test
    fun sameStoreAndAmount_withinTenMinutes_isOneGroup_oldestFirst() {
        val groups = Duplicates.groups(
            listOf(c("b", "Shufersal", 286.40, 5 * min), c("a", "shufersal ", 286.4, 0), c("c", "Shufersal", 286.40, 30 * min)),
        )
        assertEquals(listOf(listOf("a", "b")), groups)
    }

    @Test
    fun aChainOfReceiptsEachWithinTheWindow_isOneGroup() {
        val groups = Duplicates.groups(
            listOf(c("a", "Delek", 100.0, 0), c("b", "Delek", 100.0, 9 * min), c("c", "Delek", 100.0, 18 * min)),
        )
        assertEquals(listOf(listOf("a", "b", "c")), groups)
    }

    @Test
    fun notFlagged_whenTheAmountOrStoreDiffers_noAmount_orTheUserSaidSo() {
        assertEquals(
            emptyList<List<String>>(),
            Duplicates.groups(
                listOf(
                    c("a", "Delek", 100.0, 0), c("b", "Delek", 100.01, min),
                    c("c", "IKEA", null, 0), c("d", "IKEA", null, min),
                    c("e", "Aroma", 38.0, 0), c("f", "Aroma", 38.0, min, notDuplicate = true),
                    c("g", "Zara", 99.0, 0), c("h", "H&M", 99.0, min),
                ),
            ),
        )
    }

    @Test
    fun theNewestGroupComesFirst() {
        val groups = Duplicates.groups(
            listOf(c("a", "Delek", 1.0, 0), c("b", "Delek", 1.0, min), c("x", "Aroma", 2.0, 60 * min), c("y", "Aroma", 2.0, 61 * min)),
        )
        assertEquals(listOf(listOf("x", "y"), listOf("a", "b")), groups)
    }

    private fun r(id: String, at: Long, pdf: String? = null, amount: Double? = null) = CapturedReceiptEntity(
        link = "https://example.test/$id", senderLabel = "IKEA", timestamp = at, clientId = id, capturedAt = at,
        pdfPath = pdf, totalAmount = amount, totalSource = amount?.let { "page" },
    )

    @Test
    fun merge_keepsTheOneWithAPdf_andTakesWhatTheUserSetOnTheOther() {
        val withPdf = r("new", at = 2 * min, pdf = "/files/new.pdf")
        val edited = r("old", at = 0, amount = 499.0).copy(category = "HOME", returnBy = 1_000L, storeName = "IKEA Rishon")
        val (kept, removed) = Duplicates.merge(listOf(edited, withPdf))
        assertEquals("new", kept.clientId)
        assertEquals("/files/new.pdf", kept.pdfPath)
        assertEquals("IKEA Rishon", kept.storeName)
        assertEquals("HOME", kept.category)
        assertEquals(1_000L, kept.returnBy)
        assertEquals(499.0, kept.totalAmount!!, 0.0)
        assertEquals("page", kept.totalSource)
        assertEquals(listOf("old"), removed.map { it.clientId })
    }

    @Test
    fun merge_withoutPdfs_keepsTheOneWithAnAmount_thenTheOldest_andItsOwnValues() {
        assertEquals("b", Duplicates.merge(listOf(r("a", 0), r("b", min, amount = 5.0))).first.clientId)
        val (kept, _) = Duplicates.merge(listOf(r("b", min).copy(category = "LEISURE"), r("a", 0).copy(category = "HOME")))
        assertEquals("a", kept.clientId)
        assertEquals("HOME", kept.category)
    }
}
