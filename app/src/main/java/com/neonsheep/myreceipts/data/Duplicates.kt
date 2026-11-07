package com.neonsheep.myreceipts.data

import kotlin.math.roundToLong

/**
 * Receipts that look like the same purchase: the same store and amount within [WINDOW_MS] (a store
 * that sent its text twice, or a link opened from two texts). Only flagged: the user decides, and
 * nothing is deleted on its own.
 */
object Duplicates {
    const val WINDOW_MS = 10 * 60 * 1000L

    /** What a receipt is compared by. [store] and [at] are the effective ones, as the user set them. */
    data class Candidate(
        val id: String,
        val store: String,
        val amount: Double?,
        val at: Long,
        val notDuplicate: Boolean,
    )

    /**
     * The groups of receipts that look like one, each oldest first, the newest group first. A
     * receipt without an amount is never in one (nothing to compare), nor one the user said isn't.
     */
    fun groups(candidates: List<Candidate>): List<List<String>> =
        candidates
            .filter { it.amount != null && !it.notDuplicate }
            .groupBy { it.store.trim().lowercase() to (it.amount!! * 100).roundToLong() }
            .values
            .flatMap { sameStoreAndAmount ->
                // Runs where each one is within the window of the one before it.
                val runs = mutableListOf<MutableList<Candidate>>()
                sameStoreAndAmount.sortedBy { it.at }.forEach { c ->
                    val run = runs.lastOrNull()
                    if (run != null && c.at - run.last().at <= WINDOW_MS) run += c else runs += mutableListOf(c)
                }
                runs.filter { it.size > 1 }
            }
            .sortedByDescending { run -> run.last().at }
            .map { run -> run.map { it.id } }

    /**
     * [group] as one receipt: the one with a PDF, else with an amount, else the oldest, with
     * whatever the user set on the others and it lacks. The others are what to delete.
     */
    fun merge(group: List<CapturedReceiptEntity>): Pair<CapturedReceiptEntity, List<CapturedReceiptEntity>> {
        require(group.size > 1) { "A merge needs two receipts or more" }
        val kept = group.sortedWith(
            compareByDescending<CapturedReceiptEntity> { it.pdfPath != null }
                .thenByDescending { it.totalAmount != null }
                .thenBy { it.timestamp },
        ).first()
        val others = group.filter { it.clientId != kept.clientId }
        fun <T> first(own: T?, of: (CapturedReceiptEntity) -> T?): T? = own ?: others.firstNotNullOfOrNull(of)
        val withAmount = if (kept.totalAmount == null) others.firstOrNull { it.totalAmount != null } else null
        return kept.copy(
            storeName = first(kept.storeName) { it.storeName },
            purchasedAt = first(kept.purchasedAt) { it.purchasedAt },
            category = first(kept.category) { it.category },
            returnBy = first(kept.returnBy) { it.returnBy },
            warrantyUntil = first(kept.warrantyUntil) { it.warrantyUntil },
            totalAmount = withAmount?.totalAmount ?: kept.totalAmount,
            totalSource = withAmount?.totalSource ?: kept.totalSource,
        ) to others
    }
}
