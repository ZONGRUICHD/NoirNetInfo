package com.zongruichd.noirnetinfo.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zongruichd.noirnetinfo.data.IpScope
import com.zongruichd.noirnetinfo.data.IpVersion
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.data.SimDetails
import com.zongruichd.noirnetinfo.ui.components.CopyableRow
import com.zongruichd.noirnetinfo.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboard = LocalClipboardManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }

    fun onCopy(value: String) {
        clipboard.setText(AnnotatedString(value))
        scope.launch { snackbar.showSnackbar("已复制") }
    }

    val snapshot = state.snapshot

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text("NoirNetInfo") },
                actions = {
                    IconButton(
                        onClick = { snapshot?.let { onCopy(it.toShareText()) } },
                        enabled = snapshot != null,
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制全部")
                    }
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (snapshot != null && (!snapshot.locationGranted || !snapshot.phoneGranted)) {
                    item {
                        PermissionBanner(
                            locationGranted = snapshot.locationGranted,
                            phoneGranted = snapshot.phoneGranted,
                            onGrant = {
                                permissionLauncher.launch(neededPermissions(snapshot))
                            },
                        )
                    }
                }

                item {
                    HeroCard(snapshot = snapshot, onCopy = ::onCopy)
                }

                if (snapshot != null) {
                    item {
                        AddressSection(snapshot = snapshot, onCopy = ::onCopy)
                    }
                    item {
                        WifiSection(snapshot = snapshot, onCopy = ::onCopy)
                    }
                    item {
                        CellularSection(snapshot = snapshot, onCopy = ::onCopy)
                    }
                    itemsIndexed(snapshot.sims, key = { _, sim -> sim.subscriptionId }) { _, sim ->
                        SimSection(sim = sim, onCopy = ::onCopy)
                    }
                    item {
                        PublicIpSection(snapshot = snapshot, onCopy = ::onCopy)
                    }
                    item {
                        InterfaceSection(snapshot = snapshot, onCopy = ::onCopy)
                    }
                }
            }
        }
    }
}

private fun neededPermissions(snapshot: NetworkSnapshot): Array<String> {
    val list = buildList {
        if (!snapshot.locationGranted) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (!snapshot.phoneGranted) {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
    }
    return list.toTypedArray()
}

@Composable
private fun PermissionBanner(
    locationGranted: Boolean,
    phoneGranted: Boolean,
    onGrant: () -> Unit,
) {
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
                    if (!locationGranted) append("位置权限用于读取 Wi-Fi 名称。")
                    if (!locationGranted && !phoneGranted) append(" ")
                    if (!phoneGranted) append("电话权限用于读取 SIM 与运营商信息。")
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
private fun HeroCard(
    snapshot: NetworkSnapshot?,
    onCopy: (String) -> Unit,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            val transports = snapshot?.connectivity?.transports.orEmpty()
            val icon = when {
                transports.any { it.contains("Wi-Fi") } -> Icons.Outlined.Wifi
                transports.any { it.contains("蜂窝") } -> Icons.Outlined.CellTower
                else -> Icons.Outlined.Hub
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    transports.joinToString(" · ").ifBlank { "正在读取…" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            val v4 = snapshot?.addresses?.firstOrNull { it.version == IpVersion.V4 && it.scope != IpScope.LINK_LOCAL }
            val v6 = snapshot?.addresses?.firstOrNull {
                it.version == IpVersion.V6 && it.scope == IpScope.GLOBAL
            } ?: snapshot?.addresses?.firstOrNull {
                it.version == IpVersion.V6 && it.scope == IpScope.ULA
            }
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
                val conn = snapshot?.connectivity
                if (conn != null) {
                    if (conn.validated) AssistChip(onClick = {}, label = { Text("已验证") })
                    if (conn.vpn) AssistChip(onClick = {}, label = { Text("VPN") })
                    if (conn.captivePortal) AssistChip(onClick = {}, label = { Text("强制门户") })
                    if (conn.metered) AssistChip(onClick = {}, label = { Text("计费网络") })
                    if (conn.hasInternet) AssistChip(onClick = {}, label = { Text("可上网") })
                }
            }
            if (snapshot != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "更新于 ${formatTime(snapshot.collectedAtMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                TextButton(onClick = { onCopy(snapshot.toShareText()) }) {
                    Text("复制全部信息")
                }
            }
        }
    }
}

