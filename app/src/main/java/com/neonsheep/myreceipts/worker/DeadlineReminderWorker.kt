package com.neonsheep.myreceipts.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neonsheep.myreceipts.MainActivity
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.data.AppDatabase
import com.neonsheep.myreceipts.data.CapturedReceiptEntity
import com.neonsheep.myreceipts.data.Deadline
import com.neonsheep.myreceipts.data.Reminders
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * Shows the reminder of a receipt's return or warranty deadline. Scheduled on the phone with
 * WorkManager, which keeps it across restarts; in every mode, as nothing leaves the phone.
 */
class DeadlineReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val clientId = inputData.getString(KEY_CLIENT_ID) ?: return Result.failure()
        val kind = inputData.getString(KEY_KIND)?.let { runCatching { Deadline.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val expected = inputData.getLong(KEY_DEADLINE, -1)
        val receipt = AppDatabase.getInstance(applicationContext).receiptDao().getByClientId(clientId)
        // Deleted, or the deadline changed or was cleared since: that change scheduled its own.
        if (receipt == null || kind.of(receipt) != expected) return Result.success()
        notify(applicationContext, receipt, kind, expected)
        return Result.success()
    }

    companion object {
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_KIND = "kind"
        private const val KEY_DEADLINE = "deadline"
        const val CHANNEL = "deadlines"

        private fun name(clientId: String, kind: Deadline) = "deadline-${kind.name}-$clientId"

        /** Schedules, moves or cancels [receipt]'s reminders to match its deadlines now. */
        fun schedule(context: Context, receipt: CapturedReceiptEntity, now: Long = System.currentTimeMillis()) {
            val work = WorkManager.getInstance(context)
            Reminders.plan(receipt, now).forEach { (kind, at) ->
                val deadline = kind.of(receipt)
                if (at == null || deadline == null) {
                    work.cancelUniqueWork(name(receipt.clientId, kind))
                    return@forEach
                }
                val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
                    .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
                    .setInputData(input(receipt.clientId, kind, deadline))
                    .addTag(CHANNEL)
                    .build()
                work.enqueueUniqueWork(name(receipt.clientId, kind), ExistingWorkPolicy.REPLACE, request)
            }
        }

        internal fun input(clientId: String, kind: Deadline, deadline: Long) =
            workDataOf(KEY_CLIENT_ID to clientId, KEY_KIND to kind.name, KEY_DEADLINE to deadline)

        internal fun uniqueName(clientId: String, kind: Deadline) = name(clientId, kind)

        /** Cancels a deleted receipt's reminders. */
        fun cancel(context: Context, clientId: String) {
            val work = WorkManager.getInstance(context)
            Deadline.entries.forEach { work.cancelUniqueWork(name(clientId, it)) }
        }

        /** Whether reminders can be shown: always before Android 13, after that only once allowed. */
        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /** The notification's id: one per receipt and deadline, so a new one replaces the old. */
        fun notificationId(clientId: String, kind: Deadline): Int = name(clientId, kind).hashCode()

        internal fun notify(context: Context, receipt: CapturedReceiptEntity, kind: Deadline, deadline: Long) {
            if (!canNotify(context)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.channel_deadlines),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .format(Instant.ofEpochMilli(deadline).atZone(ZoneId.systemDefault()))
            val (title, text) = when (kind) {
                Deadline.RETURN -> R.string.reminder_return_title to R.string.reminder_return_text
                Deadline.WARRANTY -> R.string.reminder_warranty_title to R.string.reminder_warranty_text
            }
            val open = PendingIntent.getActivity(
                context,
                notificationId(receipt.clientId, kind),
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_RECEIPT, receipt.clientId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(title, receipt.store))
                .setContentText(context.getString(text, day))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(notificationId(receipt.clientId, kind), notification)
            } catch (_: SecurityException) {
                // Taken away between the check and now: nothing to show.
            }
        }
    }
}
