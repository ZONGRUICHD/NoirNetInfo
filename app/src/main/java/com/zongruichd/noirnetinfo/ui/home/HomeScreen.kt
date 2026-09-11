package com.zongruichd.noirnetinfo.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zongruichd.noirnetinfo.R
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.ui.screens.AddressPage
import com.zongruichd.noirnetinfo.ui.screens.CellularOverviewPage
import com.zongruichd.noirnetinfo.ui.screens.InterfacePage
import com.zongruichd.noirnetinfo.ui.screens.OverviewPage
import com.zongruichd.noirnetinfo.ui.screens.PermissionBanner
import com.zongruichd.noirnetinfo.ui.screens.PublicIpPage
import com.zongruichd.noirnetinfo.ui.screens.SettingsPage
import com.zongruichd.noirnetinfo.ui.screens.SimDetailPage
import com.zongruichd.noirnetinfo.ui.screens.WifiPage
import com.zongruichd.noirnetinfo.ui.util.toShareText
import kotlinx.coroutines.launch

private const val DEST_OVERVIEW = "overview"
private const val DEST_ADDRESS = "address"
private const val DEST_PUBLIC = "public"
private const val DEST_IFACE = "iface"
private const val DEST_WIFI = "wifi"
private const val DEST_CELL = "cell"
private const val DEST_SIM = "sim"
private const val DEST_SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val shizuku by vm.shizuku.collectAsState()
    val update by vm.update.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var dest by rememberSaveable { mutableStateOf(DEST_OVERVIEW) }
    var simId by rememberSaveable { mutableIntStateOf(-1) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }

    fun onCopy(value: String) {
        clipboard.setText(AnnotatedString(value))
        scope.launch { snackbar.showSnackbar("已复制") }
    }

    val snapshot = state.snapshot
    val selectedSim = snapshot?.slots?.firstOrNull { it.subscriptionId == simId }
    LaunchedEffect(snapshot?.slots, dest, simId) {
        if (dest == DEST_SIM && snapshot?.slots?.none { it.subscriptionId == simId } == true) {
            dest = DEST_CELL
        }
    }
    val title = when (dest) {
        DEST_OVERVIEW -> "概览"
        DEST_ADDRESS -> "地址"
        DEST_PUBLIC -> "公网"
        DEST_IFACE -> "接口"
        DEST_WIFI -> "Wi-Fi"
        DEST_CELL -> "蜂窝"
        DEST_SIM -> selectedSim?.let { "${it.title} · ${it.subtitle}" } ?: "SIM"
        DEST_SETTINGS -> "设置"
        else -> "NoirNetInfo"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                DrawerHeader()
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { DrawerLabel("网络") }
                    item {
                        DrawerItem("概览", Icons.Outlined.Home, dest == DEST_OVERVIEW) {
                            dest = DEST_OVERVIEW
                            scope.launch { drawerState.close() }
                        }
                    }
                    item {
                        DrawerItem("地址", Icons.Outlined.Language, dest == DEST_ADDRESS) {
                            dest = DEST_ADDRESS
                            scope.launch { drawerState.close() }
                        }
                    }
                    item {
                        DrawerItem("公网", Icons.Outlined.Public, dest == DEST_PUBLIC) {
                            dest = DEST_PUBLIC
                            scope.launch { drawerState.close() }
                        }
                    }
                    item {
                        DrawerItem("接口", Icons.Outlined.Router, dest == DEST_IFACE) {
                            dest = DEST_IFACE
                            scope.launch { drawerState.close() }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); HorizontalDivider(); DrawerLabel("无线") }
                    item {
                        DrawerItem("Wi-Fi", Icons.Outlined.Wifi, dest == DEST_WIFI) {
                            dest = DEST_WIFI
                            scope.launch { drawerState.close() }
                        }
                    }
                    item {
                        DrawerItem("蜂窝总览", Icons.Outlined.CellTower, dest == DEST_CELL) {
                            dest = DEST_CELL
                            scope.launch { drawerState.close() }
                        }
                    }
                    val slots = snapshot?.slots.orEmpty()
                    item { Spacer(Modifier.height(8.dp)); HorizontalDivider(); DrawerLabel("卡槽") }
                    if (slots.isEmpty()) {
                        item {
                            Text(
                                if (snapshot?.phoneGranted == false) "授予电话权限后显示 SIM" else "未检测到 SIM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        slots.forEach { slot ->
                            item {
                                DrawerItem(
                                    label = "${slot.title} · ${slot.subtitle}",
                                    icon = Icons.Outlined.SimCard,
                                    selected = dest == DEST_SIM && simId == slot.subscriptionId,
                                ) {
                                    simId = slot.subscriptionId
                                    dest = DEST_SIM
                                    scope.launch { drawerState.close() }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); HorizontalDivider(); DrawerLabel("系统") }
                    item {
                        DrawerItem("设置", Icons.Outlined.Settings, dest == DEST_SETTINGS) {
                            dest = DEST_SETTINGS
                            scope.launch { drawerState.close() }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "打开分类")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            dest = DEST_SETTINGS
                        }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "设置")
                        }
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
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
                    if (dest != DEST_SETTINGS && snapshot != null && (!snapshot.locationGranted || !snapshot.phoneGranted)) {
                        item {
                            PermissionBanner(
                                locationGranted = snapshot.locationGranted,
                                phoneGranted = snapshot.phoneGranted,
                                forCells = dest == DEST_CELL || dest == DEST_SIM,
                                onGrant = {
                                    permissionLauncher.launch(neededPermissions(snapshot))
                                },
                            )
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when (dest) {
                                DEST_OVERVIEW -> OverviewPage(snapshot, ::onCopy)
                                DEST_ADDRESS -> snapshot?.let { AddressPage(it, ::onCopy) }
                                DEST_PUBLIC -> snapshot?.let { PublicIpPage(it, ::onCopy) }
                                DEST_IFACE -> snapshot?.let { InterfacePage(it, ::onCopy) }
                                DEST_WIFI -> snapshot?.let { WifiPage(it, ::onCopy) }
                                DEST_CELL -> snapshot?.let { CellularOverviewPage(it, ::onCopy) }
                                DEST_SIM -> selectedSim?.let { sim ->
                                    SimDetailPage(
                                        slot = sim,
                                        locationGranted = snapshot?.locationGranted == true,
                                        onCopy = ::onCopy,
                                        onOpenSettings = { dest = DEST_SETTINGS },
                                    )
                                }
                                DEST_SETTINGS -> SettingsPage(
                                    slots = snapshot?.slots.orEmpty(),
                                    shizuku = shizuku,
                                    update = update,
                                    onCopy = ::onCopy,
                                    onRequestShizuku = vm::requestShizuku,
                                    onCheckUpdate = vm::checkUpdate,
                                    onDownloadUpdate = vm::downloadUpdate,
                                    onConsumeInstallUri = vm::consumeInstallUri,
                                    onApplyLock = { subId, gsm, wcdma, lte, nr, lteBands, nrBands, wcdmaBands, arfcn, pci, servingRat ->
                                        vm.applyCellularLock(
                                            subId, gsm, wcdma, lte, nr,
                                            lteBands, nrBands, wcdmaBands,
                                            arfcn, pci, servingRat,
                                        )
                                    },
                                    onClearLock = vm::clearCellularLock,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Box(Modifier.fillMaxWidth().height(168.dp)) {
        Image(
            painter = painterResource(R.drawable.logo_app),
            contentDescription = "NoirNetInfo Logo",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text("NoirNetInfo", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("本机网络诊断", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
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
