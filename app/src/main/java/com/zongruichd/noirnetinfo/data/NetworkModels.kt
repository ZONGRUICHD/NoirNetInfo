package com.zongruichd.noirnetinfo.data

data class NetworkSnapshot(
    val collectedAtMillis: Long,
    val connectivity: ConnectivityInfo,
    val addresses: List<IpEntry>,
    val wifi: WifiDetails?,
    val cellular: CellularDetails?,
    val slots: List<CellularSlot>,
    val interfaces: List<IfaceDetails>,
    val publicIp: PublicIpInfo = PublicIpInfo(),
    val locationGranted: Boolean,
    val phoneGranted: Boolean,
)

data class ConnectivityInfo(
    val transports: List<String>,
    val hasInternet: Boolean,
    val validated: Boolean,
    val captivePortal: Boolean,
    val vpn: Boolean,
    val metered: Boolean,
    val interfaceName: String?,
    val mtu: Int?,
    val dns: List<String>,
    val domains: String?,
    val dhcpServer: String?,
    val gateways: List<String>,
)

data class IpEntry(
    val hostAddress: String,
    val cidr: String,
    val version: IpVersion,
    val scope: IpScope,
    val iface: String,
)

enum class IpVersion { V4, V6 }

enum class IpScope {
    LOOPBACK,
    LINK_LOCAL,
    PRIVATE,
    ULA,
    GLOBAL,
    OTHER,
}

data class WifiDetails(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val linkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val standard: String?,
    val security: String?,
    val hiddenSsid: Boolean?,
    val needsLocation: Boolean,
)

data class CellularDetails(
    val operatorName: String?,
    val operatorNumeric: String?,
    val networkType: String?,
    val dataEnabled: Boolean?,
    val roaming: Boolean?,
    val phoneType: String?,
)

data class CellularSlot(
    val slotIndex: Int,
    val subscriptionId: Int,
    val isDefaultData: Boolean,
    val isDefaultVoice: Boolean,
    val isDefaultSms: Boolean,
    val sim: SimCardInfo,
    val operator: OperatorInfo,
    val servingCells: List<CellRecord>,
    val neighborCells: List<CellRecord>,
) {
    val title: String
        get() = "SIM ${slotIndex + 1}"

    val subtitle: String
        get() = sim.carrierName ?: operator.networkOperatorName ?: sim.displayName ?: "卡槽 ${slotIndex + 1}"

    val primaryServing: CellRecord?
        get() = servingCells.firstOrNull { it.connectionStatus == CellRole.PRIMARY } ?: servingCells.firstOrNull()
}

data class SimCardInfo(
    val displayName: String?,
    val carrierName: String?,
    val iccid: String?,
    val number: String?,
    val state: String?,
    val embedded: Boolean?,
    val opportunistic: Boolean?,
    val countryIso: String?,
    val cardId: String?,
    val mcc: String?,
    val mnc: String?,
    val portIndex: Int?,
)

data class OperatorInfo(
    val networkOperatorName: String?,
    val simOperatorName: String?,
    val networkNumeric: String?,
    val simNumeric: String?,
    val roaming: Boolean?,
    val dataRoaming: Boolean?,
    val dataEnabled: Boolean?,
    val phoneType: String?,
    val dataNetworkType: String?,
    val voiceNetworkType: String?,
    val networkClass: String?,
    val generation: String?,
    val serviceState: String?,
    val nrState: String?,
    val nrFrequencyRange: String?,
    val displayOverride: String?,
    val carrierAggregation: Boolean?,
)

data class CellRecord(
    val rat: String,
    val registered: Boolean,
    val connectionStatus: CellRole,
    val mcc: String?,
    val mnc: String?,
    val identity: Map<String, String>,
    val radio: Map<String, String>,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rssi: Int? = null,
    val dbm: Int? = null,
    val asu: Int? = null,
    val level: Int? = null,
    val band: String? = null,
    val frequencyMhz: Double? = null,
    val arfcn: Int? = null,
)

enum class CellRole { PRIMARY, SECONDARY, NEIGHBOR, UNKNOWN }

data class IfaceDetails(
    val name: String,
    val displayName: String,
    val up: Boolean,
    val loopback: Boolean,
    val virtual: Boolean,
    val mtu: Int,
    val ipv4: List<String>,
    val ipv6: List<String>,
)

data class PublicIpInfo(
    val ipv4: String? = null,
    val ipv6: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
)
