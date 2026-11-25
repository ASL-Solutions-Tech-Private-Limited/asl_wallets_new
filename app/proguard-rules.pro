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

# ---- Micro ATM (Module 7 · FingPay mATM SDK + Morefun mPOS reader)
# Activity-based SDKs launched by name, with native/JNI + reflection and card crypto; keep whole.
-keep class com.fingpay.** { *; }
-dontwarn com.fingpay.**
-keep class myobfuscated.** { *; }
-dontwarn myobfuscated.**
-keep class com.mf.** { *; }
-dontwarn com.mf.**
-keep class com.inuker.** { *; }
-dontwarn com.inuker.**
# android-gif-drawable: the SDK shows an animated GIF during card verification (JNI).
-keep class pl.droidsonroids.gif.** { *; }
-dontwarn pl.droidsonroids.gif.**
# BouncyCastle: card/PIN crypto used by the reader.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---- PayU CheckoutPro (Module 4 · Add money)
# PayU's own screens use reflection; keep them whole.
-keep class com.payu.** { *; }
-dontwarn com.payu.**
# Google Pay in-app client: optional plug-in PayU looks for at runtime; not shipped.
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.PaymentsClient
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.Wallet
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.WalletUtils
