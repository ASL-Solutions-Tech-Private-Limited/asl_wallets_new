package com.asl.wallet.core.designsystem.component

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.staticCompositionLocalOf
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * The retailer's sound choices (Profile → Settings → Sounds), for every service's receipt.
 * [voiceWithDateTime] = the old app's "Use voice with date and time"; off = "only amount" (the old default).
 */
data class ReceiptSound(
    val printSound: Boolean = true,
    val voice: Boolean = true,
    val voiceWithDateTime: Boolean = false,
)

/** Provided once at the app root from the saved settings. */
val LocalReceiptSound = staticCompositionLocalOf { ReceiptSound() }

/** Sounds stay quiet when the phone is on silent or vibrate. */
internal fun soundsAllowed(context: Context): Boolean = runCatching {
    (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode == AudioManager.RINGER_MODE_NORMAL
}.getOrDefault(false)

/** "₹ 15,234.50" is spoken as whole rupees, like the old app ("15234"). */
fun spokenRupees(amount: BigDecimal): String = amount.setScale(0, RoundingMode.DOWN).toPlainString()

/**
 * Speaks receipt announcements in the app's language (English, Hindi or Bengali, Indian voices), like the old app's
 * TextSpeaker. One engine for the whole app; text waits until the engine is ready. Silent if the phone has no voice
 * for that language.
 */
object ReceiptVoice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Locale>? = null

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        val app = context.applicationContext
        val lang = app.resources.configuration.locales[0]?.language ?: "en"
        val locale = Locale(if (lang in setOf("hi", "bn")) lang else "en", "IN")
        synchronized(this) {
            if (ready) { say(text, locale); return }
            pending = text to locale
            if (tts == null) tts = TextToSpeech(app) { status ->
                synchronized(this) {
                    ready = status == TextToSpeech.SUCCESS
                    pending?.let { (t, l) -> if (ready) say(t, l) }
                    pending = null
                }
            }
        }
    }

    fun stop() { runCatching { tts?.stop() } }

    private fun say(text: String, locale: Locale) {
        val engine = tts ?: return
        val r = engine.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) engine.setLanguage(Locale("en", "IN"))
        engine.setSpeechRate(0.95f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    /** "On 10 November 2026 at 4:30 PM, using the ASL app. …" when the retailer chose the long announcement. */
    fun withDateTime(context: Context, text: String): String {
        val lang = context.resources.configuration.locales[0]?.language ?: "en"
        val when_ = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale(if (lang in setOf("hi", "bn")) lang else "en", "IN")).format(Date())
        return context.getString(com.asl.wallet.core.designsystem.R.string.ds_voice_prefix, when_) + " " + text
    }
}
