package com.zongruichd.noirnetinfo.data

data class NetworkSnapshot(
    val collectedAtMillis: Long,
    val connectivity: ConnectivityInfo,
    val addresses: List<IpEntry>,
    val wifi: WifiDetails?,
    val cellular: CellularDetails?,
    val sims: List<SimDetails>,
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

data class SimDetails(
    val slotIndex: Int,
    val subscriptionId: Int,
    val displayName: String?,
    val carrierName: String?,
    val mcc: String?,
    val mnc: String?,
    val countryIso: String?,
    val number: String?,
    val embedded: Boolean?,
    val simState: String?,
    val networkOperator: String?,
    val networkType: String?,
    val roaming: Boolean?,
)

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
