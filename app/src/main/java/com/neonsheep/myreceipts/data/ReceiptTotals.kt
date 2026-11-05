package com.neonsheep.myreceipts.data

/**
 * The amount paid, from a receipt PDF's text as Android reads it (one line per text run, Hebrew
 * in reading order). No AI: the line labelled as the amount to pay, and the one amount on it.
 *
 * Weezmo's and Tabit's PDFs carry it ("סה"כ לתשלום ₪89.80", "לתשלום : 124.40", "סה"כ: ILS164.73").
 * Shufersal's and Pairzon's are images or have broken fonts; their amount comes from the backend,
 * which reads the receipt page ([TotalSource.PAGE]).
 *
 * The strongest kind of label found decides, and only if all its lines agree: a wrong amount is
 * worse than none, as the user can type one in.
 */
object ReceiptTotals {
    // Each label with spaces, colons, stars, direction marks and currency words removed, and ״ as ".
    // Index = kind: 0 is the strongest.
    private val KINDS: List<Set<String>> = listOf(
        setOf("סה\"כלתשלום", "סכוםלתשלום", "סה\"כתשלום"),
        setOf("לתשלום"),
        setOf("סה\"כ", "סה\"כשולם", "סכוםעסקה", "סכוםעיסקה"),
    )
    private val CURRENCY = Regex("""₪|ש"ח|ILS|NIS""")

    // An amount, with or without a currency next to it; a whole number counts only with one.
    private val MONEY = Regex("""(₪|ILS|NIS)?\s*(\d{1,3}(?:,\d{3})+|\d+)(\.\d{1,2})?\s*(₪|ש"ח)?""")

    fun fromText(text: String): Double? {
        val found = text.lines().mapNotNull { line -> candidate(normalise(line)) }
        if (found.isEmpty()) return null
        val strongest = found.minOf { it.first }
        val amounts = found.filter { it.first == strongest }.map { it.second }.toSet()
        return amounts.singleOrNull()?.let { it / 100.0 }
    }

    /** The label's kind and the amount in agorot, when [line] is a labelled amount. */
    private fun candidate(line: String): Pair<Int, Long>? {
        val amounts = mutableListOf<Long>()
        val label = MONEY.replace(line) { m ->
            val marked = m.groupValues[1].isNotEmpty() || m.groupValues[4].isNotEmpty()
            val decimals = m.groupValues[3]
            if (!marked && decimals.length != 3) return@replace m.value
            val shekels = m.groupValues[2].replace(",", "").toLong()
            val agorot = when (decimals.length) {
                0 -> 0L
                2 -> decimals.substring(1).toLong() * 10
                else -> decimals.substring(1).toLong()
            }
            amounts += shekels * 100 + agorot
            ""
        }
        val key = CURRENCY.replace(label, "").replace(Regex("""[\s*:]"""), "")
        val kind = KINDS.indexOfFirst { key in it }
        if (kind < 0 || amounts.size != 1 || amounts[0] <= 0) return null
        return kind to amounts[0]
    }

    private fun normalise(line: String): String =
        line.replace('״', '"').replace(Regex("[‎‏‪-‮]"), "")
}
