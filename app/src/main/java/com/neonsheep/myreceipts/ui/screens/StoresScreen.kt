package com.neonsheep.myreceipts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.data.BudgetStatus
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.MonthSpending
import com.neonsheep.myreceipts.ui.components.Badge
import com.neonsheep.myreceipts.ui.components.MerchantLogo
import com.neonsheep.myreceipts.ui.components.storeDisplayName
import com.neonsheep.myreceipts.ui.components.storeSecondaryName
import com.neonsheep.myreceipts.ui.theme.Chip
import com.neonsheep.myreceipts.ui.theme.Faint
import com.neonsheep.myreceipts.ui.theme.Hero
import com.neonsheep.myreceipts.ui.theme.Ink
import java.text.Collator
import java.util.Locale

private enum class StoreSort(val labelRes: Int, val tag: String) {
    LATEST(R.string.sort_latest, "sort_latest"),
    MOST_SPENT(R.string.sort_most_spent, "sort_most_spent"),
    NAME(R.string.sort_name, "sort_name"),
}

/** Every store with receipts: how many, the latest, this month's total, and its category's budget. */
@Composable
fun StoresScreen(
    receipts: List<CapturedReceipt>,
    spending: MonthSpending,
    budgets: Map<String, Double>,
    categoryOf: (String) -> Category,
    onStoreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sort by rememberSaveable { mutableStateOf(StoreSort.LATEST) }
    val thisMonth = remember(spending) { spending.byStore.associate { it.store to it.total } }
    val stores = remember(receipts, sort, thisMonth) {
        val all = receipts.groupBy { it.senderLabel }.map { (name, items) ->
            VendorSummary(name, items.size, items.maxOf { it.timestamp })
        }
        val collator = Collator.getInstance(Locale.getDefault())
        when (sort) {
            StoreSort.LATEST -> all.sortedByDescending { it.lastReceiptDate }
            StoreSort.MOST_SPENT -> all.sortedByDescending { thisMonth[it.name] ?: 0.0 }
            StoreSort.NAME -> all.sortedWith(compareBy(collator) { storeDisplayName(it.name) })
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.nav_stores),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.home_subtitle, storeCountLabel(stores.size), receiptCountLabel(receipts.size)),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StoreSort.entries.forEach { s ->
                val on = s == sort
                Text(
                    stringResource(s.labelRes),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (on) MaterialTheme.colorScheme.surface else Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) Hero else Chip)
                        .selectable(selected = on, role = Role.RadioButton) { sort = s }
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag(s.tag),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(stores, key = { it.name }) { store ->
                val category = categoryOf(store.name)
                StoreCard(store, category, thisMonth[store.name] ?: 0.0, spending.byCategory[category], budgets[category.name]) {
                    onStoreClick(store.name)
                }
            }
        }
    }
}

@Composable
private fun StoreCard(
    store: VendorSummary,
    category: Category,
    monthTotal: Double,
    categorySpent: Double?,
    categoryLimit: Double?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("store_card_${store.name}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MerchantLogo(store.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        storeDisplayName(store.name),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    storeSecondaryName(store.name)?.let {
                        Text(
                            " ($it)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Faint,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    stringResource(R.string.vendor_row, receiptCountLabel(store.receiptCount), formatDate(store.lastReceiptDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Faint,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatAmount(monthTotal),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(stringResource(R.string.this_month_short), style = MaterialTheme.typography.bodySmall, color = Faint)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge("${category.emoji} ${stringResource(category.labelRes)}", state = null)
            if (categoryLimit != null) {
                val status = BudgetStatus(categorySpent ?: 0.0, categoryLimit)
                Badge(
                    if (status.state == BudgetStatus.State.OVER) stringResource(R.string.over_budget_badge, formatAmount(-status.left))
                    else stringResource(R.string.percent_of_budget, Math.round(status.fraction * 100).toInt()),
                    status.state,
                )
            }
        }
    }
}
