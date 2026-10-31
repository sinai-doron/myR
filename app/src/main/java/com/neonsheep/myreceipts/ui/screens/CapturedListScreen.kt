package com.neonsheep.myreceipts.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.ui.components.CardTitle
import com.neonsheep.myreceipts.ui.components.MerchantLogo
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.Stat
import com.neonsheep.myreceipts.ui.theme.DangerRed
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import com.neonsheep.myreceipts.ui.theme.Purple
import com.neonsheep.myreceipts.ui.theme.PurpleLight
import com.neonsheep.myreceipts.ui.theme.SuccessGreen
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val pdfStatus: String? = null,
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
    accountEmail: String? = null,
    onSignOut: () -> Unit = {},
) {
    var showAccount by remember { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // Header, as on gym-tracker's home
        Row(modifier = Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (accountEmail != null) {
                AccountButton(email = accountEmail, onClick = { showAccount = true })
            }
        }
        Text(
            if (receipts.isEmpty()) "Receipts from your SMS, in one place"
            else "${receiptCountLabel(receipts.size)} · ${storeCountLabel(vendors.size)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(20.dp))

        if (vendors.isEmpty()) {
            EmptyState()
        } else {
            MonthCard(receipts)
            Spacer(Modifier.height(12.dp))
            SectionCard(padding = 0.dp) {
                CardTitle(
                    "🧾 Stores",
                    trailing = vendors.size.toString(),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                )
                vendors.forEachIndexed { index, vendor ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    VendorRow(vendor = vendor, onClick = { onVendorClick(vendor.name) })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showAccount && accountEmail != null) {
        AlertDialog(
            onDismissRequest = { showAccount = false },
            title = { Text("Signed in") },
            text = { Text(accountEmail) },
            confirmButton = {
                TextButton(onClick = { showAccount = false; onSignOut() }) {
                    Text("Sign out", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccount = false }) { Text("Close", color = Purple) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/** The signed-in account's initial in a round purple badge; tap for the account and sign-out. */
@Composable
private fun AccountButton(email: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PurpleLight)
            .clickable(onClick = onClick)
            .testTag("account"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            email.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Purple,
        )
    }
}

/** This month at a glance: receipts, stores, and how many PDFs are saved on the phone. */
@Composable
private fun MonthCard(receipts: List<CapturedReceipt>) {
    val monthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val thisMonth = remember(receipts) { receipts.filter { it.timestamp >= monthStart } }
    val saved = remember(receipts) { receipts.count { r -> r.pdfPath?.let { File(it).exists() } == true } }
    val monthName = remember { SimpleDateFormat("MMMM", Locale.getDefault()).format(Date()) }

    SectionCard {
        CardTitle("📅 This month", trailing = monthName)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(thisMonth.size.toString(), "receipts", Purple, Modifier.weight(1f))
            Stat(thisMonth.map { it.senderLabel }.distinct().size.toString(), "stores", Purple, Modifier.weight(1f))
            Stat(saved.toString(), "PDFs saved", SuccessGreen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyState() {
    SectionCard(padding = 28.dp) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(PurpleLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // The app logo in its own colours (tinting flattens it)
                Image(
                    painter = painterResource(R.drawable.ic_receipt),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VendorRow(vendor: VendorSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantLogo(senderLabel = vendor.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                vendor.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${receiptCountLabel(vendor.receiptCount)} · ${formatDate(vendor.lastReceiptDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun receiptCountLabel(count: Int) = if (count == 1) "1 receipt" else "$count receipts"
private fun storeCountLabel(count: Int) = if (count == 1) "1 store" else "$count stores"

private val timestampFormatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
private val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val amountFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("he-IL")).apply {
    currency = Currency.getInstance("ILS")
}

internal fun formatTimestamp(epochMillis: Long): String = timestampFormatter.format(Date(epochMillis))
internal fun formatDate(epochMillis: Long): String = dateFormatter.format(Date(epochMillis))
internal fun formatAmount(amount: Double): String = amountFormatter.format(amount)

/** Receipts older than this are kept but never downloaded automatically. */
internal const val AUTO_DOWNLOAD_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

internal fun CapturedReceipt.hasPdf(): Boolean = pdfPath?.let { File(it).exists() } == true
internal fun CapturedReceipt.isPastDownloadWindow(): Boolean =
    System.currentTimeMillis() - timestamp > AUTO_DOWNLOAD_WINDOW_MS

@Preview(showBackground = true)
@Composable
private fun PreviewEmpty() {
    MyReceiptsTheme { VendorListScreen(receipts = emptyList()) }
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
