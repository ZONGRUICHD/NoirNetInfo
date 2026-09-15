package com.zongruichd.noirnetinfo.data

import kotlin.math.roundToInt

object BandLookup {
    data class RadioBand(
        val name: String,
        val frequencyMhz: Double?,
    )

    fun lte(earfcn: Int): RadioBand? {
        val band = LTE.firstOrNull { earfcn in it.nMin..it.nMax } ?: return null
        val mhz = band.fDlLow + 0.1 * (earfcn - band.nOffs)
        return RadioBand("B${band.id} LTE", (mhz * 10).roundToInt() / 10.0)
    }

    fun nr(nrarfcn: Int): RadioBand {
        val mhz = nrFrequencyMhz(nrarfcn)
        val candidates = NR.filter { mhz != null && mhz in it.fMin..it.fMax }
        val name = candidates.joinToString(" / ") { "n${it.id}" }
            .takeIf { it.isNotEmpty() }?.let { "$it（频率推测）" } ?: "NR（频段未知）"
        return RadioBand(name, mhz)
    }

    fun gsm(arfcn: Int): RadioBand? = when (arfcn) {
        in 0..124, in 975..1023 -> RadioBand("GSM 900", null)
        in 128..251 -> RadioBand("GSM 850", null)
        in 512..810 -> RadioBand("PCS 1900 / DCS 1800（待确认）", null)
        in 811..885 -> RadioBand("DCS 1800", null)
        else -> RadioBand("GSM", null)
    }

    fun wcdma(uarfcn: Int): RadioBand? = when (uarfcn) {
        in 10562..10838 -> RadioBand("B1 WCDMA 2100", 2112.4 + (uarfcn - 10562) * 0.2)
        in 9662..9938 -> RadioBand("B2 WCDMA 1900", 1932.4 + (uarfcn - 9662) * 0.2)
        in 4357..4458 -> RadioBand("B5 WCDMA 850", uarfcn * 0.2)
        in 2937..3088 -> RadioBand("B8 WCDMA 900", 340.0 + uarfcn * 0.2)
        in 1537..1738 -> RadioBand("B4 WCDMA 1700", 1805.0 + uarfcn * 0.2)
        in 1162..1513 -> RadioBand("B3 WCDMA 1800", 1575.0 + uarfcn * 0.2)
        else -> RadioBand("WCDMA", null)
    }

    private fun nrFrequencyMhz(nrarfcn: Int): Double? = when (nrarfcn) {
        in 0..599_999 -> nrarfcn * 0.005
        in 600_000..2_016_666 -> 3000.0 + (nrarfcn - 600_000) * 0.015
        in 2_016_667..3_279_165 -> 24250.08 + (nrarfcn - 2_016_667) * 0.060
        else -> null
    }

    private data class LteDef(val id: Int, val nOffs: Int, val nMin: Int, val nMax: Int, val fDlLow: Double)
    private data class NrDef(val id: Int, val fMin: Double, val fMax: Double)

    private val LTE = listOf(
        LteDef(1, 0, 0, 599, 2110.0),
        LteDef(2, 600, 600, 1199, 1930.0),
        LteDef(3, 1200, 1200, 1949, 1805.0),
        LteDef(4, 1950, 1950, 2399, 2110.0),
        LteDef(5, 2400, 2400, 2649, 869.0),
        LteDef(7, 2750, 2750, 3449, 2620.0),
        LteDef(8, 3450, 3450, 3799, 925.0),
        LteDef(11, 4750, 4750, 4949, 1475.9),
        LteDef(12, 5010, 5010, 5179, 729.0),
        LteDef(13, 5180, 5180, 5279, 746.0),
        LteDef(17, 5730, 5730, 5849, 734.0),
        LteDef(18, 5850, 5850, 5999, 860.0),
        LteDef(19, 6000, 6000, 6149, 875.0),
        LteDef(20, 6150, 6150, 6449, 791.0),
        LteDef(21, 6450, 6450, 6599, 1495.9),
        LteDef(25, 8040, 8040, 8689, 1930.0),
        LteDef(26, 8690, 8690, 9039, 859.0),
        LteDef(28, 9210, 9210, 9659, 758.0),
        LteDef(31, 9870, 9870, 9919, 462.5),
        LteDef(32, 9920, 9920, 10359, 1452.0),
        LteDef(34, 36200, 36200, 36349, 2010.0),
        LteDef(38, 37750, 37750, 38249, 2570.0),
        LteDef(39, 38250, 38250, 38649, 1880.0),
        LteDef(40, 38650, 38650, 39649, 2300.0),
        LteDef(41, 39650, 39650, 41589, 2496.0),
        LteDef(42, 41590, 41590, 43589, 3400.0),
        LteDef(43, 43590, 43590, 45589, 3600.0),
        LteDef(66, 66436, 66436, 67335, 2110.0),
        LteDef(71, 68586, 68586, 68935, 617.0),
    )

    private val NR = listOf(
        NrDef(1, 2110.0, 2170.0),
        NrDef(3, 1805.0, 1880.0),
        NrDef(5, 869.0, 894.0),
        NrDef(7, 2620.0, 2690.0),
        NrDef(8, 925.0, 960.0),
        NrDef(20, 791.0, 821.0),
        NrDef(28, 758.0, 803.0),
        NrDef(38, 2570.0, 2620.0),
        NrDef(40, 2300.0, 2400.0),
        NrDef(41, 2496.0, 2690.0),
        NrDef(77, 3300.0, 4200.0),
        NrDef(78, 3300.0, 3800.0),
        NrDef(79, 4400.0, 5000.0),
        NrDef(257, 26500.0, 29500.0),
        NrDef(258, 24250.0, 27500.0),
    )
}
