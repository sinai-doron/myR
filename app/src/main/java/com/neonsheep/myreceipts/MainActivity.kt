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
import android.widget.Toast
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
import com.neonsheep.myreceipts.data.CapturedReceiptEntity
import com.neonsheep.myreceipts.data.ReceiptLinks
import com.neonsheep.myreceipts.data.SmsBackfill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.neonsheep.myreceipts.ui.screens.CapturedReceipt
import com.neonsheep.myreceipts.ui.screens.ReceiptDetailScreen
import com.neonsheep.myreceipts.ui.screens.VendorListScreen
import com.neonsheep.myreceipts.ui.screens.VendorReceiptsScreen
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val NOT_LOADED = Any()

class MainActivity : ComponentActivity() {

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val receiveGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        val readGranted = permissions[Manifest.permission.READ_SMS] == true
        if (!receiveGranted) {
            Toast.makeText(this, getString(R.string.sms_permission_denied), Toast.LENGTH_LONG).show()
        }
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
        requestSmsPermissionIfNeeded()

        val dao = AppDatabase.getInstance(this).receiptDao()
        val receiptsFlow = dao.getAll().map { entities ->
            entities.map { it.toUiModel() }
        }

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
        }

        setContent {
            val receipts by receiptsFlow.collectAsState(initial = emptyList())
            // NOT_LOADED until the encrypted session has been read, so the sign-in screen doesn't flash
            val user by auth.user.map<SignedInUser?, Any?> { it }.collectAsState(initial = NOT_LOADED)
            var signingIn by remember { mutableStateOf(false) }
            var signInError by remember { mutableStateOf<String?>(null) }
            var selectedVendor by remember { mutableStateOf<String?>(null) }
            var selectedReceiptId by remember { mutableStateOf<String?>(null) }
            val selectedReceipt = receipts.firstOrNull { it.clientId == selectedReceiptId }
            val scope = rememberCoroutineScope()
            MyReceiptsTheme {
                val signedIn = user as? SignedInUser
                when {
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
                        )
                    }
                    selectedVendor != null -> {
                        val vendorReceipts = receipts.filter { it.senderLabel == selectedVendor }
                        VendorReceiptsScreen(
                            vendor = selectedVendor!!,
                            receipts = vendorReceipts,
                            onInfoClick = { selectedReceiptId = it.clientId },
                            onDelete = { receipt ->
                                scope.launch { dao.deleteByClientId(receipt.clientId) }
                            },
                            onBack = { selectedVendor = null },
                        )
                    }
                    else -> {
                        VendorListScreen(
                            receipts = receipts,
                            onVendorClick = { selectedVendor = it },
                            accountEmail = signedIn.email,
                            onSignOut = {
                                scope.launch {
                                    auth.signOut()
                                    GoogleIdTokens.forget(this@MainActivity)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /** Google's account picker, then the backend. Returns an error to show, or null. */
    private suspend fun signIn(auth: AuthRepository): String? = try {
        val google = GoogleIdTokens(this)
        auth.signIn(deviceLabel = Build.MODEL) { nonce -> google.idToken(nonce) }
        null
    } catch (e: SignInCancelled) {
        null
    } catch (e: SignInFailed) {
        e.message
    } catch (e: BackendException) {
        when (e) {
            BackendException.NotAllowed -> "This Google account isn't allowed to use My Receipts."
            is BackendException.Network -> "Can't reach the server. Check your connection and try again."
            is BackendException.RateLimited -> "Too many attempts. Try again in a minute."
            else -> "Sign-in failed (${e.message}). Try again."
        }
    }

    /** Sends the receipt's link to the backend and saves the PDF. Returns an error to show, or null. */
    private suspend fun getPdf(receipt: CapturedReceipt): String? {
        val result = Backend.fetchReceiptPdf(this, receipt.link, receipt.clientId)
        result.getOrNull()?.let { path ->
            AppDatabase.getInstance(this).receiptDao().updatePdfPath(receipt.clientId, path)
            return null
        }
        return when (val e = result.exceptionOrNull()) {
            BackendException.Unauthorized -> "You've been signed out. Sign in again."
            is BackendException.Network -> "Can't reach the server. Check your connection and try again."
            is BackendException.RateLimited -> "Too many requests. Try again in ${e.retryAfterSeconds} seconds."
            is BackendException.Http ->
                if (e.code == 410) "The store no longer has this receipt. Links expire after a while."
                else if (e.code == 400) "The server doesn't accept links from this store."
                else "The server couldn't make a PDF of this receipt."
            else -> "Couldn't get the PDF."
        }
    }

    private fun requestSmsPermissionIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_SMS)
        }
        if (needed.isNotEmpty()) {
            smsPermissionLauncher.launch(needed.toTypedArray())
        } else if (!SmsBackfill.isBackfillDone(this)) {
            val dao = AppDatabase.getInstance(this).receiptDao()
            CoroutineScope(Dispatchers.IO).launch {
                SmsBackfill.backfillIfNeeded(this@MainActivity, dao)
            }
        }
    }
}

fun CapturedReceiptEntity.toUiModel(): CapturedReceipt = CapturedReceipt(
    link = link,
    senderLabel = senderLabel,
    timestamp = timestamp,
    clientId = clientId,
    totalAmount = totalAmount,
    pdfPath = pdfPath,
)
