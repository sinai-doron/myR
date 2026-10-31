package com.doron.myreceipts.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.doron.myreceipts.R
import com.doron.myreceipts.ui.theme.Green600
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorReceiptsScreen(
    vendor: String,
    receipts: List<CapturedReceipt>,
    onInfoClick: (CapturedReceipt) -> Unit,
    onDelete: (CapturedReceipt) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            MerchantLogo(senderLabel = vendor)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = vendor,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (receipts.size == 1) "1 receipt" else "${receipts.size} receipts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(receipts, key = { it.clientId }) { receipt ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            onDelete(receipt)
                            true
                        } else false
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                text = "Delete",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    },
                ) {
                    ReceiptRow(
                        receipt = receipt,
                        onInfoClick = { onInfoClick(receipt) },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: CapturedReceipt, onInfoClick: () -> Unit) {
    val context = LocalContext.current
    val hasPdf = receipt.pdfPath?.let { File(it).exists() } == true
    val isOlderThan30Days = (System.currentTimeMillis() - receipt.timestamp) > 30L * 24 * 60 * 60 * 1000

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                if (hasPdf) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(receipt.pdfPath!!),
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(receipt.link))
                    context.startActivity(intent)
                }
            }
            .padding(start = 20.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTimestamp(receipt.timestamp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (receipt.totalAmount != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formatAmount(receipt.totalAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Green600,
                )
            }
        }
        if (hasPdf) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Downloaded",
                modifier = Modifier.size(18.dp),
                tint = Green600,
            )
        } else if (!isOlderThan30Days) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
