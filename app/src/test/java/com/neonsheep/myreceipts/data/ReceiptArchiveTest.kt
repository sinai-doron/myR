package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReceiptArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val pdfBytes = "%PDF-1.7\nreceipt\n%%EOF".toByteArray()

    private val sms = ReceiptArchive.Receipt(
        clientId = "0c4a3e2b-1111-2222-3333-444455556666",
        link = "https://wee.ai/r/abc",
        senderLabel = "IKEA",
        timestamp = 1_000,
        capturedAt = 1_001,
        totalAmount = 730.0,
        totalSource = TotalSource.PAGE,
        storeName = "IKEA Rishon",
        category = Category.HOME.name,
        returnBy = 5_000,
        invoiceNumber = "7734-21",
        businessId = "512345678",
        vatAmount = 106.07,
    )
    private val scan = ReceiptArchive.Receipt(
        clientId = "file-0123456789abcdef0123456789abcdef",
        link = "",
        senderLabel = "Scanned receipt",
        timestamp = 2_000,
        capturedAt = 2_000,
        origin = Origin.SCAN,
        totalSource = TotalSource.NONE,
    )

    @Test
    fun `everything written comes back, PDFs included`() {
        val pdf = tmp.newFile("x.pdf").apply { writeBytes(pdfBytes) }
        val contents = ReceiptArchive.Contents(
            exportedAt = 9,
            receipts = listOf(sms, scan),
            storeCategories = mapOf("IKEA" to Category.HOME.name),
            budgets = mapOf(BudgetEntity.ALL to 3000.0, Category.GROCERIES.name to 1500.0),
        )
        val zip = ByteArrayOutputStream().also { out ->
            ReceiptArchive.write(out, contents) { id -> pdf.takeIf { id == scan.clientId } }
        }.toByteArray()

        val read = ReceiptArchive.read(ByteArrayInputStream(zip), tmp.newFolder("staging"))

        assertEquals(listOf(sms.copy(hasPdf = false), scan.copy(hasPdf = true)), read.contents.receipts)
        assertEquals(contents.storeCategories, read.contents.storeCategories)
        assertEquals(contents.budgets, read.contents.budgets)
        assertEquals(setOf(scan.clientId), read.pdfs.keys)
        assertTrue(pdfBytes.contentEquals(read.pdfs.getValue(scan.clientId).readBytes()))
    }

    @Test
    fun `an entry can never write outside the staging folder`() {
        val staging = tmp.newFolder("staging")
        val zip = zipOf(
            "pdfs/../../evil.pdf" to pdfBytes,
            "pdfs/../escape.pdf" to pdfBytes,
            "/abs/evil.pdf" to pdfBytes,
            "pdfs/sub/dir.pdf" to pdfBytes,
            "other.txt" to "hi".toByteArray(),
            ReceiptArchive.JSON_ENTRY to jsonOf(sms),
        )
        val read = ReceiptArchive.read(ByteArrayInputStream(zip), staging)
        assertTrue(read.pdfs.isEmpty())
        assertEquals(emptyList<String>(), staging.list()!!.toList())
        assertFalse(File(tmp.root, "evil.pdf").exists())
        assertFalse(File(tmp.root, "escape.pdf").exists())
    }

    @Test
    fun `a PDF that isn't one, or belongs to no receipt, is left out`() {
        val zip = zipOf(
            "pdfs/${sms.clientId}.pdf" to "<html>".toByteArray(),
            "pdfs/someone-else.pdf" to pdfBytes,
            ReceiptArchive.JSON_ENTRY to jsonOf(sms),
        )
        assertTrue(ReceiptArchive.read(ByteArrayInputStream(zip), tmp.newFolder("s")).pdfs.isEmpty())
    }

    @Test
    fun `receipts that don't hold up are left out, the rest kept`() {
        assertFalse(ReceiptArchive.holdsUp(sms.copy(clientId = "../x")))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(origin = "email")))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(link = "javascript:alert(1)")))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(link = "")))
        assertFalse(ReceiptArchive.holdsUp(scan.copy(link = "https://wee.ai/r/x")))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(senderLabel = "")))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(totalAmount = -1.0)))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(category = "NOT_A_CATEGORY")))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(businessId = "51234567X")))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(invoiceNumber = "9".repeat(41))))
        assertFalse(ReceiptArchive.holdsUp(sms.copy(vatAmount = -1.0)))
        assertTrue(ReceiptArchive.holdsUp(sms))
        assertTrue(ReceiptArchive.holdsUp(scan))

        val zip = zipOf(ReceiptArchive.JSON_ENTRY to jsonOf(sms, sms.copy(clientId = "../x"), sms))
        assertEquals(listOf(sms), ReceiptArchive.read(ByteArrayInputStream(zip), tmp.newFolder("s")).contents.receipts)
    }

    @Test
    fun `not an export, a newer one, or a damaged file, each says so`() {
        assertUnreadable(ReceiptArchive.Unreadable.Reason.NOT_AN_EXPORT, zipOf("photo.jpg" to byteArrayOf(1, 2)))
        assertUnreadable(ReceiptArchive.Unreadable.Reason.NOT_AN_EXPORT, zipOf(ReceiptArchive.JSON_ENTRY to "{".toByteArray()))
        assertUnreadable(
            ReceiptArchive.Unreadable.Reason.NEWER_FORMAT,
            zipOf(ReceiptArchive.JSON_ENTRY to """{"format":2,"exportedAt":1,"receipts":[]}""".toByteArray()),
        )
        assertUnreadable(ReceiptArchive.Unreadable.Reason.NOT_AN_EXPORT, "not a zip at all".toByteArray())
    }

    private fun assertUnreadable(reason: ReceiptArchive.Unreadable.Reason, bytes: ByteArray) {
        try {
            ReceiptArchive.read(ByteArrayInputStream(bytes), tmp.newFolder())
            fail("expected $reason")
        } catch (e: ReceiptArchive.Unreadable) {
            assertEquals(reason, e.reason)
        }
    }

    private fun jsonOf(vararg receipts: ReceiptArchive.Receipt): ByteArray {
        val out = ByteArrayOutputStream()
        ReceiptArchive.write(out, ReceiptArchive.Contents(exportedAt = 1, receipts = receipts.toList())) { null }
        // Take the JSON back out of the zip just written.
        java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == ReceiptArchive.JSON_ENTRY) return z.readBytes()
            }
        }
        error("no json")
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
