package com.neonsheep.myreceipts.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neonsheep.myreceipts.auth.Backend
import com.neonsheep.myreceipts.net.BackendException
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Failure reports, kept on the phone until they can be sent to our server, and only while the user
 * leaves them on (Settings; on by default). Saved as one line each in a small file, newest
 * [QUEUE_MAX] only, so a crash can be written in the moment before the app ends.
 */
object FailureReports {
    private const val TAG = "FailureReports"
    private const val PREFS = "failure_reports"
    private const val KEY_ENABLED = "enabled"
    private const val QUEUE_FILE = "failure-reports.jsonl"
    private const val WORK = "send-failure-reports"
    internal const val QUEUE_MAX = 50

    @Volatile
    private var app: Context? = null

    /** Called once, from the Application: from here on reports can be recorded. */
    fun init(context: Context) {
        app = context.applicationContext
        if (enabled(context) && queued(context).isNotEmpty()) scheduleSend(context)
    }

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /** Turning reports off also forgets any not yet sent. */
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) {
            synchronized(this) { queueFile(context).delete() }
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }

    /** The app crashed: written now, synchronously, and sent the next time the app runs. */
    fun recordCrash(error: Throwable) {
        val context = app ?: return
        if (!enabled(context)) return
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        append(context, FailureReport.crashOf(error, version, Build.VERSION.SDK_INT, Build.MODEL ?: "unknown"))
    }

    /** A receipt text whose site the backend doesn't serve: its site and sender, never the link or text. */
    fun unsupportedSite(link: String, sender: String) {
        val domain = FailureReport.domainOf(link) ?: return
        record(FailureReport.UnsupportedSite(domain, FailureReport.senderOf(sender)))
    }

    /** A receipt's PDF with no amount found: its site. A file or scan has no site, so isn't reported. */
    fun totalNotFound(link: String) {
        val domain = FailureReport.domainOf(link) ?: return
        record(FailureReport.TotalNotFound(domain))
    }

    private fun record(report: FailureReport) {
        val context = app ?: return
        if (!enabled(context)) return
        append(context, report)
        scheduleSend(context)
    }

    internal fun queued(context: Context): List<FailureReport> = synchronized(this) {
        val file = queueFile(context)
        if (!file.exists()) return emptyList()
        val lines = file.readLines().filter { it.isNotBlank() }
        val parsed = lines.map { line ->
            line to runCatching { FailureReport.json.decodeFromString(FailureReport.serializer(), line) }.getOrNull()
        }
        // A line that no longer reads (an older shape) is removed, never sent, so the queue's lines and
        // what [drop] counts stay the same.
        if (parsed.any { it.second == null }) {
            val kept = parsed.filter { it.second != null }.map { it.first }
            if (kept.isEmpty()) file.delete() else file.writeText(kept.joinToString("\n", postfix = "\n"))
        }
        parsed.mapNotNull { it.second }
    }

    private fun append(context: Context, report: FailureReport): Unit = synchronized(this) {
        try {
            val file = queueFile(context)
            val lines = (if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()) +
                FailureReport.json.encodeToString(FailureReport.serializer(), report)
            file.writeText(lines.takeLast(QUEUE_MAX).joinToString("\n", postfix = "\n"))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save a failure report: ${e.javaClass.simpleName}")
        }
    }

    /** Removes the oldest [count] reports, once they're sent or the server refused them. */
    internal fun drop(context: Context, count: Int) {
        synchronized(this) {
            val file = queueFile(context)
            if (!file.exists()) return
            val rest = file.readLines().filter { it.isNotBlank() }.drop(count)
            if (rest.isEmpty()) file.delete() else file.writeText(rest.joinToString("\n", postfix = "\n"))
        }
    }

    private fun scheduleSend(context: Context) {
        val request = OneTimeWorkRequestBuilder<SendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // A few minutes' wait gathers a burst into one request.
            .setInitialDelay(2, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun queueFile(context: Context) = File(context.filesDir, QUEUE_FILE)

    /** Sends what's saved, [FailureReport.BATCH_MAX] at a time, while signed in. */
    class SendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val context = applicationContext
            if (!enabled(context)) return Result.success()
            val auth = Backend.auth(context)
            // Signed out: kept until the next sign-in and report.
            if (auth.currentUser() == null) return Result.success()
            repeat(QUEUE_MAX / FailureReport.BATCH_MAX + 1) {
                val batch = queued(context).take(FailureReport.BATCH_MAX)
                if (batch.isEmpty()) return Result.success()
                try {
                    auth.withAccessToken { token -> Backend.api.sendReports(token, FailureReport.batch(batch)) }
                    drop(context, batch.size)
                } catch (e: BackendException) {
                    when {
                        // Refused: sending it again won't change that.
                        e is BackendException.Http && e.code in 400..499 -> drop(context, batch.size)
                        e is BackendException.Unauthorized -> return Result.success()
                        else -> return Result.retry()
                    }
                }
            }
            return Result.success()
        }
    }
}
