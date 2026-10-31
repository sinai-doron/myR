package com.neonsheep.myreceipts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        requestSmsPermissionIfNeeded()

        val dao = AppDatabase.getInstance(this).receiptDao()
        val receiptsFlow = dao.getAll().map { entities ->
            entities.map { it.toUiModel() }
        }

        setContent {
            val receipts by receiptsFlow.collectAsState(initial = emptyList())
            var selectedVendor by remember { mutableStateOf<String?>(null) }
            var selectedReceipt by remember { mutableStateOf<CapturedReceipt?>(null) }
            val scope = rememberCoroutineScope()
            MyReceiptsTheme {
                when {
                    selectedReceipt != null -> {
                        ReceiptDetailScreen(
                            receipt = selectedReceipt!!,
                            onBack = { selectedReceipt = null },
                        )
                    }
                    selectedVendor != null -> {
                        val vendorReceipts = receipts.filter { it.senderLabel == selectedVendor }
                        VendorReceiptsScreen(
                            vendor = selectedVendor!!,
                            receipts = vendorReceipts,
                            onInfoClick = { selectedReceipt = it },
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
                        )
                    }
                }
            }
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
