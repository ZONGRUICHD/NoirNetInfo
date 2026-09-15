package com.zongruichd.noirnetinfo.data

import java.net.InetAddress

/** Literal IP input only: never resolve a hostname while classifying an address. */
internal fun classifyIp(host: String): IpScope {
    val literal = host.substringBefore('%')
    if (!literal.matches(Regex("[0-9a-fA-F:.]+"))) return IpScope.OTHER
    val address = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return IpScope.OTHER
    val bytes = address.address.map { it.toInt() and 255 }
    return when {
        address.isAnyLocalAddress -> IpScope.OTHER
        address.isLoopbackAddress -> IpScope.LOOPBACK
        address.isLinkLocalAddress -> IpScope.LINK_LOCAL
        address.isMulticastAddress -> IpScope.OTHER
        bytes.size == 4 -> when {
            bytes[0] == 100 && bytes[1] in 64..127 -> IpScope.SHARED
            address.isSiteLocalAddress -> IpScope.PRIVATE
            bytes[0] == 0 || bytes[0] >= 240 -> IpScope.OTHER
            bytes.take(3) == listOf(192, 0, 2) ||
                bytes.take(3) == listOf(198, 51, 100) ||
                bytes.take(3) == listOf(203, 0, 113) ||
                (bytes[0] == 198 && bytes[1] in 18..19) -> IpScope.OTHER
            else -> IpScope.GLOBAL
        }
        bytes[0] and 254 == 252 -> IpScope.ULA
        bytes.take(4) == listOf(32, 1, 13, 184) -> IpScope.OTHER
        bytes[0] and 224 == 32 -> IpScope.GLOBAL
        else -> IpScope.OTHER
    }
}
