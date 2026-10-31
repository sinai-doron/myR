package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantConfigTest {

    private val testJson = """
        {
          "merchants": [
            {
              "name": "Shufersal",
              "domains": ["shufersal.co.il"],
              "senderHints": ["Shufersal"]
            },
            {
              "name": "Pairzon",
              "domains": ["pairzon.com"],
              "senderHints": ["OSHERAD", "Pairzon"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun fromJson_parsesMerchants() {
        val config = MerchantConfig.fromJson(testJson)
        assertEquals(2, config.merchants.size)
        assertEquals("Shufersal", config.merchants[0].name)
        assertEquals(listOf("shufersal.co.il"), config.merchants[0].domains)
    }

    @Test
    fun getAllDomains_returnsFlatSet() {
        val config = MerchantConfig.fromJson(testJson)
        val domains = config.getAllDomains()
        assertEquals(setOf("shufersal.co.il", "pairzon.com"), domains)
    }

    @Test
    fun fromJson_emptySenderHints_defaultsToEmptyList() {
        val json = """{"merchants": [{"name": "Test", "domains": ["test.com"]}]}"""
        val config = MerchantConfig.fromJson(json)
        assertTrue(config.merchants[0].senderHints.isEmpty())
    }
}
