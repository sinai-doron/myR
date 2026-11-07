package com.neonsheep.myreceipts.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * What the app tells our server when something goes wrong (spec: failure reports, to our own server only).
 * The shapes match doron-backend's `receipt-report.ts`, which refuses anything else. They never hold a
 * link, a message, an amount or the text of an error: a crash is its exception's class and the code
 * locations it went through; a site is its hostname; a sender is the SMS sender's name.
 * No Android here, so it's unit tested.
 */
@Serializable
sealed class FailureReport {
    @Serializable
    @SerialName("crash")
    data class Crash(
        val exception: String,
        val cause: String? = null,
        val frames: List<String>,
        val appVersion: String,
        val androidVersion: Int,
        val model: String,
    ) : FailureReport()

    /** A text that looked like a receipt, from a site the backend doesn't serve yet. */
    @Serializable
    @SerialName("unsupported_site")
    data class UnsupportedSite(val domain: String, val sender: String) : FailureReport()

    /** A receipt's PDF whose amount couldn't be found. */
    @Serializable
    @SerialName("total_not_found")
    data class TotalNotFound(val domain: String) : FailureReport()

    companion object {
        const val FRAMES_MAX = 40
        const val BATCH_MAX = 20

        val json = Json { classDiscriminator = "kind"; encodeDefaults = false; explicitNulls = false }

        private val CLASS_NAME = Regex("^[A-Za-z0-9_.$]{1,200}$")
        private val FRAME = Regex("""^[A-Za-z0-9_.$<>-]+\((?:[A-Za-z0-9_.$-]+(?::\d{1,7})?|Unknown Source|Native Method)\)$""")
        private val LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
        private val HOSTNAME = Regex("^(?:$LABEL\\.)+(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
        private val NOT_SENDER = Regex("""[^\p{L}\p{N} +_.-]""")
        private val NOT_MODEL = Regex("""[^A-Za-z0-9 ._()+-]""")
        private val NOT_VERSION = Regex("""[^A-Za-z0-9._+-]""")

        /** A crash as the server takes it: class names and code locations, never the error's message. */
        fun crashOf(error: Throwable, appVersion: String, androidVersion: Int, model: String): Crash = Crash(
            exception = className(error.javaClass.name),
            cause = error.cause?.let { className(it.javaClass.name) },
            frames = error.stackTrace.asSequence().map(::frame).filter { FRAME.matches(it) && it.length <= 300 }
                .take(FRAMES_MAX).toList(),
            appVersion = appVersion.replace(NOT_VERSION, "").take(40).ifEmpty { "unknown" },
            androidVersion = androidVersion.coerceIn(1, 100),
            model = model.replace(NOT_MODEL, "").trim().take(60).ifEmpty { "unknown" },
        )

        /** A receipt link's site, or null when it has none the server would take (an IP address, say). */
        fun domainOf(link: String): String? {
            val host = try {
                URI(link).host?.lowercase()
            } catch (_: Exception) {
                null
            } ?: return null
            return host.takeIf { it.length <= 253 && HOSTNAME.matches(it) }
        }

        /** An SMS sender's name, kept to the letters, digits and marks a sender name has. */
        fun senderOf(label: String): String =
            label.replace(NOT_SENDER, "").trim().take(20).trim().ifEmpty { "unknown" }

        /** The request body for a batch of saved reports. */
        fun batch(reports: List<FailureReport>): String =
            json.encodeToString(Batch.serializer(), Batch(reports.take(BATCH_MAX)))

        private fun className(name: String) = name.takeIf { CLASS_NAME.matches(it) } ?: "unknown"

        private fun frame(e: StackTraceElement): String {
            val where = when {
                e.isNativeMethod -> "Native Method"
                e.fileName == null -> "Unknown Source"
                e.lineNumber >= 0 -> "${e.fileName}:${e.lineNumber}"
                else -> e.fileName!!
            }
            return "${e.className}.${e.methodName}($where)"
        }
    }

    @Serializable
    data class Batch(val reports: List<FailureReport>)
}
