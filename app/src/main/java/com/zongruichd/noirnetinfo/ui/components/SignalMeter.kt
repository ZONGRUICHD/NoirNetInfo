package com.zongruichd.noirnetinfo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun SignalMeter(
    label: String,
    value: Int?,
    unit: String,
    range: IntRange,
    higherIsBetter: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val min = range.first.toFloat()
    val max = range.last.toFloat()
    val shown = value?.coerceIn(range.first, range.last)
    val raw = if (shown == null) 0f else (shown - min) / (max - min).coerceAtLeast(1f)
    val quality = if (higherIsBetter) raw else 1f - raw
    val color = when {
        shown == null -> MaterialTheme.colorScheme.outlineVariant
        quality >= 0.75f -> MaterialTheme.colorScheme.primary
        quality >= 0.45f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Column(modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                text = shown?.let { "$it $unit" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (shown == null) 0f else raw.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

fun rsrpQuality(rsrp: Int?): String = when {
    rsrp == null -> "未知"
    rsrp >= -80 -> "优秀"
    rsrp >= -90 -> "良好"
    rsrp >= -100 -> "一般"
    rsrp >= -110 -> "较差"
    else -> "很差"
}
