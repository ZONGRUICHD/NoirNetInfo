package com.zongruichd.noirnetinfo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zongruichd.noirnetinfo.data.IpScope
import com.zongruichd.noirnetinfo.data.IpVersion
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.ui.components.CopyableRow
import com.zongruichd.noirnetinfo.ui.components.ExpandableBlock
import com.zongruichd.noirnetinfo.ui.components.SectionCard
import com.zongruichd.noirnetinfo.ui.util.formatTime
import com.zongruichd.noirnetinfo.ui.util.label
import com.zongruichd.noirnetinfo.ui.util.wifiBandOf

@Composable
fun PermissionBanner(
    locationGranted: Boolean,
    phoneGranted: Boolean,
    forCells: Boolean,
    onGrant: () -> Unit,
) {
    if (locationGranted && phoneGranted) return
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("需要额外权限", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    if (!locationGranted) {
                        append(if (forCells) "位置权限用于读取服务小区、邻区和 Wi-Fi 名称。" else "位置权限用于读取 Wi-Fi 名称。")
                    }
                    if (!locationGranted && !phoneGranted) append(" ")
                    if (!phoneGranted) append("电话权限用于读取 SIM、运营商与制式。")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrant) { Text("授予权限") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewPage(snapshot: NetworkSnapshot?, onCopy: (String) -> Unit) {
    val transports = snapshot?.connectivity?.transports.orEmpty()
    val icon = when {
        transports.any { it.contains("Wi-Fi") } -> Icons.Outlined.Wifi
        transports.any { it.contains("蜂窝") } -> Icons.Outlined.CellTower
        else -> Icons.Outlined.Home
    }
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    transports.joinToString(" · ").ifBlank { "正在读取…" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            val v4 = snapshot?.addresses?.firstOrNull { it.version == IpVersion.V4 && it.scope != IpScope.LINK_LOCAL }
            val v6 = snapshot?.addresses?.firstOrNull { it.version == IpVersion.V6 && it.scope == IpScope.GLOBAL }
                ?: snapshot?.addresses?.firstOrNull { it.version == IpVersion.V6 && it.scope == IpScope.ULA }
            Text(
                v4?.hostAddress ?: "暂无 IPv4",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (v6 != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    v6.hostAddress,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot?.connectivity?.let { conn ->
                    if (conn.validated) AssistChip(onClick = {}, label = { Text("已验证") })
                    if (conn.vpn) AssistChip(onClick = {}, label = { Text("VPN") })
                    if (conn.captivePortal) AssistChip(onClick = {}, label = { Text("强制门户") })
                    if (conn.metered) AssistChip(onClick = {}, label = { Text("计费网络") })
                    if (conn.hasInternet) AssistChip(onClick = {}, label = { Text("可上网") })
                }
                snapshot?.slots?.firstOrNull()?.operator?.generation?.let {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
            }
            if (snapshot != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "更新于 ${formatTime(snapshot.collectedAtMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }

    if (snapshot != null) {
        SectionCard(title = "快捷摘要", icon = Icons.Outlined.Home) {
            CopyableRow("主 IPv4", v4Of(snapshot), onCopy)
            CopyableRow("主 IPv6", v6Of(snapshot), onCopy)
            CopyableRow("Wi-Fi", snapshot.wifi?.ssid ?: snapshot.wifi?.let { "已连接" }, onCopy, mono = false)
            val slot = snapshot.slots.firstOrNull()
            CopyableRow("默认数据卡", slot?.let { "${it.title} · ${it.subtitle}" }, onCopy, mono = false)
            CopyableRow("公网 IPv4", snapshot.publicIp.ipv4, onCopy, showDivider = false)
        }
    }
}

private fun v4Of(snapshot: NetworkSnapshot) =
    snapshot.addresses.firstOrNull { it.version == IpVersion.V4 && it.scope != IpScope.LINK_LOCAL }?.hostAddress

private fun v6Of(snapshot: NetworkSnapshot) =
    snapshot.addresses.firstOrNull { it.version == IpVersion.V6 && it.scope == IpScope.GLOBAL }?.hostAddress
        ?: snapshot.addresses.firstOrNull { it.version == IpVersion.V6 && it.scope == IpScope.ULA }?.hostAddress

@Composable
fun AddressPage(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    SectionCard(
        title = "本机地址",
        icon = Icons.Outlined.Language,
        summary = "${snapshot.addresses.size} 条 · DNS ${snapshot.connectivity.dns.size}",
        collapsible = snapshot.addresses.size > 6,
        initiallyExpanded = true,
    ) {
        val v4 = snapshot.addresses.filter { it.version == IpVersion.V4 }
        val v6 = snapshot.addresses.filter { it.version == IpVersion.V6 }
        if (v4.isEmpty() && v6.isEmpty()) {
            CopyableRow("地址", null, onCopy, placeholder = "没有可用的非回环地址", showDivider = false)
        } else {
            v4.forEachIndexed { index, entry ->
                CopyableRow(
                    label = "IPv4 · ${entry.iface} · ${entry.scope.label()}",
                    value = entry.cidr,
                    onCopy = onCopy,
                    showDivider = index != v4.lastIndex || v6.isNotEmpty(),
                )
            }
            v6.forEachIndexed { index, entry ->
                CopyableRow(
                    label = "IPv6 · ${entry.iface} · ${entry.scope.label()}",
                    value = entry.cidr,
                    onCopy = onCopy,
                    showDivider = index != v6.lastIndex,
                )
            }
        }
        val conn = snapshot.connectivity
        CopyableRow("网关", conn.gateways.joinToString().ifBlank { null }, onCopy)
        CopyableRow("DNS", conn.dns.joinToString().ifBlank { null }, onCopy)
        CopyableRow("DHCP", conn.dhcpServer, onCopy)
        CopyableRow(
            "接口 / MTU",
            listOfNotNull(conn.interfaceName, conn.mtu?.let { "MTU $it" }).joinToString(" · ").ifBlank { null },
            onCopy,
            showDivider = false,
        )
    }
}

@Composable
fun WifiPage(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    val wifi = snapshot.wifi
    SectionCard(title = "已连接 Wi-Fi", icon = Icons.Outlined.Wifi) {
        if (wifi == null) {
            CopyableRow("状态", "当前不是 Wi-Fi 连接", onCopy, mono = false, showDivider = false)
            return@SectionCard
        }
        CopyableRow("SSID", wifi.ssid ?: if (wifi.needsLocation) "需要位置权限" else "未知", onCopy, mono = wifi.ssid != null)
        CopyableRow("BSSID", wifi.bssid, onCopy)
        CopyableRow("信号", wifi.rssiDbm?.let { "$it dBm" }, onCopy, mono = false)
        CopyableRow(
            "速率",
            listOfNotNull(wifi.linkSpeedMbps?.let { "Rx $it Mbps" }, wifi.txLinkSpeedMbps?.let { "Tx $it Mbps" })
                .joinToString(" · ").ifBlank { null },
            onCopy,
            mono = false,
        )
        CopyableRow("频段", wifi.frequencyMhz?.let { "$it MHz · ${wifiBandOf(it)}" }, onCopy, mono = false)
        CopyableRow("标准", wifi.standard, onCopy, mono = false)
        CopyableRow("加密", wifi.security, onCopy, mono = false)
        CopyableRow("隐藏 SSID", wifi.hiddenSsid?.let { if (it) "是" else "否" }, onCopy, mono = false, showDivider = false)
    }
    SectionCard(title = "链路", icon = Icons.Outlined.Router) {
        CopyableRow("网关", snapshot.connectivity.gateways.joinToString().ifBlank { null }, onCopy)
        CopyableRow("DNS", snapshot.connectivity.dns.joinToString().ifBlank { null }, onCopy)
        CopyableRow("DHCP", snapshot.connectivity.dhcpServer, onCopy, showDivider = false)
    }
}

@Composable
fun PublicIpPage(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    val pub = snapshot.publicIp
    SectionCard(title = "公网地址", icon = Icons.Outlined.Language) {
        if (pub.loading) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("正在查询公网 IP…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            CopyableRow("公网 IPv4", pub.ipv4, onCopy)
            CopyableRow("公网 IPv6", pub.ipv6, onCopy, showDivider = pub.error != null)
            AnimatedVisibility(visible = pub.error != null) {
                CopyableRow("说明", pub.error, onCopy, mono = false, showDivider = false)
            }
        }
    }
}

@Composable
fun InterfacePage(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    val active = snapshot.interfaces.filter { it.up && !it.loopback }
    SectionCard(
        title = "网络接口（${snapshot.interfaces.size}）",
        icon = Icons.Outlined.Router,
        summary = active.joinToString(" · ") { it.name }.ifBlank { "没有活动接口" },
        collapsible = true,
        initiallyExpanded = true,
    ) {
        snapshot.interfaces.forEach { iface ->
            val flags = buildList {
                if (iface.up) add("UP") else add("DOWN")
                if (iface.loopback) add("loopback")
                if (iface.virtual) add("virtual")
                if (iface.mtu > 0) add("MTU ${iface.mtu}")
            }
            val addrs = (iface.ipv4 + iface.ipv6)
            ExpandableBlock(
                title = "${iface.name} · ${flags.joinToString(" · ")}",
                subtitle = addrs.firstOrNull() ?: "无地址",
                initiallyExpanded = iface.up && !iface.loopback && addrs.size <= 2,
            ) {
                if (addrs.isEmpty()) {
                    CopyableRow("地址", null, onCopy, placeholder = "无地址", showDivider = false)
                } else {
                    addrs.forEachIndexed { i, addr ->
                        CopyableRow(
                            if (addr.contains(':')) "IPv6" else "IPv4",
                            addr,
                            onCopy,
                            showDivider = i != addrs.lastIndex,
                        )
                    }
                }
            }
        }
    }
}
