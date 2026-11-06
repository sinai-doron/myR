package com.neonsheep.myreceipts.data

import com.neonsheep.myreceipts.R
import androidx.annotation.StringRes
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** What a store's receipts are counted as. Stored by [name]. */
enum class Category(@param:StringRes val labelRes: Int, val emoji: String) {
    GROCERIES(R.string.category_groceries, "🛒"),
    EATING_OUT(R.string.category_eating_out, "☕"),
    HOME(R.string.category_home, "🏠"),
    HEALTH(R.string.category_health, "💊"),
    TRANSPORT(R.string.category_transport, "⛽"),
    CLOTHING(R.string.category_clothing, "👟"),
    LEISURE(R.string.category_leisure, "🎬"),
    OTHER(R.string.category_other, "🧾"),
    ;

    companion object {
        fun fromName(name: String?): Category? = entries.firstOrNull { it.name == name }

        // A first guess from the store's name, for the stores whose receipts come by SMS. The user
        // can change any of them; their choice is kept per store.
        private val GUESSES: List<Pair<List<String>, Category>> = listOf(
            listOf("shufersal", "yochananof", "carrefour", "osher", "alonit", "rami", "victory", "tiv taam") to GROCERIES,
            listOf("nonomimi", "buytheway", "aroma", "cofix", "greg", "wolt") to EATING_OUT,
            listOf("ikea", "jumbo", "maxstock", "teura", "home center", "kravitz") to HOME,
            listOf("superpharm", "super-pharm", "good pharm") to HEALTH,
            listOf("delek", "doralon", "dor alon", "paz", "sonol", "pango", "cellopark") to TRANSPORT,
            listOf("decathlon", "delta", "castro", "fox", "zara", "h&m", "terminal") to CLOTHING,
            listOf("movieland", "lego", "cinema", "yes planet", "toys") to LEISURE,
        )

        /** The category a store starts in, from its SMS sender name. */
        fun guessFor(senderLabel: String): Category {
            val name = senderLabel.lowercase()
            return GUESSES.firstOrNull { (hints, _) -> hints.any { name.contains(it) } }?.second ?: OTHER
        }
    }
}

/**
 * One receipt, as spending sees it: which store, when, and the amount paid if known. [category] is the
 * receipt's own category when the user moved it; null counts it in its store's.
 */
data class Spend(val store: String, val timestamp: Long, val amount: Double?, val category: Category? = null)

/** How much went on what in one month. Amounts in shekels. */
data class MonthSpending(
    val month: YearMonth,
    val total: Double,
    val byCategory: Map<Category, Double>,
    /** Each store's total, largest first, with the category it counts as. */
    val byStore: List<StoreSpending>,
    /** Receipts this month whose amount isn't known: not in any total. */
    val withoutAmount: Int,
    val receipts: Int,
)

data class StoreSpending(val store: String, val category: Category, val total: Double, val receipts: Int)

/** Where a budget stands: spent of limit. */
data class BudgetStatus(val spent: Double, val limit: Double) {
    val fraction: Double get() = if (limit <= 0) 0.0 else spent / limit
    val left: Double get() = limit - spent
    val state: State
        get() = when {
            spent > limit -> State.OVER
            fraction >= NEAR -> State.NEAR
            else -> State.OK
        }

    enum class State { OK, NEAR, OVER }

    companion object {
        /** From this share of the budget on, it is "near the limit". */
        const val NEAR = 0.8
    }
}

object Spending {
    /** The month a receipt counts in: when its text arrived, on this phone's clock. */
    fun monthOf(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(zone))

    fun forMonth(
        month: YearMonth,
        receipts: List<Spend>,
        categoryOf: (String) -> Category,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthSpending {
        val inMonth = receipts.filter { monthOf(it.timestamp, zone) == month }
        val known = inMonth.filter { it.amount != null }
        val byStore = known.groupBy { it.store }
            .map { (store, rs) -> StoreSpending(store, categoryOf(store), rs.sumOf { it.amount!! }.cents(), rs.size) }
            .sortedByDescending { it.total }
        return MonthSpending(
            month = month,
            total = known.sumOf { it.amount!! }.cents(),
            // By each receipt's category, so a receipt moved to another category counts there.
            byCategory = known.groupBy { it.category ?: categoryOf(it.store) }
                .mapValues { (_, rs) -> rs.sumOf { it.amount!! }.cents() },
            byStore = byStore,
            withoutAmount = inMonth.size - known.size,
            receipts = inMonth.size,
        )
    }

    // Sums of shekel amounts, rounded back to whole agorot.
    private fun Double.cents(): Double = Math.round(this * 100) / 100.0
}
