package com.neonsheep.myreceipts.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * doron-backend's HTTP API: mobile sign-in (auth design §6) and the receipts app.
 *
 * Tokens travel only in JSON bodies and the Authorization header, never in a URL, and nothing
 * here logs a token, a receipt link or a PDF.
 */
class BackendApi(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val base = baseUrl.trimEnd('/')

    suspend fun challenge(): Challenge =
        post("/api/auth/mobile/challenge", ChallengeRequest(platform = PLATFORM))

    suspend fun signInWithGoogle(challengeId: String, idToken: String, deviceLabel: String): SignInResponse =
        post(
            "/api/auth/mobile/google",
            SignInRequest(challengeId, idToken, Device(platform = PLATFORM, label = deviceLabel)),
        )

    suspend fun refresh(refreshToken: String): IssuedTokens =
        post("/api/auth/token/refresh", RefreshRequest(refreshToken))

    suspend fun logout(accessToken: String) {
        call(request("/api/auth/logout", accessToken).post(EMPTY_BODY).build()) { }
    }

    /**
     * Asks the backend to turn a receipt link into a PDF and writes it to [target].
     * The file is written to a temporary sibling first, so a failed download never leaves a
     * half-written PDF behind. Returns the amount paid, in shekels, when the backend read one from
     * the receipt page (X-Receipt-Total), or null.
     */
    suspend fun downloadReceiptPdf(
        accessToken: String,
        receiptUrl: String,
        target: File,
        background: Boolean = false,
    ): Double? {
        val body = json.encodeToString(ReceiptPdfRequest(receiptUrl)).toRequestBody(JSON)
        val request = request("/api/receipts/pdf", accessToken).post(body)
            // Catch-up of old receipts: the backend may hold these back to keep new receipts fast.
            .apply { if (background) header("X-Receipt-Priority", "background") }
            .build()
        return call(request, pdfClient()) { response ->
            val type = response.header("Content-Type").orEmpty()
            if (!type.startsWith("application/pdf")) throw BackendException.Http(response.code)
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "${target.name}.part")
            try {
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                if (partial.length() == 0L) throw BackendException.Http(response.code)
                if (!partial.renameTo(target)) throw IOException("could not move the PDF into place")
            } finally {
                partial.delete()
            }
            Totals.parse(response.header("X-Receipt-Total"))
        }
    }

    private suspend inline fun <reified Req, reified Res> post(path: String, body: Req): Res {
        val request = request(path, accessToken = null)
            .post(json.encodeToString(body).toRequestBody(JSON))
            .build()
        return call(request) { response -> json.decodeFromString<Res>(response.body.string()) }
    }

    private fun request(path: String, accessToken: String?): Request.Builder =
        Request.Builder().url(base + path).apply {
            if (accessToken != null) header("Authorization", "Bearer $accessToken")
        }

    private suspend fun <T> call(request: Request, http: OkHttpClient = client, onSuccess: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw BackendException.Network(e)
            }
            response.use {
                when {
                    it.isSuccessful -> onSuccess(it)
                    it.code == 401 -> throw BackendException.Unauthorized
                    it.code == 403 -> throw BackendException.NotAllowed
                    it.code == 429 -> throw BackendException.RateLimited(it.header("Retry-After")?.toLongOrNull() ?: 60)
                    else -> throw BackendException.Http(it.code)
                }
            }
        }

    // Rendering a receipt takes a headless browser on the server up to ~45 seconds, plus the
    // server's warm-up when it has scaled to zero (an instance and then Chrome starting).
    private fun pdfClient(): OkHttpClient = client.newBuilder()
        .readTimeout(PDF_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(PDF_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class ChallengeRequest(val platform: String)

    @Serializable
    data class Challenge(val challengeId: String, val nonce: String)

    @Serializable
    private data class Device(val platform: String, val label: String)

    @Serializable
    private data class SignInRequest(val challengeId: String, val idToken: String, val device: Device)

    @Serializable
    private data class RefreshRequest(val refreshToken: String)

    @Serializable
    private data class ReceiptPdfRequest(val url: String)

    object Totals {
        private val PLAIN_AMOUNT = Regex("""\d{1,7}(\.\d{1,2})?""")

        /** The X-Receipt-Total header's amount, or null when absent or not a plain positive amount. */
        fun parse(value: String?): Double? =
            value?.trim()?.takeIf { PLAIN_AMOUNT.matches(it) }?.toDouble()?.takeIf { it > 0 }
    }

    @Serializable
    data class User(val id: String, val email: String, val displayName: String? = null)

    @Serializable
    data class IssuedTokens(val accessToken: String, val accessTokenExpiresIn: Long, val refreshToken: String)

    @Serializable
    data class SignInResponse(
        val accessToken: String,
        val accessTokenExpiresIn: Long,
        val refreshToken: String,
        val user: User,
    )

    companion object {
        private const val PLATFORM = "android"
        private val JSON = "application/json".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private val json = Json { ignoreUnknownKeys = true }

        // The backend scales to zero, so the first call after a quiet spell waits for a new
        // instance to start (about 10 seconds on staging, seen up to 24 with Chrome's first
        // launch). These leave room for that on a slow mobile connection.
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 60L
        const val PDF_READ_TIMEOUT_SECONDS = 150L
        const val PDF_CALL_TIMEOUT_SECONDS = 180L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

sealed class BackendException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No valid session: the access or refresh token was refused. */
    data object Unauthorized : BackendException("unauthorized") {
        private fun readResolve(): Any = Unauthorized
    }

    /** Google sign-in worked but this account is not on the backend's allowlist. */
    data object NotAllowed : BackendException("not allowed") {
        private fun readResolve(): Any = NotAllowed
    }

    class RateLimited(val retryAfterSeconds: Long) : BackendException("rate limited")
    class Http(val code: Int) : BackendException("HTTP $code")
    class Network(cause: IOException) : BackendException("network: ${cause.message}", cause)
}
