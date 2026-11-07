package com.neonsheep.myreceipts.data

import java.net.URI

data class FilterResult(
    val matchedLinks: List<String>,
    val senderLabel: String,
)

interface ReceiptFilter {
    fun filter(messageBody: String, sender: String): FilterResult
}

/**
 * Links on a receipt platform that are not receipts. A wee.ai SMS often carries the receipt
 * (`/r/<id>`) next to marketing (`/s/`), unsubscribe (`/u/`) and landing links (`/l/`, `/pages/`),
 * and a marketing text from the same sender carries only those.
 *
 * MAXstock's texts link to a purchase history page on Pairzon (`h.html?b=…&t=…`), not a receipt:
 * it asks for a one-time code that expires five minutes after the text, so it is never kept.
 *
 * Tabit's short links (`tbit.be/<code>`) look the same whether they open an invoice, a survey, a
 * table booking or a promotion, so the text decides: Tabit's receipt texts always say they are an
 * invoice ("לצפייה בחשבונית מ…"), and the others never do.
 */
object ReceiptLinks {
    private val receiptPathPrefixes = mapOf(
        "wee.ai" to listOf("/r/"),
    )

    // Paths that are never a receipt, by domain: exact paths, any case.
    private val notReceiptPaths = mapOf(
        "pairzon.com" to listOf("/h.html"),
    )

    // Domains whose links look alike for receipts and everything else: kept only when the text says receipt.
    private val textMustSay = mapOf(
        "tbit.be" to listOf("חשבונית", "קבלה", "invoice", "receipt"),
    )

    /** Whether the text the link came in allows it: for most sites always, for [textMustSay] only with a receipt word. */
    fun textAllows(url: String, messageBody: String): Boolean {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            return false
        } ?: return false
        val words = textMustSay.entries
            .firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }
            ?.value
            ?: return true
        val body = messageBody.lowercase()
        return words.any { body.contains(it) }
    }

    fun isReceipt(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        val path = uri.rawPath.orEmpty()
        val excluded = notReceiptPaths.entries
            .firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }
            ?.value.orEmpty()
        if (excluded.any { path.equals(it, ignoreCase = true) }) return false
        val prefixes = receiptPathPrefixes.entries
            .firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }
            ?.value
            ?: return true
        return prefixes.any { path.startsWith(it) }
    }
}

class CompositeReceiptFilter(
    private val filters: List<ReceiptFilter>,
) : ReceiptFilter {
    override fun filter(messageBody: String, sender: String): FilterResult {
        for (filter in filters) {
            val result = filter.filter(messageBody, sender)
            if (result.matchedLinks.isNotEmpty()) return result
        }
        return FilterResult(emptyList(), sender)
    }
}

class HeuristicReceiptFilter : ReceiptFilter {

    private val receiptKeywords = listOf(
        "קבלה", "חשבונית", "פתקית", "רכישה", "תשלום", "הזמנה",
        "receipt", "invoice", "order confirmation",
    )
    private val negativeKeywords = listOf(
        "מבצע", "הנחה", "הצטרפ", "הירשם", "sale", "promo",
    )
    private val urlPattern = Regex("""https?://[^\s֐-׿]+""")

    override fun filter(messageBody: String, sender: String): FilterResult {
        val bodyLower = messageBody.lowercase()
        val hasKeyword = receiptKeywords.any { bodyLower.contains(it) }
        if (!hasKeyword) return FilterResult(emptyList(), sender)

        val hasNegative = negativeKeywords.any { bodyLower.contains(it) }
        if (hasNegative) return FilterResult(emptyList(), sender)

        val urls = urlPattern.findAll(messageBody).map { it.value }
            .filter { ReceiptLinks.isReceipt(it) && ReceiptLinks.textAllows(it, messageBody) }
            .toList()
        return FilterResult(matchedLinks = urls, senderLabel = sender)
    }
}

class DomainMatchFilter(config: MerchantConfig) : ReceiptFilter {

    private val allowedDomains: Set<String> = config.getAllDomains()
    private val urlPattern = Regex("""https?://[^\s֐-׿]+""")

    override fun filter(messageBody: String, sender: String): FilterResult {
        val matchedLinks = urlPattern.findAll(messageBody)
            .map { it.value }
            .filter { url -> isOnList(url) && ReceiptLinks.isReceipt(url) && ReceiptLinks.textAllows(url, messageBody) }
            .toList()

        return FilterResult(
            matchedLinks = matchedLinks,
            senderLabel = sender,
        )
    }

    private fun isOnList(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false

        return allowedDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }
}
