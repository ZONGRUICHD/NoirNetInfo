package com.zongruichd.noirnetinfo.data

import android.telephony.TelephonyManager

object TelephonyLabels {
    fun phoneType(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        TelephonyManager.PHONE_TYPE_NONE -> "无"
        else -> "未知"
    }

    fun simState(state: Int): String = when (state) {
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

    fun networkTypeName(type: Int): String = when (type) {
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

    fun networkClass(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM,
        -> "2G"
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        -> "3G"
        TelephonyManager.NETWORK_TYPE_LTE,
        TelephonyManager.NETWORK_TYPE_IWLAN,
        -> "4G"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        else -> "未知"
    }

    fun serviceState(state: Int): String = when (state) {
        android.telephony.ServiceState.STATE_IN_SERVICE -> "已注册"
        android.telephony.ServiceState.STATE_OUT_OF_SERVICE -> "无服务"
        android.telephony.ServiceState.STATE_EMERGENCY_ONLY -> "仅紧急呼叫"
        android.telephony.ServiceState.STATE_POWER_OFF -> "飞行模式/关机"
        else -> "未知"
    }
}
