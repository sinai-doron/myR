package com.neonsheep.myreceipts.data

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Android's own PDF reader, on a made-up receipt drawn as text: the amount comes out. */
@RunWith(AndroidJUnit4::class)
class PdfTotalTest {
    @Test
    fun aTextPdf_givesItsAmountPaid() {
        assumeTrue("needs Android 15", PdfText.available)
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "made-up.pdf")
        PdfDocument().apply {
            val page = startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = Paint().apply { textSize = 14f }
            listOf(
                "מברשת שיניים ₪59.90",
                "משחת שיניים ₪29.90",
                "סה\"כ ללא מע\"מ ₪76.10",
                "סה\"כ לתשלום ₪89.80",
            ).forEachIndexed { i, line -> page.canvas.drawText(line, 40f, 60f + i * 24, paint) }
            finishPage(page)
            file.outputStream().use { writeTo(it) }
            close()
        }
        try {
            assertEquals(89.8, ReceiptTotals.fromText(PdfText.read(file)!!)!!, 0.001)
        } finally {
            file.delete()
        }
    }
}
