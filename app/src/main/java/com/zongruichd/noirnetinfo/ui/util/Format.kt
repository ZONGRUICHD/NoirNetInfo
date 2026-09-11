package com.zongruichd.noirnetinfo.ui.util

import com.zongruichd.noirnetinfo.data.CellRecord
import com.zongruichd.noirnetinfo.data.CellRole
import com.zongruichd.noirnetinfo.data.IpScope
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun IpScope.label(): String = when (this) {
    IpScope.LOOPBACK -> "回环"
    IpScope.LINK_LOCAL -> "链路本地"
    IpScope.PRIVATE -> "私网"
    IpScope.ULA -> "ULA"
    IpScope.GLOBAL -> "全球"
    IpScope.OTHER -> "其他"
}

fun wifiBandOf(mhz: Int): String = when (mhz) {
    in 2400..2500 -> "2.4 GHz"
    in 4900..5900 -> "5 GHz"
    in 5925..7125 -> "6 GHz"
    else -> "未知频段"
}

fun formatTime(millis: Long): String {
    return DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

fun CellRole.label(): String = when (this) {
    CellRole.PRIMARY -> "主服务小区"
    CellRole.SECONDARY -> "辅服务小区"
    CellRole.NEIGHBOR -> "邻区"
    CellRole.UNKNOWN -> "未知角色"
}

fun CellRecord.headline(): String {
    val pci = identity["PCI"] ?: identity["PSC"] ?: identity["BSIC"]
    val id = identity["ECI"] ?: identity["NCI"] ?: identity["CI"] ?: identity["BID"]
    return listOfNotNull(rat, pci?.let { "PCI $it" }, id?.let { "ID $it" }).joinToString(" · ")
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
    slots.forEach { slot ->
        appendLine("${slot.title} ${slot.subtitle}")
        appendLine("  卡槽 ${slot.slotIndex}  SubId ${slot.subscriptionId}")
        appendLine("  ICCID ${slot.sim.iccid}  MCC ${slot.sim.mcc} MNC ${slot.sim.mnc}")
        appendLine("  ${slot.operator.generation} ${slot.operator.dataNetworkType} ${slot.operator.serviceState}")
        slot.servingCells.forEach { cell ->
            appendLine("  服务 ${cell.rat} ${cell.connectionStatus.label()} ${cell.identity} ${cell.radio}")
        }
        slot.neighborCells.forEach { cell ->
            appendLine("  邻区 ${cell.headline()} ${cell.radio}")
        }
        appendLine()
    }
    appendLine("公网 IPv4: ${publicIp.ipv4}")
    appendLine("公网 IPv6: ${publicIp.ipv6}")
}
