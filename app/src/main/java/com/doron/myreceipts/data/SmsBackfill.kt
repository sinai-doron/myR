package com.doron.myreceipts.data

import android.content.Context
import android.provider.Telephony
import com.doron.myreceipts.receiver.buildStrictFilter
import com.doron.myreceipts.worker.ReceiptDownloadWorker

object SmsBackfill {

    private const val PREFS_NAME = "myreceipts_prefs"
    private const val KEY_BACKFILL_DONE = "backfill_done"

    fun isBackfillDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKFILL_DONE, false)

    suspend fun backfillIfNeeded(context: Context, dao: ReceiptDao) {
        if (isBackfillDone(context)) return

        val config = MerchantConfig.load(context)
        val filter = buildStrictFilter(config)
        val now = System.currentTimeMillis()
        val downloadCutoff = now - 30L * 24 * 60 * 60 * 1000

        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        ) ?: return

        cursor.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val sender = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val timestamp = it.getLong(dateIdx)

                val result = filter.filter(body, sender)
                for (link in result.matchedLinks) {
                    val clientId = generateClientId(link, timestamp)
                    dao.insert(
                        CapturedReceiptEntity(
                            link = link,
                            senderLabel = result.senderLabel,
                            timestamp = timestamp,
                            clientId = clientId,
                            capturedAt = now,
                        )
                    )
                    if (timestamp >= downloadCutoff) {
                        ReceiptDownloadWorker.enqueue(context, link, clientId)
                    }
                }
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACKFILL_DONE, true)
            .apply()
    }
}
