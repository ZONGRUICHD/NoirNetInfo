package com.zongruichd.noirnetinfo.shizuku

import android.os.Build
import android.telephony.TelephonyManager

object RatMask {
    val GSM: Long = if (Build.VERSION.SDK_INT >= 30) {
        TelephonyManager.NETWORK_TYPE_BITMASK_GSM or
            TelephonyManager.NETWORK_TYPE_BITMASK_GPRS or
            TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
    } else {
        0
    }

    val WCDMA: Long = if (Build.VERSION.SDK_INT >= 30) {
        TelephonyManager.NETWORK_TYPE_BITMASK_UMTS or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSPA or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP or
            TelephonyManager.NETWORK_TYPE_BITMASK_TD_SCDMA
    } else {
        0
    }

    val LTE: Long = if (Build.VERSION.SDK_INT >= 30) {
        TelephonyManager.NETWORK_TYPE_BITMASK_LTE or
            TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA
    } else {
        0
    }

    val NR: Long = if (Build.VERSION.SDK_INT >= 30) {
        TelephonyManager.NETWORK_TYPE_BITMASK_NR
    } else {
        0
    }

    val CDMA: Long = if (Build.VERSION.SDK_INT >= 30) {
        TelephonyManager.NETWORK_TYPE_BITMASK_CDMA or
            TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT or
            TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_0 or
            TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_A or
            TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_B or
            TelephonyManager.NETWORK_TYPE_BITMASK_EHRPD
    } else {
        0
    }

    val ALL: Long = GSM or WCDMA or LTE or NR or CDMA
}
