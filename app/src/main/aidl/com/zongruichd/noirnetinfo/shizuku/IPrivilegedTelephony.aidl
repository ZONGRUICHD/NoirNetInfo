package com.zongruichd.noirnetinfo.shizuku;

interface IPrivilegedTelephony {
    void destroy() = 16777114;

    String status() = 1;

    long getAllowedNetworkTypes(int subId) = 2;

    String setAllowedNetworkTypes(int subId, long types) = 3;

    String setSelection(int subId, in int[] rans, in int[] bandCounts, in int[] bands, int channelRan, in int[] channels) = 4;

    String clearSelection(int subId) = 5;

    String setNetworkAutomatic(int subId) = 6;

    String lockPci(int subId, int pci, int arfcn, String rat) = 7;
}
