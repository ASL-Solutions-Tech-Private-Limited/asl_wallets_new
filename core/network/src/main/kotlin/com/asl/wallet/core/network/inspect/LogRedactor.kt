package com.asl.wallet.core.network.inspect

import com.asl.wallet.core.common.format.Masking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Makes an HTTP body safe to keep in a debug inspector (Chucker, UAT debug builds only) and to share.
 * Passwords, OTPs, tokens and biometric (PID) data are removed; Aadhaar, account, PAN, mobile and email are
 * masked. It never throws and never returns the original text of something it could not read.
 */
object LogRedactor {
    const val REMOVED = "[redacted]"

    private val SECRET = setOf(
        "password", "old_password", "new_password", "confirm_password", "oldpassword", "newpassword", "confirmpassword",
        "currentpassword", "current_password", "oldpass", "newpass", "confirmpass", "userpass", "pass",
        "otp", "pin", "mpin", "authpin", "pin_code", "accesstoken", "access_token", "refreshtoken", "refresh_token",
        "token", "apitoken", "userauth", "secret", "biometricdata", "piddata", "pid", "hash", "encodefptxnid", "referencekey",
        "pidoptionwadh", "wadh", "salt", "hashstring",
    )
    private val AADHAAR = setOf("aadhaarno", "aadharno", "aadhaar", "aadhar", "aadhaarnumber", "aadharnumber", "adhaarnumber", "customeraadhaar")
    private val ACCOUNT = setOf("accountno", "bankaccountno", "accountnumber", "account_no", "acno", "accno", "acc_no")
    private val PAN = setOf("panno", "pan", "pancard", "shoppan", "shop_pan")
    private val MOBILE = setOf("phone", "mobile", "mobileno", "mobilenumber", "whatsapp_no", "userid", "username", "merchantphonenumber")
    private val EMAIL = setOf("email", "emailid")

    private val json = Json { prettyPrint = true; isLenient = true }

    private fun isSecret(k: String) = k in SECRET || k.endsWith("password") || k.endsWith("token")

    /** [contentType] is the HTTP content type, e.g. "application/json; charset=utf-8". */
    fun redact(body: ByteArray, contentType: String?): String = try {
        val type = contentType?.lowercase().orEmpty()
        when {
            body.isEmpty() -> ""
            type.startsWith("image/") || type.contains("octet-stream") || type.contains("pdf") -> "[binary body, ${body.size} bytes]"
            !isProbablyText(body) -> "[binary parts hidden]\n" + redactText(printableLines(String(body, Charsets.ISO_8859_1)))
            else -> redactString(String(body, Charsets.UTF_8))
        }
    } catch (_: Throwable) {
        "[body hidden: could not be redacted]"
    }

    fun redactString(text: String): String {
        val t = text.trim()
        if (t.startsWith("{") || t.startsWith("[")) {
            runCatching { json.parseToJsonElement(t) }.getOrNull()?.let { return json.encodeToString(JsonElement.serializer(), walk(it, null)) }
        }
        return redactText(text)
    }

    private fun walk(e: JsonElement, key: String?): JsonElement {
        val k = key?.lowercase()?.replace("-", "_")?.replace(" ", "")
        return when (e) {
            is JsonObject -> JsonObject(e.mapValues { (name, v) -> walk(v, name) })
            is JsonArray -> JsonArray(e.map { walk(it, key) })
            JsonNull -> e
            is JsonPrimitive -> {
                val v = e.contentOrNull ?: return e
                when {
                    k == null -> if (e.isString) JsonPrimitive(redactText(v)) else e
                    isSecret(k) -> JsonPrimitive(if (v.isEmpty()) "" else REMOVED)
                    k in AADHAAR -> JsonPrimitive(Masking.aadhaar(v))
                    k in ACCOUNT -> JsonPrimitive(Masking.account(v))
                    k in PAN -> JsonPrimitive(if (v.isBlank()) v else Masking.pan(v))
                    k in MOBILE && v.filter(Char::isDigit).length >= 10 -> JsonPrimitive(Masking.mobile(v))
                    k in EMAIL && v.contains('@') -> JsonPrimitive(Masking.email(v))
                    e.isString -> JsonPrimitive(redactText(v))
                    else -> e
                }
            }
        }
    }

    private val PID_BLOCK = Regex("<PidData\\b.*?</PidData>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val PID_OPTIONS = Regex("<PidOptions\\b.*?</PidOptions>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val BEARER = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+")
    private val JWT = Regex("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+")
    private val TWELVE = Regex("(?<!\\d)\\d{4} ?\\d{4} ?\\d{4}(?!\\d)")
    private val LONG_DIGITS = Regex("(?<!\\d)\\d{9,18}(?!\\d)")
    private val PAN_RX = Regex("(?<![A-Z])[A-Z]{5}\\d{4}[A-Z](?![A-Z])")
    /** multipart part: `name="password"` + headers + blank line + value line. */
    private val FORM_FIELD = Regex("(name=\"([^\"]+)\"[^\\r\\n]*\\r?\\n(?:[^\\r\\n]+\\r?\\n)*\\r?\\n)([^\\r\\n]*)")

    /** `"key": "value"` pairs, for JSON that could not be parsed (truncated or malformed). */
    private val JSON_PAIR = Regex("\"([A-Za-z_][A-Za-z0-9_\\-]*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)(\"|$)")

    private fun maskByKey(key: String, v: String): String {
        val k = key.lowercase().replace("-", "_")
        return when {
            isSecret(k) -> if (v.isEmpty()) "" else REMOVED
            k in AADHAAR -> Masking.aadhaar(v)
            k in ACCOUNT -> Masking.account(v)
            k in PAN -> Masking.pan(v)
            k in MOBILE -> Masking.mobile(v)
            k in EMAIL -> Masking.email(v)
            else -> v
        }
    }

    fun redactText(text: String): String {
        var s = PID_BLOCK.replace(text) { "[PID block removed, ${it.value.length} chars]" }
        // A PID block cut off by truncation has no closing tag: drop everything after its start.
        s = Regex("<PidData\\b.*", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(s, "[PID block removed]")
        s = JSON_PAIR.replace(s) { m -> "\"${m.groupValues[1]}\": \"" + maskByKey(m.groupValues[1], m.groupValues[2]) + m.groupValues[3] }
        s = PID_OPTIONS.replace(s, "[PID options removed]")
        s = FORM_FIELD.replace(s) { m ->
            m.groupValues[1] + maskByKey(m.groupValues[2], m.groupValues[3])
        }
        s = BEARER.replace(s, "Bearer $REMOVED")
        s = JWT.replace(s, REMOVED)
        s = TWELVE.replace(s) { Masking.aadhaar(it.value.filter(Char::isDigit)) }
        s = LONG_DIGITS.replace(s) { Masking.account(it.value) }
        s = PAN_RX.replace(s) { Masking.pan(it.value) }
        return s
    }

    private fun isProbablyText(b: ByteArray): Boolean {
        val n = minOf(b.size, 512)
        var bad = 0
        for (i in 0 until n) { val c = b[i].toInt() and 0xFF; if (c < 0x09 || c in 0x0E..0x1F) bad++ }
        return bad * 10 < n
    }

    /** For multipart with images: keep the printable lines (field names and values), drop binary parts. */
    private fun printableLines(s: String): String = s.split("\n")
        .filter { line -> line.length < 300 && line.all { it == '\t' || it == '\r' || it.code in 0x20..0x7E } }
        .joinToString("\n")
}
