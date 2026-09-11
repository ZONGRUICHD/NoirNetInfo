# Release APK is not minified; keep file for future R8 enablement.
-keep class com.zongruichd.noirnetinfo.shizuku.** { *; }
-keepclassmembers class com.zongruichd.noirnetinfo.shizuku.PrivilegedTelephonyService {
    public <init>();
    public <init>(android.content.Context);
}

