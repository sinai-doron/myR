package com.neonsheep.myreceipts.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neonsheep.myreceipts.auth.Backend
import com.neonsheep.myreceipts.data.AppDatabase
import com.neonsheep.myreceipts.data.ReceiptAmounts
import com.neonsheep.myreceipts.data.PdfStatus
import com.neonsheep.myreceipts.net.BackendException
import java.io.File
import java.util.concurrent.TimeUnit

class ReceiptDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val clientId = inputData.getString(KEY_CLIENT_ID) ?: return Result.failure()

        val dao = AppDatabase.getInstance(applicationContext).receiptDao()
        val finalFile = Backend.pdfFile(applicationContext, clientId)
        if (finalFile.exists()) {
            dao.updatePdfPath(clientId, finalFile.absolutePath)
            return Result.success()
        }
        Log.d(TAG, "Getting PDF for $clientId")

        // Signed in: the backend fetches the store's own PDF. While it can't be reached or is busy,
        // try again later (the link may still work for days); the phone prints the page only once
        // those retries are used up, or when signed out.
        val fromBackend = Backend.fetchReceiptPdf(applicationContext, url, clientId)
        fromBackend.getOrNull()?.let { pdf ->
            ReceiptAmounts.pdfSaved(dao, clientId, pdf.path, pdf.total)
            Log.d(TAG, "PDF saved from the backend for $clientId")
            return Result.success()
        }
        when (val e = fromBackend.exceptionOrNull()) {
            // The store no longer has it, or the backend doesn't serve this store yet (it logs the
            // site, which e-mails the owner to add it). Either way, don't print a page on the phone:
            // "Get PDF again" fetches the store's own PDF once the store is added.
            is BackendException.Http -> if (e.code == 410 || e.code == 400) {
                dao.updatePdfStatus(clientId, if (e.code == 410) PdfStatus.GONE else PdfStatus.UNSUPPORTED)
                Log.w(TAG, "No PDF for $clientId from the backend: HTTP ${e.code}")
                return Result.success()
            }
            else -> Unit
        }
        if (backendWorthRetrying(fromBackend.exceptionOrNull()) && runAttemptCount < BACKEND_ATTEMPTS) {
            Log.w(TAG, "Backend unavailable for $clientId, retrying later (attempt ${runAttemptCount + 1})")
            return Result.retry()
        }

        val pdfPath = ReceiptPdfDownloader(applicationContext).download(url, clientId)
            ?.let { keepFirst(File(it), finalFile) }
        if (pdfPath != null) {
            ReceiptAmounts.pdfSaved(dao, clientId, pdfPath, pageTotal = null)
            Log.d(TAG, "PDF saved by the phone for $clientId")
            return Result.success()
        }

        Log.w(TAG, "No PDF for $clientId")
        return if (runAttemptCount < BACKEND_ATTEMPTS + 2) Result.retry() else Result.success()
    }

    /** Network trouble, a busy or failing backend: worth another go. Signed out or refused: not. */
    private fun backendWorthRetrying(e: Throwable?): Boolean = when (e) {
        is BackendException.Network, is BackendException.RateLimited -> true
        is BackendException.Http -> e.code >= 500
        else -> false
    }

    /** Moves the phone's PDF into place, unless another PDF got there first. */
    private fun keepFirst(made: File, target: File): String {
        if (target.exists() || !made.renameTo(target)) made.delete()
        return target.absolutePath
    }

    companion object {
        private const val TAG = "ReceiptDownload"
        // Backend tries before the phone prints instead: about 15 minutes with the backoff above.
        private const val BACKEND_ATTEMPTS = 5
        private const val KEY_URL = "url"
        private const val KEY_CLIENT_ID = "clientId"

        fun enqueue(context: Context, url: String, clientId: String) {
            val data = Data.Builder()
                .putString(KEY_URL, url)
                .putString(KEY_CLIENT_ID, clientId)
                .build()

            val request = OneTimeWorkRequestBuilder<ReceiptDownloadWorker>()
                .setInputData(data)
                // Nothing to do offline; and back off 30 s, 1, 2, 4, 8 min… between attempts
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("pdf_$clientId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
