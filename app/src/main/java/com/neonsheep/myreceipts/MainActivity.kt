package com.neonsheep.myreceipts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.neonsheep.myreceipts.auth.AuthRepository
import com.neonsheep.myreceipts.auth.Backend
import com.neonsheep.myreceipts.auth.GoogleIdTokens
import com.neonsheep.myreceipts.auth.SignInCancelled
import com.neonsheep.myreceipts.auth.SignInFailed
import com.neonsheep.myreceipts.auth.SignedInUser
import com.neonsheep.myreceipts.net.BackendException
import com.neonsheep.myreceipts.ui.screens.SignInScreen
import com.neonsheep.myreceipts.worker.ReceiptBackfillWorker
import com.neonsheep.myreceipts.worker.ReceiptDownloadWorker
import com.neonsheep.myreceipts.worker.DeadlineReminderWorker
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.neonsheep.myreceipts.ui.screens.SmsDisclosureScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neonsheep.myreceipts.data.AppDatabase
import com.neonsheep.myreceipts.data.BudgetEntity
import com.neonsheep.myreceipts.data.Category
import com.neonsheep.myreceipts.data.SampleReceipts
import com.neonsheep.myreceipts.data.Spend
import com.neonsheep.myreceipts.data.Spending
import com.neonsheep.myreceipts.data.StoreCategoryEntity
import com.neonsheep.myreceipts.ui.screens.SpendingScreen
import java.time.YearMonth
import com.neonsheep.myreceipts.data.ReceiptAmounts
import com.neonsheep.myreceipts.data.CapturedReceiptEntity
import com.neonsheep.myreceipts.data.PdfStatus
import com.neonsheep.myreceipts.data.ReceiptLinks
import com.neonsheep.myreceipts.data.SmsBackfill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.neonsheep.myreceipts.ui.screens.CapturedReceipt
import com.neonsheep.myreceipts.ui.screens.ReceiptDetailScreen
import com.neonsheep.myreceipts.ui.screens.HomeScreen
import com.neonsheep.myreceipts.ui.screens.StoresScreen
import com.neonsheep.myreceipts.ui.components.BottomBar
import com.neonsheep.myreceipts.ui.components.Tab
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.saveable.rememberSaveable
import com.neonsheep.myreceipts.ui.screens.VendorReceiptsScreen
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val NOT_LOADED = Any()

class MainActivity : ComponentActivity() {

    // Whether receipt texts are captured. Read again on every resume: the permission can be
    // granted or taken away in Settings while the app is in the background.
    private val smsGranted = mutableStateOf(false)

    // Our explanation of SMS use (SmsDisclosureScreen), which always comes before Android's prompt.
    private val disclosureOpen = mutableStateOf(false)

    // A receipt to open, from a tapped reminder.
    private val openReceipt = mutableStateOf<String?>(null)

