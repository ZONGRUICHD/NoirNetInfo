package com.zongruichd.noirnetinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.CellWifi
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zongruichd.noirnetinfo.data.CellRecord
import com.zongruichd.noirnetinfo.data.CellRole
import com.zongruichd.noirnetinfo.data.CellularSlot
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.ui.components.CopyableMap
import com.zongruichd.noirnetinfo.ui.components.CopyableRow
import com.zongruichd.noirnetinfo.ui.components.ExpandableBlock
import com.zongruichd.noirnetinfo.ui.components.SectionCard
import com.zongruichd.noirnetinfo.ui.components.SignalMeter
import com.zongruichd.noirnetinfo.ui.components.onOff
import com.zongruichd.noirnetinfo.ui.components.rsrpQuality
import com.zongruichd.noirnetinfo.ui.components.yesNo
import com.zongruichd.noirnetinfo.ui.util.headline
import com.zongruichd.noirnetinfo.ui.util.label

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CellularOverviewPage(snapshot: NetworkSnapshot, onCopy: (String) -> Unit, onOpenSim: (Int) -> Unit) {
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
        val serving = slot.primaryServing
        SectionCard(
            title = "${slot.title} · ${slot.subtitle}",
            icon = Icons.Outlined.SimCard,
            summary = listOfNotNull(
                slot.operator.generation,
                serving?.headline(),
                serving?.rsrp?.let { "$it dBm" },
            ).joinToString(" · ").ifBlank { null },
            collapsible = true,
            initiallyExpanded = true,
        ) {
            TextButton(onClick = { onOpenSim(slot.subscriptionId) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("查看服务小区与邻区") }
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
    onCopy: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val serving = slot.primaryServing
    SectionCard(title = "无线质量", icon = Icons.Outlined.CellTower) {
        FlowRow(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = {}, label = { Text(slot.operator.generation ?: "未知代际") })
            serving?.rat?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            serving?.connectionStatus?.label()?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            slot.operator.displayOverride?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            if (slot.operator.carrierAggregation == true) AssistChip(onClick = {}, label = { Text("CA") })
        }
        CopyableRow("测量时间", serving?.measuredAtMillis?.let {
            "系统缓存 · ${com.zongruichd.noirnetinfo.ui.util.formatTime(it)}"
        }, onCopy, mono = false)
        if (serving?.rat == "LTE" || serving?.rat == "NR 5G") {
            SignalMeter("RSRP / SS-RSRP", serving.rsrp, "dBm", -140..-44)
            SignalMeter("RSRQ / SS-RSRQ", serving.rsrq, "dB", -43..20)
            SignalMeter("SINR / SS-SINR", serving.sinr, "dB", -23..40)
        } else {
            SignalMeter("接收信号", serving?.dbm, "dBm", -120..-40)
        }
        CopyableRow("信号质量", rsrpQuality(serving?.rsrp), onCopy, mono = false)
        CopyableRow("电平", serving?.level?.let { "$it / 4" }, onCopy, mono = false, showDivider = false)
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("锁网 / Shizuku 在设置里")
        }
    }

    SectionCard(
        title = "SIM",
        icon = Icons.Outlined.SimCard,
        summary = listOfNotNull(slot.sim.carrierName ?: slot.sim.displayName, slot.sim.iccid).joinToString(" · "),
        collapsible = true,
        initiallyExpanded = true,
    ) {
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

    SectionCard(
        title = "卡槽",
        icon = Icons.Outlined.Storage,
        summary = "槽 ${slot.slotIndex} · Sub ${slot.subscriptionId}",
        collapsible = true,
        initiallyExpanded = false,
    ) {
        CopyableRow("卡槽序号", "${slot.slotIndex}（SIM ${slot.slotIndex + 1}）", onCopy)
        CopyableRow("Subscription ID", slot.subscriptionId.toString(), onCopy)
        CopyableRow("默认数据", yesNo(slot.isDefaultData), onCopy, mono = false)
        CopyableRow("默认通话", yesNo(slot.isDefaultVoice), onCopy, mono = false)
        CopyableRow("默认短信", yesNo(slot.isDefaultSms), onCopy, mono = false)
        CopyableRow("端口", slot.sim.portIndex?.toString(), onCopy, showDivider = false)
    }

    SectionCard(
        title = "运营商",
        icon = Icons.Outlined.CellWifi,
        summary = listOfNotNull(
            slot.operator.generation,
            slot.operator.dataNetworkType,
            slot.operator.serviceState,
        ).joinToString(" · "),
        collapsible = true,
        initiallyExpanded = false,
    ) {
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

    SectionCard(
        title = "邻区（${slot.neighborCells.size}）",
        icon = Icons.Outlined.CellTower,
        summary = slot.neighborCells.take(3).joinToString(" · ") { it.headline() }
            .ifBlank { "没有可显示的邻区" },
        collapsible = true,
        initiallyExpanded = false,
    ) {
        if (slot.neighborCells.isEmpty()) {
            CopyableRow("邻区", "没有可显示的邻区", onCopy, mono = false, showDivider = false)
        } else {
            slot.neighborCells.forEach { cell ->
                ExpandableBlock(
                    title = listOfNotNull(cell.rat, cell.band, cell.headline()).joinToString(" · "),
                    subtitle = buildString {
                        cell.rsrp?.let { append("RSRP $it dBm") }
                        cell.rsrq?.let { if (isNotEmpty()) append("  "); append("RSRQ $it dB") }
                        cell.sinr?.let { if (isNotEmpty()) append("  "); append("SINR $it dB") }
                        cell.dbm?.let { if (isEmpty()) append("$it dBm") }
                    }.ifBlank { cell.radio.entries.joinToString { "${it.key} ${it.value}" } },
                ) {
                    CopyableMap(cell.identity + cell.radio, onCopy)
                }
            }
        }
    }
}

@Composable
private fun CellCard(title: String, cell: CellRecord, onCopy: (String) -> Unit) {
    val pci = cell.identity["PCI"] ?: cell.identity["PSC"] ?: cell.identity["BSIC"]
    SectionCard(
        title = "$title · ${cell.rat}",
        icon = Icons.Outlined.CellTower,
        summary = listOfNotNull(
            cell.band,
            pci?.let { "PCI $it" },
            cell.arfcn?.let { "ARFCN $it" },
            cell.rsrp?.let { "$it dBm" },
        ).joinToString(" · "),
        collapsible = true,
        initiallyExpanded = cell.connectionStatus == CellRole.PRIMARY,
    ) {
        CopyableRow("制式", cell.rat, onCopy, mono = false)
        CopyableRow("角色", cell.connectionStatus.label(), onCopy, mono = false)
        CopyableRow("Band", cell.band, onCopy, mono = false)
        CopyableRow("频率", cell.frequencyMhz?.let { "$it MHz" }, onCopy)
        CopyableRow("ARFCN", cell.arfcn?.toString(), onCopy)
        CopyableRow("PCI / PSC", pci, onCopy)
        CopyableRow("RSRP", cell.rsrp?.let { "$it dBm" }, onCopy)
        CopyableRow("RSRQ", cell.rsrq?.let { "$it dB" }, onCopy)
        CopyableRow("SINR", cell.sinr?.let { "$it dB" }, onCopy, showDivider = false)
        ExpandableBlock("全部标识 / 测量", "${cell.identity.size + cell.radio.size} 项") {
            CopyableMap(cell.identity + cell.radio, onCopy)
        }
    }
}
