package com.neonsheep.myreceipts.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.neonsheep.myreceipts.data.BudgetEntity
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.MonthSpending
import com.neonsheep.myreceipts.data.Spend
import com.neonsheep.myreceipts.data.Spending
import com.neonsheep.myreceipts.ui.components.AccountInitial
import com.neonsheep.myreceipts.ui.components.Badge
import com.neonsheep.myreceipts.ui.components.EmojiTile
import com.neonsheep.myreceipts.ui.components.SectionLabel
import com.neonsheep.myreceipts.ui.components.storeDisplayName
import com.neonsheep.myreceipts.ui.theme.Chip
import com.neonsheep.myreceipts.ui.theme.Faint
import com.neonsheep.myreceipts.ui.theme.GreenOnDark
import com.neonsheep.myreceipts.ui.theme.Hero
import com.neonsheep.myreceipts.ui.theme.HeroChip
import com.neonsheep.myreceipts.ui.theme.HeroText
import com.neonsheep.myreceipts.ui.theme.HeroTrack
import com.neonsheep.myreceipts.ui.theme.Muted
import com.neonsheep.myreceipts.ui.theme.NearBadge
import com.neonsheep.myreceipts.ui.theme.OverBadge
import com.neonsheep.myreceipts.ui.theme.OverBadgeInk
import java.time.LocalDate
import java.time.YearMonth
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
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Add
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
import com.neonsheep.myreceipts.data.BudgetStatus
import com.neonsheep.myreceipts.data.SampleReceipts
import com.neonsheep.myreceipts.ui.components.CardTitle
import com.neonsheep.myreceipts.ui.components.MerchantLogo
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.Stat
import com.neonsheep.myreceipts.ui.theme.Red
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import com.neonsheep.myreceipts.ui.theme.Green
import com.neonsheep.myreceipts.ui.theme.GreenSoft
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * A receipt as the screens show it. [senderLabel] and [timestamp] are the store and purchase time the user
 * set, else what the text said ([CapturedReceiptEntity.store], [CapturedReceiptEntity.boughtAt]).
 */
data class CapturedReceipt(
    val link: String,
    val senderLabel: String,
    val timestamp: Long,
    val clientId: String,
    val totalAmount: Double? = null,
    val pdfPath: String? = null,
    val pdfStatus: String? = null,
    val totalSource: String? = null,
    /** This receipt's own [Category] name, when the user moved it; null: its store's. */
    val category: String? = null,
    val returnBy: Long? = null,
    val warrantyUntil: Long? = null,
    val notDuplicate: Boolean = false,
    /** The store as the text named it, before any correction. */
    val textSender: String = senderLabel,
    /** When the text arrived, before any correction of the date. */
    val textArrivedAt: Long = timestamp,
    /** How it came in: [com.neonsheep.myreceipts.data.Origin]. */
    val origin: String = com.neonsheep.myreceipts.data.Origin.SMS,
)

data class VendorSummary(
    val name: String,
    val receiptCount: Int,
    val lastReceiptDate: Long,
)

