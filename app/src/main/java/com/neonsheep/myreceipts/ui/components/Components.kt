package com.neonsheep.myreceipts.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.ui.theme.Purple
import com.neonsheep.myreceipts.ui.theme.PurpleLight
import com.neonsheep.myreceipts.ui.theme.SuccessGreen
import com.neonsheep.myreceipts.ui.theme.SuccessInk

/** White rounded card on the lavender background: the basic building block, as in gym-tracker. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(padding),
        content = content,
    )
}

/** Card heading with an optional value on the far side. */
@Composable
fun CardTitle(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Big number over a small caption, like the week stats in gym-tracker. */
@Composable
fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = color)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

enum class PillTone { Purple, Green, Grey }

/** Rounded chip for short facts and statuses. */
@Composable
fun Pill(text: String, tone: PillTone = PillTone.Purple, loading: Boolean = false) {
    val (bg, ink) = when (tone) {
        PillTone.Purple -> PurpleLight to Purple
        PillTone.Green -> SuccessGreen.copy(alpha = 0.14f) to SuccessInk
        PillTone.Grey -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = ink)
        Text(text, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/** White bar with a back arrow, used by every screen below the home screen. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, subtitle: String? = null, leading: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (leading != null) {
            Spacer(Modifier.width(4.dp))
            leading()
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The merchant's logo when we have one, otherwise its initial on a purple circle. */
@Composable
fun MerchantLogo(senderLabel: String, size: Dp = 44.dp) {
    val logoResId = resolveMerchantLogo(senderLabel)
    if (logoResId != null) {
        Image(
            painter = painterResource(logoResId),
            contentDescription = senderLabel,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(Purple, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = senderLabel.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (size.value * 0.42f).sp,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

internal fun resolveMerchantLogo(senderLabel: String): Int? = when (senderLabel.lowercase()) {
    "shufersal" -> R.drawable.ic_merchant_shufersal
    "pairzon", "osherad" -> R.drawable.ic_merchant_pairzon
    else -> null
}
