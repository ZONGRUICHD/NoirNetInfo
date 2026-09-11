package com.zongruichd.noirnetinfo.data

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object PublicIpClient {
    fun fetch(): PublicIpInfo {
        val v4 = runCatching { get("https://api.ipify.org") }.getOrNull()
        val v6raw = runCatching { get("https://api64.ipify.org") }.getOrNull()
        val v6 = v6raw?.takeIf { it.contains(':') }
        val error = if (v4 == null && v6 == null) {
            "公网地址查询失败（可能无外网或被拦截）"
        } else {
            null
        }
        return PublicIpInfo(ipv4 = v4, ipv6 = v6, error = error, loading = false)
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeUnit.SECONDS.toMillis(6).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(6).toInt()
            requestMethod = "GET"
            setRequestProperty("Accept", "text/plain")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }.trim()
                .takeIf { it.isNotEmpty() } ?: error("empty")
        } finally {
            connection.disconnect()
        }
    }
}