@Composable
fun HomeScreen(
    receipts: List<CapturedReceipt>,
    /** This month, by category and store, from the receipts with an amount. */
    spending: MonthSpending,
    /** Monthly limits by category name, and [BudgetEntity.ALL] for the whole month. */
    budgets: Map<String, Double>,
    categoryOf: (String) -> Category,
    modifier: Modifier = Modifier,
    accountEmail: String? = null,
    /** The account circle opens Settings: the account, sign-out, export and import. */
    onOpenSettings: () -> Unit = {},
    captureOff: Boolean = false,
    onTurnOnCapture: () -> Unit = {},
    onOpenSpending: () -> Unit = {},
    onOpenStores: () -> Unit = {},
    onOpenReceipt: (CapturedReceipt) -> Unit = {},
    onAddSamples: () -> Unit = {},
    onRemoveSamples: () -> Unit = {},
    /** Groups of receipts that look like one purchase ([com.neonsheep.myreceipts.data.Duplicates]). */
    duplicates: List<List<CapturedReceipt>> = emptyList(),
    onNotSame: (List<CapturedReceipt>) -> Unit = {},
    onMerge: (List<CapturedReceipt>) -> Unit = {},
    /** Opens the ways to add a receipt by hand (scan, file). */
    onAddReceipt: () -> Unit = {},
) {
    val monthName = remember { SimpleDateFormat("LLLL", Locale.getDefault()).format(Date()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The app, the month, and the account
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Chip),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_receipt), contentDescription = null, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.app_name))
                Text(
                    monthName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onAddReceipt, modifier = Modifier.testTag("add_receipt")) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_receipt), tint = Green)
            }
            if (accountEmail != null) {
                AccountInitial(
                    accountEmail,
                    Modifier.clickable(onClickLabel = stringResource(R.string.settings), onClick = onOpenSettings).testTag("account"),
                )
            }
        }

        if (captureOff) CaptureOffBanner(onTurnOn = onTurnOnCapture)
        if (receipts.any { it.isSample() }) SamplesBanner(onRemove = onRemoveSamples)
        // One at a time: the next shows once this one is answered.
        duplicates.firstOrNull()?.let { group ->
            SameReceiptCard(group, onNotSame = { onNotSame(group) }, onMerge = { onMerge(group) })
        }

        // The month and its budget, even before the first receipt
        MonthHero(spending.total, budgets[BudgetEntity.ALL], onOpenSpending)

        val tiles = remember(spending, budgets) {
            Category.entries
                .filter { (spending.byCategory[it] ?: 0.0) > 0 || budgets.containsKey(it.name) }
                .sortedByDescending { spending.byCategory[it] ?: 0.0 }
                .take(4)
        }
        if (tiles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.category_heading), Modifier.weight(1f))
                Text(
                    stringResource(R.string.budgets_link),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Green,
                    modifier = Modifier.clickable(onClick = onOpenSpending).padding(4.dp),
                )
            }
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { c ->
                        CategoryTile(c, spending.byCategory[c] ?: 0.0, budgets[c.name], onOpenSpending, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (receipts.isEmpty()) {
            EmptyState(onAddSamples, onAddReceipt)
        } else {
            RecentReceipts(receipts, categoryOf, onOpenReceipt, onOpenStores)
        }
        Spacer(Modifier.height(4.dp))
    }

}

