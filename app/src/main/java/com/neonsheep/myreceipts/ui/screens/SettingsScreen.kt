package com.neonsheep.myreceipts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.ui.components.CardTitle
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.components.TopBar
import com.neonsheep.myreceipts.ui.theme.Green
import com.neonsheep.myreceipts.ui.theme.Red

/** The account, and moving receipts to another phone. */
@Composable
fun SettingsScreen(
    accountEmail: String?,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    /** What the last export or import did, or why it couldn't; null: nothing to say. */
    status: String? = null,
    /** An export or import is running. */
    busy: Boolean = false,
    /** More settings, from other features, shown above the account. */
    extra: @Composable () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = stringResource(R.string.settings), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                CardTitle(stringResource(R.string.move_receipts))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.move_receipts_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Action(stringResource(R.string.export_receipts), "export", enabled = !busy, onClick = onExport)
                Action(stringResource(R.string.import_receipts), "import", enabled = !busy, onClick = onImport)
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp).testTag("backup_status"),
                    )
                }
            }
            extra()
            SectionCard {
                CardTitle(stringResource(R.string.signed_in))
                if (accountEmail != null) {
                    Text(
                        accountEmail,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out")) {
                    Text(stringResource(R.string.sign_out), color = Red)
                }
            }
        }
    }
}

@Composable
private fun Action(text: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) Green else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