    // Reminders need nothing back: once allowed, the scheduled ones show; if not, they don't.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_SMS] == true
        smsGranted.value = hasSmsPermissions()
        if (readGranted) {
            val dao = AppDatabase.getInstance(this).receiptDao()
            CoroutineScope(Dispatchers.IO).launch {
                SmsBackfill.backfillIfNeeded(this@MainActivity, dao)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Light-only theme: dark status and navigation bar icons on the lavender background
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        smsGranted.value = hasSmsPermissions()
        openReceipt.value = intent?.getStringExtra(EXTRA_RECEIPT)
        // First launch: explain before anything asks. After "Not now", the list's banner offers it.
        disclosureOpen.value = !smsGranted.value && !prefs().getBoolean(KEY_DISCLOSURE_SEEN, false)
        if (smsGranted.value) backfillInboxIfNeeded()

        val dao = AppDatabase.getInstance(this).receiptDao()
        val receiptsFlow = dao.getAll().map { entities ->
            entities.map { it.toUiModel() }
        }

        val spendingDao = AppDatabase.getInstance(this).spendingDao()
        val auth = Backend.auth(this)

        // Link PDFs that are on the phone but not yet on their receipt (saved while the app was
        // closed, or copied over from an older install).
        CoroutineScope(Dispatchers.IO).launch {
            // Links captured before the filter knew they weren't receipts (marketing, unsubscribe).
            dao.getAllOnce()
                .filterNot { ReceiptLinks.isReceipt(it.link) }
                .forEach { r ->
                    Backend.pdfFile(this@MainActivity, r.clientId).delete()
                    dao.deleteByClientId(r.clientId)
                }
            dao.getAllOnce()
                .filter { it.pdfPath == null }
                .forEach { r ->
                    val file = Backend.pdfFile(this@MainActivity, r.clientId)
                    if (file.exists()) dao.updatePdfPath(r.clientId, file.absolutePath)
                }
            // Receipts saved before amounts were kept: read their PDFs once.
            ReceiptAmounts.catchUp(dao)
            // Old receipts without a PDF: caught up slowly in the background, signed in only.
            if (auth.currentUser() != null) ReceiptBackfillWorker.start(this@MainActivity)
        }

        // Made once, outside composition, so a redraw never starts new database queries.
        val storeCategoriesFlow = spendingDao.storeCategories()
            .map { list -> list.associate { it.senderLabel to it.category } }
        val budgetsFlow = spendingDao.budgets()
            .map { list -> list.associate { it.category to it.monthlyLimit } }
        val userFlow = auth.user.map<SignedInUser?, Any?> { it }

        setContent {
            val receipts by receiptsFlow.collectAsState(initial = emptyList())
            val storeCategories by storeCategoriesFlow.collectAsState(initial = emptyMap())
            val budgets by budgetsFlow.collectAsState(initial = emptyMap())
            // The tab, and the month the spending tab shows.
            var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
            var spendingMonth by remember { mutableStateOf(YearMonth.now()) }
            // NOT_LOADED until the encrypted session has been read, so the sign-in screen doesn't flash
            val user by userFlow.collectAsState(initial = NOT_LOADED)
            var signingIn by remember { mutableStateOf(false) }
            var signInError by remember { mutableStateOf<String?>(null) }
            var selectedVendor by remember { mutableStateOf<String?>(null) }
            var selectedReceiptId by remember { mutableStateOf<String?>(null) }
            val selectedReceipt = receipts.firstOrNull { it.clientId == selectedReceiptId }
            val scope = rememberCoroutineScope()
            LaunchedEffect(openReceipt.value) {
                openReceipt.value?.let { selectedReceiptId = it; openReceipt.value = null }
            }
            MyReceiptsTheme {
                val signedIn = user as? SignedInUser
                when {
                    // Before sign-in too, so anyone opening the app sees it before any SMS prompt.
                    disclosureOpen.value -> SmsDisclosureScreen(
                        onContinue = {
                            disclosureOpen.value = false
                            prefs().edit().putBoolean(KEY_DISCLOSURE_SEEN, true).apply()
                            requestSmsPermissions()
                        },
                        onNotNow = {
                            disclosureOpen.value = false
                            prefs().edit().putBoolean(KEY_DISCLOSURE_SEEN, true).apply()
                        },
                    )
                    user === NOT_LOADED -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    signedIn == null -> SignInScreen(
                        signingIn = signingIn,
                        error = signInError,
                        onSignIn = {
                            signingIn = true
                            signInError = null
                            scope.launch {
                                signInError = signIn(auth)
                                signingIn = false
                            }
                        },
                    )
                    selectedReceipt != null -> {
                        ReceiptDetailScreen(
                            receipt = selectedReceipt,
                            onBack = { selectedReceiptId = null },
                            onGetPdf = { receipt -> getPdf(receipt) },
                            onSetAmount = { receipt, amount -> dao.setUserTotal(receipt.clientId, amount) },
                            storeCategory = Category.fromName(storeCategories[selectedReceipt.senderLabel])
                                ?: Category.guessFor(selectedReceipt.senderLabel),
                            onEdit = { receipt, e ->
                                dao.applyEdit(
                                    receipt.clientId, e.storeName, e.purchasedAt, e.category, e.returnBy, e.warrantyUntil,
                                    amount = e.amount, amountChanged = e.amountChanged,
                                )
                                dao.getByClientId(receipt.clientId)?.let { saved ->
                                    DeadlineReminderWorker.schedule(this@MainActivity, saved)
                                    if (saved.returnBy != null || saved.warrantyUntil != null) askForNotifications()
                                }
                            },
                        )
                    }
                    selectedVendor != null -> {
                        val vendorReceipts = receipts.filter { it.senderLabel == selectedVendor }
                        VendorReceiptsScreen(
                            vendor = selectedVendor!!,
                            receipts = vendorReceipts,
                            onInfoClick = { selectedReceiptId = it.clientId },
                            onDelete = { receipt ->
                                scope.launch {
                                    dao.deleteByClientId(receipt.clientId)
                                    DeadlineReminderWorker.cancel(this@MainActivity, receipt.clientId)
                                }
                            },
                            onBack = { selectedVendor = null },
                        )
                    }
                    else -> {
                        val categoryOf = { store: String ->
                            Category.fromName(storeCategories[store]) ?: Category.guessFor(store)
                        }
                        val spends = receipts.map { Spend(it.senderLabel, it.timestamp, it.totalAmount, Category.fromName(it.category)) }
                        val thisMonth = Spending.forMonth(YearMonth.now(), spends, categoryOf)
                        // Back from another tab goes home; from home, it leaves the app.
                        BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            val content = Modifier.weight(1f)
                            when (tab) {
                                Tab.HOME -> HomeScreen(
                                    receipts = receipts,
                                    spending = thisMonth,
                                    budgets = budgets,
                                    categoryOf = categoryOf,
                                    modifier = content,
                                    accountEmail = signedIn.email,
                                    captureOff = !smsGranted.value,
                                    onTurnOnCapture = { disclosureOpen.value = true },
                                    onOpenSpending = { spendingMonth = YearMonth.now(); tab = Tab.SPENDING },
                                    onOpenStores = { tab = Tab.STORES },
                                    onOpenReceipt = { selectedReceiptId = it.clientId },
                                    onAddSamples = { scope.launch { dao.insertAll(SampleReceipts.build()) } },
                                    onRemoveSamples = { scope.launch { dao.deleteSamples() } },
                                    onSignOut = {
                                        scope.launch {
                                            auth.signOut()
                                            GoogleIdTokens.forget(this@MainActivity)
                                        }
                                    },
                                )
                                Tab.SPENDING -> SpendingScreen(
                                    spending = Spending.forMonth(spendingMonth, spends, categoryOf),
                                    budgets = budgets,
                                    isCurrentMonth = spendingMonth >= YearMonth.now(),
                                    onMonth = { spendingMonth = it },
                                    onBack = { tab = Tab.HOME },
                                    onSetBudget = { category, limit ->
                                        if (limit == null) spendingDao.removeBudget(category)
                                        else spendingDao.setBudget(BudgetEntity(category, limit))
                                    },
                                    onSetCategory = { store, category ->
                                        spendingDao.setStoreCategory(StoreCategoryEntity(store, category.name))
                                    },
                                    modifier = content,
                                )
                                Tab.STORES -> StoresScreen(
                                    receipts = receipts,
                                    spending = thisMonth,
                                    budgets = budgets,
                                    categoryOf = categoryOf,
                                    onStoreClick = { selectedVendor = it },
                                    modifier = content,
                                )
                            }
                            BottomBar(selected = tab, onSelect = { tab = it })
                        }
                    }
                }
            }
        }
    }

