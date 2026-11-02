package com.neonsheep.myreceipts.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import com.neonsheep.myreceipts.ui.theme.Purple
import com.neonsheep.myreceipts.ui.theme.PurpleLight

/**
 * What the app does with text messages, shown before Android's SMS permission prompt (Google
 * Play's prominent-disclosure rule). Every line here must stay true of the code: if what is
 * read, kept or sent changes, change this screen and the Play declaration with it.
 */
@Composable
fun SmsDisclosureScreen(onContinue: () -> Unit, onNotNow: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SectionCard(padding = 24.dp, modifier = Modifier.testTag("sms_disclosure")) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(72.dp).background(PurpleLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_receipt),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Keep every receipt automatically",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Shops send receipts as links in text messages, and the links stop working after a " +
                        "while. To save them for returns and warranties, My Receipts needs to read your " +
                        "text messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Point(
                        "🔎",
                        "What it reads",
                        "New texts as they arrive, and once, the texts already on your phone, to find " +
                            "receipt links from shops.",
                    )
                    Point(
                        "🧾",
                        "What it keeps",
                        "Only the receipt link, the shop's name and the time. The message itself is never " +
                            "saved.",
                    )
                    Point(
                        "🔒",
                        "What it ignores",
                        "Every other message: codes, banks, personal texts. They are skipped on your phone " +
                            "and never sent anywhere.",
                    )
                    Point(
                        "☁️",
                        "What it sends",
                        "The receipt link, to our server, to fetch the receipt as a PDF. Nothing else from " +
                            "your messages.",
                    )
                    Point(
                        "✉️",
                        "It never sends texts",
                        "Android's next prompt says \"send and view\" because that is how it groups this " +
                            "permission. My Receipts only reads.",
                    )
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sms_continue"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                ) {
                    Text("Continue", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                }
                TextButton(onClick = onNotNow, modifier = Modifier.testTag("sms_not_now")) {
                    Text("Not now")
                }
            }
        }
    }
}

@Composable
private fun Point(emoji: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shown on the receipt list while capture is off, so "Not now" is never a dead end. */
@Composable
fun CaptureOffBanner(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier.testTag("capture_off"), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Automatic capture is off",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "New receipt texts won't be saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onTurnOn, modifier = Modifier.testTag("capture_turn_on")) { Text("Turn on") }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PreviewSmsDisclosure() {
    MyReceiptsTheme { SmsDisclosureScreen(onContinue = {}, onNotNow = {}) }
}
