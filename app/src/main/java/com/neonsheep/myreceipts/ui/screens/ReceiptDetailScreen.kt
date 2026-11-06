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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.TotalSource
import com.neonsheep.myreceipts.ui.components.CardTitle
import com.neonsheep.myreceipts.ui.components.MerchantLogo
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.TopBar
import com.neonsheep.myreceipts.ui.theme.Red
import com.neonsheep.myreceipts.ui.theme.Green
import kotlinx.coroutines.launch

@Composable
fun ReceiptDetailScreen(
    receipt: CapturedReceipt,
    onBack: () -> Unit,
    // Sends the link to the backend for a PDF; returns an error message, or null when it worked.
    onGetPdf: (suspend (CapturedReceipt) -> String?)? = null,
    // Saves the amount the user typed, or none.
    onSetAmount: (suspend (CapturedReceipt, Double?) -> Unit)? = null,
    /** The category the receipt's store counts in. */
    storeCategory: Category = Category.guessFor(receipt.senderLabel),
    /** Saves the user's corrections and deadlines. Null: no editing (samples). */
    onEdit: (suspend (CapturedReceipt, ReceiptEdit) -> Unit)? = null,
) {
    val scopeForEdit = rememberCoroutineScope()
    var editing by remember(receipt.clientId) { mutableStateOf(false) }
    if (editing && onEdit != null) {
        EditReceiptScreen(
            receipt = receipt,
            storeCategory = storeCategory,
            onCancel = { editing = false },
            onSave = { edit ->
                editing = false
                scopeForEdit.launch { onEdit(receipt, edit) }
            },
        )
        return
    }
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val hasPdf = receipt.hasPdf()
    val scope = rememberCoroutineScope()
    var fetching by remember(receipt.clientId) { mutableStateOf(false) }
    var fetchError by remember(receipt.clientId) { mutableStateOf<String?>(null) }
    var editingAmount by remember(receipt.clientId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = stringResource(R.string.receipt_title), onBack = onBack)
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
                            color = Green,
                            modifier = Modifier
                                .testTag("amount")
                                .then(if (onSetAmount != null) Modifier.clickable { editingAmount = true } else Modifier),
                        )
                    }
                }
                Row {
                    if (onSetAmount != null) {
                        TextButton(
                            onClick = { editingAmount = true },
                            modifier = Modifier.testTag("edit_amount"),
                        ) {
                            Text(
                                stringResource(if (receipt.totalAmount == null) R.string.add_amount else R.string.change_amount),
                                color = Green,
                            )
                        }
                    }
                    if (onEdit != null && !receipt.isSample()) {
                        TextButton(onClick = { editing = true }, modifier = Modifier.testTag("edit_receipt")) {
                            Text(stringResource(R.string.edit), color = Green)
                        }
                    }
                }
                amountNote(receipt)?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
                DownloadPill(receipt, hasPdf = hasPdf)
            }

            // The record as stored on the phone
            SectionCard {
                CardTitle(stringResource(R.string.details))
                Spacer(Modifier.height(8.dp))
                val category = Category.fromName(receipt.category) ?: storeCategory
                DetailRow(stringResource(R.string.label_category), "${category.emoji} ${stringResource(category.labelRes)}")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                receipt.returnBy?.let {
                    DetailRow(stringResource(R.string.label_return), formatDay(it), tag = "detail_return")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                receipt.warrantyUntil?.let {
                    DetailRow(stringResource(R.string.label_warranty), formatDay(it), tag = "detail_warranty")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                DetailRow(stringResource(R.string.label_received), formatTimestamp(receipt.textArrivedAt))
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
                receipt.isSample() -> Text(
                    stringResource(R.string.sample_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("sample_note"),
                )
                hasPdf -> {
                    PrimaryButton(stringResource(R.string.open_pdf)) { openReceipt(context, receipt) }
                    SecondaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
                }
                onGetPdf != null -> {
                    PrimaryButton(stringResource(R.string.get_pdf), loading = fetching, tag = "get_pdf", onClick = getPdf)
                    SecondaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
                }
                else -> PrimaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
            }
            // Always offered: fetch the PDF again and replace the saved one, e.g. to swap a printout
            // for the store's own file.
            if (hasPdf && onGetPdf != null && !receipt.isSample()) {
                TextButton(
                    onClick = getPdf,
                    enabled = !fetching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("get_pdf_again"),
                ) {
                    if (fetching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Green)
                    } else {
                        Text(
                            stringResource(R.string.get_pdf_again),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Green,
                        )
                    }
                }
            }
            if (editingAmount && onSetAmount != null) {
                AmountDialog(
                    initial = receipt.totalAmount,
                    onDismiss = { editingAmount = false },
                    onSave = { amount ->
                        editingAmount = false
                        scope.launch { onSetAmount(receipt, amount) }
                    },
                )
            }
            if (fetchError != null) {
                Text(
                    fetchError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Red,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Where the amount came from, when it was read rather than typed. */
@Composable
private fun amountNote(receipt: CapturedReceipt): String? = when {
    receipt.totalAmount == null -> null
    receipt.totalSource == TotalSource.PAGE -> stringResource(R.string.amount_from_page)
    receipt.totalSource == TotalSource.PDF -> stringResource(R.string.amount_from_pdf)
    else -> null
}

/** The amount as typed: digits with up to two decimals, "," or "."; null for an empty field. */
internal fun parseTypedAmount(text: String): Result<Double?> {
    val t = text.trim().replace(',', '.').removePrefix("₪").trim()
    if (t.isEmpty()) return Result.success(null)
    if (!Regex("""\d{1,7}(\.\d{0,2})?""").matches(t)) return Result.failure(IllegalArgumentException())
    return Result.success(t.toDouble().takeIf { it > 0 })
}

@Composable
private fun AmountDialog(initial: Double?, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { "%.2f".format(java.util.Locale.US, it) } ?: "") }
    val parsed = parseTypedAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.amount_paid)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("₪") },
                singleLine = true,
                isError = parsed.isFailure,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.testTag("amount_field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsed.getOrNull()) },
                enabled = parsed.isSuccess,
                modifier = Modifier.testTag("amount_save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
        colors = ButtonDefaults.buttonColors(containerColor = Green),
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
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Green),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

private fun formatDay(at: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(at))

@Composable
private fun DetailRow(label: String, value: String, tag: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