    /** Google's account picker, then the backend. Returns an error to show, or null. */
    private suspend fun signIn(auth: AuthRepository): String? = try {
        val google = GoogleIdTokens(this)
        auth.signIn(deviceLabel = Build.MODEL) { nonce -> google.idToken(nonce) }
        ReceiptBackfillWorker.start(this)
        null
    } catch (e: SignInCancelled) {
        null
    } catch (e: SignInFailed) {
        e.message
    } catch (e: BackendException) {
        when (e) {
            BackendException.NotAllowed -> getString(R.string.error_not_allowed)
            is BackendException.Network -> getString(R.string.error_network)
            is BackendException.RateLimited -> getString(R.string.error_sign_in_rate_limited)
            else -> getString(R.string.error_sign_in_failed, e.message)
        }
    }

    /** Sends the receipt's link to the backend and saves the PDF. Returns an error to show, or null. */
    private suspend fun getPdf(receipt: CapturedReceipt): String? {
        val result = Backend.fetchReceiptPdf(this, receipt.link, receipt.clientId)
        val dao = AppDatabase.getInstance(this).receiptDao()
        result.getOrNull()?.let { pdf ->
            ReceiptAmounts.pdfSaved(dao, receipt.clientId, pdf.path, pdf.total)
            // A queued retry would only fetch the same PDF again. Kept if this tap failed.
            ReceiptDownloadWorker.cancel(this, receipt.clientId)
            return null
        }
        val e = result.exceptionOrNull()
        if (e is BackendException.Http && e.code in setOf(400, 410)) {
            dao.updatePdfStatus(receipt.clientId, if (e.code == 410) PdfStatus.GONE else PdfStatus.UNSUPPORTED)
        }
        return when (e) {
            BackendException.Unauthorized -> getString(R.string.error_signed_out)
            is BackendException.Network -> getString(R.string.error_network)
            is BackendException.RateLimited -> getString(R.string.error_rate_limited_seconds, e.retryAfterSeconds.toInt())
            is BackendException.Http ->
                if (e.code == 410) getString(R.string.error_gone)
                else if (e.code == 400) getString(R.string.error_unsupported)
                else getString(R.string.error_server_pdf)
            else -> getString(R.string.error_pdf)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_RECEIPT)?.let { openReceipt.value = it }
    }

