package com.neonsheep.myreceipts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.ui.components.CardTitle
import com.neonsheep.myreceipts.ui.components.MerchantLogo
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.TopBar
import com.neonsheep.myreceipts.ui.theme.DangerRed
import com.neonsheep.myreceipts.ui.theme.Purple
import com.neonsheep.myreceipts.ui.theme.SuccessInk
import kotlinx.coroutines.launch

@Composable
fun ReceiptDetailScreen(
    receipt: CapturedReceipt,
    onBack: () -> Unit,
    // Sends the link to the backend for a PDF; returns an error message, or null when it worked.
    onGetPdf: (suspend (CapturedReceipt) -> String?)? = null,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val hasPdf = receipt.hasPdf()
    val scope = rememberCoroutineScope()
    var fetching by remember(receipt.clientId) { mutableStateOf(false) }
    var fetchError by remember(receipt.clientId) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = "Receipt", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Who and how much
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MerchantLogo(senderLabel = receipt.senderLabel, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            receipt.senderLabel,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            formatTimestamp(receipt.timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (receipt.totalAmount != null) {
                        Text(
                            formatAmount(receipt.totalAmount),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = SuccessInk,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                DownloadPill(receipt, hasPdf = hasPdf)
            }

            // The record as stored on the phone
            SectionCard {
                CardTitle("Details")
                Spacer(Modifier.height(8.dp))
                DetailRow(stringResource(R.string.label_received), formatTimestamp(receipt.timestamp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(stringResource(R.string.label_link), receipt.link)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(stringResource(R.string.label_id), receipt.clientId)
            }

            Spacer(Modifier.height(4.dp))
            val getPdf: () -> Unit = {
                fetching = true
                fetchError = null
                scope.launch {
                    fetchError = onGetPdf?.invoke(receipt)
                    fetching = false
                }
            }
            when {
                hasPdf -> {
                    PrimaryButton("Open PDF") { openReceipt(context, receipt) }
                    SecondaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
                }
                onGetPdf != null -> {
                    PrimaryButton("Get PDF", loading = fetching, tag = "get_pdf", onClick = getPdf)
                    SecondaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
                }
                else -> PrimaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
            }
            // Always offered: fetch the PDF again and replace the saved one, e.g. to swap a printout
            // for the store's own file.
            if (hasPdf && onGetPdf != null) {
                TextButton(
                    onClick = getPdf,
                    enabled = !fetching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("get_pdf_again"),
                ) {
                    if (fetching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Purple)
                    } else {
                        Text(
                            "Get PDF again",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Purple,
                        )
                    }
                }
            }
            if (fetchError != null) {
                Text(
                    fetchError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DangerRed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, loading: Boolean = false, tag: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = Purple),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
