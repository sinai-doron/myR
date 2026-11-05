package com.neonsheep.myreceipts.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neonsheep.myreceipts.auth.Backend
import com.neonsheep.myreceipts.data.AppDatabase
import com.neonsheep.myreceipts.worker.BackfillPolicy.Next
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Catches up on old receipts that have no PDF, gently (see [BackfillPolicy]): a few at a time, on
 * Wi-Fi, through the backend only and marked as background work. New receipts don't wait for it;
 * they have their own download the moment the SMS arrives.
 */
class ReceiptBackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (Backend.auth(context).currentUser() == null) return Result.success() // signing in restarts it
        val dao = AppDatabase.getInstance(context).receiptDao()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var budget = BackfillPolicy.Budget(
            day = runCatching { LocalDate.parse(prefs.getString(KEY_DAY, "")) }.getOrDefault(LocalDate.MIN),
            used = prefs.getInt(KEY_USED, 0),
        ).forToday(LocalDate.now())

        if (budget.remaining == 0) {
            schedule(context, BackfillPolicy.minutesUntilTomorrow(LocalTime.now().let { it.hour * 60 + it.minute }))
            return Result.success()
        }

        val batch = dao.backfillCandidates(minOf(BackfillPolicy.BATCH, budget.remaining))
        if (batch.isEmpty()) {
            Log.d(TAG, "Nothing left to catch up")
            return Result.success()
        }

        for ((index, receipt) in batch.withIndex()) {
            if (index > 0) delay(TimeUnit.SECONDS.toMillis(BackfillPolicy.GAP_SECONDS))
            if (isStopped) return Result.success()

            val result = Backend.fetchReceiptPdf(context, receipt.link, receipt.clientId, background = true)
            budget = budget.spend()
            prefs.edit().putString(KEY_DAY, budget.day.toString()).putInt(KEY_USED, budget.used).apply()

            val error = result.exceptionOrNull()
            result.getOrNull()?.let { path -> dao.updatePdfPath(receipt.clientId, path) }
            BackfillPolicy.verdictFor(error)?.let { dao.updatePdfStatus(receipt.clientId, it) }

            when (val next = BackfillPolicy.nextAfter(error)) {
                Next.Continue -> Unit
                Next.Stop -> return Result.success()
                is Next.Pause -> {
                    Log.w(TAG, "Backend busy or unreachable, pausing ${next.minutes} min")
                    schedule(context, next.minutes)
                    return Result.success()
                }
            }
        }
        schedule(context, BackfillPolicy.NEXT_BATCH_MINUTES)
        return Result.success()
    }

    companion object {
        private const val TAG = "ReceiptBackfill"
        private const val WORK = "receipt_backfill"
        private const val PREFS = "receipt_backfill"
        private const val KEY_DAY = "day"
        private const val KEY_USED = "used"

        /** Starts the catch-up if it isn't already queued or running. Safe to call on every launch. */
        fun start(context: Context) = enqueue(context, delayMinutes = 0, policy = ExistingWorkPolicy.KEEP)

        private fun schedule(context: Context, minutes: Long) =
            enqueue(context, delayMinutes = minutes, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)

        private fun enqueue(context: Context, delayMinutes: Long, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<ReceiptBackfillWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi, not mobile data
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, request)
        }
    }
}
