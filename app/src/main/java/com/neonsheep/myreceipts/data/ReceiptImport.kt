package com.neonsheep.myreceipts.data

import java.net.URI
import java.security.MessageDigest

/**
 * The rules for receipts that come in other than by SMS: a PDF or photo from the file picker, the
 * scanner or Android's Share menu, or a link shared from another app. No Android here, so it's unit
 * tested; [com.neonsheep.myreceipts.data.ReceiptFiles] does the reading and saving.
 */
object ReceiptImport {
    /** Bigger than any receipt: a long scanned one is a few MB. Stops a video or an archive being read into memory. */
    const val MAX_BYTES = 25L * 1024 * 1024

    enum class Kind { PDF, IMAGE }

    /** What the file is, from its type, else its name. Null: not something a receipt comes as. */
    fun kindOf(mimeType: String?, name: String?): Kind? {
        val type = mimeType?.lowercase()?.substringBefore(';')?.trim()
        when {
            type == "application/pdf" -> return Kind.PDF
            type != null && type.startsWith("image/") -> return Kind.IMAGE
        }
        // Some apps share with a vague type (application/octet-stream): the name decides.
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        return when (ext) {
            "pdf" -> Kind.PDF
            "jpg", "jpeg", "png", "webp", "heic", "heif" -> Kind.IMAGE
            else -> null
        }
    }

    /** Whether the bytes really are a PDF, whatever the file said it was. */
    fun looksLikePdf(head: ByteArray): Boolean {
        val magic = "%PDF-".toByteArray()
        // The header may come after a little junk; PDF readers allow it in the first 1 KB.
        val limit = minOf(head.size, 1024) - magic.size
        for (i in 0..limit) {
            if ((magic.indices).all { head[i + it] == magic[it] }) return true
        }
        return false
    }

    /**
     * A file receipt's id: the same file twice is the same receipt, so sharing it again doesn't add a
     * second one. From the content only, never the name or where it came from.
     */
    fun fileClientId(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "file-" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /** Shared text's receipt links: on a site we know, and a receipt rather than an advert or a history page. */
    fun receiptLinksIn(text: String, config: MerchantConfig): List<String> =
        DomainMatchFilter(config).filter(text, sender = "").matchedLinks.distinct()

    /** Every web link in shared text, receipt or not: to say "not a site we know" rather than "no link". */
    fun anyLinkIn(text: String): Boolean = URL.containsMatchIn(text)

    /**
     * The store for a shared link, before the user names it: the merchant whose site it is, else the
     * site itself without "www.". A receipt platform (wee.ai, tbit.be) isn't the store, but it's the
     * best guess the link gives, and the user can change it.
     */
    fun storeForLink(link: String, config: MerchantConfig): String {
        val host = try {
            URI(link).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return link
        val merchant = config.merchants.firstOrNull { m ->
            m.domains.any { host == it || host.endsWith(".$it") }
        }
        return merchant?.name ?: host.removePrefix("www.")
    }

    private val URL = Regex("""https?://[^\s֐-׿]+""")
}
