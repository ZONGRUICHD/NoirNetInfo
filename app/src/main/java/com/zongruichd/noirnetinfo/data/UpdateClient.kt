package com.zongruichd.noirnetinfo.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zongruichd.noirnetinfo.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class AppRelease(
    val tag: String,
    val name: String,
    val changelog: String?,
    val htmlUrl: String?,
    val apkUrl: String?,
    val apkName: String?,
) {
    val newerThanCurrent: Boolean
        get() = compareVersions(tag, BuildConfig.VERSION_NAME) > 0
}

object UpdateClient {
    private const val LATEST =
        "https://api.github.com/repos/ZONGRUICHD/NoirNetInfo/releases/latest"

    fun check(): AppRelease {
        val json = httpGet(LATEST, accept = "application/vnd.github+json")
        val obj = JSONObject(json)
        val assets = obj.optJSONArray("assets")
        var apkUrl: String? = null
        var apkName: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").ifBlank { null }
                    apkName = name
                    break
                }
            }
        }
        val tag = obj.optString("tag_name")
        if (tag.isBlank()) error("GitHub Release 没有 tag")
        return AppRelease(
            tag = tag,
            name = obj.optString("name"),
            changelog = obj.optString("body").ifBlank { null },
            htmlUrl = obj.optString("html_url").ifBlank { null },
            apkUrl = apkUrl,
            apkName = apkName,
        )
    }

    fun download(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit,
    ): Uri {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "NoirNetInfo-update.apk")
        if (dest.exists()) dest.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(60).toInt()
            requestMethod = "GET"
            setRequestProperty("User-Agent", "NoirNetInfo/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("下载失败 HTTP $code")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        copied += n
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (total > 0 && dest.length() != total) error("下载不完整，请重试")
            if (dest.length() < 1024) error("下载的文件过小，可能不是 APK")
        } finally {
            connection.disconnect()
        }
        onProgress(1f)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            dest,
        )
    }

    private fun httpGet(url: String, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
            requestMethod = "GET"
            setRequestProperty("User-Agent", "NoirNetInfo/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", accept)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal fun compareVersions(latestTag: String, currentName: String): Int {
    val a = versionParts(latestTag)
    val b = versionParts(currentName)
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

private fun versionParts(raw: String): List<Int> {
    val core = raw.trim().removePrefix("v").substringBefore("-")
    return core.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}
