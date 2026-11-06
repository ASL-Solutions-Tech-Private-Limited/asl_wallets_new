# Strip all android.util.Log calls from release builds (no data in logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
    public static boolean isLoggable(java.lang.String, int);
}
# Keep line numbers for crash reports, hide source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- PayU CheckoutPro (Module 4 · Add money)
# PayU's own screens use reflection; keep them whole.
-keep class com.payu.** { *; }
-dontwarn com.payu.**
# Google Pay in-app client: optional plug-in PayU looks for at runtime; not shipped.
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.PaymentsClient
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.Wallet
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.WalletUtils
