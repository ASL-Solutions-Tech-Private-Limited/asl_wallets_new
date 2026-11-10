package com.asl.wallet.core.common.demo

/**
 * A UAT-only demo the tester can switch on (e.g. the AEPS demo). Bound only in the uatDebug build, so release and
 * Live builds have none. While on, the demo answers its own API calls inside the app: nothing reaches the server.
 */
interface DemoToggle {
    val title: String
    val subtitle: String
    fun isOn(): Boolean
    fun set(on: Boolean)
}
