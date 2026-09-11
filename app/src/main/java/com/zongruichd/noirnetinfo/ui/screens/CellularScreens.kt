package com.zongruichd.noirnetinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.CellWifi
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zongruichd.noirnetinfo.data.CellRecord
import com.zongruichd.noirnetinfo.data.CellularSlot
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.shizuku.ShizukuUiState
import com.zongruichd.noirnetinfo.ui.components.CopyableMap
import com.zongruichd.noirnetinfo.ui.components.CopyableRow
import com.zongruichd.noirnetinfo.ui.components.SectionCard
import com.zongruichd.noirnetinfo.ui.components.SignalMeter
import com.zongruichd.noirnetinfo.ui.components.onOff
import com.zongruichd.noirnetinfo.ui.components.rsrpQuality
import com.zongruichd.noirnetinfo.ui.components.yesNo
import com.zongruichd.noirnetinfo.ui.util.headline
import com.zongruichd.noirnetinfo.ui.util.label

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CellularOverviewPage(snapshot: NetworkSnapshot, onCopy: (String) -> Unit) {
    val cell = snapshot.cellular
    SectionCard(title = "蜂窝总览", icon = Icons.Outlined.CellTower) {
        if (cell == null) {
            CopyableRow("状态", "无法读取蜂窝信息", onCopy, mono = false, showDivider = false)
            return@SectionCard
        }
        CopyableRow("运营商", cell.operatorName, onCopy, mono = false)
        CopyableRow("PLMN", cell.operatorNumeric, onCopy)
        CopyableRow("制式", cell.networkType ?: if (!snapshot.phoneGranted) "需要电话权限" else null, onCopy, mono = false)
        CopyableRow("电话类型", cell.phoneType, onCopy, mono = false)
        CopyableRow("数据开关", onOff(cell.dataEnabled), onCopy, mono = false)
        CopyableRow("漫游", yesNo(cell.roaming), onCopy, mono = false, showDivider = snapshot.slots.isNotEmpty())
        if (snapshot.slots.isEmpty()) {
            CopyableRow(
                "SIM",
                if (!snapshot.phoneGranted) "需要电话权限以读取卡槽信息" else "未检测到活动 SIM",
                onCopy,
                mono = false,
                showDivider = false,
            )
        }
    }
    snapshot.slots.forEach { slot ->
        SectionCard(title = "${slot.title} · ${slot.subtitle}", icon = Icons.Outlined.SimCard) {
            val serving = slot.primaryServing
            CopyableRow("代际", slot.operator.generation, onCopy, mono = false)
            CopyableRow("注册", slot.operator.serviceState, onCopy, mono = false)
            CopyableRow("主小区", serving?.headline(), onCopy, mono = false)
            CopyableRow("Band", serving?.band, onCopy, mono = false)
            CopyableRow("RSRP / 质量", serving?.rsrp?.let { "$it dBm · ${rsrpQuality(it)}" }, onCopy, mono = false, showDivider = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimDetailPage(
    slot: CellularSlot,
    locationGranted: Boolean,
    shizuku: ShizukuUiState,
    onCopy: (String) -> Unit,
    onRequestShizuku: () -> Unit,
    onApplyLock: (
        enableGsm: Boolean,
        enableWcdma: Boolean,
        enableLte: Boolean,
        enableNr: Boolean,
        lteBands: List<Int>,
        nrBands: List<Int>,
        wcdmaBands: List<Int>,
        arfcn: Int?,
        pci: Int?,
    ) -> Unit,
    onClearLock: () -> Unit,
) {
    val serving = slot.primaryServing
    SectionCard(title = "无线质量", icon = Icons.Outlined.CellTower) {
        FlowRow(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = {}, label = { Text(slot.operator.generation ?: "未知代际") })
            serving?.rat?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            serving?.connectionStatus?.label()?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            slot.operator.displayOverride?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            if (slot.operator.carrierAggregation == true) AssistChip(onClick = {}, label = { Text("CA") })
        }
        SignalMeter("RSRP / SS-RSRP", serving?.rsrp, "dBm", -140..-44)
        SignalMeter("RSRQ / SS-RSRQ", serving?.rsrq, "dB", -20..-3)
        SignalMeter("SINR / SS-SINR", serving?.sinr, "dB", -20..30)
        CopyableRow("信号质量", rsrpQuality(serving?.rsrp), onCopy, mono = false)
        CopyableRow("电平", serving?.level?.let { "$it / 4" }, onCopy, mono = false, showDivider = false)
    }

    SectionCard(title = "卡槽", icon = Icons.Outlined.Storage) {
        CopyableRow("卡槽序号", "${slot.slotIndex}（SIM ${slot.slotIndex + 1}）", onCopy)
        CopyableRow("Subscription ID", slot.subscriptionId.toString(), onCopy)
        CopyableRow("默认数据", yesNo(slot.isDefaultData), onCopy, mono = false)
        CopyableRow("默认通话", yesNo(slot.isDefaultVoice), onCopy, mono = false)
        CopyableRow("默认短信", yesNo(slot.isDefaultSms), onCopy, mono = false)
        CopyableRow("端口", slot.sim.portIndex?.toString(), onCopy, showDivider = false)
    }

    SectionCard(title = "SIM", icon = Icons.Outlined.SimCard) {
        CopyableRow("名称", slot.sim.displayName, onCopy, mono = false)
        CopyableRow("运营商名", slot.sim.carrierName, onCopy, mono = false)
        CopyableRow("ICCID", slot.sim.iccid, onCopy)
        CopyableRow("号码", slot.sim.number, onCopy)
        CopyableRow("状态", slot.sim.state, onCopy, mono = false)
        CopyableRow("MCC / MNC", listOfNotNull(slot.sim.mcc, slot.sim.mnc).joinToString(" / ").ifBlank { null }, onCopy)
        CopyableRow("国家", slot.sim.countryIso?.uppercase(), onCopy, mono = false)
        CopyableRow("eSIM", yesNo(slot.sim.embedded), onCopy, mono = false)
        CopyableRow("Opportunistic", yesNo(slot.sim.opportunistic), onCopy, mono = false)
        CopyableRow("Card ID", slot.sim.cardId, onCopy, showDivider = false)
    }

    SectionCard(title = "运营商", icon = Icons.Outlined.CellWifi) {
        CopyableRow("网络运营商", slot.operator.networkOperatorName, onCopy, mono = false)
        CopyableRow("SIM 运营商", slot.operator.simOperatorName, onCopy, mono = false)
        CopyableRow("网络 PLMN", slot.operator.networkNumeric, onCopy)
        CopyableRow("SIM PLMN", slot.operator.simNumeric, onCopy)
        CopyableRow("注册状态", slot.operator.serviceState, onCopy, mono = false)
        CopyableRow("代际", slot.operator.generation, onCopy, mono = false)
        CopyableRow("数据制式", slot.operator.dataNetworkType, onCopy, mono = false)
        CopyableRow("语音制式", slot.operator.voiceNetworkType, onCopy, mono = false)
        CopyableRow("显示覆盖", slot.operator.displayOverride, onCopy, mono = false)
        CopyableRow("5G 状态", slot.operator.nrState, onCopy, mono = false)
        CopyableRow("NR 频段范围", slot.operator.nrFrequencyRange, onCopy, mono = false)
        CopyableRow("载波聚合", yesNo(slot.operator.carrierAggregation), onCopy, mono = false)
        CopyableRow("电话类型", slot.operator.phoneType, onCopy, mono = false)
        CopyableRow("数据开关", onOff(slot.operator.dataEnabled), onCopy, mono = false)
        CopyableRow("漫游", yesNo(slot.operator.roaming), onCopy, mono = false)
        CopyableRow("数据漫游", yesNo(slot.operator.dataRoaming), onCopy, mono = false, showDivider = false)
    }

    ShizukuLockCard(
        slot = slot,
        shizuku = shizuku,
        onCopy = onCopy,
        onRequestShizuku = onRequestShizuku,
        onApplyLock = onApplyLock,
        onClearLock = onClearLock,
    )

    if (!locationGranted) {
        SectionCard(title = "小区", icon = Icons.Outlined.CellTower) {
            CopyableRow("服务小区", "需要位置权限才能读取 TAC/PCI/RSRP 与邻区", onCopy, mono = false, showDivider = false)
        }
        return
    }

    if (slot.servingCells.isEmpty()) {
        SectionCard(title = "服务小区", icon = Icons.Outlined.CellTower) {
            CopyableRow("状态", "当前没有已注册小区（可能无信号或飞行模式）", onCopy, mono = false, showDivider = false)
        }
    } else {
        slot.servingCells.forEach { cell ->
            CellCard(title = cell.connectionStatus.label(), cell = cell, onCopy = onCopy)
        }
    }

    SectionCard(title = "邻区（${slot.neighborCells.size}）", icon = Icons.Outlined.CellTower) {
        if (slot.neighborCells.isEmpty()) {
            CopyableRow("邻区", "没有可显示的邻区", onCopy, mono = false, showDivider = false)
        } else {
            slot.neighborCells.forEachIndexed { index, cell ->
                CopyableRow(
                    label = "${cell.rat} · ${cell.headline()}${cell.band?.let { " · $it" } ?: ""}",
                    value = buildString {
                        cell.rsrp?.let { append("RSRP $it dBm") }
                        cell.rsrq?.let { if (isNotEmpty()) append("  "); append("RSRQ $it dB") }
                        cell.sinr?.let { if (isNotEmpty()) append("  "); append("SINR $it dB") }
                        cell.dbm?.let { if (isEmpty()) append("$it dBm") }
                    }.ifBlank { cell.radio.entries.joinToString { "${it.key} ${it.value}" } },
                    onCopy = onCopy,
                    showDivider = index != slot.neighborCells.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun CellCard(title: String, cell: CellRecord, onCopy: (String) -> Unit) {
    SectionCard(title = "$title · ${cell.rat}", icon = Icons.Outlined.CellTower) {
        CopyableRow("制式", cell.rat, onCopy, mono = false)
        CopyableRow("角色", cell.connectionStatus.label(), onCopy, mono = false)
        CopyableRow("已注册", yesNo(cell.registered), onCopy, mono = false)
        CopyableRow("Band", cell.band, onCopy, mono = false)
        CopyableRow("频率", cell.frequencyMhz?.let { "$it MHz" }, onCopy)
        CopyableRow("ARFCN", cell.arfcn?.toString(), onCopy)
        Text(
            "标识",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
        )
        CopyableMap(cell.identity, onCopy)
        Text(
            "无线测量",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
        )
        CopyableMap(cell.radio, onCopy)
        Spacer(Modifier.height(8.dp))
    }
}

private val LteBandChoices = listOf(1, 3, 5, 7, 8, 20, 28, 34, 38, 39, 40, 41, 42)
private val NrBandChoices = listOf(1, 3, 5, 8, 28, 41, 77, 78, 79)
private val WcdmaBandChoices = listOf(1, 2, 5, 8)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShizukuLockCard(
    slot: CellularSlot,
    shizuku: ShizukuUiState,
    onCopy: (String) -> Unit,
    onRequestShizuku: () -> Unit,
    onApplyLock: (
        enableGsm: Boolean,
        enableWcdma: Boolean,
        enableLte: Boolean,
        enableNr: Boolean,
        lteBands: List<Int>,
        nrBands: List<Int>,
        wcdmaBands: List<Int>,
        arfcn: Int?,
        pci: Int?,
    ) -> Unit,
    onClearLock: () -> Unit,
) {
    val serving = slot.primaryServing
    val guessedBand = serving?.band?.filter { it.isDigit() }?.toIntOrNull()
    var gsm by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var wcdma by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var lte by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var nr by rememberSaveable(slot.subscriptionId) { mutableStateOf(true) }
    var lteBands by remember(slot.subscriptionId) {
        mutableStateOf(guessedBand?.takeIf { it in LteBandChoices }?.let { setOf(it) } ?: emptySet())
    }
    var nrBands by remember(slot.subscriptionId) {
        mutableStateOf(guessedBand?.takeIf { it in NrBandChoices }?.let { setOf(it) } ?: emptySet())
    }
    var wcdmaBands by remember(slot.subscriptionId) { mutableStateOf(emptySet<Int>()) }
    var arfcnText by rememberSaveable(slot.subscriptionId) { mutableStateOf(serving?.arfcn?.toString().orEmpty()) }
    var pciText by rememberSaveable(slot.subscriptionId) {
        mutableStateOf(serving?.identity?.get("PCI").orEmpty())
    }

    SectionCard(title = "Shizuku 锁网", icon = Icons.Outlined.Lock) {
        CopyableRow("状态", shizuku.mode, onCopy, mono = false)
        CopyableRow(
            "能力",
            when {
                shizuku.uid == 0 -> "Root：制式 / Band / 频点 / PCI(AT)"
                shizuku.uid == 2000 -> "ADB：制式 / Band / 频点（PCI 需要 Root）"
                else -> "授权后可用 shell 权限调用电话接口"
            },
            onCopy,
            mono = false,
            showDivider = false,
        )
        if (!shizuku.ready) {
            Button(
                onClick = onRequestShizuku,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    when {
                        !shizuku.installed -> "未检测到 Shizuku"
                        !shizuku.running -> "请先启动 Shizuku"
                        else -> "授权 Shizuku"
                    },
                )
            }
        }

        Text("制式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = nr, onClick = { nr = !nr }, label = { Text("NR 5G") })
            FilterChip(selected = lte, onClick = { lte = !lte }, label = { Text("LTE") })
            FilterChip(selected = wcdma, onClick = { wcdma = !wcdma }, label = { Text("WCDMA") })
            FilterChip(selected = gsm, onClick = { gsm = !gsm }, label = { Text("GSM") })
        }

        Text("LTE Band", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        BandChips(LteBandChoices, lteBands, prefix = "B") { lteBands = it }
        Text("NR Band", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        BandChips(NrBandChoices, nrBands, prefix = "n") { nrBands = it }
        Text("WCDMA Band", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        BandChips(WcdmaBandChoices, wcdmaBands, prefix = "B") { wcdmaBands = it }

        OutlinedTextField(
            value = arfcnText,
            onValueChange = { arfcnText = it.filter { ch -> ch.isDigit() } },
            label = { Text("频点 EARFCN / NR-ARFCN") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        OutlinedTextField(
            value = pciText,
            onValueChange = { pciText = it.filter { ch -> ch.isDigit() } },
            label = { Text("PCI（Root 模式）") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        Button(
            onClick = {
                onApplyLock(
                    gsm, wcdma, lte, nr,
                    lteBands.toList(), nrBands.toList(), wcdmaBands.toList(),
                    arfcnText.toIntOrNull(),
                    pciText.toIntOrNull(),
                )
            },
            enabled = shizuku.ready,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        ) { Text("应用锁定") }
        TextButton(
            onClick = onClearLock,
            enabled = shizuku.ready,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) { Text("解除锁定 / 恢复自动") }

        shizuku.lastResult?.let { result ->
            Text(
                result,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )
        }
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
