package com.neonsheep.myreceipts.ui.screens

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Checkbox
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
import com.neonsheep.myreceipts.data.Origin
import com.neonsheep.myreceipts.data.ReceiptFiles
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
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
    /** Opens straight on the edit screen: a file just added, whose store the user names. */
    startEditing: Boolean = false,
    /** Turns a scan's or photo's pages a quarter turn clockwise; false when it couldn't. Null: not offered. */
    onRotate: (suspend (CapturedReceipt) -> Boolean)? = null,
    /** Has the backend's AI read the receipt's PDF, after the user agrees. Null: not offered. */
    onReadWithAi: (suspend (CapturedReceipt) -> AiRead)? = null,
    /** Saves the details the user kept from an AI reading. */
    onApplyAi: (suspend (CapturedReceipt, AiDetails) -> Unit)? = null,
) {
    val scopeForEdit = rememberCoroutineScope()
    var editing by remember(receipt.clientId) { mutableStateOf(startEditing) }
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
    var askingAi by remember(receipt.clientId) { mutableStateOf(false) }
    var readingAi by remember(receipt.clientId) { mutableStateOf(false) }
    // What the AI read, shown field by field for the user to keep or not.
    var aiDetails by remember(receipt.clientId) { mutableStateOf<AiDetails?>(null) }
    var aiMessages by remember(receipt.clientId) { mutableStateOf<List<Int>>(emptyList()) }

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
                // A PDF to read from: the AI can read its details, if the user asks.
                if (onReadWithAi != null && onApplyAi != null && hasPdf && !receipt.isSample()) {
                    TextButton(
                        onClick = { askingAi = true },
                        enabled = !readingAi,
                        modifier = Modifier.testTag("read_with_ai"),
                    ) {
                        if (readingAi) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Green)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_reading), color = Green)
                        } else {
                            Text(stringResource(R.string.ai_read), color = Green)
                        }
                    }
                }
                aiMessages.forEach { message ->
                    Text(
                        stringResource(message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ai_message"),
                    )
                }
                amountNote(receipt)?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("amount_note"),
                    )
                }
                Spacer(Modifier.height(14.dp))
                DownloadPill(receipt, hasPdf = hasPdf)
            }

            // A scan or photo: the page itself, to see it's the right way up and turn it if not.
            if (hasPdf && Origin.isPicture(receipt.origin) && receipt.pdfPath != null) {
                PagePreview(receipt.pdfPath, onRotate = onRotate?.let { rotate -> suspend { rotate(receipt) } })
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
                DetailRow(
                    stringResource(if (receipt.isFile()) R.string.label_added else R.string.label_received),
                    formatTimestamp(receipt.textArrivedAt),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (!receipt.isFile()) {
                    DetailRow(stringResource(R.string.label_link), receipt.link)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                receipt.invoiceNumber?.let {
                    DetailRow(stringResource(R.string.label_invoice_number), it, tag = "detail_invoice")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                receipt.businessId?.let {
                    DetailRow(stringResource(R.string.label_business_id), it, tag = "detail_business_id")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                receipt.vatAmount?.let {
                    DetailRow(stringResource(R.string.label_vat), formatAmount(it), tag = "detail_vat")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
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
                    PrimaryButton(stringResource(R.string.open_pdf), tag = "open_pdf") { openReceipt(context, receipt) }
                    if (!receipt.isFile()) {
                        SecondaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
                    }
                }
                receipt.isFile() -> Unit
                onGetPdf != null -> {
                    PrimaryButton(stringResource(R.string.get_pdf), loading = fetching, tag = "get_pdf", onClick = getPdf)
                    SecondaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
                }
                else -> PrimaryButton(stringResource(R.string.open_in_browser)) { openInBrowser(context, receipt) }
            }
            // Always offered: fetch the PDF again and replace the saved one, e.g. to swap a printout
            // for the store's own file.
            if (hasPdf && onGetPdf != null && !receipt.isSample() && !receipt.isFile()) {
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
            if (askingAi && onReadWithAi != null) {
                AlertDialog(
                    onDismissRequest = { askingAi = false },
                    title = { Text(stringResource(R.string.ai_confirm_title)) },
                    text = { Text(stringResource(R.string.ai_confirm_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                askingAi = false
                                readingAi = true
                                aiMessages = emptyList()
                                scope.launch {
                                    val read = onReadWithAi(receipt)
                                    readingAi = false
                                    aiMessages = read.messages
                                    if (read is AiRead.Read) {
                                        if (aiRows(receipt, read.details).isEmpty()) {
                                            aiMessages = listOf(R.string.ai_nothing_new) + read.messages
                                        } else {
                                            aiDetails = read.details
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("ai_confirm"),
                        ) { Text(stringResource(R.string.ai_confirm_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { askingAi = false }) { Text(stringResource(R.string.cancel)) }
                    },
                )
            }
            aiDetails?.let { details ->
                if (onApplyAi != null) {
                    AiReviewDialog(
                        rows = aiRows(receipt, details),
                        onDismiss = { aiDetails = null },
                        onSave = { kept ->
                            aiDetails = null
                            scope.launch { onApplyAi(receipt, kept) }
                        },
                    )
                }
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

/** The first page, drawn on the phone, with a button that turns it a quarter at a time. */
@Composable
private fun PagePreview(pdfPath: String, onRotate: (suspend () -> Boolean)?) {
    val scope = rememberCoroutineScope()
    // Bumped after each turn, so the page is drawn again from the new file.
    var version by remember(pdfPath) { mutableIntStateOf(0) }
    var turning by remember(pdfPath) { mutableStateOf(false) }
    val page by produceState<ImageBitmap?>(null, pdfPath, version) {
        value = ReceiptFiles.preview(File(pdfPath), PREVIEW_WIDTH_PX)?.asImageBitmap()
    }
    SectionCard {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp),
            contentAlignment = Alignment.Center,
        ) {
            page?.let {
                Image(
                    it,
                    contentDescription = stringResource(R.string.page_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().testTag("page_preview"),
                )
            } ?: CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Green)
        }
        if (onRotate != null) {
            TextButton(
                onClick = {
                    turning = true
                    scope.launch {
                        if (onRotate()) version++
                        turning = false
                    }
                },
                enabled = !turning,
                modifier = Modifier.fillMaxWidth().testTag("rotate_page"),
            ) {
                Text("↻ " + stringResource(R.string.rotate_page), color = Green)
            }
        }
    }
}

private const val PREVIEW_WIDTH_PX = 720

/** Where the amount came from, when it was read rather than typed. */
@Composable
private fun amountNote(receipt: CapturedReceipt): String? = when {
    // Looked for and not found, or a scan the phone can't read: ask for it.
    receipt.totalAmount == null && (receipt.totalSource == TotalSource.NONE || receipt.isFile()) ->
        stringResource(R.string.amount_not_read)
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

/** What the AI read from a receipt, in the app's terms: each null when it read nothing there. */
data class AiDetails(
    val store: String? = null,
    /** The purchase day, kept at the receipt's own time of day. */
    val purchasedAt: Long? = null,
    /** In shekels. */
    val amount: Double? = null,
    val invoiceNumber: String? = null,
    val businessId: String? = null,
    /** In shekels. */
    val vat: Double? = null,
    /** End of the last day, as the app keeps deadlines. */
    val returnBy: Long? = null,
    val warrantyUntil: Long? = null,
)

/** What reading a receipt with AI came to, as the screen shows it. */
sealed class AiRead {
    /** Messages to show under the amount: string resources. */
    abstract val messages: List<Int>

    /** Read: the details found, offered field by field. */
    data class Read(val details: AiDetails, override val messages: List<Int>) : AiRead()

    /** Read, but not a receipt, or nothing in it could be read. */
    data class NotFound(override val messages: List<Int>) : AiRead()

    /** Not read: signed out, allowance used, unavailable, too large, or failed. */
    data class Failed(override val messages: List<Int>) : AiRead()
}

/** One field the AI read that differs from what the receipt has: what it read, and what is there now. */
internal data class AiRow(
    val field: AiField,
    val read: String,
    val now: String?,
    /** For a date: the moment it stands for, as the receipt stores it. */
    val millis: Long? = null,
)

internal enum class AiField(val label: Int, val editable: Boolean) {
    STORE(R.string.label_store, true),
    DATE(R.string.label_date, false),
    AMOUNT(R.string.amount_paid, true),
    INVOICE(R.string.label_invoice_number, true),
    BUSINESS_ID(R.string.label_business_id, true),
    VAT(R.string.label_vat, true),
    RETURN_BY(R.string.label_return, false),
    WARRANTY(R.string.label_warranty, false),
}

private fun money(v: Double) = "%.2f".format(java.util.Locale.US, v)

/** The fields worth showing: read, and not the same as what the receipt already has. */
internal fun aiRows(receipt: CapturedReceipt, d: AiDetails): List<AiRow> = buildList {
    fun add(field: AiField, read: String?, now: String?, millis: Long? = null) {
        if (read != null && read != now) add(AiRow(field, read, now, millis))
    }
    add(AiField.STORE, d.store, receipt.senderLabel.takeIf { it != receipt.textSender || !receipt.isFile() })
    add(AiField.DATE, d.purchasedAt?.let(::formatDay), formatDay(receipt.timestamp), d.purchasedAt)
    add(AiField.AMOUNT, d.amount?.let(::money), receipt.totalAmount?.let(::money))
    add(AiField.INVOICE, d.invoiceNumber, receipt.invoiceNumber)
    add(AiField.BUSINESS_ID, d.businessId, receipt.businessId)
    add(AiField.VAT, d.vat?.let(::money), receipt.vatAmount?.let(::money))
    add(AiField.RETURN_BY, d.returnBy?.let(::formatDay), receipt.returnBy?.let(::formatDay), d.returnBy)
    add(AiField.WARRANTY, d.warrantyUntil?.let(::formatDay), receipt.warrantyUntil?.let(::formatDay), d.warrantyUntil)
}

/**
 * Every field the AI read that differs from the receipt, each with a box: ticked where the receipt has
 * nothing yet, unticked where it would replace something. Text fields can be corrected before saving.
 */
@Composable
private fun AiReviewDialog(rows: List<AiRow>, onDismiss: () -> Unit, onSave: (AiDetails) -> Unit) {
    val kept = remember(rows) { mutableStateMapOf<AiField, Boolean>().apply { rows.forEach { put(it.field, it.now == null) } } }
    val values = remember(rows) { mutableStateMapOf<AiField, String>().apply { rows.forEach { put(it.field, it.read) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_review_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.ai_check), style = MaterialTheme.typography.bodyMedium)
                rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = kept[row.field] == true,
                            onCheckedChange = { kept[row.field] = it },
                            modifier = Modifier.testTag("ai_keep_${row.field.name.lowercase()}"),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            if (row.field.editable) {
                                OutlinedTextField(
                                    value = values[row.field].orEmpty(),
                                    onValueChange = { values[row.field] = it },
                                    label = { Text(stringResource(row.field.label)) },
                                    singleLine = true,
                                    modifier = Modifier.testTag("ai_value_${row.field.name.lowercase()}"),
                                )
                            } else {
                                Text(stringResource(row.field.label), style = MaterialTheme.typography.bodySmall)
                                Text(row.read, style = MaterialTheme.typography.bodyLarge)
                            }
                            row.now?.let {
                                Text(
                                    stringResource(R.string.ai_now, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(keptDetails(rows, kept, values)) },
                enabled = rows.any { kept[it.field] == true },
                modifier = Modifier.testTag("ai_save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What the user kept, as the receipt will store it. A corrected text that doesn't parse is left out. */
private fun keptDetails(rows: List<AiRow>, kept: Map<AiField, Boolean>, values: Map<AiField, String>): AiDetails {
    fun text(f: AiField) = values[f]?.trim()?.takeIf { kept[f] == true && it.isNotEmpty() }
    fun amount(f: AiField) = text(f)?.let { parseTypedAmount(it).getOrNull() }
    val read = rows.associate { it.field to it }
    return AiDetails(
        store = text(AiField.STORE),
        purchasedAt = if (kept[AiField.DATE] == true) read[AiField.DATE]?.millis else null,
        amount = amount(AiField.AMOUNT),
        invoiceNumber = text(AiField.INVOICE)?.take(40),
        businessId = text(AiField.BUSINESS_ID)?.filter { it.isDigit() }?.takeIf { it.length == 9 },
        vat = amount(AiField.VAT),
        returnBy = if (kept[AiField.RETURN_BY] == true) read[AiField.RETURN_BY]?.millis else null,
        warrantyUntil = if (kept[AiField.WARRANTY] == true) read[AiField.WARRANTY]?.millis else null,
    )
}
