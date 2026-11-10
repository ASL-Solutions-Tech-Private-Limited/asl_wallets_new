package com.asl.wallet.demo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UAT debug only. One readable record of the Add Money demo, in order: every API request and response exactly as
 * sent and received (only the Authorization header is hidden), what the PayU SDK asked for, and what a real server
 * would do at each step. Kept as a Markdown file in the private cache and shared from a notification, so it can go
 * straight to the backend team. Also written to Logcat under [TAG].
 *
 * Only the demo's made-up retailer is in it (no real name, phone or email), and only PayU **test** hashes.
 */
class DemoLog(private val context: Context) {

    private val entries = mutableListOf<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(title: String, text: String) {
        val block = "### ${time.format(Date())} · $title\n\n$text\n"
        entries += block
        text.chunked(3_500).forEachIndexed { i, part -> Log.i(TAG, if (i == 0) "$title\n$part" else part) }
        write()
        notifyShare()
    }

    @Synchronized
    fun http(method: String, url: String, headers: List<Pair<String, String>>, body: String?, code: Int, responseHeaders: List<Pair<String, String>>, response: String) {
        val req = buildString {
            append("$method $url\n")
            headers.forEach { (k, v) -> append("$k: ${if (k.equals("Authorization", true)) "Bearer [hidden]" else v}\n") }
            if (!body.isNullOrBlank()) append("\n").append(body.trim()).append("\n")
        }
        val res = buildString {
            append("HTTP $code\n")
            responseHeaders.forEach { (k, v) -> append("$k: $v\n") }
            append("\n").append(response.trim()).append("\n")
        }
        add("$method ${url.substringAfter("/api/v1/")}", "Request\n```http\n$req```\n\nResponse\n```http\n$res```")
    }

    @Synchronized
    fun clear() { entries.clear(); write() }

    private fun file(): File = File(File(context.cacheDir, "receipts/demo-log").apply { mkdirs() }, FILE)

    private fun write() = runCatching {
        file().writeText(
            "# ASL Wallet · Add Money (PayU) demo log\n\n" +
                "UAT debug build. The ASL server answers here are a stand-in that follows the backend spec; the PayU screens are " +
                "PayU's test environment, or a simulated screen when no PayU test key is set. No real money moves.\n\n" +
                entries.joinToString("\n"),
        )
    }

    private var shortcutAdded = false

    /** A notification (when the app may post one) and an app shortcut (always): both open the share sheet. */
    private fun notifyShare() = runCatching {
        if (!shortcutAdded) {
            shortcutAdded = true
            val open = Intent(context, DemoLogShareActivity::class.java).setAction(Intent.ACTION_VIEW)
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                ShortcutInfoCompat.Builder(context, "add_money_demo_log")
                    .setShortLabel("Share demo log")
                    .setLongLabel("Share Add Money demo log")
                    .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_share))
                    .setIntent(open)
                    .build(),
            )
        }
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return@runCatching
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Add Money demo log", NotificationManager.IMPORTANCE_LOW))
        }
        val share = PendingIntent.getActivity(
            context, 7, Intent(context, DemoLogShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Add Money demo log")
            .setContentText("${entries.size} steps recorded · tap to share")
            .setContentIntent(share)
            .addAction(android.R.drawable.ic_menu_share, "Share log", share)
            .setOnlyAlertOnce(true)
            .build()
        @Suppress("MissingPermission") nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        const val TAG = "AslAddMoneyDemo"
        private const val FILE = "add-money-payu-demo-log.md"

        /** The share sheet for the saved log, or null if there's no log yet. */
        fun shareIntent(context: Context): Intent? {
            val f = File(File(context.cacheDir, "receipts/demo-log"), FILE)
            if (!f.exists() || f.length() < 200) return null
            val uri = FileProvider.getUriForFile(context, context.packageName + ".receipts", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ASL Wallet · Add Money (PayU) demo log")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("log", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(send, "Share demo log").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        private const val CHANNEL = "add_money_demo"
        private const val NOTIFICATION_ID = 4711
    }
}