@Composable
private fun AddressSection(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    SectionCard(title = "本机地址", icon = Icons.Outlined.Language) {
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
        CopyableRow("接口 / MTU", listOfNotNull(conn.interfaceName, conn.mtu?.let { "MTU $it" }).joinToString(" · ").ifBlank { null }, onCopy, showDivider = false)
    }
}

@Composable
private fun WifiSection(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    val wifi = snapshot.wifi
    SectionCard(title = "Wi-Fi", icon = Icons.Outlined.Wifi) {
        if (wifi == null) {
            CopyableRow("状态", "当前不是 Wi-Fi 连接", onCopy, mono = false, showDivider = false)
            return@SectionCard
        }
        CopyableRow(
            "SSID",
            wifi.ssid ?: if (wifi.needsLocation) "需要位置权限" else "未知",
            onCopy,
            mono = wifi.ssid != null,
        )
        CopyableRow("BSSID", wifi.bssid, onCopy)
        CopyableRow("信号", wifi.rssiDbm?.let { "$it dBm" }, onCopy, mono = false)
        CopyableRow(
            "速率",
            listOfNotNull(
                wifi.linkSpeedMbps?.let { "Rx $it Mbps" },
                wifi.txLinkSpeedMbps?.let { "Tx $it Mbps" },
            ).joinToString(" · ").ifBlank { null },
            onCopy,
            mono = false,
        )
        CopyableRow(
            "频段",
            wifi.frequencyMhz?.let { "$it MHz · ${bandOf(it)}" },
            onCopy,
            mono = false,
        )
        CopyableRow("标准", wifi.standard, onCopy, mono = false)
        CopyableRow("加密", wifi.security, onCopy, mono = false, showDivider = false)
    }
}

@Composable
private fun CellularSection(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    val cell = snapshot.cellular
    SectionCard(title = "蜂窝网络", icon = Icons.Outlined.CellTower) {
        if (cell == null) {
            CopyableRow("状态", "无法读取蜂窝信息", onCopy, mono = false, showDivider = false)
            return@SectionCard
        }
        CopyableRow("运营商", cell.operatorName, onCopy, mono = false)
        CopyableRow("PLMN", cell.operatorNumeric, onCopy)
        CopyableRow("制式", cell.networkType ?: if (!snapshot.phoneGranted) "需要电话权限" else null, onCopy, mono = false)
        CopyableRow("电话类型", cell.phoneType, onCopy, mono = false)
        CopyableRow("数据开关", cell.dataEnabled?.let { if (it) "开启" else "关闭" }, onCopy, mono = false)
        CopyableRow("漫游", cell.roaming?.let { if (it) "是" else "否" }, onCopy, mono = false, showDivider = snapshot.sims.isNotEmpty())
        if (snapshot.sims.isEmpty() && !snapshot.phoneGranted) {
            CopyableRow("SIM", "需要电话权限以读取卡槽信息", onCopy, mono = false, showDivider = false)
        } else if (snapshot.sims.isEmpty()) {
            CopyableRow("SIM", "未检测到活动 SIM", onCopy, mono = false, showDivider = false)
        }
    }
}

