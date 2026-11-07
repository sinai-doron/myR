package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReceiptImportTest {
    private val config = MerchantConfig.fromJson(File("src/main/assets/merchants.json").readText())

    @Test
    fun `a PDF or a photo, by type or else by name`() {
        assertEquals(ReceiptImport.Kind.PDF, ReceiptImport.kindOf("application/pdf", null))
        assertEquals(ReceiptImport.Kind.IMAGE, ReceiptImport.kindOf("image/jpeg", null))
        assertEquals(ReceiptImport.Kind.IMAGE, ReceiptImport.kindOf("IMAGE/HEIC", "x"))
        // WhatsApp and some file managers say octet-stream: the name decides.
        assertEquals(ReceiptImport.Kind.PDF, ReceiptImport.kindOf("application/octet-stream", "o_2032882.pdf"))
        assertEquals(ReceiptImport.Kind.IMAGE, ReceiptImport.kindOf(null, "IMG_0001.JPG"))
        assertNull(ReceiptImport.kindOf("video/mp4", "clip.mp4"))
        assertNull(ReceiptImport.kindOf("application/zip", "receipts.zip"))
        assertNull(ReceiptImport.kindOf(null, null))
    }

    @Test
    fun `a PDF is a PDF by its bytes, not its name`() {
        assertTrue(ReceiptImport.looksLikePdf("%PDF-1.7\n...".toByteArray()))
        // A little junk before the header is allowed, as PDF readers allow it.
        assertTrue(ReceiptImport.looksLikePdf("\r\n%PDF-1.4".toByteArray()))
        assertFalse(ReceiptImport.looksLikePdf("<html><body>".toByteArray()))
        assertFalse(ReceiptImport.looksLikePdf(ByteArray(0)))
        assertFalse(ReceiptImport.looksLikePdf("%PD".toByteArray()))
    }

    @Test
    fun `the same file is the same receipt, a different one isn't`() {
        val a = ReceiptImport.fileClientId("%PDF-1.7 a".toByteArray())
        assertEquals(a, ReceiptImport.fileClientId("%PDF-1.7 a".toByteArray()))
        assertNotEquals(a, ReceiptImport.fileClientId("%PDF-1.7 b".toByteArray()))
        assertTrue(a.matches(Regex("file-[0-9a-f]{32}")))
    }

    @Test
    fun `a shared message's receipt link, from a site we know`() {
        val text = "תודה שקנית בשופרסל! לקבלה: https://www.shufersal.co.il/r/abc123 שיהיה יום טוב"
        assertEquals(listOf("https://www.shufersal.co.il/r/abc123"), ReceiptImport.receiptLinksIn(text, config))
    }

    @Test
    fun `a shared wee_ai advert is not a receipt`() {
        val text = "Our sale is on! https://wee.ai/s/promo"
        assertEquals(emptyList<String>(), ReceiptImport.receiptLinksIn(text, config))
        assertTrue(ReceiptImport.anyLinkIn(text))
    }

    @Test
    fun `a link from a site we don't know, or no link at all`() {
        assertEquals(emptyList<String>(), ReceiptImport.receiptLinksIn("https://example.com/r/1", config))
        assertTrue(ReceiptImport.anyLinkIn("https://example.com/r/1"))
        assertFalse(ReceiptImport.anyLinkIn("just some words"))
    }

    @Test
    fun `the same link twice in a message is one receipt`() {
        val link = "https://wee.ai/r/abc"
        assertEquals(listOf(link), ReceiptImport.receiptLinksIn("$link and again $link", config))
    }

    @Test
    fun `a shared link's store is the merchant, else the site`() {
        assertEquals("Shufersal", ReceiptImport.storeForLink("https://www.shufersal.co.il/r/1", config))
        assertEquals("wee.ai", ReceiptImport.storeForLink("https://wee.ai/r/abc", config))
        assertEquals("tbit.be", ReceiptImport.storeForLink("https://www.tbit.be/x", config))
    }
}
