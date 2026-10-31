package com.doron.myreceipts.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.doron.myreceipts.data.AppDatabase
import com.doron.myreceipts.data.CapturedReceiptEntity
import com.doron.myreceipts.data.CompositeReceiptFilter
import com.doron.myreceipts.data.DomainMatchFilter
import com.doron.myreceipts.data.HeuristicReceiptFilter
import com.doron.myreceipts.data.MerchantConfig
import com.doron.myreceipts.data.ReceiptDao
import com.doron.myreceipts.data.ReceiptFilter
import com.doron.myreceipts.data.generateClientId
import com.doron.myreceipts.worker.ReceiptDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        val config = MerchantConfig.load(context)
        val filter = buildFilter(config)
        val result = filter.filter(body, sender)
        if (result.matchedLinks.isEmpty()) return

        val pendingResult = goAsync()
        val dao = AppDatabase.getInstance(context).receiptDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReceiptCaptureHandler.processMessage(
                    context = context,
                    matchedLinks = result.matchedLinks,
                    senderLabel = result.senderLabel,
                    timestamp = timestamp,
                    dao = dao,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

fun buildFilter(config: MerchantConfig): ReceiptFilter =
    CompositeReceiptFilter(listOf(DomainMatchFilter(config), HeuristicReceiptFilter()))

fun buildStrictFilter(config: MerchantConfig): ReceiptFilter =
    DomainMatchFilter(config)

object ReceiptCaptureHandler {
    suspend fun processMessage(
        context: Context,
        matchedLinks: List<String>,
        senderLabel: String,
        timestamp: Long,
        dao: ReceiptDao,
    ) {
        val now = System.currentTimeMillis()
        for (link in matchedLinks) {
            val clientId = generateClientId(link, timestamp)
            dao.insert(
                CapturedReceiptEntity(
                    link = link,
                    senderLabel = senderLabel,
                    timestamp = timestamp,
                    clientId = clientId,
                    capturedAt = now,
                )
            )
            ReceiptDownloadWorker.enqueue(context, link, clientId)
        }
    }
}
