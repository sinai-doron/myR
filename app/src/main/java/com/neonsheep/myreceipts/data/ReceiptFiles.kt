package com.neonsheep.myreceipts.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.auth.Backend
import com.neonsheep.myreceipts.worker.ReceiptDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Receipts that come in other than by SMS. A PDF or photo is saved on the phone as the receipt's PDF
 * (a photo becomes a one-page PDF) and its amount is read on the phone; nothing is sent anywhere. A
 * shared link goes the way an SMS link goes: to the backend, for its PDF.
 */
object ReceiptFiles {
    private const val TAG = "ReceiptFiles"

    /** A photo's longest side, in pixels, once in the PDF: sharp enough to read, small enough to keep. */
    private const val MAX_IMAGE_SIDE = 2400

    /** The PDF page's width in points (A4); its height follows the photo. */
    private const val PAGE_WIDTH = 595

    /** Pixels per point when a page is drawn again to turn it: about 216 dpi, as sharp as the scanner's pages. */
    private const val RENDER_SCALE = 3

    sealed interface Result {
        /** A new receipt. [isFile]: the user is asked for what the file can't say (store, maybe amount). */
        data class Added(val clientId: String, val isFile: Boolean) : Result

        /** The same file or link was saved before: that receipt. */
        data class AlreadySaved(val clientId: String) : Result

        /** Not a PDF or a photo. */
        data object NotAReceipt : Result

        /** Over [ReceiptImport.MAX_BYTES]. */
        data object TooBig : Result

        /** Couldn't be opened, or isn't what it said it was. */
        data object Unreadable : Result

        /** Shared text with no link in it. */
        data object NoLink : Result

        /** Shared text with links, none of them a receipt from a site we know. */
        data object UnknownSite : Result
    }

    /** A PDF or photo: saved as the receipt's PDF, its amount read on the phone. [origin]: [Origin.FILE] or [Origin.SCAN]. */
    suspend fun importFile(
        context: Context,
        dao: ReceiptDao,
        uri: Uri,
        mimeType: String?,
        origin: String,
        now: Long = System.currentTimeMillis(),
    ): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val type = mimeType ?: runCatching { resolver.getType(uri) }.getOrNull()
        val kind = ReceiptImport.kindOf(type, displayName(resolver, uri)) ?: return@withContext Result.NotAReceipt
        val bytes = when (val read = readCapped(resolver, uri)) {
            is Read.Bytes -> read.bytes
            Read.TooBig -> return@withContext Result.TooBig
            Read.Failed -> return@withContext Result.Unreadable
        }
        val pdf = when (kind) {
            ReceiptImport.Kind.PDF -> bytes.takeIf { ReceiptImport.looksLikePdf(it) }
            ReceiptImport.Kind.IMAGE -> imageToPdf(bytes)
        } ?: return@withContext Result.Unreadable

        // A photo is kept as one, so it can be turned the right way up later.
        val savedAs = if (kind == ReceiptImport.Kind.IMAGE && origin == Origin.FILE) Origin.PHOTO else origin
        val clientId = ReceiptImport.fileClientId(bytes)
        dao.getByClientId(clientId)?.let { return@withContext Result.AlreadySaved(it.clientId) }

