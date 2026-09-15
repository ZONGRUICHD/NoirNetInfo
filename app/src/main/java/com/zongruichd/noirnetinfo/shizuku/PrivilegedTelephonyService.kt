package com.zongruichd.noirnetinfo.shizuku

import androidx.annotation.Keep
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.system.Os
import android.telephony.RadioAccessSpecifier
import android.telephony.TelephonyManager
import android.util.Log

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
        if (Build.VERSION.SDK_INT >= 33) {
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
            if (Build.VERSION.SDK_INT >= 33) {
                tm.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    types,
                )
                "已设置制式掩码 $types"
            } else {
                "当前系统不支持公开制式设置接口（需要 Android 13+）"
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
        if (Build.VERSION.SDK_INT < 28) return "Band/频点接口需要 Android 9+"
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
            "已提交 Band/频点选择请求（${specifiers.size} 组）；需观察小区信息确认是否生效"
        } catch (t: Throwable) {
            "Band/频点锁定失败: ${t.message}"
        }
    }

    @SuppressLint("MissingPermission")
    override fun clearSelection(subId: Int): String {
        if (Build.VERSION.SDK_INT < 28) return "自动选网需要 Android 9+"
        val tm = tm(subId)
        val messages = mutableListOf<String>()
        runCatching {
            val method = TelephonyManager::class.java.methods.firstOrNull {
                it.name == "setSystemSelectionChannels" && it.parameterTypes.size == 1
            }
            (method ?: error("当前系统没有 setSystemSelectionChannels")).invoke(tm, emptyList<RadioAccessSpecifier>())
            messages += "已清除 Band/频点选择"
        }.onFailure { messages += "清除频点失败: ${it.message}" }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                tm.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    RatMask.ALL,
                )
                messages += "已恢复所有制式"
            }
        }.onFailure { messages += "恢复制式失败: ${it.message}" }
        runCatching { tm.setNetworkSelectionModeAutomatic() }
            .onSuccess { messages += "已切换自动选网" }
            .onFailure { messages += "自动选网失败: ${it.message}" }
        return messages.joinToString("；")
    }

    @SuppressLint("MissingPermission")
    override fun setNetworkAutomatic(subId: Int): String {
        if (Build.VERSION.SDK_INT < 28) return "自动选网需要 Android 9+"
        return try {
            tm(subId).setNetworkSelectionModeAutomatic()
            "已切换为自动选网"
        } catch (t: Throwable) {
            "自动选网失败: ${t.message}"
        }
    }

    override fun lockPci(subId: Int, pci: Int, arfcn: Int, rat: String): String {
        return "此设备尚无经过验证的 PCI 锁定适配；Root 授权不能保证支持。未向调制解调器写入指令。"
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

    companion object {
        private const val TAG = "NoirPrivTel"
    }
}
