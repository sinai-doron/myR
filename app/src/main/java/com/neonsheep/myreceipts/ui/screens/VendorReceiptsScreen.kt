package com.neonsheep.myreceipts.ui.screens

import com.neonsheep.myreceipts.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.neonsheep.myreceipts.data.PdfStatus
import com.neonsheep.myreceipts.ui.components.MerchantLogo
import com.neonsheep.myreceipts.ui.components.Pill
import com.neonsheep.myreceipts.ui.components.PillTone
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.TopBar
import com.neonsheep.myreceipts.ui.theme.Green
import java.io.File

@Composable
fun VendorReceiptsScreen(
    vendor: String,
    receipts: List<CapturedReceipt>,
    onInfoClick: (CapturedReceipt) -> Unit,
    onDelete: (CapturedReceipt) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            title = vendor,
            subtitle = receiptCountLabel(receipts.size),
            onBack = onBack,
            leading = { MerchantLogo(senderLabel = vendor, size = 40.dp) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SectionCard(padding = 0.dp) {
                receipts.forEachIndexed { index, receipt ->
                    key(receipt.clientId) {
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        SwipeToDelete(onDelete = { onDelete(receipt) }) {
                            ReceiptRow(receipt = receipt, onInfoClick = { onInfoClick(receipt) })
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.swipe_to_delete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        onDismiss = { value -> if (value != SwipeToDismissBoxValue.Settled) onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Red only while swiping, so it can't bleed around the card's rounded corners
                    .background(
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) Color.Transparent
                        else MaterialTheme.colorScheme.error,
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    stringResource(R.string.delete),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        },
    ) { content() }
}

@Composable
private fun ReceiptRow(receipt: CapturedReceipt, onInfoClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { openReceipt(context, receipt) }
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatTimestamp(receipt.timestamp),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (receipt.totalAmount != null) {
                Text(
                    formatAmount(receipt.totalAmount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Green,
                )
            }
            Spacer(Modifier.height(8.dp))
            DownloadPill(receipt, hasPdf = receipt.hasPdf())
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onInfoClick) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = Green.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Where the receipt's PDF stands: saved, on its way, or link only. */
@Composable
internal fun DownloadPill(receipt: CapturedReceipt, hasPdf: Boolean = receipt.hasPdf()) {
    // hasPdf is a parameter so the pill redraws when the file appears, even if the receipt doesn't change
    when {
        receipt.isSample() -> Pill(stringResource(R.string.pill_sample), PillTone.Grey)
        hasPdf -> Pill(stringResource(R.string.pill_pdf_saved), PillTone.Green)
        receipt.pdfStatus == PdfStatus.UNSUPPORTED -> Pill(stringResource(R.string.pill_unsupported), PillTone.Grey)
        receipt.pdfStatus == PdfStatus.GONE -> Pill(stringResource(R.string.pill_gone), PillTone.Grey)
        receipt.pdfStatus == PdfStatus.FAILED -> Pill(stringResource(R.string.pill_failed), PillTone.Grey)
        receipt.isPastDownloadWindow() -> Pill(stringResource(R.string.pill_link_only), PillTone.Grey)
        else -> Pill(stringResource(R.string.pill_downloading), PillTone.Green, loading = true)
    }
}

/** Opens the saved PDF, or the original link in the browser when there's no PDF. A sample has neither. */
internal fun openReceipt(context: Context, receipt: CapturedReceipt) {
    if (receipt.isSample()) return
    if (receipt.hasPdf() && receipt.pdfPath != null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(receipt.pdfPath))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    } else {
        openInBrowser(context, receipt)
    }
}

internal fun openInBrowser(context: Context, receipt: CapturedReceipt) {
    if (receipt.isFile()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, receipt.link.toUri()))
}