@Composable
private fun SimSection(sim: SimDetails, onCopy: (String) -> Unit) {
    SectionCard(
        title = "SIM ${sim.slotIndex + 1}",
        icon = Icons.Outlined.SimCard,
    ) {
        CopyableRow("名称", sim.displayName, onCopy, mono = false)
        CopyableRow("运营商", sim.carrierName ?: sim.networkOperator, onCopy, mono = false)
        CopyableRow(
            "MCC / MNC",
            listOfNotNull(sim.mcc, sim.mnc).joinToString(" / ").ifBlank { null },
            onCopy,
        )
        CopyableRow("国家", sim.countryIso?.uppercase(), onCopy, mono = false)
        CopyableRow("号码", sim.number, onCopy)
        CopyableRow("状态", sim.simState, onCopy, mono = false)
        CopyableRow("制式", sim.networkType, onCopy, mono = false)
        CopyableRow("eSIM", sim.embedded?.let { if (it) "是" else "否" }, onCopy, mono = false)
        CopyableRow("漫游", sim.roaming?.let { if (it) "是" else "否" }, onCopy, mono = false, showDivider = false)
    }
}

@Composable
private fun PublicIpSection(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    val pub = snapshot.publicIp
    SectionCard(title = "公网地址", icon = Icons.Outlined.Language) {
        if (pub.loading) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
private fun InterfaceSection(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    SectionCard(title = "网络接口", icon = Icons.Outlined.Router) {
        snapshot.interfaces.forEachIndexed { index, iface ->
            val flags = buildList {
                if (iface.up) add("UP") else add("DOWN")
                if (iface.loopback) add("loopback")
                if (iface.virtual) add("virtual")
                if (iface.mtu > 0) add("MTU ${iface.mtu}")
            }
            CopyableRow(
                label = "${iface.name} · ${flags.joinToString(" · ")}",
                value = (iface.ipv4 + iface.ipv6).joinToString("\n").ifBlank { null },
                onCopy = onCopy,
                placeholder = "无地址",
                showDivider = index != snapshot.interfaces.lastIndex,
            )
        }
    }
}

private fun IpScope.label(): String = when (this) {
    IpScope.LOOPBACK -> "回环"
    IpScope.LINK_LOCAL -> "链路本地"
    IpScope.PRIVATE -> "私网"
    IpScope.ULA -> "ULA"
    IpScope.GLOBAL -> "全球"
    IpScope.OTHER -> "其他"
}

private fun bandOf(mhz: Int): String = when (mhz) {
    in 2400..2500 -> "2.4 GHz"
    in 4900..5900 -> "5 GHz"
    in 5925..7125 -> "6 GHz"
    else -> "未知频段"
}

private fun formatTime(millis: Long): String {
    return DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

fun NetworkSnapshot.toShareText(): String = buildString {
    appendLine("NoirNetInfo")
    appendLine("连接: ${connectivity.transports.joinToString()}")
    appendLine("验证: ${connectivity.validated}  VPN: ${connectivity.vpn}  计费: ${connectivity.metered}")
    appendLine("网关: ${connectivity.gateways.joinToString()}")
    appendLine("DNS: ${connectivity.dns.joinToString()}")
    appendLine()
    appendLine("本机地址")
    addresses.forEach { appendLine("  ${it.version} ${it.cidr} (${it.iface}, ${it.scope.label()})") }
    appendLine()
    wifi?.let {
        appendLine("Wi-Fi")
        appendLine("  SSID: ${it.ssid}")
        appendLine("  BSSID: ${it.bssid}")
        appendLine("  RSSI: ${it.rssiDbm}")
        appendLine("  ${it.frequencyMhz} MHz  ${it.standard}  ${it.security}")
        appendLine()
    }
    cellular?.let {
        appendLine("蜂窝")
        appendLine("  ${it.operatorName} ${it.operatorNumeric} ${it.networkType}")
        appendLine()
    }
    sims.forEach { sim ->
        appendLine("SIM ${sim.slotIndex + 1}")
        appendLine("  ${sim.displayName} ${sim.carrierName} MCC ${sim.mcc} MNC ${sim.mnc}")
        appendLine("  ${sim.simState} ${sim.networkType}")
        appendLine()
    }
    appendLine("公网 IPv4: ${publicIp.ipv4}")
    appendLine("公网 IPv6: ${publicIp.ipv6}")
}
