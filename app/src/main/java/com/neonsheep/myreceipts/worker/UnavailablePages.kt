package com.neonsheep.myreceipts.worker

import kotlinx.serialization.json.Json

/**
 * Pages a vendor shows instead of the receipt, which must never be saved as one. Shufersal's
 * links expire after a while, and an expired one says "the invoice is being processed, it will be
 * available in a minute" for good. doron-backend keeps the same list for its renderer.
 */
private val UNAVAILABLE_MARKERS = listOf(
    "החשבונית בעיבוד", // Shufersal: "the invoice is being processed"
    "תוקף הכניסה שלך פג", // Pairzon (MAXstock): "your login has expired"; links last 5 minutes
)

/** [pageText] is `document.body.innerText`, as WebView's evaluateJavascript hands it back (JSON). */
internal fun isUnavailablePage(pageText: String?): Boolean {
    if (pageText.isNullOrBlank() || pageText == "null") return false
    val text = runCatching { Json.decodeFromString<String>(pageText) }.getOrDefault(pageText)
    return UNAVAILABLE_MARKERS.any { text.contains(it) }
}
