package com.asl.wallet.demo

import android.content.Context
import com.asl.wallet.core.common.demo.DemoToggle

/**
 * UAT debug only. The AEPS demo: walk the whole AEPS flow (bank → status → daily 2FA → fingerprint → transaction →
 * receipt) without the bank, the 2FA or a scanner. While it is on, AEPS calls are answered inside the app
 * ([AepsDemoInterceptor]): none reaches the server and no money moves. Every demo receipt says DEMO.
 */
class AepsDemo(context: Context) : DemoToggle {
    private val prefs = context.getSharedPreferences("asl_uat_demos", Context.MODE_PRIVATE)

    override val title = "AEPS demo mode"
    override val subtitle = "Go straight to AEPS with a demo scanner and bank (daily 2FA skipped). Nothing is sent; no money moves. (UAT only)"
    override fun isOn() = prefs.getBoolean(KEY, false)
    override fun set(on: Boolean) {
        prefs.edit().putBoolean(KEY, on).apply()
    }

    private companion object { const val KEY = "aeps" }
}
