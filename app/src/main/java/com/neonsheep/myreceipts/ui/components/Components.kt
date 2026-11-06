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
import com.neonsheep.myreceipts.data.BudgetStatus
import com.neonsheep.myreceipts.ui.theme.Chip
import com.neonsheep.myreceipts.ui.theme.Faint
import com.neonsheep.myreceipts.ui.theme.Green
import com.neonsheep.myreceipts.ui.theme.GreenSoft
import com.neonsheep.myreceipts.ui.theme.Muted
import com.neonsheep.myreceipts.ui.theme.NearBadge
import com.neonsheep.myreceipts.ui.theme.NearBadgeInk
import com.neonsheep.myreceipts.ui.theme.OkBadge
import com.neonsheep.myreceipts.ui.theme.OkBadgeInk
import com.neonsheep.myreceipts.ui.theme.OverBadge
import com.neonsheep.myreceipts.ui.theme.OverBadgeInk
import com.neonsheep.myreceipts.ui.theme.Soft

/** White rounded card on the mint ground: the basic building block. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
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
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
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

/** Small spaced-out heading above a group, like "BY CATEGORY". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Faint) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp),
        color = color,
        modifier = modifier,
    )
}

/** Big number over a small caption. */
@Composable
fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = color)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

enum class PillTone { Brand, Green, Grey }

/** Small rounded tag for a receipt's PDF state. */
@Composable
fun Pill(text: String, tone: PillTone = PillTone.Brand, loading: Boolean = false) {
    val (bg, ink) = when (tone) {
        PillTone.Brand, PillTone.Green -> GreenSoft to Green
        PillTone.Grey -> Chip to Muted
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = ink)
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp), color = ink)
    }
}

/** A budget badge: green under, amber near, red over. Null state is a neutral chip. */
@Composable
fun Badge(text: String, state: BudgetStatus.State?, modifier: Modifier = Modifier) {
    val (bg, ink) = when (state) {
        BudgetStatus.State.OK -> OkBadge to OkBadgeInk
        BudgetStatus.State.NEAR -> NearBadge to NearBadgeInk
        BudgetStatus.State.OVER -> OverBadge to OverBadgeInk
        null -> Chip to Muted
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (state == null) FontWeight.Normal else FontWeight.SemiBold),
        color = ink,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Bar with a back arrow, used by the screens opened from a tab. Without [onBack], a tab's own title. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, subtitle: String? = null, leading: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (leading != null) {
            Spacer(Modifier.width(4.dp))
            leading()
        }
        if (onBack != null || leading != null) Spacer(Modifier.width(12.dp))
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

/** The merchant's logo when we have one, otherwise its initial in green on a soft tile. */
@Composable
fun MerchantLogo(senderLabel: String, size: Dp = 42.dp) {
    val shape = RoundedCornerShape(size * 0.29f)
    val logoResId = resolveMerchantLogo(senderLabel)
    if (logoResId != null) {
        Image(
            painter = painterResource(logoResId),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(Soft, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = storeDisplayName(senderLabel).firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp,
                ),
                color = Green,
            )
        }
    }
}

/** A tile with a category's emoji, on the same soft ground as the store tiles. */
@Composable
fun EmojiTile(emoji: String, size: Dp = 34.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Soft, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.48f).sp)
    }
}

/** The signed-in account's initial in a round badge. */
@Composable
fun AccountInitial(email: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(com.neonsheep.myreceipts.ui.theme.AvatarGround),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            email.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Green,
        )
    }
}

internal fun resolveMerchantLogo(senderLabel: String): Int? = when (senderLabel.lowercase()) {
    "shufersal" -> R.drawable.logo_shufersal
    "pairzon", "osherad" -> R.drawable.ic_merchant_pairzon
    "ikea" -> R.drawable.ic_merchant_ikea
    "lego" -> R.drawable.logo_lego
    "decathlon" -> R.drawable.logo_decathlon
    "movieland" -> R.drawable.logo_movieland
    else -> null
}
