package com.neonsheep.myreceipts.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neonsheep.myreceipts.auth.Backend
import com.neonsheep.myreceipts.worker.DeadlineReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Export and import, for moving to a new phone: [ReceiptArchive] to and from a file the user picks. Import
 * adds what isn't here yet and never changes what is: a receipt already on the phone, a store's category
 * or a budget the user has set all stay as they are.
 */
object ReceiptBackup {
    private const val TAG = "ReceiptBackup"

    /** What an import did. */
    data class Imported(val added: Int, val alreadyHere: Int)

    /** Every receipt (not the made-up samples), with its PDF, categories and budgets, written to [uri]. */
    suspend fun export(context: Context, db: AppDatabase, uri: Uri, now: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) {
            val receipts = db.receiptDao().getAllOnce().filterNot { SampleReceipts.isSample(it.clientId) }
            val contents = ReceiptArchive.Contents(
                exportedAt = now,
                receipts = receipts.map { ReceiptArchive.Receipt.of(it, hasPdf = false) },
                storeCategories = db.spendingDao().storeCategories().first().associate { it.senderLabel to it.category },
                budgets = db.spendingDao().budgets().first().associate { it.category to it.monthlyLimit },
            )
            val paths = receipts.associate { it.clientId to it.pdfPath }
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("no output")
            out.use { stream ->
                ReceiptArchive.write(stream, contents) { id -> paths[id]?.let(::File) }
            }
            receipts.size
        }

    /** Adds the receipts in the archive at [uri] that aren't on this phone yet. Throws [ReceiptArchive.Unreadable]. */
    suspend fun import(context: Context, db: AppDatabase, uri: Uri): Imported = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "import-${System.nanoTime()}").apply { mkdirs() }
        try {
            val read = context.contentResolver.openInputStream(uri)?.use { ReceiptArchive.read(it, staging) }
                ?: throw ReceiptArchive.Unreadable(ReceiptArchive.Unreadable.Reason.DAMAGED)
            apply(context, db, read)
        } finally {
            staging.deleteRecursively()
        }
    }

    internal suspend fun apply(context: Context, db: AppDatabase, read: ReceiptArchive.Read): Imported {
        val dao = db.receiptDao()
        var added = 0
        var alreadyHere = 0
        for (r in read.contents.receipts) {
            if (dao.getByClientId(r.clientId) != null) {
                alreadyHere++
                continue
            }
            val pdfPath = read.pdfs[r.clientId]?.let { staged ->
                val target = Backend.pdfFile(context, r.clientId)
                target.parentFile?.mkdirs()
                try {
                    staged.copyTo(target, overwrite = true).absolutePath
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't save an imported PDF: ${e.javaClass.simpleName}")
                    null
                }
            }
            if (dao.insert(r.toEntity(pdfPath)) != -1L) {
                added++
                dao.getByClientId(r.clientId)?.let { DeadlineReminderWorker.schedule(context, it) }
            }
        }
        // Categories and budgets only where this phone has none yet.
        val spending = db.spendingDao()
        val categories = spending.storeCategories().first().map { it.senderLabel }.toSet()
        read.contents.storeCategories
            .filterKeys { it !in categories }
            .forEach { (store, category) -> spending.setStoreCategory(StoreCategoryEntity(store, category)) }
        val budgets = spending.budgets().first().map { it.category }.toSet()
        read.contents.budgets
            .filterKeys { it !in budgets }
            .forEach { (key, limit) -> spending.setBudget(BudgetEntity(key, limit)) }
        return Imported(added, alreadyHere)
    }
}
