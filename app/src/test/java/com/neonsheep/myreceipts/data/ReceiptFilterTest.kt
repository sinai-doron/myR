package com.neonsheep.myreceipts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReceiptFilterTest {

    private lateinit var filter: ReceiptFilter

    @Before
    fun setup() {
        val config = MerchantConfig(
            merchants = listOf(
                MerchantConfig.Merchant(
                    name = "Shufersal",
                    domains = listOf("shufersal.co.il"),
                    senderHints = listOf("Shufersal"),
                ),
                MerchantConfig.Merchant(
                    name = "Pairzon",
                    domains = listOf("pairzon.com"),
                    senderHints = listOf("OSHERAD", "Pairzon"),
                ),
            ),
        )
        filter = DomainMatchFilter(config)
    }

    @Test
    fun shufersal_singleLink_extracted() {
        val body = "קבלה דיגיטלית שלך מסופרסל: https://shufersal.co.il/receipt/abc123"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://shufersal.co.il/receipt/abc123", result.matchedLinks[0])
        assertEquals("Shufersal", result.senderLabel)
    }

    @Test
    fun osherad_twoLinks_onlyOnListExtracted() {
        val body = "הקבלה שלך: https://pairzon.com/r/456 לפרטים: https://bit.ly/xyz"
        val result = filter.filter(body, "OSHERAD")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://pairzon.com/r/456", result.matchedLinks[0])
    }

    @Test
    fun noLinks_otpMessage_returnsEmpty() {
        val body = "Your verification code is 123456"
        val result = filter.filter(body, "Google")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun noLinks_bankAlert_returnsEmpty() {
        val body = "חיוב בסך 150.00 ש\"ח בכרטיס *1234. יתרה: 5,000 ש\"ח"
        val result = filter.filter(body, "Leumi")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun offListLinksOnly_returnsEmpty() {
        val body = "Click here: https://bit.ly/abc123 to view your statement"
        val result = filter.filter(body, "SomeBank")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun hebrewMultipart_extractsLink() {
        val body = "שלום, תודה שקנית בסופרסל! " +
            "הקבלה הדיגיטלית שלך: https://shufersal.co.il/receipt/long123456789"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
        assertTrue(result.matchedLinks[0].contains("shufersal.co.il"))
    }

    @Test
    fun subdomain_matches() {
        val body = "Receipt: https://receipts.shufersal.co.il/view/123"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun httpWithoutS_alsoMatches() {
        val body = "Receipt: http://shufersal.co.il/receipt/123"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun multipleOnListLinks_allExtracted() {
        val body = "https://pairzon.com/a https://pairzon.com/b"
        val result = filter.filter(body, "OSHERAD")

        assertEquals(2, result.matchedLinks.size)
    }

    @Test
    fun urlFollowedByHebrew_noSpace_linkExtracted() {
        val body = "הקבלה:https://shufersal.co.il/receipt/123הקבלה שלך"
        val result = filter.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://shufersal.co.il/receipt/123", result.matchedLinks[0])
    }

    @Test
    fun senderPassedThrough_asLabel() {
        val body = "https://shufersal.co.il/receipt/1"
        val result = filter.filter(body, "UnknownSender")

        assertEquals("UnknownSender", result.senderLabel)
    }

    @Test
    fun receiptPlatformDomain_matchedByDomainFilter() {
        val config = MerchantConfig(
            merchants = emptyList(),
            receiptPlatforms = listOf("wee.ai"),
        )
        val platformFilter = DomainMatchFilter(config)
        val body = "פתקית: https://wee.ai/r/abc123"
        val result = platformFilter.filter(body, "IKEA")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://wee.ai/r/abc123", result.matchedLinks[0])
        assertEquals("IKEA", result.senderLabel)
    }

    @Test
    fun aromaAndSchnitt_matched_fromTheShippedList() {
        val platformFilter = DomainMatchFilter(MerchantConfig.fromJson(java.io.File("src/main/assets/merchants.json").readText()))

        val aroma = platformFilter.filter("תודה שביקרת בארומה, לקבלה: https://receipts.dorix.co.il/r/x1y2", "AROMA")
        assertEquals(listOf("https://receipts.dorix.co.il/r/x1y2"), aroma.matchedLinks)
        val schnitt = platformFilter.filter("הקבלה שלך משניט: https://apps.beecommcloud.com/receipt/abc", "Schnitt")
        assertEquals(listOf("https://apps.beecommcloud.com/receipt/abc"), schnitt.matchedLinks)
    }

    @Test
    fun maxstockHistoryPage_isNotAReceipt() {
        val body = "MAXstock: לצפייה ברכישה https://maxstock.pairzon.com/h.html?b=12&t=eyJ0"
        assertTrue(filter.filter(body, "MAXstock").matchedLinks.isEmpty())
        assertFalse(ReceiptLinks.isReceipt("https://pairzon.com/H.HTML?b=1"))
        assertTrue(ReceiptLinks.isReceipt("https://pairzon.com/r/456"))
    }
}

class HeuristicReceiptFilterTest {

    private val heuristic = HeuristicReceiptFilter()

    @Test
    fun hebrewReceiptKeyword_unknownDomain_extracted() {
        val body = "היי, הקבלה שלך מוכנה: https://unknown-store.com/receipt/123"
        val result = heuristic.filter(body, "UnknownStore")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://unknown-store.com/receipt/123", result.matchedLinks[0])
        assertEquals("UnknownStore", result.senderLabel)
    }

    @Test
    fun hebrewInvoiceKeyword_extracted() {
        val body = "חשבונית מספר 12345: https://shop.example.com/inv/99"
        val result = heuristic.filter(body, "Shop")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun englishReceiptKeyword_extracted() {
        val body = "Your receipt is ready: https://store.example.com/r/42"
        val result = heuristic.filter(body, "Store")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun noReceiptKeyword_ignored() {
        val body = "Special offer! Visit https://promo.example.com/deal"
        val result = heuristic.filter(body, "PromoSender")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun receiptKeyword_noUrl_ignored() {
        val body = "הקבלה שלך תגיע בדואר"
        val result = heuristic.filter(body, "Store")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun otp_withReceiptWordInContext_ignored() {
        val body = "Your verification code is 123456"
        val result = heuristic.filter(body, "Google")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun ikeaNote_patkitKeyword_extracted() {
        val body = "לקוח יקר, בהמשך לבקשתך, נשלחה אליך פתקית ירוקה מאיקאה. לצפייה בפתקית: https://wee.ai/r/AbCdEfGhIjKlMnOpQrStUvWx הפתקית ופרטי הרכישה ישמרו עבורך בהתאם למדיניות הפרטיות: https://goo.gl/example"
        val result = heuristic.filter(body, "IKEA")

        assertEquals(2, result.matchedLinks.size)
        assertEquals("https://wee.ai/r/AbCdEfGhIjKlMnOpQrStUvWx", result.matchedLinks[0])
        assertEquals("IKEA", result.senderLabel)
    }

    @Test
    fun multipleUrls_allExtracted() {
        val body = "הקבלה: https://a.com/1 https://b.com/2"
        val result = heuristic.filter(body, "Multi")

        assertEquals(2, result.matchedLinks.size)
    }

    @Test
    fun purchaseKeyword_extracted() {
        val body = "פרטי הרכישה שלך: https://store.example.com/order/55"
        val result = heuristic.filter(body, "Store")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun paymentKeyword_extracted() {
        val body = "אישור תשלום: https://pay.example.com/confirm/88"
        val result = heuristic.filter(body, "PayService")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun orderKeyword_extracted() {
        val body = "הזמנה מספר 12345: https://shop.example.com/order/12345"
        val result = heuristic.filter(body, "Shop")

        assertEquals(1, result.matchedLinks.size)
    }

    @Test
    fun otpWithReceiptWord_excluded() {
        val body = "קוד האימות לקבלת הגישה: 584923 https://verify.example.com/auth"
        val result = heuristic.filter(body, "Bank")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun marketingWithReceiptWord_excluded() {
        val body = "מבצע! קבלה 20% הנחה: https://promo.example.com/sale"
        val result = heuristic.filter(body, "Store")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun purePromotion_excluded() {
        val body = "הנחה מיוחדת! קבלה שלך מחכה: https://deals.example.com/offer"
        val result = heuristic.filter(body, "Deals")

        assertTrue(result.matchedLinks.isEmpty())
    }
}

class CompositeReceiptFilterTest {

    @Test
    fun domainMatch_takesOverHeuristic() {
        val config = MerchantConfig(
            merchants = listOf(
                MerchantConfig.Merchant(
                    name = "Shufersal",
                    domains = listOf("shufersal.co.il"),
                ),
            ),
        )
        val composite = CompositeReceiptFilter(
            listOf(DomainMatchFilter(config), HeuristicReceiptFilter()),
        )

        val body = "הקבלה שלך: https://shufersal.co.il/r/1 https://bit.ly/promo"
        val result = composite.filter(body, "Shufersal")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://shufersal.co.il/r/1", result.matchedLinks[0])
    }

    @Test
    fun noDomainMatch_fallsBackToHeuristic() {
        val config = MerchantConfig(merchants = emptyList())
        val composite = CompositeReceiptFilter(
            listOf(DomainMatchFilter(config), HeuristicReceiptFilter()),
        )

        val body = "הקבלה שלך: https://newstore.com/receipt/42"
        val result = composite.filter(body, "NewStore")

        assertEquals(1, result.matchedLinks.size)
        assertEquals("https://newstore.com/receipt/42", result.matchedLinks[0])
    }

    @Test
    fun noMatch_anywhere_returnsEmpty() {
        val config = MerchantConfig(merchants = emptyList())
        val composite = CompositeReceiptFilter(
            listOf(DomainMatchFilter(config), HeuristicReceiptFilter()),
        )

        val body = "Special offer! Visit https://promo.com/deal"
        val result = composite.filter(body, "Promo")

        assertTrue(result.matchedLinks.isEmpty())
    }

    @Test
    fun weeAi_onlyReceiptPathsAreReceipts() {
        val config = MerchantConfig(merchants = emptyList(), receiptPlatforms = listOf("wee.ai"))
        val composite = CompositeReceiptFilter(listOf(DomainMatchFilter(config), HeuristicReceiptFilter()))
        val body = "חשבונית מרכזה: https://wee.ai/r/Rc123 מבצעים: https://wee.ai/s/Mk456 הסרה: https://wee.ai/u/Un789 https://wee.ai//l/abc https://wee.ai/pages/sph/l"

        val result = composite.filter(body, "Merkaza")

        assertEquals(listOf("https://wee.ai/r/Rc123"), result.matchedLinks)
    }

    @Test
    fun weeAi_marketingOnlyTextIsNotCaptured_evenByTheKeywordFallback() {
        val config = MerchantConfig(merchants = emptyList(), receiptPlatforms = listOf("wee.ai"))
        val composite = CompositeReceiptFilter(listOf(DomainMatchFilter(config), HeuristicReceiptFilter()))
        val body = "חודש של מבצעים במרכזה! לתשלום בקופה הציגו: https://wee.ai/s/Mk456 להסרה https://wee.ai/u/Un789"

        assertTrue(composite.filter(body, "Merkaza").matchedLinks.isEmpty())
    }

    @Test
    fun receiptLinks_otherHostsAreUnaffected() {
        assertTrue(ReceiptLinks.isReceipt("https://public.pairzon.com/1267/AbC"))
        assertTrue(ReceiptLinks.isReceipt("https://www.shufersal.co.il/dishortcuturl/AbC"))
        assertFalse(ReceiptLinks.isReceipt("https://wee.ai/s/AbC"))
        assertFalse(ReceiptLinks.isReceipt("not a url"))
    }
}
