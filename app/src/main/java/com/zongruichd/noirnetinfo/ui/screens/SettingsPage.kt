package com.zongruichd.noirnetinfo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zongruichd.noirnetinfo.BuildConfig
import com.zongruichd.noirnetinfo.R
import com.zongruichd.noirnetinfo.data.CellularSlot
import com.zongruichd.noirnetinfo.shizuku.ShizukuUiState
import com.zongruichd.noirnetinfo.ui.components.CopyableRow
import com.zongruichd.noirnetinfo.ui.components.SectionCard
import com.zongruichd.noirnetinfo.ui.home.UpdateUiState

@Composable
fun SettingsPage(
    slots: List<CellularSlot>,
    shizuku: ShizukuUiState,
    update: UpdateUiState,
    onCopy: (String) -> Unit,
    onRequestShizuku: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onConsumeInstallUri: () -> Unit,
    onApplyLock: (
        subId: Int,
        enableGsm: Boolean,
        enableWcdma: Boolean,
        enableLte: Boolean,
        enableNr: Boolean,
        lteBands: List<Int>,
        nrBands: List<Int>,
        wcdmaBands: List<Int>,
        arfcn: Int?,
        pci: Int?,
        servingRat: String?,
    ) -> Unit,
    onClearLock: (Int) -> Unit,
) {
    val context = LocalContext.current
    var installerError by remember { mutableStateOf<String?>(null) }
    fun installDownloaded() {
        val uri = update.installUri ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onSuccess { onConsumeInstallUri() }
            .onFailure { installerError = "无法打开安装器：${it.message}" }
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) installDownloaded()
        else installerError = "尚未允许安装；APK 已保留，可点击继续安装重试。"
    }
    fun continueInstall() {
        installerError = null
        if (context.packageManager.canRequestPackageInstalls()) installDownloaded()
        else runCatching {
            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")))
        }.onFailure { installerError = "无法打开安装授权设置：${it.message}" }
    }
    LaunchedEffect(update.installUri) {
        if (update.installUri != null) continueInstall()
    }

    SectionCard(title = "关于", icon = Icons.Outlined.VerifiedUser) {
        Image(
            painter = painterResource(R.drawable.logo_app),
            contentDescription = "NoirNetInfo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        CopyableRow("应用", "NoirNetInfo", onCopy, mono = false)
        CopyableRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onCopy)
        CopyableRow("包名", BuildConfig.APPLICATION_ID, onCopy, showDivider = false)
    }

    SectionCard(title = "检查更新", icon = Icons.Outlined.SystemUpdate) {
        CopyableRow("当前版本", BuildConfig.VERSION_NAME, onCopy)
        CopyableRow("远端版本", update.latestName ?: update.latestTag, onCopy, mono = false)
        CopyableRow(
            "状态",
            when {
                update.checking -> "正在查询 GitHub Release…"
                update.downloading -> "正在下载 ${((update.progress ?: 0f) * 100).toInt()}%"
                update.hasUpdate -> "发现新版本，可直接安装"
                update.latestTag != null -> "已是最新版本"
                else -> "点下面按钮检查 GitHub Release"
            },
            onCopy,
            mono = false,
            showDivider = update.changelog != null || update.message != null,
        )
        update.changelog?.let { notes ->
            CopyableRow("更新说明", notes, onCopy, mono = false, showDivider = update.message != null)
        }
        update.message?.let { CopyableRow("说明", it, onCopy, mono = false, showDivider = false) }
        if (update.downloading) {
            LinearProgressIndicator(
                progress = { update.progress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Button(
            onClick = onCheckUpdate,
            enabled = !update.checking && !update.downloading,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) { Text(if (update.checking) "检查中…" else "检查更新") }
        if (update.hasUpdate && update.apkUrl != null) {
            Button(
                onClick = onDownloadUpdate,
                enabled = !update.downloading,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            ) { Text(if (update.downloading) "下载中…" else "下载并安装") }
        }
        if (update.installUri != null) {
            Button(onClick = { continueInstall() }, modifier = Modifier.padding(16.dp)) { Text("继续安装已下载 APK") }
        }
        installerError?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        update.htmlUrl?.let { url ->
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("在浏览器打开 Release") }
        }
        Spacer(Modifier.height(8.dp))
    }

    SectionCard(title = "Shizuku", icon = Icons.Outlined.Lock) {
        CopyableRow("状态", shizuku.mode, onCopy, mono = false)
        CopyableRow(
            "能力",
            when {
                shizuku.uid == 0 -> "Root：尝试制式 / Band / 频点；支持情况取决于设备"
                shizuku.uid == 2000 -> "ADB：尝试制式 / Band / 频点；支持情况取决于系统"
                else -> "授权后用 shell 权限调用电话接口"
            },
            onCopy,
            mono = false,
            showDivider = false,
        )
        if (!shizuku.ready) {
            Button(
                onClick = onRequestShizuku,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    when {
                        !shizuku.installed -> "未检测到 Shizuku"
                        !shizuku.running -> "请先启动 Shizuku"
                        else -> "授权 Shizuku"
                    },
                )
            }
        } else {
            Text(
                "已授权。制式设置需要 Android 13+；Band / 频点取决于系统实现。PCI 尚未适配。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (slots.isEmpty()) {
            CopyableRow("锁网", "没有可用 SIM，无法下发锁定", onCopy, mono = false, showDivider = false)
        } else {
            ShizukuLockPanel(
                slots = slots,
                shizuku = shizuku,
                onApplyLock = onApplyLock,
                onClearLock = onClearLock,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val LteBandChoices = listOf(1, 3, 5, 7, 8, 20, 28, 34, 38, 39, 40, 41, 42)
private val NrBandChoices = listOf(1, 3, 5, 8, 28, 41, 77, 78, 79)
private val WcdmaBandChoices = listOf(1, 2, 5, 8)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShizukuLockPanel(
    slots: List<CellularSlot>,
    shizuku: ShizukuUiState,
    onApplyLock: (
        subId: Int,
        enableGsm: Boolean,
        enableWcdma: Boolean,
        enableLte: Boolean,
        enableNr: Boolean,
        lteBands: List<Int>,
        nrBands: List<Int>,
        wcdmaBands: List<Int>,
        arfcn: Int?,
        pci: Int?,
        servingRat: String?,
    ) -> Unit,
    onClearLock: (Int) -> Unit,
) {
    var selectedSub by rememberSaveable {
        mutableStateOf(slots.firstOrNull { it.isDefaultData }?.subscriptionId ?: slots.first().subscriptionId)
    }
    val slot = slots.firstOrNull { it.subscriptionId == selectedSub } ?: slots.first()
    val serving = slot.primaryServing
    var channelRat by rememberSaveable(slot.subscriptionId) { mutableStateOf("LTE") }
    var gsm by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var wcdma by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var lte by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var nr by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var lteBands by remember(slot.subscriptionId) {
        mutableStateOf(emptySet<Int>())
    }
    var nrBands by remember(slot.subscriptionId) {
        mutableStateOf(emptySet<Int>())
    }
    var wcdmaBands by remember(slot.subscriptionId) { mutableStateOf(emptySet<Int>()) }
    var arfcnText by rememberSaveable(slot.subscriptionId) { mutableStateOf("") }
    var pciText by rememberSaveable(slot.subscriptionId) {
        mutableStateOf("")
    }

    if (slots.size > 1) {
        Text("目标 SIM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slots.forEach { item ->
                FilterChip(
                    selected = item.subscriptionId == slot.subscriptionId,
                    onClick = { selectedSub = item.subscriptionId },
                    label = { Text("${item.title} · ${item.subtitle}") },
                )
            }
        }
    }

    Text("未选择 Band、频点留空表示不追加限制。锁网可能导致断网。", modifier = Modifier.padding(16.dp))
    Text("制式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = nr, onClick = { nr = !nr }, label = { Text("NR 5G") })
        FilterChip(selected = lte, onClick = { lte = !lte }, label = { Text("LTE") })
        FilterChip(selected = wcdma, onClick = { wcdma = !wcdma }, label = { Text("WCDMA") })
        FilterChip(selected = gsm, onClick = { gsm = !gsm }, label = { Text("GSM") })
    }

    Text("LTE Band", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    BandChips(LteBandChoices, lteBands, prefix = "B") { lteBands = it }
    Text("NR Band", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    BandChips(NrBandChoices, nrBands, prefix = "n") { nrBands = it }
    Text("WCDMA Band", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    BandChips(WcdmaBandChoices, wcdmaBands, prefix = "B") { wcdmaBands = it }

    Text("频点所属制式（留空不限制）", modifier = Modifier.padding(horizontal = 16.dp))
    FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("LTE", "NR").forEach { rat ->
            FilterChip(selected = channelRat == rat, onClick = { channelRat = rat }, label = { Text(rat) })
        }
    }
    OutlinedTextField(
        value = arfcnText,
        onValueChange = { arfcnText = it.filter { ch -> ch.isDigit() } },
        label = { Text("频点 EARFCN / NR-ARFCN") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
    OutlinedTextField(
        value = pciText,
        onValueChange = { pciText = it.filter { ch -> ch.isDigit() } },
        label = { Text("PCI（尚未适配此设备）") },
        enabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )

    Button(
        onClick = {
            onApplyLock(
                slot.subscriptionId,
                gsm, wcdma, lte, nr,
                lteBands.toList(), nrBands.toList(), wcdmaBands.toList(),
                arfcnText.toIntOrNull(),
                pciText.toIntOrNull(),
                channelRat,
            )
        },
        enabled = shizuku.ready && (gsm || wcdma || lte || nr) &&
            (arfcnText.isBlank() || (arfcnText.toIntOrNull()?.let { it in 0..(if (channelRat == "NR") 3279165 else 262143) } == true && if (channelRat == "NR") nr else lte)),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) { Text("应用锁定") }
    TextButton(
        onClick = { onClearLock(slot.subscriptionId) },
        enabled = shizuku.ready,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) { Text("解除锁定 / 恢复自动") }

    shizuku.lastResult?.let { result ->
        Text(
            result,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandChips(
    choices: List<Int>,
    selected: Set<Int>,
    prefix: String,
    onChange: (Set<Int>) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { band ->
            FilterChip(
                selected = band in selected,
                onClick = {
                    onChange(if (band in selected) selected - band else selected + band)
                },
                label = { Text("$prefix$band") },
            )
        }
    }
}
