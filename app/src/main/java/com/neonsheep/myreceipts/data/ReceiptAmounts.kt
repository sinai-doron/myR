package com.neonsheep.myreceipts.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps each receipt's amount paid. The backend's reading of the receipt page comes first; without
 * it, the PDF's text is read on the phone; and an amount the user typed is never replaced.
 */
object ReceiptAmounts {
    /** A PDF was saved for the receipt: record it, with the page's amount or else the PDF's. */
    suspend fun pdfSaved(dao: ReceiptDao, clientId: String, path: String, pageTotal: Double?) {
        dao.updatePdfPath(clientId, path)
        if (pageTotal != null) dao.setPageTotal(clientId, pageTotal) else readPdf(dao, clientId, File(path))
    }

    /** Receipts saved before amounts were kept: read their PDFs once. */
    suspend fun catchUp(dao: ReceiptDao) {
        if (!PdfText.available) return
        dao.withoutTotalLookedFor().forEach { r -> r.pdfPath?.let { readPdf(dao, r.clientId, File(it)) } }
    }

    private suspend fun readPdf(dao: ReceiptDao, clientId: String, file: File) {
        // A phone that can't read PDF text leaves it unlooked-for, for the user to type.
        if (!PdfText.available) return
        val text = withContext(Dispatchers.IO) { PdfText.read(file) } ?: return
        val amount = ReceiptTotals.fromText(text)
        dao.setPdfTotal(clientId, amount, if (amount != null) TotalSource.PDF else TotalSource.NONE)
    }
}
