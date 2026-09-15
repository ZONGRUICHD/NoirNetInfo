package com.zongruichd.noirnetinfo.data

import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test fun lteIncludesZeroAndRejectsUnknownChannels() {
        assertEquals(2110.0, BandLookup.lte(0)!!.frequencyMhz!!, 0.00001)
        assertEquals("B1 LTE", BandLookup.lte(599)!!.name)
        assertNull(BandLookup.lte(-1))
    }
    @Test fun nrPreservesRasterPrecisionAndOverlappingBands() {
        val band = BandLookup.nr(630000)
        assertEquals(3450.0, band.frequencyMhz!!, 0.00001)
        assertTrue(band.name.contains("n77"))
        assertTrue(band.name.contains("n78"))
        assertTrue(band.name.contains("推测"))
        assertEquals(24250.08, BandLookup.nr(2016667).frequencyMhz!!, 0.00001)
        assertNull(BandLookup.nr(3279166).frequencyMhz)
    }
    @Test fun wcdmaUsesCorrectBandAndDownlinkOffset() {
        assertTrue(BandLookup.wcdma(1162)!!.name.startsWith("B3"))
        assertEquals(1807.4, BandLookup.wcdma(1162)!!.frequencyMhz!!, 0.00001)
        assertEquals(871.4, BandLookup.wcdma(4357)!!.frequencyMhz!!, 0.00001)
        assertEquals(927.4, BandLookup.wcdma(2937)!!.frequencyMhz!!, 0.00001)
        assertEquals(2112.4, BandLookup.wcdma(1537)!!.frequencyMhz!!, 0.00001)
    }
    @Test fun gsmDoesNotGuessOverlappingBands() {
        assertTrue(BandLookup.gsm(512)!!.name.contains("PCS"))
        assertEquals("DCS 1800", BandLookup.gsm(811)!!.name)
    }
    @Test fun classifiesSharedPrivateAndSpecialAddresses() {
        assertEquals(IpScope.SHARED, classifyIp("100.64.0.1"))
        assertEquals(IpScope.SHARED, classifyIp("100.127.255.254"))
        assertEquals(IpScope.PRIVATE, classifyIp("172.31.0.1"))
        assertEquals(IpScope.GLOBAL, classifyIp("8.8.8.8"))
        assertEquals(IpScope.OTHER, classifyIp("0.0.0.0"))
        assertEquals(IpScope.OTHER, classifyIp("192.0.2.1"))
        assertEquals(IpScope.OTHER, classifyIp("example.com"))
    }
    @Test fun recognizesEntireIpv6LinkLocalPrefix() {
        assertEquals(IpScope.LINK_LOCAL, classifyIp("febf::1%wlan0"))
        assertEquals(IpScope.ULA, classifyIp("fd00::1"))
        assertEquals(IpScope.LOOPBACK, classifyIp("0:0:0:0:0:0:0:1"))
        assertEquals(IpScope.OTHER, classifyIp("::"))
        assertEquals(IpScope.OTHER, classifyIp("2001:db8::1"))
        assertEquals(IpScope.GLOBAL, classifyIp("2606:4700:4700::1111"))
    }
}
