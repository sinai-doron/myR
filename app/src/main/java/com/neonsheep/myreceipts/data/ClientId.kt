package com.neonsheep.myreceipts.data

import java.util.UUID

fun generateClientId(link: String, timestamp: Long): String {
    return UUID.nameUUIDFromBytes("$link|$timestamp".toByteArray()).toString()
}
