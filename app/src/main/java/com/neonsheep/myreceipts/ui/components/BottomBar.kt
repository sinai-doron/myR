package com.neonsheep.myreceipts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsheep.myreceipts.R
import com.neonsheep.myreceipts.ui.theme.Green
import com.neonsheep.myreceipts.ui.theme.Line
import com.neonsheep.myreceipts.ui.theme.Muted

enum class Tab(val labelRes: Int, val tag: String) {
    HOME(R.string.nav_home, "tab_home"),
    SPENDING(R.string.nav_spending, "tab_spending"),
    STORES(R.string.nav_stores, "tab_stores"),
}

/** Home, Spending and Stores. The account sits on Home; the app has no settings screen. */
@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = Line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 10.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                val color = if (on) Green else Muted
                Column(
                    modifier = Modifier
                        .widthIn(min = 72.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 4.dp)
                        .testTag(tab.tag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(iconFor(tab), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(tab.labelRes),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = color,
                    )
                }
            }
        }
    }
}

private fun iconFor(tab: Tab): ImageVector = when (tab) {
    Tab.HOME -> HomeIcon
    Tab.SPENDING -> SpendingIcon
    Tab.STORES -> StoresIcon
}

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

// Four tiles, bars, and a receipt: drawn as strokes, like the design.
private val HomeIcon = strokeIcon(
    "home",
    "M5.5 4h4A1.5 1.5 0 0 1 11 5.5v4A1.5 1.5 0 0 1 9.5 11h-4A1.5 1.5 0 0 1 4 9.5v-4A1.5 1.5 0 0 1 5.5 4z",
    "M14.5 4h4A1.5 1.5 0 0 1 20 5.5v4A1.5 1.5 0 0 1 18.5 11h-4A1.5 1.5 0 0 1 13 9.5v-4A1.5 1.5 0 0 1 14.5 4z",
    "M5.5 13h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 9.5 20h-4A1.5 1.5 0 0 1 4 18.5v-4A1.5 1.5 0 0 1 5.5 13z",
    "M14.5 13h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z",
)
private val SpendingIcon = strokeIcon("spending", "M5 20V12M10 20V8M15 20v-6M20 20V5")
private val StoresIcon = strokeIcon("stores", "M6 3h12v18l-2-1.5L14 21l-2-1.5L10 21l-2-1.5L6 21z", "M9 8h6M9 12h6M9 16h3")
