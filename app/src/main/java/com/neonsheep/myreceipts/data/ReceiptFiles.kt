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
                origin = origin,
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
