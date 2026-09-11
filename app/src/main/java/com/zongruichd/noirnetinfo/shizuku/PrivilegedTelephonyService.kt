package com.zongruichd.noirnetinfo.shizuku

import android.annotation.Keep
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.system.Os
import android.telephony.RadioAccessSpecifier
import android.telephony.TelephonyManager
import android.util.Log
import java.io.File

class PrivilegedTelephonyService : IPrivilegedTelephony.Stub {
    private var appContext: Context? = null

    constructor() {
        Log.i(TAG, "constructor uid=${Os.getuid()}")
    }

    @Keep
    constructor(context: Context) : this() {
        appContext = context
        Log.i(TAG, "constructor(context) uid=${Os.getuid()}")
    }

    override fun destroy() {
        Log.i(TAG, "destroy")
        System.exit(0)
    }

    override fun status(): String {
        val uid = Os.getuid()
        val mode = when (uid) {
            0 -> "root"
            2000 -> "adb"
            else -> "uid=$uid"
        }
        return "ok uid=$uid mode=$mode"
    }

    @SuppressLint("MissingPermission")
    override fun getAllowedNetworkTypes(subId: Int): Long {
        val tm = tm(subId)
        if (Build.VERSION.SDK_INT >= 30) {
            return runCatching {
                tm.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
            }.getOrDefault(-1L)
        }
        return -1L
    }

    @SuppressLint("MissingPermission")
    override fun setAllowedNetworkTypes(subId: Int, types: Long): String {
        val tm = tm(subId)
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                tm.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    types,
                )
                "已设置制式掩码 $types"
            } else {
                val method = TelephonyManager::class.java.getMethod(
                    "setPreferredNetworkType",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                val ok = method.invoke(tm, subId, preferredFromMask(types)) as Boolean
                if (ok) "已设置首选网络类型" else "setPreferredNetworkType 返回 false"
            }
        } catch (t: Throwable) {
            "制式锁定失败: ${t.message}"
        }
    }

    @SuppressLint("MissingPermission")
    override fun setSelection(
        subId: Int,
        rans: IntArray,
        bandCounts: IntArray,
        bands: IntArray,
        channelRan: Int,
        channels: IntArray,
    ): String {
        val tm = tm(subId)
        return try {
            val specifiers = ArrayList<RadioAccessSpecifier>()
            var offset = 0
            rans.forEachIndexed { index, ran ->
                val count = bandCounts.getOrNull(index) ?: 0
                val slice = if (count > 0) bands.copyOfRange(offset, offset + count) else IntArray(0)
                offset += count
                val spec = RadioAccessSpecifier(
                    ran,
                    slice.takeIf { it.isNotEmpty() },
                    if (channelRan == ran && channels.isNotEmpty()) channels else null,
                )
                specifiers.add(spec)
            }
            val method = TelephonyManager::class.java.methods.firstOrNull {
                it.name == "setSystemSelectionChannels" && it.parameterTypes.size == 1
            } ?: throw IllegalStateException("当前系统没有 setSystemSelectionChannels")
            method.invoke(tm, specifiers)
            "已应用 Band/频点选择（${specifiers.size} 组）"
        } catch (t: Throwable) {
            "Band/频点锁定失败: ${t.message}"
        }
    }

    @SuppressLint("MissingPermission")
    override fun clearSelection(subId: Int): String {
        val tm = tm(subId)
        val messages = mutableListOf<String>()
        runCatching {
            val method = TelephonyManager::class.java.methods.firstOrNull {
                it.name == "setSystemSelectionChannels" && it.parameterTypes.size == 1
            }
            method?.invoke(tm, emptyList<RadioAccessSpecifier>())
            messages += "已清除 Band/频点选择"
        }.onFailure { messages += "清除频点失败: ${it.message}" }
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                tm.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    RatMask.ALL,
                )
                messages += "已恢复所有制式"
            }
        }.onFailure { messages += "恢复制式失败: ${it.message}" }
        runCatching { tm.setNetworkSelectionModeAutomatic() }
        return messages.joinToString("；")
    }

    @SuppressLint("MissingPermission")
    override fun setNetworkAutomatic(subId: Int): String {
        return try {
            tm(subId).setNetworkSelectionModeAutomatic()
            "已切换为自动选网"
        } catch (t: Throwable) {
            "自动选网失败: ${t.message}"
        }
    }

    override fun lockPci(subId: Int, pci: Int, arfcn: Int, rat: String): String {
        if (Os.getuid() != 0) {
            return "PCI 锁定需要 Shizuku 以 Root 身份运行（当前 uid=${Os.getuid()}）。ADB 模式只能锁制式/Band/频点。"
        }
        val at = when {
            rat.contains("NR", true) -> "AT+QNWLOCK=\"common/5g\",1,$pci,$arfcn"
            rat.contains("LTE", true) || rat.contains("4G", true) ->
                "AT+QNWLOCK=\"common/4g\",1,$pci,$arfcn"
            else -> "AT+QNWLOCK=\"common/4g\",1,$pci,$arfcn"
        }
        val devices = listOf("/dev/smd11", "/dev/smd8", "/dev/smd7", "/dev/smd0")
        val existing = devices.filter { File(it).exists() }
        if (existing.isEmpty()) {
            return "已处于 Root，但没找到调制解调器 AT 口（/dev/smd*）。这台机器可能不是高通，或节点路径不同。"
        }
        val errors = mutableListOf<String>()
        existing.forEach { path ->
            val ok = runCatching {
                File(path).outputStream().use { out ->
                    out.write("$at\r".toByteArray())
                    out.flush()
                }
            }
            if (ok.isSuccess) return "已向 $path 写入 $at"
            errors += "${path}: ${ok.exceptionOrNull()?.message}"
        }
        return "PCI AT 指令失败: ${errors.joinToString()}"
    }

    private fun tm(subId: Int): TelephonyManager {
        val ctx = appContext ?: currentApp()
        val base = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return if (subId != Int.MAX_VALUE) {
            runCatching { base.createForSubscriptionId(subId) }.getOrDefault(base)
        } else {
            base
        }
    }

    private fun currentApp(): Context {
        val thread = Class.forName("android.app.ActivityThread")
        return thread.getMethod("currentApplication").invoke(null) as Context
    }

    private fun preferredFromMask(types: Long): Int {
        return when {
            types and RatMask.NR != 0L && types and RatMask.LTE != 0L -> 33
            types and RatMask.NR != 0L -> 32
            types and RatMask.LTE != 0L -> 22
            types and RatMask.WCDMA != 0L -> 20
            types and RatMask.GSM != 0L -> 1
            else -> 22
        }
    }

    companion object {
        private const val TAG = "NoirPrivTel"
    }
}
