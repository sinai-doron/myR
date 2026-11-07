package com.neonsheep.myreceipts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.ReceiptDates
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.SectionLabel
import com.neonsheep.myreceipts.ui.components.TopBar
import com.neonsheep.myreceipts.ui.theme.Green
import com.neonsheep.myreceipts.ui.theme.GreenSoft
import java.text.DateFormat
import java.util.Date

/**
 * What the user changed on a receipt. Null [storeName] or [purchasedAt] goes back to what the text said;
 * null [category] goes back to the store's. [amountChanged] keeps a read amount from becoming "typed" when
 * the user didn't touch it.
 */
data class ReceiptEdit(
    val storeName: String?,
    val purchasedAt: Long?,
    val amount: Double?,
    val amountChanged: Boolean,
    val category: String?,
    val returnBy: Long?,
    val warrantyUntil: Long?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditReceiptScreen(
    receipt: CapturedReceipt,
    /** The category the receipt's store counts in, shown as the default choice. */
    storeCategory: Category,
    onCancel: () -> Unit,
    onSave: (ReceiptEdit) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var store by remember { mutableStateOf(if (receipt.senderLabel == receipt.textSender) "" else receipt.senderLabel) }
    var boughtAt by remember { mutableStateOf(receipt.timestamp) }
    val initialAmount = receipt.totalAmount?.let { "%.2f".format(java.util.Locale.US, it) } ?: ""
    var amountText by remember { mutableStateOf(initialAmount) }
    var category by remember { mutableStateOf(Category.fromName(receipt.category)) }
    // Deadlines are kept as presets (days or years) so they follow a changed purchase date.
    var returnDays by remember { mutableStateOf(ReceiptDates.returnPreset(receipt.timestamp, receipt.returnBy)) }
    var warrantyYears by remember { mutableStateOf(ReceiptDates.warrantyPreset(receipt.timestamp, receipt.warrantyUntil)) }
    // A deadline set some other way (none today, but kept safe) stays unless the user picks another.
    val keptReturnBy = receipt.returnBy.takeIf { returnDays == null }
    val keptWarranty = receipt.warrantyUntil.takeIf { warrantyYears == null }
    var returnTouched by remember { mutableStateOf(false) }
    var warrantyTouched by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val amount = parseTypedAmount(amountText)
    // Any choice below the text fields closes the keyboard, so it never covers the next tap.
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = stringResource(R.string.edit_receipt), onBack = onCancel)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it.take(60) },
                    label = { Text(stringResource(R.string.label_store)) },
                    placeholder = { Text(receipt.textSender) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("edit_store"),
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { focus.clearFocus(); pickingDate = true }
                        .padding(vertical = 8.dp)
                        .testTag("edit_date"),
                ) {
                    Text(stringResource(R.string.label_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(boughtAt)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Green,
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.amount_paid)) },
                    prefix = { Text("₪") },
                    singleLine = true,
                    isError = amount.isFailure,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("edit_amount_field"),
                )
            }

            SectionCard {
                SectionLabel(stringResource(R.string.label_category))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Choice(
                        text = stringResource(R.string.category_as_store, stringResource(storeCategory.labelRes)),
                        selected = category == null,
                        tag = "category_store",
                    ) { category = null }
                    Category.entries.forEach { c ->
                        Choice("${c.emoji} ${stringResource(c.labelRes)}", selected = category == c, tag = "category_${c.name}") { category = c }
                    }
                }
            }

            SectionCard {
                SectionLabel(stringResource(R.string.label_return))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(stringResource(R.string.deadline_none), selected = returnDays == null && (returnTouched || keptReturnBy == null), tag = "return_none") {
                        returnDays = null; returnTouched = true
                    }
                    ReceiptDates.RETURN_DAYS.forEach { d ->
                        Choice(pluralStringResource(R.plurals.days, d, d), selected = returnDays == d, tag = "return_$d") {
                            returnDays = d; returnTouched = true
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel(stringResource(R.string.label_warranty))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(stringResource(R.string.deadline_none), selected = warrantyYears == null && (warrantyTouched || keptWarranty == null), tag = "warranty_none") {
                        warrantyYears = null; warrantyTouched = true
                    }
                    ReceiptDates.WARRANTY_YEARS.forEach { y ->
                        Choice(pluralStringResource(R.plurals.years, y, y), selected = warrantyYears == y, tag = "warranty_$y") {
                            warrantyYears = y; warrantyTouched = true
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.deadline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = {
                    onSave(
                        ReceiptEdit(
                            storeName = store.trim().takeIf { it.isNotEmpty() && it != receipt.textSender },
                            purchasedAt = boughtAt.takeIf { it != receipt.textArrivedAt },
                            amount = amount.getOrNull(),
                            amountChanged = amountText != initialAmount,
                            category = category?.name,
                            returnBy = returnDays?.let { ReceiptDates.returnBy(boughtAt, it) } ?: keptReturnBy.takeIf { !returnTouched },
                            warrantyUntil = warrantyYears?.let { ReceiptDates.warrantyUntil(boughtAt, it) } ?: keptWarranty.takeIf { !warrantyTouched },
                        ),
                    )
                },
                enabled = amount.isSuccess,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("edit_save"),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = ReceiptDates.asPickerDay(boughtAt))
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { boughtAt = ReceiptDates.onPickedDay(boughtAt, it) }
                    pickingDate = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val focus = LocalFocusManager.current
    FilterChip(
        selected = selected,
        onClick = { focus.clearFocus(); onClick() },
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenSoft, selectedLabelColor = Green),
        modifier = Modifier.testTag(tag),
    )
}
