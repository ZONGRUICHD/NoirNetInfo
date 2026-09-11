package com.zongruichd.noirnetinfo.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkCollector {

    fun collect(context: Context): NetworkSnapshot {
        val app = context.applicationContext
        val locationGranted = has(app, Manifest.permission.ACCESS_FINE_LOCATION) ||
            has(app, Manifest.permission.ACCESS_COARSE_LOCATION)
        val phoneGranted = has(app, Manifest.permission.READ_PHONE_STATE)

        val cm = app.getSystemService(ConnectivityManager::class.java)
        val active = cm.activeNetwork
        val caps = active?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
        val lp = active?.let { runCatching { cm.getLinkProperties(it) }.getOrNull() }

        val connectivity = connectivity(cm, caps, lp)
        val interfaces = interfaces()
        val addresses = addressesFrom(lp, interfaces)
        val wifi = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            wifi(app, caps, locationGranted)
        } else {
            null
        }
        val cellular = cellular(app, phoneGranted)
        val sims = sims(app, phoneGranted)

        return NetworkSnapshot(
            collectedAtMillis = System.currentTimeMillis(),
            connectivity = connectivity,
            addresses = addresses,
            wifi = wifi,
            cellular = cellular,
            sims = sims,
            interfaces = interfaces,
            locationGranted = locationGranted,
            phoneGranted = phoneGranted,
        )
    }

    private fun has(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun connectivity(
        cm: ConnectivityManager,
        caps: NetworkCapabilities?,
        lp: LinkProperties?,
    ): ConnectivityInfo {
        val transports = buildList {
            if (caps == null) return@buildList
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("蜂窝")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("以太网")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("蓝牙")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)
            ) {
                add("USB")
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) add("Wi-Fi Aware")
        }
        @Suppress("DEPRECATION")
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true ||
            runCatching { cm.getNetworkInfo(ConnectivityManager.TYPE_VPN)?.isConnected == true }
                .getOrDefault(false)
        val gateways = lp?.routes.orEmpty()
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway?.hostAddress }
            .distinct()
        val dhcp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lp?.dhcpServerAddress?.hostAddress
        } else {
            null
        }
        return ConnectivityInfo(
            transports = transports.ifEmpty { listOf("无活动网络") },
            hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            captivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
            vpn = vpn,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
            interfaceName = lp?.interfaceName,
            mtu = lp?.mtu?.takeIf { it > 0 },
            dns = lp?.dnsServers.orEmpty().mapNotNull { it.hostAddress },
            domains = lp?.domains?.takeIf { it.isNotBlank() },
            dhcpServer = dhcp,
            gateways = gateways,
        )
    }

    @SuppressLint("MissingPermission")
    private fun wifi(
        context: Context,
        caps: NetworkCapabilities?,
        locationGranted: Boolean,
    ): WifiDetails? {
        val fromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps?.transportInfo as? WifiInfo
        } else {
            null
        }
        val info = fromCaps ?: run {
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
                ?: return null
            @Suppress("DEPRECATION")
            wm.connectionInfo
        } ?: return null

        if (info.networkId == -1 && fromCaps == null && info.ssid.isNullOrBlank()) {
            return null
        }

        val rawSsid = info.ssid?.trim()?.removeSurrounding("\"")
        val unknown = rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>"
        val ssid = when {
            !unknown -> rawSsid
            !locationGranted -> null
            else -> null
        }
        val bssid = info.bssid?.takeUnless {
            it.isBlank() || it == "02:00:00:00:00:00" || it == "00:00:00:00:00:00"
        }
        return WifiDetails(
            ssid = ssid,
            bssid = bssid,
            rssiDbm = info.rssi.takeIf { it in -126..-1 },
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            txLinkSpeedMbps = if (Build.VERSION.SDK_INT >= 29) {
                info.txLinkSpeedMbps.takeIf { it > 0 }
            } else {
                null
            },
            frequencyMhz = info.frequency.takeIf { it > 0 },
            standard = wifiStandard(info),
            security = securityType(info),
            hiddenSsid = info.hiddenSSID,
            needsLocation = unknown && !locationGranted,
        )
    }

    private fun wifiStandard(info: WifiInfo): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return when (info.wifiStandard) {
            android.net.wifi.ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g"
            android.net.wifi.ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (n)"
            android.net.wifi.ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (ac)"
            android.net.wifi.ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (ax)"
            android.net.wifi.ScanResult.WIFI_STANDARD_11AD -> "Wi-Fi 60GHz (ad)"
            else -> if (Build.VERSION.SDK_INT >= 33 &&
                info.wifiStandard == android.net.wifi.ScanResult.WIFI_STANDARD_11BE
            ) {
                "Wi-Fi 7 (be)"
            } else {
                null
            }
        }
    }

    private fun securityType(info: WifiInfo): String? {
        if (Build.VERSION.SDK_INT < 31) return null
        return when (info.currentSecurityType) {
            WifiInfo.SECURITY_TYPE_OPEN -> "开放"
            WifiInfo.SECURITY_TYPE_WEP -> "WEP"
            WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2 PSK"
            WifiInfo.SECURITY_TYPE_EAP -> "EAP"
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3 SAE"
            WifiInfo.SECURITY_TYPE_OWE -> "OWE"
            WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI PSK"
            WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI CERT"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3 Enterprise"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3 Enterprise 192"
            WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2 -> "Passpoint R1/R2"
            WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "Passpoint R3"
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    private fun cellular(context: Context, phoneGranted: Boolean): CellularDetails? {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val operatorName = tm.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: tm.simOperatorName?.takeIf { it.isNotBlank() }
        val numeric = tm.networkOperator?.takeIf { it.isNotBlank() }
            ?: tm.simOperator?.takeIf { it.isNotBlank() }
        if (operatorName == null && numeric == null && tm.simState == TelephonyManager.SIM_STATE_ABSENT) {
            return CellularDetails(
                operatorName = null,
                operatorNumeric = null,
                networkType = null,
                dataEnabled = runCatching { tm.isDataEnabled }.getOrNull(),
                roaming = runCatching { tm.isNetworkRoaming }.getOrNull(),
                phoneType = phoneType(tm.phoneType),
            )
        }
        val networkType = if (phoneGranted) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 24) networkTypeName(tm.dataNetworkType) else null
            }.getOrNull()
        } else {
            null
        }
        return CellularDetails(
            operatorName = operatorName,
            operatorNumeric = numeric,
            networkType = networkType,
            dataEnabled = runCatching { tm.isDataEnabled }.getOrNull(),
            roaming = runCatching { tm.isNetworkRoaming }.getOrNull(),
            phoneType = phoneType(tm.phoneType),
        )
    }

    @SuppressLint("MissingPermission")
    private fun sims(context: Context, phoneGranted: Boolean): List<SimDetails> {
        if (!phoneGranted) return emptyList()
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        val subs = runCatching { sm.activeSubscriptionInfoList }.getOrNull().orEmpty()
        if (subs.isEmpty()) return emptyList()
        return subs.map { info ->
            val subTm = runCatching { tm.createForSubscriptionId(info.subscriptionId) }.getOrNull()
            val mcc = if (Build.VERSION.SDK_INT >= 29) info.mccString else @Suppress("DEPRECATION") info.mcc.takeIf { it != 0 }?.toString()
            val mnc = if (Build.VERSION.SDK_INT >= 29) info.mncString else @Suppress("DEPRECATION") info.mnc.takeIf { it != 0 }?.toString()
            SimDetails(
                slotIndex = info.simSlotIndex,
                subscriptionId = info.subscriptionId,
                displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() },
                carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() },
                mcc = mcc,
                mnc = mnc,
                countryIso = info.countryIso?.takeIf { it.isNotBlank() },
                number = info.number?.takeIf { it.isNotBlank() },
                embedded = if (Build.VERSION.SDK_INT >= 28) info.isEmbedded else null,
                simState = simState(subTm?.simState ?: tm.simState),
                networkOperator = subTm?.networkOperatorName?.takeIf { it.isNotBlank() },
                networkType = subTm?.let {
                    runCatching { networkTypeName(it.dataNetworkType) }.getOrNull()
                },
                roaming = runCatching { subTm?.isNetworkRoaming }.getOrNull(),
            )
        }.sortedBy { it.slotIndex }
    }

    private fun interfaces(): List<IfaceDetails> {
        val list = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
            .getOrNull()
            .orEmpty()
        return list.map { nif ->
            val addrs = runCatching { nif.inetAddresses.toList() }.getOrNull().orEmpty()
            IfaceDetails(
                name = nif.name,
                displayName = nif.displayName ?: nif.name,
                up = runCatching { nif.isUp }.getOrDefault(false),
                loopback = runCatching { nif.isLoopback }.getOrDefault(false),
                virtual = runCatching { nif.isVirtual }.getOrDefault(false),
                mtu = runCatching { nif.mtu }.getOrDefault(0),
                ipv4 = addrs.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
                ipv6 = addrs.filterIsInstance<Inet6Address>().mapNotNull { scoped(it) },
            )
        }.sortedWith(
            compareByDescending<IfaceDetails> { it.up }
                .thenBy { it.loopback }
                .thenBy { it.name },
        )
    }

    private fun addressesFrom(lp: LinkProperties?, ifaces: List<IfaceDetails>): List<IpEntry> {
        val fromLink = lp?.linkAddresses.orEmpty().mapNotNull { la ->
            val inet = la.address ?: return@mapNotNull null
            if (inet.isLoopbackAddress) return@mapNotNull null
            val host = if (inet is Inet6Address) scoped(inet) else inet.hostAddress
            host ?: return@mapNotNull null
            IpEntry(
                hostAddress = host,
                cidr = "$host/${la.prefixLength}",
                version = if (inet is Inet4Address) IpVersion.V4 else IpVersion.V6,
                scope = scopeOf(inet),
                iface = lp.interfaceName ?: "",
            )
        }
        if (fromLink.isNotEmpty()) {
            val extras = ifaces.asSequence()
                .filter { it.up && !it.loopback }
                .flatMap { iface ->
                    (iface.ipv4.map { it to IpVersion.V4 } + iface.ipv6.map { it to IpVersion.V6 })
                        .map { (host, version) ->
                            IpEntry(
                                hostAddress = host,
                                cidr = host,
                                version = version,
                                scope = scopeOf(host, version),
                                iface = iface.name,
                            )
                        }
                }
                .filter { extra -> fromLink.none { it.hostAddress.substringBefore('%') == extra.hostAddress.substringBefore('%') } }
                .toList()
            return (fromLink + extras).distinctBy { it.cidr + it.iface }
        }
        return ifaces.asSequence()
            .filter { it.up && !it.loopback }
            .flatMap { iface ->
                (iface.ipv4.map { it to IpVersion.V4 } + iface.ipv6.map { it to IpVersion.V6 })
                    .map { (host, version) ->
                        IpEntry(
                            hostAddress = host,
                            cidr = host,
                            version = version,
                            scope = scopeOf(host, version),
                            iface = iface.name,
                        )
                    }
            }
            .toList()
    }

    private fun scoped(address: Inet6Address): String? {
        val host = address.hostAddress ?: return null
        return host.substringBefore('%')
    }

    private fun scopeOf(address: InetAddress): IpScope {
        return when (address) {
            is Inet4Address -> scopeOf(address.hostAddress ?: return IpScope.OTHER, IpVersion.V4)
            is Inet6Address -> when {
                address.isLoopbackAddress -> IpScope.LOOPBACK
                address.isLinkLocalAddress -> IpScope.LINK_LOCAL
                else -> scopeOf(address.hostAddress?.substringBefore('%') ?: return IpScope.OTHER, IpVersion.V6)
            }
            else -> IpScope.OTHER
        }
    }

    private fun scopeOf(host: String, version: IpVersion): IpScope {
        val lower = host.lowercase()
        return when (version) {
            IpVersion.V4 -> {
                val p = lower.split('.')
                val a = p.getOrNull(0)?.toIntOrNull()
                val b = p.getOrNull(1)?.toIntOrNull()
                when {
                    a == 127 -> IpScope.LOOPBACK
                    a == 10 -> IpScope.PRIVATE
                    a == 192 && b == 168 -> IpScope.PRIVATE
                    a == 172 && b in 16..31 -> IpScope.PRIVATE
                    a == 169 && b == 254 -> IpScope.LINK_LOCAL
                    else -> IpScope.GLOBAL
                }
            }
            IpVersion.V6 -> when {
                lower == "::1" -> IpScope.LOOPBACK
                lower.startsWith("fe80") -> IpScope.LINK_LOCAL
                lower.startsWith("fc") || lower.startsWith("fd") -> IpScope.ULA
                lower.startsWith("ff") -> IpScope.OTHER
                else -> IpScope.GLOBAL
            }
        }
    }

    private fun phoneType(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        TelephonyManager.PHONE_TYPE_NONE -> "无"
        else -> "未知"
    }

    private fun simState(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "未插入"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "需要 PIN"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "需要 PUK"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "网络锁定"
        TelephonyManager.SIM_STATE_READY -> "就绪"
        TelephonyManager.SIM_STATE_NOT_READY -> "未就绪"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "永久禁用"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "读卡错误"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "受限"
        else -> "未知"
    }

    private fun networkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO 0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO A"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO B"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "未知"
        else -> "类型 $type"
    }

}
