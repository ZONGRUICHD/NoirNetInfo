package com.zongruichd.noirnetinfo.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

object CellularCollector {

    @SuppressLint("MissingPermission")
    fun collect(context: Context, phoneGranted: Boolean, locationGranted: Boolean): Pair<CellularDetails?, List<CellularSlot>> {
        val app = context.applicationContext
        val tm = app.getSystemService(TelephonyManager::class.java)
        val overview = tm?.let { overview(it, phoneGranted) }
        if (!phoneGranted) return overview to emptyList()
        val sm = app.getSystemService(SubscriptionManager::class.java) ?: return overview to emptyList()
        val subs = runCatching { sm.activeSubscriptionInfoList }.getOrNull().orEmpty()
        if (subs.isEmpty()) return overview to emptyList()

        val defaultData = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }.getOrNull()
        val defaultVoice = runCatching { SubscriptionManager.getDefaultVoiceSubscriptionId() }.getOrNull()
        val defaultSms = runCatching { SubscriptionManager.getDefaultSmsSubscriptionId() }.getOrNull()

        val slots = subs.sortedBy { it.simSlotIndex }.map { info ->
            val subTm = runCatching { tm?.createForSubscriptionId(info.subscriptionId) }.getOrNull() ?: tm
            val mcc = if (Build.VERSION.SDK_INT >= 29) info.mccString else @Suppress("DEPRECATION") info.mcc.takeIf { it != 0 }?.toString()
            val mnc = if (Build.VERSION.SDK_INT >= 29) info.mncString else @Suppress("DEPRECATION") info.mnc.takeIf { it != 0 }?.toString()
            val cells = if (locationGranted && subTm != null) {
                runCatching { subTm.allCellInfo }.getOrNull().orEmpty().mapNotNull { toRecord(it) }
            } else {
                emptyList()
            }
            val serving = cells.filter {
                it.connectionStatus == CellRole.PRIMARY ||
                    it.connectionStatus == CellRole.SECONDARY ||
                    it.registered
            }.distinctBy { it.rat + it.identity.toString() }
            val neighbors = cells.filter { cell ->
                serving.none { it.identity == cell.identity && it.rat == cell.rat }
            }

            CellularSlot(
                slotIndex = info.simSlotIndex,
                subscriptionId = info.subscriptionId,
                isDefaultData = info.subscriptionId == defaultData,
                isDefaultVoice = info.subscriptionId == defaultVoice,
                isDefaultSms = info.subscriptionId == defaultSms,
                sim = SimCardInfo(
                    displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() },
                    carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() },
                    iccid = info.iccId?.takeIf { it.isNotBlank() },
                    number = info.number?.takeIf { it.isNotBlank() },
                    state = TelephonyLabels.simState(subTm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN),
                    embedded = if (Build.VERSION.SDK_INT >= 28) info.isEmbedded else null,
                    opportunistic = if (Build.VERSION.SDK_INT >= 29) info.isOpportunistic else null,
                    countryIso = info.countryIso?.takeIf { it.isNotBlank() },
                    cardId = if (Build.VERSION.SDK_INT >= 29) info.cardId.takeIf { it >= 0 }?.toString() else null,
                    mcc = mcc,
                    mnc = mnc,
                    portIndex = if (Build.VERSION.SDK_INT >= 33) info.portIndex.takeIf { it >= 0 } else null,
                ),
                operator = operator(subTm, phoneGranted),
                servingCells = serving.sortedBy { it.connectionStatus.ordinal },
                neighborCells = neighbors,
            )
        }
        return overview to slots
    }

    @SuppressLint("MissingPermission")
    private fun overview(tm: TelephonyManager, phoneGranted: Boolean): CellularDetails {
        val operatorName = tm.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: tm.simOperatorName?.takeIf { it.isNotBlank() }
        val numeric = tm.networkOperator?.takeIf { it.isNotBlank() }
            ?: tm.simOperator?.takeIf { it.isNotBlank() }
        val networkType = if (phoneGranted) {
            runCatching { TelephonyLabels.networkTypeName(tm.dataNetworkType) }.getOrNull()
        } else null
        return CellularDetails(
            operatorName = operatorName,
            operatorNumeric = numeric,
            networkType = networkType,
            dataEnabled = runCatching { tm.isDataEnabled }.getOrNull(),
            roaming = runCatching { tm.isNetworkRoaming }.getOrNull(),
            phoneType = TelephonyLabels.phoneType(tm.phoneType),
        )
    }

    @SuppressLint("MissingPermission")
    private fun operator(tm: TelephonyManager?, phoneGranted: Boolean): OperatorInfo {
        if (tm == null) return OperatorInfo(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
        val ss = if (phoneGranted) runCatching { tm.serviceState }.getOrNull() else null
        val display = if (phoneGranted && Build.VERSION.SDK_INT >= 30) {
            runCatching { tm.telephonyDisplayInfo }.getOrNull()
        } else null
        val dataType = if (phoneGranted) runCatching { tm.dataNetworkType }.getOrNull() else null
        val voiceType = if (phoneGranted) runCatching { tm.voiceNetworkType }.getOrNull() else null
        val override = display?.let { overrideName(it.overrideNetworkType) }
        val generation = generationOf(dataType, override, ss)
        val ca = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { ss?.isUsingCarrierAggregation }.getOrNull()
        } else {
            override?.contains("CA") == true
        }
        return OperatorInfo(
            networkOperatorName = tm.networkOperatorName?.takeIf { it.isNotBlank() },
            simOperatorName = tm.simOperatorName?.takeIf { it.isNotBlank() },
            networkNumeric = tm.networkOperator?.takeIf { it.isNotBlank() },
            simNumeric = tm.simOperator?.takeIf { it.isNotBlank() },
            roaming = runCatching { tm.isNetworkRoaming }.getOrNull(),
            dataRoaming = if (Build.VERSION.SDK_INT >= 29) runCatching { tm.isDataRoamingEnabled }.getOrNull() else null,
            dataEnabled = runCatching { tm.isDataEnabled }.getOrNull(),
            phoneType = TelephonyLabels.phoneType(tm.phoneType),
            dataNetworkType = dataType?.let { TelephonyLabels.networkTypeName(it) },
            voiceNetworkType = voiceType?.let { TelephonyLabels.networkTypeName(it) },
            networkClass = dataType?.let { TelephonyLabels.networkClass(it) },
            generation = generation,
            serviceState = ss?.state?.let { TelephonyLabels.serviceState(it) },
            nrState = ss?.let { nrStateName(it) },
            nrFrequencyRange = ss?.let { nrRangeName(it) },
            displayOverride = override,
            carrierAggregation = ca,
        )
    }

    private fun generationOf(dataType: Int?, override: String?, ss: ServiceState?): String {
        val nr = nrStateName(ss)
        return when {
            override?.contains("5G SA") == true || nr == "5G SA 已连接" -> "5G SA"
            override?.contains("5G NSA") == true || nr == "5G NSA 已连接" -> "5G NSA"
            dataType == TelephonyManager.NETWORK_TYPE_NR -> "5G"
            override?.contains("CA") == true || dataType == TelephonyManager.NETWORK_TYPE_LTE ->
                if (override?.contains("CA") == true) "4G+" else "4G"
            dataType != null -> TelephonyLabels.networkClass(dataType)
            else -> "未知"
        }
    }

    private fun overrideName(type: Int): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return when (type) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE -> null
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE CA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE Advanced Pro"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G SA / Advanced"
            else -> "override $type"
        }
    }

    private fun nrStateName(ss: ServiceState?): String? {
        if (ss == null || Build.VERSION.SDK_INT < 29) return null
        return when (ss.nrState) {
            ServiceState.NR_STATE_NONE -> "无 5G"
            ServiceState.NR_STATE_RESTRICTED -> "5G 受限"
            ServiceState.NR_STATE_NOT_RESTRICTED -> "5G 可连接"
            ServiceState.NR_STATE_CONNECTED -> "5G 已连接"
            else -> null
        }
    }

    private fun nrRangeName(ss: ServiceState?): String? {
        if (ss == null || Build.VERSION.SDK_INT < 29) return null
        return when (ss.nrFrequencyRange) {
            ServiceState.FREQUENCY_RANGE_LOW -> "FR1 低频"
            ServiceState.FREQUENCY_RANGE_MID -> "FR1 中频"
            ServiceState.FREQUENCY_RANGE_HIGH -> "FR1 高频"
            ServiceState.FREQUENCY_RANGE_MMWAVE -> "FR2 毫米波"
            else -> null
        }
    }

    private fun toRecord(info: CellInfo): CellRecord? {
        val role = roleOf(info)
        return when {
            info is CellInfoLte -> lte(info, role)
            info is CellInfoGsm -> gsm(info, role)
            info is CellInfoWcdma -> wcdma(info, role)
            info is CellInfoCdma -> cdma(info, role)
            Build.VERSION.SDK_INT >= 29 && info is CellInfoTdscdma -> tdscdma(info, role)
            Build.VERSION.SDK_INT >= 29 && info is CellInfoNr -> nr(info, role)
            else -> null
        }
    }

    private fun roleOf(info: CellInfo): CellRole {
        if (Build.VERSION.SDK_INT < 28) {
            return if (info.isRegistered) CellRole.PRIMARY else CellRole.NEIGHBOR
        }
        return when (info.cellConnectionStatus) {
            CellInfo.CONNECTION_PRIMARY_SERVING -> CellRole.PRIMARY
            CellInfo.CONNECTION_SECONDARY_SERVING -> CellRole.SECONDARY
            CellInfo.CONNECTION_NONE -> if (info.isRegistered) CellRole.PRIMARY else CellRole.NEIGHBOR
            else -> if (info.isRegistered) CellRole.UNKNOWN else CellRole.NEIGHBOR
        }
    }

    private fun lte(info: CellInfoLte, role: CellRole): CellRecord {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val earfcn = id.earfcn.takeIf { it > 0 && it != CellInfo.UNAVAILABLE }
        val band = earfcn?.let { BandLookup.lte(it) }
        val rsrp = ss.rsrp.avail()
        val rsrq = ss.rsrq.avail()
        val sinr = ss.rssnr.avail()
        val rssi = if (Build.VERSION.SDK_INT >= 29) ss.rssi.avail() else null
        val identity = linkedMapOf<String, String>()
        put(identity, "MCC", id.mccStringCompat())
        put(identity, "MNC", id.mncStringCompat())
        put(identity, "TAC", id.tac.avail())
        put(identity, "PCI", id.pci.avail())
        put(identity, "ECI", id.ci.avail())
        put(identity, "EARFCN", earfcn)
        if (Build.VERSION.SDK_INT >= 28) put(identity, "带宽 kHz", id.bandwidth.takeIf { it > 0 })
        if (Build.VERSION.SDK_INT >= 30) put(identity, "Bands", id.bands.takeIf { it.isNotEmpty() }?.joinToString { "B$it" })
        val radio = linkedMapOf<String, String>()
        put(radio, "RSRP", rsrp, " dBm")
        put(radio, "RSRQ", rsrq, " dB")
        put(radio, "SINR", sinr, " dB")
        put(radio, "RSSI", rssi, " dBm")
        put(radio, "CQI", ss.cqi.avail())
        put(radio, "dBm", ss.dbm.avail())
        put(radio, "ASU", ss.asuLevel.takeIf { it >= 0 })
        put(radio, "电平", ss.level.takeIf { it >= 0 })
        if (Build.VERSION.SDK_INT >= 34) put(radio, "TA", ss.timingAdvance.avail())
        return CellRecord(
            rat = "LTE",
            registered = info.isRegistered,
            connectionStatus = role,
            mcc = id.mccStringCompat(),
            mnc = id.mncStringCompat(),
            identity = identity,
            radio = radio,
            rsrp = rsrp,
            rsrq = rsrq,
            sinr = sinr,
            rssi = rssi,
            dbm = ss.dbm.avail(),
            asu = ss.asuLevel.takeIf { it >= 0 },
            level = ss.level.takeIf { it >= 0 },
            band = band?.name,
            frequencyMhz = band?.frequencyMhz,
            arfcn = earfcn,
        )
    }

    private fun nr(info: CellInfoNr, role: CellRole): CellRecord {
        val id = info.cellIdentity as CellIdentityNr
        val ss = info.cellSignalStrength as CellSignalStrengthNr
        val arfcn = id.nrarfcn.takeIf { it > 0 && it != CellInfo.UNAVAILABLE }
        val band = arfcn?.let { BandLookup.nr(it) }
        val rsrp = ss.ssRsrp.avail()
        val rsrq = ss.ssRsrq.avail()
        val sinr = ss.ssSinr.avail()
        val identity = linkedMapOf<String, String>()
        put(identity, "MCC", id.mccString)
        put(identity, "MNC", id.mncString)
        put(identity, "TAC", id.tac.avail())
        put(identity, "PCI", id.pci.avail())
        put(identity, "NCI", id.nci.availLong())
        put(identity, "NR-ARFCN", arfcn)
        if (Build.VERSION.SDK_INT >= 30) put(identity, "Bands", id.bands.takeIf { it.isNotEmpty() }?.joinToString { "n$it" })
        val radio = linkedMapOf<String, String>()
        put(radio, "SS-RSRP", rsrp, " dBm")
        put(radio, "SS-RSRQ", rsrq, " dB")
        put(radio, "SS-SINR", sinr, " dB")
        put(radio, "CSI-RSRP", ss.csiRsrp.avail(), " dBm")
        put(radio, "CSI-RSRQ", ss.csiRsrq.avail(), " dB")
        put(radio, "CSI-SINR", ss.csiSinr.avail(), " dB")
        put(radio, "dBm", ss.dbm.avail())
        put(radio, "ASU", ss.asuLevel.takeIf { it >= 0 })
        put(radio, "电平", ss.level.takeIf { it >= 0 })
        return CellRecord(
            rat = "NR 5G",
            registered = info.isRegistered,
            connectionStatus = role,
            mcc = id.mccString,
            mnc = id.mncString,
            identity = identity,
            radio = radio,
            rsrp = rsrp,
            rsrq = rsrq,
            sinr = sinr,
            dbm = ss.dbm.avail(),
            asu = ss.asuLevel.takeIf { it >= 0 },
            level = ss.level.takeIf { it >= 0 },
            band = band?.name,
            frequencyMhz = band?.frequencyMhz,
            arfcn = arfcn,
        )
    }

    private fun gsm(info: CellInfoGsm, role: CellRole): CellRecord {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val arfcn = if (Build.VERSION.SDK_INT >= 24) id.arfcn.takeIf { it >= 0 && it != CellInfo.UNAVAILABLE } else null
        val band = arfcn?.let { BandLookup.gsm(it) }
        val identity = linkedMapOf<String, String>()
        put(identity, "MCC", id.mccStringCompat())
        put(identity, "MNC", id.mncStringCompat())
        put(identity, "LAC", id.lac.avail())
        put(identity, "CI", id.cid.avail())
        put(identity, "BSIC", id.bsic.avail())
        put(identity, "ARFCN", arfcn)
        val radio = linkedMapOf<String, String>()
        val rxl = if (Build.VERSION.SDK_INT >= 30) ss.rssi.avail() else ss.dbm.avail()
        put(radio, "RXLEV", rxl, " dBm")
        put(radio, "dBm", ss.dbm.avail())
        put(radio, "ASU", ss.asuLevel.takeIf { it >= 0 })
        put(radio, "电平", ss.level.takeIf { it >= 0 })
        if (Build.VERSION.SDK_INT >= 26) put(radio, "TA", ss.timingAdvance.avail())
        put(radio, "BER", ss.bitErrorRate.takeIf { it >= 0 })
        return CellRecord(
            rat = "GSM",
            registered = info.isRegistered,
            connectionStatus = role,
            mcc = id.mccStringCompat(),
            mnc = id.mncStringCompat(),
            identity = identity,
            radio = radio,
            rssi = rxl,
            dbm = ss.dbm.avail(),
            asu = ss.asuLevel.takeIf { it >= 0 },
            level = ss.level.takeIf { it >= 0 },
            band = band?.name,
            arfcn = arfcn,
        )
    }

    private fun wcdma(info: CellInfoWcdma, role: CellRole): CellRecord {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val uarfcn = if (Build.VERSION.SDK_INT >= 24) id.uarfcn.takeIf { it > 0 && it != CellInfo.UNAVAILABLE } else null
        val band = uarfcn?.let { BandLookup.wcdma(it) }
        val identity = linkedMapOf<String, String>()
        put(identity, "MCC", id.mccStringCompat())
        put(identity, "MNC", id.mncStringCompat())
        put(identity, "LAC", id.lac.avail())
        put(identity, "CI", id.cid.avail())
        put(identity, "PSC", id.psc.avail())
        put(identity, "UARFCN", uarfcn)
        val radio = linkedMapOf<String, String>()
        val rxl = ss.dbm.avail()
        put(radio, "RSCP", rxl, " dBm")
        if (Build.VERSION.SDK_INT >= 30) put(radio, "Ec/No", ss.ecNo.avail(), " dB")
        put(radio, "ASU", ss.asuLevel.takeIf { it >= 0 })
        put(radio, "电平", ss.level.takeIf { it >= 0 })
        return CellRecord(
            rat = "WCDMA",
            registered = info.isRegistered,
            connectionStatus = role,
            mcc = id.mccStringCompat(),
            mnc = id.mncStringCompat(),
            identity = identity,
            radio = radio,
            dbm = rxl,
            asu = ss.asuLevel.takeIf { it >= 0 },
            level = ss.level.takeIf { it >= 0 },
            band = band?.name,
            frequencyMhz = band?.frequencyMhz,
            arfcn = uarfcn,
        )
    }

    private fun tdscdma(info: CellInfoTdscdma, role: CellRole): CellRecord {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val uarfcn = id.uarfcn.takeIf { it > 0 && it != CellInfo.UNAVAILABLE }
        val identity = linkedMapOf<String, String>()
        put(identity, "MCC", id.mccString)
        put(identity, "MNC", id.mncString)
        put(identity, "LAC", id.lac.avail())
        put(identity, "CI", id.cid.avail())
        put(identity, "CPID", id.cpid.avail())
        put(identity, "UARFCN", uarfcn)
        val radio = linkedMapOf<String, String>()
        put(radio, "RSCP", ss.dbm.avail(), " dBm")
        put(radio, "ASU", ss.asuLevel.takeIf { it >= 0 })
        put(radio, "电平", ss.level.takeIf { it >= 0 })
        return CellRecord(
            rat = "TD-SCDMA",
            registered = info.isRegistered,
            connectionStatus = role,
            mcc = id.mccString,
            mnc = id.mncString,
            identity = identity,
            radio = radio,
            dbm = ss.dbm.avail(),
            asu = ss.asuLevel.takeIf { it >= 0 },
            level = ss.level.takeIf { it >= 0 },
            band = uarfcn?.let { "TD-SCDMA" },
            arfcn = uarfcn,
        )
    }

    private fun cdma(info: CellInfoCdma, role: CellRole): CellRecord {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val identity = linkedMapOf<String, String>()
        put(identity, "SID", id.systemId.takeIf { it >= 0 })
        put(identity, "NID", id.networkId.takeIf { it >= 0 })
        put(identity, "BID", id.basestationId.takeIf { it >= 0 })
        val radio = linkedMapOf<String, String>()
        put(radio, "CDMA dBm", ss.cdmaDbm.takeIf { it != Int.MAX_VALUE })
        put(radio, "CDMA Ec/Io", ss.cdmaEcio.takeIf { it != Int.MAX_VALUE }, " dB")
        put(radio, "EVDO dBm", ss.evdoDbm.takeIf { it != Int.MAX_VALUE })
        put(radio, "EVDO Ec/Io", ss.evdoEcio.takeIf { it != Int.MAX_VALUE }, " dB")
        put(radio, "EVDO SNR", ss.evdoSnr.takeIf { it >= 0 })
        put(radio, "dBm", ss.dbm.avail())
        put(radio, "电平", ss.level.takeIf { it >= 0 })
        return CellRecord(
            rat = "CDMA",
            registered = info.isRegistered,
            connectionStatus = role,
            mcc = null,
            mnc = null,
            identity = identity,
            radio = radio,
            dbm = ss.dbm.avail(),
            level = ss.level.takeIf { it >= 0 },
        )
    }

    private fun Int.avail(): Int? =
        takeUnless { this == CellInfo.UNAVAILABLE || this == Int.MAX_VALUE || this == Int.MIN_VALUE || this == Integer.MAX_VALUE }

    private fun Long.availLong(): Long? =
        takeUnless { this == CellInfo.UNAVAILABLE.toLong() || this == Long.MAX_VALUE || this < 0 }

    private fun CellIdentityLte.mccStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= 28) mccString else @Suppress("DEPRECATION") mcc.takeIf { it > 0 }?.toString()

    private fun CellIdentityLte.mncStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= 28) mncString else @Suppress("DEPRECATION") mnc.takeIf { it >= 0 }?.toString()

    private fun CellIdentityGsm.mccStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= 28) mccString else @Suppress("DEPRECATION") mcc.takeIf { it > 0 }?.toString()

    private fun CellIdentityGsm.mncStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= 28) mncString else @Suppress("DEPRECATION") mnc.takeIf { it >= 0 }?.toString()

    private fun CellIdentityWcdma.mccStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= 28) mccString else @Suppress("DEPRECATION") mcc.takeIf { it > 0 }?.toString()

    private fun CellIdentityWcdma.mncStringCompat(): String? =
        if (Build.VERSION.SDK_INT >= 28) mncString else @Suppress("DEPRECATION") mnc.takeIf { it >= 0 }?.toString()

    private fun put(map: MutableMap<String, String>, key: String, value: Any?, suffix: String = "") {
        when (value) {
            null -> return
            is String -> if (value.isNotBlank()) map[key] = value + suffix
            else -> map[key] = value.toString() + suffix
        }
    }
}
