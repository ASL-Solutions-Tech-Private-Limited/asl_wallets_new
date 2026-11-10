package com.asl.wallet.demo

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** UAT debug only. Opened from the "Share demo log" app shortcut or notification: shares the log, then closes. */
class DemoLogShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = DemoLog.shareIntent(this)
        if (intent == null) Toast.makeText(this, "No Add Money demo log yet. Open Add Money first.", Toast.LENGTH_LONG).show()
        else startActivity(intent)
        finish()
    }
}