/** The dark card: this month's spending, the budget and what's left. Tap for the spending tab. */
@Composable
private fun MonthHero(spent: Double, budget: Double?, onClick: () -> Unit) {
    val today = remember { LocalDate.now() }
    val daysLeft = today.lengthOfMonth() - today.dayOfMonth
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Hero)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)
            .testTag("month_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.spent_this_month), Modifier.weight(1f), color = HeroText)
            if (budget != null) {
                Text(
                    stringResource(R.string.of_budget, formatAmount(budget)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroText,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(HeroChip).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            formatAmount(spent),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        if (budget != null) {
            val status = BudgetStatus(spent, budget)
            LinearProgressIndicator(
                progress = { status.fraction.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = when (status.state) {
                    BudgetStatus.State.OK -> GreenOnDark
                    BudgetStatus.State.NEAR -> NearBadge
                    BudgetStatus.State.OVER -> OverBadge
                },
                trackColor = HeroTrack,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("budget_${status.state.name}")) {
                val over = status.state == BudgetStatus.State.OVER
                Text(
                    if (over) stringResource(R.string.hero_over, formatAmount(-status.left))
                    else stringResource(R.string.hero_left, formatAmount(status.left)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (over) OverBadgeInk else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (over) OverBadge else Green)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    pluralStringResource(R.plurals.days_to_go, daysLeft, daysLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroText,
                )
            }
        } else {
            Text(
                stringResource(R.string.see_spending),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GreenOnDark,
            )
        }
    }
}

/** A category this month: what it came to, and how much of its budget, if it has one. */
@Composable
private fun CategoryTile(category: Category, spent: Double, limit: Double?, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("tile_${category.name}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            EmojiTile(category.emoji)
            Spacer(Modifier.weight(1f))
            if (limit != null) {
                val status = BudgetStatus(spent, limit)
                Badge(
                    if (status.state == BudgetStatus.State.OVER) stringResource(R.string.tile_over, formatAmount(-status.left))
                    else stringResource(R.string.percent, Math.round(status.fraction * 100).toInt()),
                    status.state,
                )
            }
        }
        Text(
            stringResource(category.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatAmount(spent),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The last three receipts, newest first, and the way to every store. */
@Composable
private fun RecentReceipts(
    receipts: List<CapturedReceipt>,
    categoryOf: (String) -> Category,
    onOpenReceipt: (CapturedReceipt) -> Unit,
    onOpenStores: () -> Unit,
) {
    val recent = remember(receipts) { receipts.sortedByDescending { it.timestamp }.take(3) }
    SectionCard(padding = 0.dp) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.recent_receipts),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenStores, modifier = Modifier.testTag("all_stores")) {
                Text(stringResource(R.string.all_stores), color = Green)
            }
        }
        recent.forEach { r ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenReceipt(r) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("recent_${r.clientId}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MerchantLogo(r.senderLabel, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            storeDisplayName(r.senderLabel),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        DownloadPill(r)
                    }
                    Text(
                        stringResource(R.string.vendor_row, stringResource(categoryOf(r.senderLabel).labelRes), formatShortTimestamp(r.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (r.totalAmount != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatAmount(r.totalAmount),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * Receipts that look like the same purchase: the store, the amount and when each came in. The user
 * says whether they're the same; nothing is merged or deleted without that.
 */
@Composable
internal fun SameReceiptCard(group: List<CapturedReceipt>, onNotSame: () -> Unit, onMerge: () -> Unit) {
    val first = group.first()
    SectionCard(modifier = Modifier.testTag("same_receipt"), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.same_receipt_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(
                    R.string.same_receipt_body,
                    first.senderLabel,
                    first.totalAmount?.let { formatAmount(it) } ?: "",
                    arrivals(group.map { it.timestamp }),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNotSame, modifier = Modifier.testTag("same_receipt_no")) {
                    Text(stringResource(R.string.same_receipt_no), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onMerge, modifier = Modifier.testTag("same_receipt_merge")) {
                    Text(stringResource(R.string.same_receipt_merge), color = Green)
                }
            }
        }
    }
}

/** While sample receipts are shown: says so, and removes them all. */
@Composable
private fun SamplesBanner(onRemove: () -> Unit) {
    SectionCard(modifier = Modifier.testTag("samples_banner"), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.samples_banner_title),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.samples_banner_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove, modifier = Modifier.testTag("remove_samples")) {
                Text(stringResource(R.string.remove), color = Green)
            }
        }
    }
}

@Composable
private fun EmptyState(onAddSamples: () -> Unit, onAddReceipt: () -> Unit) {
    SectionCard(padding = 28.dp) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GreenSoft, CircleShape),
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
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAddReceipt, modifier = Modifier.testTag("empty_add_receipt")) {
                Text(stringResource(R.string.add_receipt), color = Green)
            }
            TextButton(onClick = onAddSamples, modifier = Modifier.testTag("add_samples")) {
                Text(stringResource(R.string.try_samples), color = Green)
            }
        }
    }
}

@Composable
internal fun receiptCountLabel(count: Int): String = pluralStringResource(R.plurals.receipt_count, count, count)

@Composable
internal fun storeCountLabel(count: Int): String = pluralStringResource(R.plurals.store_count, count, count)

// In the app's language at the time of the call, so a language change applies at once.
internal fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))
internal fun formatShortTimestamp(epochMillis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMillis))
/** When each came in: "6 Nov, 12:40 · 12:41", the day said once while it's the same. */
internal fun arrivals(times: List<Long>): String {
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    return times.mapIndexed { i, at ->
        val sameDay = i > 0 && day.format(Date(at)) == day.format(Date(times[i - 1]))
        if (sameDay) clock.format(Date(at)) else formatShortTimestamp(at)
    }.joinToString(" · ")
}
internal fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMillis))
/** Shekels in the app's language: "₪442.20" in English, "442.20 ₪" in Hebrew. */
internal fun formatAmount(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = Currency.getInstance("ILS")
    }.format(amount)

/** Rounded to whole shekels, for a tile with little room. */
internal fun formatWholeAmount(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = Currency.getInstance("ILS")
        maximumFractionDigits = 0
    }.format(amount)

/** Receipts older than this are kept but never downloaded automatically. */
internal const val AUTO_DOWNLOAD_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/** A made-up receipt from "Try with sample receipts". */
internal fun CapturedReceipt.isSample(): Boolean = SampleReceipts.isSample(clientId)

/** A PDF or scan added by hand: it has no link to fetch or open. */
internal fun CapturedReceipt.isFile(): Boolean = link.isEmpty()

internal fun CapturedReceipt.hasPdf(): Boolean = pdfPath?.let { File(it).exists() } == true
internal fun CapturedReceipt.isPastDownloadWindow(): Boolean =
    System.currentTimeMillis() - timestamp > AUTO_DOWNLOAD_WINDOW_MS

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PreviewHome() {
    val receipts = SampleReceipts.build().map {
        CapturedReceipt(it.link, it.senderLabel, it.timestamp, it.clientId, it.totalAmount)
    }
    MyReceiptsTheme {
        HomeScreen(
            receipts = receipts,
            spending = Spending.forMonth(YearMonth.now(), receipts.map { Spend(it.senderLabel, it.timestamp, it.totalAmount) }, Category::guessFor),
            budgets = mapOf(BudgetEntity.ALL to 2000.0, "GROCERIES" to 400.0),
            categoryOf = Category::guessFor,
            accountEmail = "doron@example.com",
        )
    }
}