    /** Android's notification prompt, when a deadline is set and reminders can't show yet. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !DeadlineReminderWorker.canNotify(this)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        val was = smsGranted.value
        smsGranted.value = hasSmsPermissions()
        if (!was && smsGranted.value) backfillInboxIfNeeded()
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun hasSmsPermissions(): Boolean = SMS_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Android's prompt, or the app's settings page once Android won't show the prompt any more
     * (refused twice): asked before, and no rationale to show means it was refused for good.
     */
    private fun requestSmsPermissions() {
        val askedBefore = prefs().getBoolean(KEY_ASKED, false)
        val promptBlocked = askedBefore && SMS_PERMISSIONS.none { shouldShowRequestPermissionRationale(it) }
        if (promptBlocked) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        } else {
            prefs().edit().putBoolean(KEY_ASKED, true).apply()
            smsPermissionLauncher.launch(SMS_PERMISSIONS)
        }
    }

    private fun backfillInboxIfNeeded() {
        if (SmsBackfill.isBackfillDone(this)) return
        val dao = AppDatabase.getInstance(this).receiptDao()
        CoroutineScope(Dispatchers.IO).launch {
            SmsBackfill.backfillIfNeeded(this@MainActivity, dao)
        }
    }

    companion object {
        /** The receipt a reminder opens. */
        const val EXTRA_RECEIPT = "com.neonsheep.myreceipts.RECEIPT"
        private val SMS_PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        private const val PREFS = "sms_disclosure"
        private const val KEY_DISCLOSURE_SEEN = "seen"
        private const val KEY_ASKED = "asked"
    }
}

fun CapturedReceiptEntity.toUiModel(): CapturedReceipt = CapturedReceipt(
    link = link,
    senderLabel = store,
    timestamp = boughtAt,
    clientId = clientId,
    totalAmount = totalAmount,
    pdfPath = pdfPath,
    pdfStatus = pdfStatus,
    totalSource = totalSource,
    category = category,
    returnBy = returnBy,
    warrantyUntil = warrantyUntil,
    notDuplicate = notDuplicate,
    textSender = senderLabel,
    textArrivedAt = timestamp,
)
