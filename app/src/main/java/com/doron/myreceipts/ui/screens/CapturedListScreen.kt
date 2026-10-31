package com.doron.myreceipts.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doron.myreceipts.R
import com.doron.myreceipts.ui.theme.MyReceiptsTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

data class CapturedReceipt(
    val link: String,
    val senderLabel: String,
    val timestamp: Long,
    val clientId: String,
    val totalAmount: Double? = null,
    val pdfPath: String? = null,
)

data class VendorSummary(
    val name: String,
    val receiptCount: Int,
    val lastReceiptDate: Long,
)

@Composable
fun VendorListScreen(
    receipts: List<CapturedReceipt>,
    onVendorClick: (String) -> Unit = {},
) {
    val vendors = remember(receipts) {
        receipts.groupBy { it.senderLabel }
            .map { (name, items) ->
                VendorSummary(
                    name = name,
                    receiptCount = items.size,
                    lastReceiptDate = items.maxOf { it.timestamp },
                )
            }
            .sortedByDescending { it.lastReceiptDate }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        if (vendors.isEmpty()) {
            EmptyState()
        } else {
            VendorList(vendors = vendors, onVendorClick = onVendorClick)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_receipt),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VendorList(vendors: List<VendorSummary>, onVendorClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(vendors, key = { it.name }) { vendor ->
            VendorRow(vendor = vendor, onClick = { onVendorClick(vendor.name) })
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun VendorRow(vendor: VendorSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantLogo(senderLabel = vendor.name)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vendor.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (vendor.receiptCount == 1) "1 receipt" else "${vendor.receiptCount} receipts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatTimestamp(vendor.lastReceiptDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MerchantLogo(senderLabel: String) {
    val logoResId = resolveMerchantLogo(senderLabel)
    if (logoResId != null) {
        Image(
            painter = painterResource(logoResId),
            contentDescription = senderLabel,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        )
    } else {
        LetterCircle(letter = senderLabel.firstOrNull()?.uppercase() ?: "?")
    }
}

@Composable
private fun LetterCircle(letter: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun resolveMerchantLogo(senderLabel: String): Int? {
    val logoMap = mapOf(
        "shufersal" to R.drawable.ic_merchant_shufersal,
        "pairzon" to R.drawable.ic_merchant_pairzon,
        "osherad" to R.drawable.ic_merchant_pairzon,
    )
    return logoMap[senderLabel.lowercase()]
}

private val timestampFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
private val amountFormatter = NumberFormat.getCurrencyInstance(Locale("he", "IL")).apply {
    currency = Currency.getInstance("ILS")
}

internal fun formatTimestamp(epochMillis: Long): String =
    timestampFormatter.format(Date(epochMillis))

internal fun formatAmount(amount: Double): String =
    amountFormatter.format(amount)

@Preview(showBackground = true)
@Composable
private fun PreviewEmpty() {
    MyReceiptsTheme {
        VendorListScreen(receipts = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewWithVendors() {
    MyReceiptsTheme {
        VendorListScreen(
            receipts = listOf(
                CapturedReceipt("https://shufersal.co.il/r/1", "Shufersal", 1728900000000L, "p-1", 74.30),
                CapturedReceipt("https://shufersal.co.il/r/2", "Shufersal", 1728800000000L, "p-2", 120.0),
                CapturedReceipt("https://pairzon.com/r/1", "OSHERAD", 1728700000000L, "p-3"),
                CapturedReceipt("https://wee.ai/r/1", "IKEA", 1728600000000L, "p-4"),
            ),
        )
    }
}
