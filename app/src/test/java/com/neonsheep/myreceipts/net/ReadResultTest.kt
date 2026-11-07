package com.neonsheep.myreceipts.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadResultTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(body: String) = json.decodeFromString<BackendApi.ReadResult>(body)

    @Test
    fun `an amount in shekels on a receipt is offered`() {
        val result = read(
            """{"isReceipt":true,"store":"Aroma","date":"2026-08-15","total":20,"currency":"ILS",""" +
                """"invoiceNumber":"7734-21","businessId":"512345678","vat":3.05,"returnBy":null,""" +
                """"warrantyUntil":"2027-08-15","budget":{"usedUsd":0.0003,"limitUsd":31}}""",
        )
        assertEquals(20.0, result.shekels!!, 0.0)
        assertEquals("7734-21", result.invoiceNumber)
        assertEquals("512345678", result.businessId)
        assertEquals("2027-08-15", result.warrantyUntil)
        assertFalse(result.budget.nearLimit)
    }

    @Test
    fun `no amount is offered from another currency, a non-receipt, or nothing read`() {
        val budget = """"budget":{"usedUsd":1,"limitUsd":31}"""
        assertNull(read("""{"isReceipt":true,"total":12.5,"currency":"OTHER",$budget}""").shekels)
        assertNull(read("""{"isReceipt":false,"total":12.5,"currency":"ILS",$budget}""").shekels)
        assertNull(read("""{"isReceipt":true,"total":null,"currency":null,$budget}""").shekels)
        assertNull(read("""{"isReceipt":true,"total":0,"currency":"ILS",$budget}""").shekels)
    }

    @Test
    fun `the heads-up comes at 80 percent of the month`() {
        assertFalse(BackendApi.AiBudget(usedUsd = 24.79, limitUsd = 31.0).nearLimit)
        assertTrue(BackendApi.AiBudget(usedUsd = 24.8, limitUsd = 31.0).nearLimit)
        assertFalse(BackendApi.AiBudget(usedUsd = 0.0, limitUsd = 0.0).nearLimit)
    }
}
