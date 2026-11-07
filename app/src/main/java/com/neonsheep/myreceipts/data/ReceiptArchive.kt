package com.neonsheep.myreceipts.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The export file: one zip with every receipt, store category and budget in `myreceipts.json`, and each
 * receipt's PDF in `pdfs/<clientId>.pdf`. For moving to a new phone, since Android's own backup is off.
 * No Android here, so it's unit tested; [ReceiptBackup] reads and writes the database and the files.
 *
 * Reading is careful, because an archive can come from anywhere: only those two kinds of entry are taken,
 * by exact name (so no entry can write outside the folder it's given), each has a size cap, a PDF must be
 * a PDF, and every receipt's fields are checked before any is used.
 */
object ReceiptArchive {
    const val FORMAT = 1
    const val JSON_ENTRY = "myreceipts.json"
    private const val PDF_PREFIX = "pdfs/"
    private const val JSON_MAX_BYTES = 20L * 1024 * 1024
    private const val RECEIPTS_MAX = 50_000
    private val CLIENT_ID = Regex("[A-Za-z0-9-]{1,80}")
    private val BUSINESS_ID = Regex("[0-9]{9}")
    private const val INVOICE_MAX = 40
    private val ORIGINS = setOf(Origin.SMS, Origin.LINK, Origin.FILE, Origin.PHOTO, Origin.SCAN)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Contents(
        val format: Int = FORMAT,
        val exportedAt: Long,
        val receipts: List<Receipt>,
        /** Store (as the text named it) to [Category] name. */
        val storeCategories: Map<String, String> = emptyMap(),
        /** [Category] name, or [BudgetEntity.ALL], to the monthly limit in shekels. */
        val budgets: Map<String, Double> = emptyMap(),
    )

    /** A receipt as [CapturedReceiptEntity] keeps it, without the phone's own row id and file path. */
    @Serializable
    data class Receipt(
        val clientId: String,
        val link: String,
        val senderLabel: String,
        val timestamp: Long,
        val capturedAt: Long,
        val origin: String = Origin.SMS,
        val totalAmount: Double? = null,
        val totalSource: String? = null,
        val pdfStatus: String? = null,
        val storeName: String? = null,
        val purchasedAt: Long? = null,
        val category: String? = null,
        val returnBy: Long? = null,
        val warrantyUntil: Long? = null,
        val notDuplicate: Boolean = false,
        /** Whether `pdfs/<clientId>.pdf` is in the archive. */
        val hasPdf: Boolean = false,
        // Added in app DB v9; absent from older exports, which read as unknown.
        val invoiceNumber: String? = null,
        val businessId: String? = null,
        val vatAmount: Double? = null,
    ) {
        fun toEntity(pdfPath: String?): CapturedReceiptEntity = CapturedReceiptEntity(
            link = link, senderLabel = senderLabel, timestamp = timestamp, clientId = clientId,
            capturedAt = capturedAt, totalAmount = totalAmount, pdfPath = pdfPath,
            // A PDF that didn't come along may be fetched again from its link.
            pdfStatus = if (pdfPath == null && hasPdf) null else pdfStatus,
            totalSource = totalSource, storeName = storeName, purchasedAt = purchasedAt, category = category,
            returnBy = returnBy, warrantyUntil = warrantyUntil, notDuplicate = notDuplicate, origin = origin,
            invoiceNumber = invoiceNumber, businessId = businessId, vatAmount = vatAmount,
        )

        companion object {
            fun of(e: CapturedReceiptEntity, hasPdf: Boolean) = Receipt(
                clientId = e.clientId, link = e.link, senderLabel = e.senderLabel, timestamp = e.timestamp,
                capturedAt = e.capturedAt, origin = e.origin, totalAmount = e.totalAmount, totalSource = e.totalSource,
                pdfStatus = e.pdfStatus, storeName = e.storeName, purchasedAt = e.purchasedAt, category = e.category,
                returnBy = e.returnBy, warrantyUntil = e.warrantyUntil, notDuplicate = e.notDuplicate, hasPdf = hasPdf,
                invoiceNumber = e.invoiceNumber, businessId = e.businessId, vatAmount = e.vatAmount,
            )
        }
    }

    /** Why an archive couldn't be read. */
    class Unreadable(val reason: Reason) : Exception(reason.name) {
        enum class Reason { NOT_AN_EXPORT, NEWER_FORMAT, DAMAGED }
    }

    /** Writes [contents], and each receipt's PDF that [pdfOf] finds, as one zip. */
    fun write(out: OutputStream, contents: Contents, pdfOf: (String) -> File?) {
        ZipOutputStream(out.buffered()).use { zip ->
            val receipts = contents.receipts.map { r ->
                val pdf = pdfOf(r.clientId)?.takeIf { it.isFile }
                if (pdf != null) {
                    zip.putNextEntry(ZipEntry("$PDF_PREFIX${r.clientId}.pdf"))
                    pdf.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                r.copy(hasPdf = pdf != null)
            }
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.encodeToString(Contents.serializer(), contents.copy(receipts = receipts)).toByteArray())
            zip.closeEntry()
        }
    }

    /** What an archive holds: its contents, and its PDFs, saved into [staging] as `<clientId>.pdf`. */
    class Read(val contents: Contents, val pdfs: Map<String, File>)

    /**
     * Reads an archive, saving its PDFs into [staging] (an empty folder the caller deletes afterwards).
     * Receipts whose fields don't hold up are left out; an archive that isn't one is [Unreadable].
     */
    fun read(input: InputStream, staging: File): Read {
        var contents: Contents? = null
        val pdfs = mutableMapOf<String, File>()
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == JSON_ENTRY -> {
                            val bytes = readCapped(zip, JSON_MAX_BYTES) ?: throw Unreadable(Unreadable.Reason.DAMAGED)
                            contents = parse(bytes)
                        }
                        name.startsWith(PDF_PREFIX) && name.endsWith(".pdf") -> {
                            val id = name.removePrefix(PDF_PREFIX).removeSuffix(".pdf")
                            // The id is the file name: only a plain one, so nothing lands outside staging.
                            if (!CLIENT_ID.matches(id)) continue
                            val bytes = readCapped(zip, ReceiptImport.MAX_BYTES) ?: continue
                            if (!ReceiptImport.looksLikePdf(bytes)) continue
                            pdfs[id] = File(staging, "$id.pdf").apply { writeBytes(bytes) }
                        }
                        // Anything else is not ours: skipped, never written.
                        else -> Unit
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Unreadable) {
            throw e
        } catch (e: java.io.IOException) {
            throw Unreadable(Unreadable.Reason.DAMAGED)
        } catch (e: IllegalArgumentException) {
            throw Unreadable(Unreadable.Reason.DAMAGED)
        }
        val found = contents ?: throw Unreadable(Unreadable.Reason.NOT_AN_EXPORT)
        return Read(found, pdfs.filterKeys { id -> found.receipts.any { it.clientId == id } })
    }

    private fun parse(bytes: ByteArray): Contents {
        val raw = try {
            json.decodeFromString(Contents.serializer(), bytes.decodeToString())
        } catch (e: Exception) {
            throw Unreadable(Unreadable.Reason.NOT_AN_EXPORT)
        }
        if (raw.format > FORMAT) throw Unreadable(Unreadable.Reason.NEWER_FORMAT)
        return raw.copy(
            receipts = raw.receipts.asSequence().filter(::holdsUp).distinctBy { it.clientId }.take(RECEIPTS_MAX).toList(),
            storeCategories = raw.storeCategories
                .filter { (store, category) -> store.length in 1..200 && Category.fromName(category) != null },
            budgets = raw.budgets.filter { (key, limit) ->
                (key == BudgetEntity.ALL || Category.fromName(key) != null) && limit > 0 && limit < 10_000_000
            },
        )
    }

    /** Whether a receipt from an archive is one this app could have written. */
    internal fun holdsUp(r: Receipt): Boolean =
        CLIENT_ID.matches(r.clientId) &&
            r.origin in ORIGINS &&
            r.link.length <= 4096 &&
            (r.link.isEmpty() || r.link.startsWith("https://") || r.link.startsWith("http://")) &&
            r.link.isNotEmpty() == Origin.hasLink(r.origin) &&
            r.senderLabel.length in 1..200 &&
            (r.storeName?.length ?: 0) <= 200 &&
            (r.totalAmount == null || (r.totalAmount >= 0 && r.totalAmount < 10_000_000)) &&
            (r.category == null || Category.fromName(r.category) != null) &&
            (r.invoiceNumber?.length ?: 0) <= INVOICE_MAX &&
            (r.businessId == null || BUSINESS_ID.matches(r.businessId)) &&
            (r.vatAmount == null || (r.vatAmount >= 0 && r.vatAmount < 10_000_000))

    private fun readCapped(input: InputStream, max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
