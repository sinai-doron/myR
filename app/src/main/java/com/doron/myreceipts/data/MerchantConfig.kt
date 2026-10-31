package com.doron.myreceipts.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MerchantConfig(
    val merchants: List<Merchant>,
    val receiptPlatforms: List<String> = emptyList(),
) {
    @Serializable
    data class Merchant(
        val name: String,
        val domains: List<String>,
        val senderHints: List<String> = emptyList(),
        val logo: String? = null,
    )

    fun getAllDomains(): Set<String> =
        merchants.flatMap { it.domains }.toSet() + receiptPlatforms

    fun findBySenderHint(senderLabel: String): Merchant? =
        merchants.find { merchant ->
            merchant.senderHints.any { it.equals(senderLabel, ignoreCase = true) }
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var cached: MerchantConfig? = null

        fun fromJson(jsonString: String): MerchantConfig = json.decodeFromString(jsonString)

        fun load(context: Context): MerchantConfig =
            cached ?: fromJson(
                context.assets.open("merchants.json").bufferedReader().use { it.readText() }
            ).also { cached = it }
    }
}
