package com.neonsheep.myreceipts.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.ui.components.SectionCard
import com.neonsheep.myreceipts.ui.theme.DangerRed
import com.neonsheep.myreceipts.ui.theme.MyReceiptsTheme
import com.neonsheep.myreceipts.ui.theme.Purple
import com.neonsheep.myreceipts.ui.theme.PurpleLight

/** The first screen when signed out: one card, one button, as gym-tracker would do it. */
@Composable
fun SignInScreen(signingIn: Boolean, error: String?, onSignIn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        SectionCard(padding = 28.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(PurpleLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_receipt),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onSignIn,
                    enabled = !signingIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_in"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                ) {
                    if (signingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            stringResource(R.string.sign_in_google),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DangerRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("sign_in_error"),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSignIn() {
    MyReceiptsTheme { SignInScreen(signingIn = false, error = null, onSignIn = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSignInError() {
    MyReceiptsTheme {
        SignInScreen(signingIn = false, error = "This Google account isn't allowed to use My Receipts.", onSignIn = {})
    }
}
