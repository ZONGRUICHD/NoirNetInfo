package com.zongruichd.noirnetinfo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = true,
    collapsible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val showBody = !collapsible || expanded
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(bottom = if (showBody) 4.dp else 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsible) {
                            Modifier.clickable { expanded = !expanded }
                        } else {
                            Modifier
                        },
                    )
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (!showBody && !summary.isNullOrBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (collapsible) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = showBody) {
                Column { content() }
            }
        }
    }
}

@Composable
fun ExpandableBlock(
    title: String,
    subtitle: String?,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title, subtitle) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column { content() }
        }
    }
}

@Composable
fun CopyableRow(
    label: String,
    value: String?,
    onCopy: (String) -> Unit,
    placeholder: String = "—",
    mono: Boolean = true,
    showDivider: Boolean = true,
) {
    val display = value?.takeIf { it.isNotBlank() } ?: placeholder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(108.dp).padding(top = 2.dp),
        )
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono && value != null) FontFamily.Monospace else FontFamily.SansSerif,
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f).padding(end = 4.dp, top = 1.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            IconButton(
                onClick = { onCopy(value) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制$label",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.size(32.dp))
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )
    }
}

@Composable
fun CopyableMap(
    values: Map<String, String>,
    onCopy: (String) -> Unit,
) {
    val entries = values.entries.toList()
    if (entries.isEmpty()) {
        CopyableRow("参数", null, onCopy, placeholder = "无可用参数", showDivider = false)
        return
    }
    entries.forEachIndexed { index, (key, value) ->
        CopyableRow(key, value, onCopy, showDivider = index != entries.lastIndex)
    }
}

fun yesNo(value: Boolean?): String? = value?.let { if (it) "是" else "否" }
fun onOff(value: Boolean?): String? = value?.let { if (it) "开启" else "关闭" }
