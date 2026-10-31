package com.neonsheep.myreceipts.auth

import android.content.Context
import android.util.Log
import com.neonsheep.myreceipts.BuildConfig
import com.neonsheep.myreceipts.net.BackendApi
import com.neonsheep.myreceipts.net.BackendException
import java.io.File

/** The app's one connection to doron-backend: the API client and the sign-in state. */
object Backend {
    private const val TAG = "Backend"

    @Volatile
    private var auth: AuthRepository? = null
    val api: BackendApi by lazy { BackendApi(BuildConfig.BACKEND_URL) }

    fun auth(context: Context): AuthRepository =
        auth ?: synchronized(this) {
            auth ?: AuthRepository(api, EncryptedSessionStore(context.applicationContext)).also { auth = it }
        }

    /** Where a receipt's PDF is kept on the phone, whoever produced it. */
    fun pdfFile(context: Context, clientId: String): File =
        File(File(context.filesDir, "receipts"), "$clientId.pdf")

    /**
     * Sends a receipt link to the backend and saves the PDF it returns.
     * Returns the file's path, or null when signed out or the backend couldn't make one.
     * Never logs the link: it can identify the shopper.
     */
    suspend fun fetchReceiptPdf(
        context: Context,
        url: String,
        clientId: String,
        background: Boolean = false,
    ): Result<String> {
        val auth = auth(context)
        if (auth.currentUser() == null) return Result.failure(BackendException.Unauthorized)
        val target = pdfFile(context, clientId)
        return try {
            auth.withAccessToken { token -> api.downloadReceiptPdf(token, url, target, background) }
            Log.d(TAG, "Backend PDF saved for $clientId (${target.length()} bytes)")
            Result.success(target.absolutePath)
        } catch (e: BackendException) {
            Log.w(TAG, "Backend PDF failed for $clientId: ${e.message}")
            Result.failure(e)
        }
    }
}
