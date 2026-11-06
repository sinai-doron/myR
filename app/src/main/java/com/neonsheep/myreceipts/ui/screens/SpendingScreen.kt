package com.neonsheep.myreceipts.ui.screens

import com.neonsheep.myreceipts.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.data.BudgetEntity
import com.neonsheep.myreceipts.data.BudgetStatus
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.MonthSpending
import com.neonsheep.myreceipts.ui.components.CardTitle
import com.neonsheep.myreceipts.ui.components.MerchantLogo
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.TopBar
import com.neonsheep.myreceipts.ui.theme.DangerRed
import com.neonsheep.myreceipts.ui.theme.Purple
import com.neonsheep.myreceipts.ui.theme.PurpleLight
import com.neonsheep.myreceipts.ui.theme.SuccessGreen
import com.neonsheep.myreceipts.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A month's spending: the total and the budget, by category, and by store. Only receipts with a
 * known amount count; the rest are named, so the user can add their amounts.
 */
@Composable
fun SpendingScreen(
    spending: MonthSpending,
    /** Monthly limits by category name, and [BudgetEntity.ALL] for the whole month. */
    budgets: Map<String, Double>,
    isCurrentMonth: Boolean,
    onMonth: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onSetBudget: suspend (category: String, limit: Double?) -> Unit,
    onSetCategory: suspend (store: String, category: Category) -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    // Which budget is being edited (a category name or ALL), and which store's category.
    var editingBudget by remember { mutableStateOf<String?>(null) }
    var editingStore by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = stringResource(R.string.spending_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthPicker(spending.month, isCurrentMonth, onMonth)

            // The month's total, and the whole-month budget
            SectionCard(modifier = Modifier.testTag("month_total")) {
                Text(
                    stringResource(R.string.spent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatAmount(spending.total),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                val overall = budgets[BudgetEntity.ALL]
                if (overall != null) {
                    BudgetBar(BudgetStatus(spending.total, overall), onClick = { editingBudget = BudgetEntity.ALL })
                } else {
                    TextButton(onClick = { editingBudget = BudgetEntity.ALL }, modifier = Modifier.testTag("set_budget")) {
                        Text(stringResource(R.string.set_monthly_budget), color = Purple)
                    }
                }
                if (spending.withoutAmount > 0) {
                    Text(
                        pluralStringResource(R.plurals.without_amount, spending.withoutAmount, spending.withoutAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).testTag("without_amount"),
                    )
                }
            }

            // By category, each with its own budget if set
            SectionCard(padding = 0.dp) {
                CardTitle(
                    stringResource(R.string.by_category),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                )
                val shown = Category.entries.filter { (spending.byCategory[it] ?: 0.0) > 0 || budgets.containsKey(it.name) }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.nothing_this_month),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                shown.forEachIndexed { i, category ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    CategoryRow(
                        category = category,
                        spent = spending.byCategory[category] ?: 0.0,
                        limit = budgets[category.name],
                        largest = shown.maxOf { spending.byCategory[it] ?: 0.0 },
                        onClick = { editingBudget = category.name },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // By store; tap one to change what it counts as
            if (spending.byStore.isNotEmpty()) {
                SectionCard(padding = 0.dp) {
                    CardTitle(
                        stringResource(R.string.by_store),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    )
                    spending.byStore.forEachIndexed { i, store ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingStore = store.store }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .testTag("store_${store.store}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MerchantLogo(senderLabel = store.store)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    store.store,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(R.string.store_row, store.category.emoji, stringResource(store.category.labelRes), receiptCountLabel(store.receipts)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                formatAmount(store.total),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    editingBudget?.let { key ->
        BudgetDialog(
            title = if (key == BudgetEntity.ALL) stringResource(R.string.monthly_budget)
            else stringResource(R.string.category_budget, stringResource(Category.fromName(key)?.labelRes ?: R.string.category_other)),
            initial = budgets[key],
            onDismiss = { editingBudget = null },
            onSave = { limit ->
                editingBudget = null
                scope.launch { onSetBudget(key, limit) }
            },
        )
    }
    editingStore?.let { store ->
        val current = spending.byStore.firstOrNull { it.store == store }?.category
        AlertDialog(
            onDismissRequest = { editingStore = null },
            title = { Text(stringResource(R.string.store_counts_as, store)) },
            text = {
                Column {
                    Category.entries.forEach { c ->
                        Text(
                            "${c.emoji}  ${stringResource(c.labelRes)}" + if (c == current) "  ✓" else "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (c == current) FontWeight.Bold else FontWeight.Normal,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingStore = null
                                    scope.launch { onSetCategory(store, c) }
                                }
                                .padding(vertical = 10.dp)
                                .testTag("category_${c.name}"),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editingStore = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun MonthPicker(month: YearMonth, isCurrentMonth: Boolean, onMonth: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.testTag("previous_month")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).testTag("month"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = { onMonth(month.plusMonths(1)) },
            enabled = !isCurrentMonth,
            modifier = Modifier.testTag("next_month"),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

@Composable
private fun CategoryRow(category: Category, spent: Double, limit: Double?, largest: Double, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("category_row_${category.name}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${category.emoji}  ${stringResource(category.labelRes)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                formatAmount(spent),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(6.dp))
        if (limit != null) {
            BudgetBar(BudgetStatus(spent, limit), onClick = onClick)
        } else {
            // Without a budget: the bar compares categories with each other.
            Bar(if (largest > 0) (spent / largest).toFloat() else 0f, Purple)
        }
    }
}

/** Spent of the limit, coloured by how close it is, with what's left or how far over. */
@Composable
internal fun BudgetBar(status: BudgetStatus, onClick: () -> Unit) {
    val color = when (status.state) {
        BudgetStatus.State.OK -> SuccessGreen
        BudgetStatus.State.NEAR -> WarningAmber
        BudgetStatus.State.OVER -> DangerRed
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("budget_${status.state.name}")) {
        Bar(status.fraction.coerceIn(0.0, 1.0).toFloat(), color)
        Spacer(Modifier.height(4.dp))
        Text(
            when (status.state) {
                BudgetStatus.State.OVER -> stringResource(R.string.budget_over, formatAmount(-status.left), formatAmount(status.limit))
                else -> stringResource(R.string.budget_left, formatAmount(status.left), formatAmount(status.limit))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (status.state == BudgetStatus.State.OVER) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = PurpleLight,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@Composable
private fun BudgetDialog(title: String, initial: Double?, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { "%.0f".format(Locale.US, it) } ?: "") }
    val parsed = parseTypedAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.budget_field)) },
                    singleLine = true,
                    isError = parsed.isFailure,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("budget_field"),
                )
                Box(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.budget_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsed.getOrNull()) },
                enabled = parsed.isSuccess,
                modifier = Modifier.testTag("budget_save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

