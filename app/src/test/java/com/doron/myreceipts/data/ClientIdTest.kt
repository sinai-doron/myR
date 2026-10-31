package com.doron.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClientIdTest {

    @Test
    fun sameInputs_produceSameId() {
        val id1 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val id2 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        assertEquals(id1, id2)
    }

    @Test
    fun differentLink_producesDifferentId() {
        val id1 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val id2 = generateClientId("https://shufersal.co.il/receipt/456", 1728900000000L)
        assertNotEquals(id1, id2)
    }

    @Test
    fun differentTimestamp_producesDifferentId() {
        val id1 = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val id2 = generateClientId("https://shufersal.co.il/receipt/123", 1728903600000L)
        assertNotEquals(id1, id2)
    }

    @Test
    fun format_isUuid() {
        val id = generateClientId("https://shufersal.co.il/receipt/123", 1728900000000L)
        val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assert(uuidRegex.matches(id)) { "Expected UUID format, got: $id" }
    }
}
