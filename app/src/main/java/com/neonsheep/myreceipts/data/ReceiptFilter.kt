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
 */
object ReceiptLinks {
    private val receiptPathPrefixes = mapOf(
        "wee.ai" to listOf("/r/"),
    )

    fun isReceipt(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        val prefixes = receiptPathPrefixes.entries
            .firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }
            ?.value
            ?: return true
        val path = uri.rawPath.orEmpty()
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

        val urls = urlPattern.findAll(messageBody).map { it.value }.filter(ReceiptLinks::isReceipt).toList()
        return FilterResult(matchedLinks = urls, senderLabel = sender)
    }
}

class DomainMatchFilter(config: MerchantConfig) : ReceiptFilter {

    private val allowedDomains: Set<String> = config.getAllDomains()
    private val urlPattern = Regex("""https?://[^\s֐-׿]+""")

    override fun filter(messageBody: String, sender: String): FilterResult {
        val matchedLinks = urlPattern.findAll(messageBody)
            .map { it.value }
            .filter { url -> isOnList(url) && ReceiptLinks.isReceipt(url) }
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
