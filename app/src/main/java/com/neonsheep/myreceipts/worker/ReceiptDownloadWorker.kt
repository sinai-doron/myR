package com.neonsheep.myreceipts.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neonsheep.myreceipts.data.AppDatabase

class ReceiptDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val clientId = inputData.getString(KEY_CLIENT_ID) ?: return Result.failure()

        Log.d(TAG, "Downloading PDF for $clientId from $url")

        val downloader = ReceiptPdfDownloader(applicationContext)
        val pdfPath = downloader.download(url, clientId)

        if (pdfPath != null) {
            val dao = AppDatabase.getInstance(applicationContext).receiptDao()
            dao.updatePdfPath(clientId, pdfPath)
            Log.d(TAG, "PDF saved: $pdfPath")
            return Result.success()
        }

        Log.w(TAG, "No download button found for $clientId")
        return if (runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "ReceiptDownload"
        private const val KEY_URL = "url"
        private const val KEY_CLIENT_ID = "clientId"

        fun enqueue(context: Context, url: String, clientId: String) {
            val data = Data.Builder()
                .putString(KEY_URL, url)
                .putString(KEY_CLIENT_ID, clientId)
                .build()

            val request = OneTimeWorkRequestBuilder<ReceiptDownloadWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("pdf_$clientId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
