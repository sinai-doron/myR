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
 *
 * Some PDFs put the labels in one column and the amounts in another, and Android reads them apart
 * with no positions (Shemrat Hazorea's order: "סה\"כ לתשלום", "סה\"כ אחרי הנחה", "₪ 2,841", ...).
 * Then an amount on its own line just after a bare "to pay" label is taken only when the receipt
 * adds up to it: an amount before VAT and its VAT, both on the page, that make it. Israeli
 * receipts show both, and the amount after a different label in the column doesn't add up.
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

    // VAT rates a receipt may show: 18% from 2025, 17% before.
    private val VAT_RATES = listOf(0.18, 0.17)

    // How many lines after a bare label its amount may be: past the column's other labels.
    private const val LINES_APART = 4

    fun fromText(text: String): Double? {
        val lines = text.lines().map(::normalise)
        val found = lines.mapNotNull { line -> candidate(line) }
        if (found.isEmpty()) return apartFromItsLabel(lines)?.let { it / 100.0 }
        val strongest = found.minOf { it.first }
        val amounts = found.filter { it.first == strongest }.map { it.second }.toSet()
        return amounts.singleOrNull()?.let { it / 100.0 }
    }

    /** The label's kind and the amount in agorot, when [line] is a labelled amount. */
    private fun candidate(line: String): Pair<Int, Long>? {
        val (key, amounts) = split(line)
        val kind = KINDS.indexOfFirst { key in it }
        if (kind < 0 || amounts.size != 1 || amounts[0] <= 0) return null
        return kind to amounts[0]
    }

    /**
     * The amount, in agorot, shown on its own line just after a strongest label that has none,
     * when exactly one such amount is what an amount before VAT and its VAT on the page add up to.
     */
    private fun apartFromItsLabel(lines: List<String>): Long? {
        val split = lines.map(::split)
        val onPage = split.flatMap { it.second }.filter { it > 0 }.toSet()
        val nearLabels = split.indices
            .filter { i -> split[i].first in KINDS[0] && split[i].second.isEmpty() }
            .flatMap { i ->
                split.subList(i + 1, minOf(split.size, i + 1 + LINES_APART))
                    .filter { (key, amounts) -> key.isEmpty() && amounts.size == 1 }
                    .map { it.second[0] }
            }
            .filter { it > 0 }
            .toSet()
        return nearLabels.filter { addsUp(it, onPage) }.singleOrNull()
    }

    /** Whether [total] is some amount on the page plus its VAT, also on the page (to the shekel). */
    private fun addsUp(total: Long, onPage: Set<Long>): Boolean = onPage.any { beforeVat ->
        val vat = total - beforeVat
        vat > 0 && vat in onPage &&
            VAT_RATES.any { rate -> kotlin.math.abs(vat - beforeVat * rate) <= 100 }
    }

    /** The line's label key, and every amount on it in agorot. */
    private fun split(line: String): Pair<String, List<Long>> {
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
        return key to amounts
    }

    private fun normalise(line: String): String =
        line.replace('״', '"').replace(Regex("[‎‏‪-‮]"), "")
}