        val file = Backend.pdfFile(context, clientId)
        if (!writeAtomically(file, pdf)) return@withContext Result.Unreadable
        val label = context.getString(if (origin == Origin.SCAN) R.string.store_scanned else R.string.store_uploaded)
        dao.insert(
            CapturedReceiptEntity(
                link = "",
                senderLabel = label,
                timestamp = now,
                clientId = clientId,
                capturedAt = now,
                origin = savedAs,
            ),
        )
        ReceiptAmounts.pdfSaved(dao, clientId, file.absolutePath, pageTotal = null)
        Result.Added(clientId, isFile = true)
    }

    /** Text shared to the app: its receipt links, each saved and fetched like an SMS link. */
    suspend fun importText(
        context: Context,
        dao: ReceiptDao,
        text: String,
        now: Long = System.currentTimeMillis(),
    ): Result = withContext(Dispatchers.IO) {
        val config = MerchantConfig.load(context)
        val links = ReceiptImport.receiptLinksIn(text, config)
        if (links.isEmpty()) {
            return@withContext if (ReceiptImport.anyLinkIn(text)) Result.UnknownSite else Result.NoLink
        }
        var first: Result? = null
        for (link in links) {
            val existing = dao.getByLink(link)
            val result = if (existing != null) {
                Result.AlreadySaved(existing.clientId)
            } else {
                val clientId = generateClientId(link, now)
                dao.insert(
                    CapturedReceiptEntity(
                        link = link,
                        senderLabel = ReceiptImport.storeForLink(link, config),
                        timestamp = now,
                        clientId = clientId,
                        capturedAt = now,
                        origin = Origin.LINK,
                    ),
                )
                ReceiptDownloadWorker.enqueue(context, link, clientId)
                Result.Added(clientId, isFile = false)
            }
            if (first == null) first = result
        }
        first!!
    }

    /**
     * Turns every page of a scan or photo a quarter turn clockwise, [quarterTurns] times. The scanner guesses
     * which way is up from how the phone is held, so a receipt shot lying on a table can come out upside down.
     * Pages are drawn again as pictures, which they already are; nothing is read from them.
     */
    suspend fun rotate(file: File, quarterTurns: Int = 1): Boolean = withContext(Dispatchers.IO) {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return@withContext true
        try {
            val out = ByteArrayOutputStream()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val doc = PdfDocument()
                    try {
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val sideways = turns % 2 == 1
                                val w = if (sideways) page.height else page.width
                                val h = if (sideways) page.width else page.height
                                val scale = RENDER_SCALE
                                val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val out2 = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                                val canvas = out2.canvas
                                canvas.drawColor(Color.WHITE)
                                // Turn about the new page's centre, then draw the old page centred on it.
                                canvas.translate(w / 2f, h / 2f)
                                canvas.rotate(90f * turns)
                                canvas.translate(-page.width / 2f, -page.height / 2f)
                                canvas.drawBitmap(
                                    bitmap, null,
                                    Rect(0, 0, page.width, page.height),
                                    Paint(Paint.FILTER_BITMAP_FLAG),
                                )
                                doc.finishPage(out2)
                                bitmap.recycle()
                            }
                        }
                        doc.writeTo(out)
                    } finally {
                        doc.close()
                    }
                }
            }
            writeAtomically(file, out.toByteArray())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't turn a receipt's pages: ${e.javaClass.simpleName}")
            false
        }
    }

    /** The first page as a picture [widthPx] wide, to show on the receipt; null when it can't be drawn. */
    suspend fun preview(file: File, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return@use null
                    renderer.openPage(0).use { page ->
                        val height = (widthPx.toLong() * page.height / page.width).toInt().coerceIn(1, widthPx * 6)
                        Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't draw a receipt's page: ${e.javaClass.simpleName}")
            null
        }
    }

    private sealed interface Read {
        class Bytes(val bytes: ByteArray) : Read
        data object TooBig : Read
        data object Failed : Read
    }

    private fun readCapped(resolver: ContentResolver, uri: Uri): Read = try {
        resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > ReceiptImport.MAX_BYTES) return Read.TooBig
                out.write(buffer, 0, n)
            }
            Read.Bytes(out.toByteArray())
        } ?: Read.Failed
    } catch (e: Exception) {
        // Never the file's name or contents: only that it couldn't be read.
        Log.w(TAG, "Couldn't read a shared file: ${e.javaClass.simpleName}")
        Read.Failed
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.lastPathSegment
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    /** A photo as a one-page PDF, the right way up, at most [MAX_IMAGE_SIDE] pixels on its longest side. */
    internal fun imageToPdf(bytes: ByteArray): ByteArray? {
        val bitmap = decode(bytes) ?: return null
        return try {
            val pageHeight = (PAGE_WIDTH.toLong() * bitmap.height / bitmap.width).toInt().coerceIn(1, 14_400)
            val doc = PdfDocument()
            try {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, null, Rect(0, 0, PAGE_WIDTH, pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
            } finally {
                doc.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't make a PDF of a photo: ${e.javaClass.simpleName}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder turns the photo by its EXIF orientation, and reads HEIC.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_SIDE) {
                    val scale = MAX_IMAGE_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't decode a photo: ${e.javaClass.simpleName}")
        null
    }

    /** Written beside, then moved into place: a half-written file never looks like a saved receipt. */
    private fun writeAtomically(file: File, bytes: ByteArray): Boolean = try {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.part")
        tmp.writeBytes(bytes)
        tmp.renameTo(file) || run { tmp.delete(); false }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't save a receipt's PDF: ${e.javaClass.simpleName}")
        false
    }
}
