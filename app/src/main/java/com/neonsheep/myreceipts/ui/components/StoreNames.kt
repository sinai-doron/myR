package com.neonsheep.myreceipts.ui.components

import androidx.compose.ui.text.intl.Locale

/**
 * Hebrew names of the stores whose receipts come by SMS, keyed by the SMS sender name in lower
 * case. A store that isn't here is shown by its sender name only.
 */
private val HEBREW_NAMES = mapOf(
    "shufersal" to "שופרסל",
    "osherad" to "אושר עד",
    "pairzon" to "פיירזון",
    "yochananof" to "יוחננוף",
    "carrefour" to "קרפור",
    "victory" to "ויקטורי",
    "rami levy" to "רמי לוי",
    "delek" to "דלק",
    "paz" to "פז",
    "sonol" to "סונול",
    "superpharm" to "סופר-פארם",
    "super-pharm" to "סופר-פארם",
    "ikea" to "איקאה",
    "decathlon" to "דקתלון",
    "lego" to "לגו",
    "movieland" to "מובילנד",
)

/** The store's Hebrew name, if we know it. */
fun hebrewStoreName(senderLabel: String): String? = HEBREW_NAMES[senderLabel.lowercase()]

private fun hebrewApp(): Boolean = Locale.current.language.let { it == "he" || it == "iw" }

/** The name to show: Hebrew in the Hebrew app when known, otherwise the SMS sender name. */
fun storeDisplayName(senderLabel: String): String =
    if (hebrewApp()) hebrewStoreName(senderLabel) ?: senderLabel else senderLabel

/** In the English app, the Hebrew name shown beside the sender name, as it appears on the receipt. */
fun storeSecondaryName(senderLabel: String): String? =
    if (hebrewApp()) null else hebrewStoreName(senderLabel)
