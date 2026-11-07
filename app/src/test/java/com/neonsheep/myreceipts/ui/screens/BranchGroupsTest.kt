package com.neonsheep.myreceipts.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BranchGroupsTest {
    private var n = 0
    private fun r(branch: String? = null, address: String? = null) =
        CapturedReceipt(link = "https://x/${n++}", senderLabel = "Shufersal", timestamp = 1L, clientId = "id-$n", branch = branch, storeAddress = address)

    @Test
    fun `nothing known keeps one group`() {
        val receipts = listOf(r(), r())
        assertEquals(listOf(BranchGroup(null, receipts)), branchGroups(receipts))
    }

    @Test
    fun `branches apart, matched by address or name, the unknown last`() {
        val netanya = r(branch = "249", address = "גיבורי ישראל 5, נתניה")
        val unknown = r()
        val netanyaByName = r(branch = "249")
        val raanana = r(address = "אחוזה 120, רעננה")
        val netanyaByAddress = r(address = " גיבורי  ישראל 5, נתניה ")
        val groups = branchGroups(listOf(netanya, unknown, netanyaByName, raanana, netanyaByAddress))
        assertEquals(
            listOf(
                BranchGroup("249 · גיבורי ישראל 5, נתניה", listOf(netanya, netanyaByName, netanyaByAddress)),
                BranchGroup("אחוזה 120, רעננה", listOf(raanana)),
                BranchGroup(null, listOf(unknown)),
            ),
            groups,
        )
    }
}
