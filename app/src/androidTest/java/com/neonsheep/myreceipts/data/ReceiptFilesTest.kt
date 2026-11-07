package com.neonsheep.myreceipts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.auth.Backend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Receipts added as files: saved on the phone as a PDF, read there, and never added twice. */
@RunWith(AndroidJUnit4::class)
class ReceiptFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var dao: ReceiptDao
    private val made = mutableListOf<File>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.receiptDao()
    }

    @After
    fun tearDown() = runBlocking {
        dao.getAllOnce().forEach { Backend.pdfFile(context, it.clientId).delete() }
        made.forEach { it.delete() }
        db.close()
    }

    @Test
    fun aPdf_becomesAReceipt_withItsAmountReadOnThePhone() = runBlocking {
        val uri = file("shared.pdf", textPdf("סה\"כ לתשלום ₪89.80"))

        val result = ReceiptFiles.importFile(context, dao, uri, "application/pdf", Origin.FILE, now = 1_000L)

        val added = result as ReceiptFiles.Result.Added
        assertTrue(added.isFile)
        val r = dao.getByClientId(added.clientId)!!
        assertEquals(Origin.FILE, r.origin)
        assertEquals("", r.link)
        assertEquals(context.getString(R.string.store_uploaded), r.senderLabel)
        assertEquals(1_000L, r.timestamp)
        assertTrue(File(r.pdfPath!!).exists())
        if (PdfText.available) {
            assertEquals(89.8, r.totalAmount!!, 0.001)
            assertEquals(TotalSource.PDF, r.totalSource)
        }
    }

    @Test
    fun theSameFileTwice_isOneReceipt() = runBlocking {
        val bytes = textPdf("סה\"כ לתשלום ₪12.00")
        val first = ReceiptFiles.importFile(context, dao, file("a.pdf", bytes), "application/pdf", Origin.FILE)
        val again = ReceiptFiles.importFile(context, dao, file("copy.pdf", bytes), null, Origin.FILE)

        assertEquals((first as ReceiptFiles.Result.Added).clientId, (again as ReceiptFiles.Result.AlreadySaved).clientId)
        assertEquals(1, dao.getAllOnce().size)
    }

    @Test
    fun aScan_withNoTextToRead_asksForTheAmount() = runBlocking {
        assumeTrue("needs Android 15", PdfText.available)
        // A scan is a picture of the paper: there's no text in it to read.
        val scan = ReceiptFiles.imageToPdf(png(400, 900))!!
        val result = ReceiptFiles.importFile(context, dao, file("scan.pdf", scan), "application/pdf", Origin.SCAN)

        val r = dao.getByClientId((result as ReceiptFiles.Result.Added).clientId)!!
        assertEquals(context.getString(R.string.store_scanned), r.senderLabel)
        assertNull(r.totalAmount)
        assertEquals(TotalSource.NONE, r.totalSource)
    }

    @Test
    fun aPhoto_becomesAOnePagePdf_shapedLikeThePhoto() = runBlocking {
        val result = ReceiptFiles.importFile(context, dao, file("photo.png", png(600, 1800)), "image/png", Origin.FILE)

        val r = dao.getByClientId((result as ReceiptFiles.Result.Added).clientId)!!
        assertEquals(Origin.PHOTO, r.origin)
        ParcelFileDescriptor.open(File(r.pdfPath!!), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                assertEquals(1, pdf.pageCount)
                pdf.openPage(0).use { page -> assertEquals(3.0, page.height.toDouble() / page.width, 0.01) }
            }
        }
    }

    @Test
    fun notAReceipt_tooBig_orNotWhatItSays_addsNothing() = runBlocking {
        assertEquals(
            ReceiptFiles.Result.NotAReceipt,
            ReceiptFiles.importFile(context, dao, file("clip.mp4", ByteArray(10)), "video/mp4", Origin.FILE),
        )
        assertEquals(
            ReceiptFiles.Result.Unreadable,
            ReceiptFiles.importFile(context, dao, file("page.pdf", "<html>".toByteArray()), "application/pdf", Origin.FILE),
        )
        val big = File(context.cacheDir, "big.pdf").also { made += it }
        big.outputStream().use { out ->
            out.write("%PDF-1.7\n".toByteArray())
            val chunk = ByteArray(1024 * 1024)
            repeat(26) { out.write(chunk) }
        }
        assertEquals(
            ReceiptFiles.Result.TooBig,
            ReceiptFiles.importFile(context, dao, Uri.fromFile(big), "application/pdf", Origin.FILE),
        )
        assertTrue(dao.getAllOnce().isEmpty())
    }

    @Test
    fun sharedText_withoutAReceiptLinkWeKnow_addsNothing() = runBlocking {
        assertEquals(ReceiptFiles.Result.NoLink, ReceiptFiles.importText(context, dao, "see you at 8"))
        assertEquals(ReceiptFiles.Result.UnknownSite, ReceiptFiles.importText(context, dao, "https://example.com/r/1"))
        assertEquals(ReceiptFiles.Result.UnknownSite, ReceiptFiles.importText(context, dao, "Sale! https://wee.ai/s/promo"))
        assertTrue(dao.getAllOnce().isEmpty())
    }

    @Test
    fun aSharedLink_alreadySavedBySms_isThatReceipt() = runBlocking {
        val link = "https://wee.ai/r/abc"
        dao.insert(CapturedReceiptEntity(link = link, senderLabel = "IKEA", timestamp = 5, clientId = "sms-1", capturedAt = 5))

        val result = ReceiptFiles.importText(context, dao, "your receipt: $link")

        assertEquals(ReceiptFiles.Result.AlreadySaved("sms-1"), result)
        assertEquals(1, dao.getAllOnce().size)
    }

    @Test
    fun anUpsideDownScan_turnsTheRightWayUp() = runBlocking {
        // A tall page, dark in its top-left corner only, as the scanner might give it upside down.
        val bitmap = Bitmap.createBitmap(300, 900, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (x in 0 until 60) for (y in 0 until 60) setPixel(x, y, Color.BLACK)
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val file = File(context.cacheDir, "turn.pdf").also { it.writeBytes(ReceiptFiles.imageToPdf(png)!!); made += it }

        assertTrue(ReceiptFiles.rotate(file, quarterTurns = 1))
        pageOf(file) { w, h, _ -> assertTrue("a quarter turn lays the page on its side", w > h) }

        assertTrue(ReceiptFiles.rotate(file, quarterTurns = 1))
        pageOf(file) { w, h, page ->
            assertTrue(h > w)
            // Two quarter turns: the dark corner is now bottom-right.
            assertTrue("bottom-right is dark", luminance(page.getPixel(w - 5, h - 5)) < 80)
            assertTrue("top-left is light", luminance(page.getPixel(5, 5)) > 200)
        }
    }

    private fun pageOf(file: File, check: (Int, Int, Bitmap) -> Unit) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                assertEquals(1, pdf.pageCount)
                pdf.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    check(page.width, page.height, bmp)
                }
            }
        }
    }

    private fun luminance(c: Int) = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3

    private fun file(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(context.cacheDir, name).also { it.writeBytes(bytes); made += it })

    private fun textPdf(vararg lines: String): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = Paint().apply { textSize = 14f }
        lines.forEachIndexed { i, line -> page.canvas.drawText(line, 40f, 60f + i * 24, paint) }
        doc.finishPage(page)
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
