package com.neonsheep.myreceipts.data

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/** A PDF's text, read with Android's own PDF reader: no library, nothing leaves the phone. */
object PdfText {
    private const val TAG = "PdfText"
    private const val MAX_PAGES = 10

    /** Whether this phone can read PDF text (Android 15 and later). */
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    /** The text of the first pages, one text run per line; null when it can't be read. */
    fun read(file: File): String? {
        if (!available || !file.exists()) return null
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    buildString {
                        for (i in 0 until minOf(renderer.pageCount, MAX_PAGES)) {
                            renderer.openPage(i).use { page ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                                    page.textContents.forEach { appendLine(it.text) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Never the file's contents: only that it couldn't be read.
            Log.w(TAG, "Couldn't read a PDF's text: ${e.javaClass.simpleName}")
            null
        }
    }
}
