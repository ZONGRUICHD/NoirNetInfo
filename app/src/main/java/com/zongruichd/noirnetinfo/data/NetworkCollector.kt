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
        val (cellular, slots) = CellularCollector.collect(app, phoneGranted, locationGranted)

        return NetworkSnapshot(
            collectedAtMillis = System.currentTimeMillis(),
            connectivity = connectivity,
            addresses = addresses,
            wifi = wifi,
            cellular = cellular,
            slots = slots,
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
        val ifaceName = lp?.interfaceName.orEmpty()
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
                iface = ifaceName,
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

}
