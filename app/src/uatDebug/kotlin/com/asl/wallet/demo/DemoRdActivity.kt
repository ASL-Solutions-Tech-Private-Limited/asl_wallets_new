package com.asl.wallet.demo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle

/**
 * UAT debug only: the "ASL Demo Scanner (UAT)". It stands in for an RD service app (Mantra, Morpho …) and returns a
 * sample PID block. The sample holds no biometric data; the demo server never sends it anywhere.
 */
class DemoRdActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle("ASL Demo Scanner (UAT)")
            .setMessage("Demo fingerprint capture.\n\nThis is not a real scanner: nothing is read from a finger and AEPS demo mode answers the bank itself.")
            .setPositiveButton("Capture") { _, _ -> finishWith(Activity.RESULT_OK) }
            .setNegativeButton("Cancel") { _, _ -> finishWith(Activity.RESULT_CANCELED) }
            .setOnCancelListener { finishWith(Activity.RESULT_CANCELED) }
            .show()
    }

    private fun finishWith(code: Int) {
        if (code == Activity.RESULT_OK) setResult(code, Intent().putExtra("PID_DATA", SAMPLE_PID)) else setResult(code)
        finish()
    }

    private companion object {
        const val SAMPLE_PID = """<?xml version="1.0"?><PidData><Resp errCode="0" errInfo="Capture Success (DEMO)" fCount="1" fType="2" nmPoints="34" qScore="82"/><DeviceInfo dpId="ASL.DEMO" rdsId="ASL.DEMO.001" rdsVer="1.0.0" mi="DEMO" mc="DEMO" dc="demo-device"/><Skey ci="20250101">DEMO</Skey><Hmac>DEMO</Hmac><Data type="X">DEMO</Data></PidData>"""
    }
}
