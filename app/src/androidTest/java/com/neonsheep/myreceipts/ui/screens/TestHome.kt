package com.neonsheep.myreceipts.ui.screens

import androidx.compose.runtime.Composable
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.Spend
import com.neonsheep.myreceipts.data.Spending
import java.time.YearMonth

/** The home screen as the app builds it: this month's spending from the receipts, no budgets. */
@Composable
fun TestHome(
    receipts: List<CapturedReceipt>,
    budgets: Map<String, Double> = emptyMap(),
    accountEmail: String? = null,
    onOpenSettings: () -> Unit = {},
    captureOff: Boolean = false,
    onTurnOnCapture: () -> Unit = {},
    onOpenSpending: () -> Unit = {},
    onOpenStores: () -> Unit = {},
    onAddSamples: () -> Unit = {},
    onRemoveSamples: () -> Unit = {},
) = HomeScreen(
    receipts = receipts,
    spending = Spending.forMonth(YearMonth.now(), receipts.map { Spend(it.senderLabel, it.timestamp, it.totalAmount) }, Category::guessFor),
    budgets = budgets,
    categoryOf = Category::guessFor,
    accountEmail = accountEmail,
    onOpenSettings = onOpenSettings,
    captureOff = captureOff,
    onTurnOnCapture = onTurnOnCapture,
    onOpenSpending = onOpenSpending,
    onOpenStores = onOpenStores,
    onAddSamples = onAddSamples,
    onRemoveSamples = onRemoveSamples,
)
