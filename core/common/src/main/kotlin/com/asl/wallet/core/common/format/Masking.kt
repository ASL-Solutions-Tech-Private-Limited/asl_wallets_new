package com.asl.wallet.core.common.format

/**
 * Masking rules used everywhere personal data is displayed or printed.
 * Full Aadhaar numbers must never be shown, logged or stored (Aadhaar Act / UIDAI rules).
 */
object Masking {

    /** "234567890123" -> "XXXX XXXX 0123". Non-12-digit input is fully masked. */
    fun aadhaar(raw: String?): String {
        val d = raw.orEmpty().filter(Char::isDigit)
        return if (d.length == 12) "XXXX XXXX ${d.takeLast(4)}" else "XXXX XXXX XXXX"
    }

    /** "9876543210" -> "98XXX XX210" */
    fun mobile(raw: String?): String {
        val d = raw.orEmpty().filter(Char::isDigit).takeLast(10)
        if (d.length != 10) return "XXXXX XXXXX"
        return "${d.take(2)}XXX XX${d.takeLast(3)}"
    }

    /** "302918444417" -> "••••4417" */
    fun account(raw: String?): String {
        val d = raw.orEmpty().filter(Char::isLetterOrDigit)
        return if (d.length <= 4) "••••" else "••••" + d.takeLast(4)
    }

    /** "ABCPS1234K" -> "ABCXXXXX4K" */
    fun pan(raw: String?): String {
        val p = raw.orEmpty().uppercase()
        return if (p.length == 10) p.take(3) + "XXXXX" + p.takeLast(2) else "XXXXXXXXXX"
    }

    /** "ajay.asltech@gmail.com" -> "aj•••@gmail.com". Invalid input is fully masked. */
    fun email(raw: String?): String {
        val e = raw.orEmpty().trim()
        val at = e.indexOf('@')
        if (at < 1 || at == e.lastIndex) return "•••"
        return e.take(minOf(2, at)) + "•••" + e.substring(at)
    }
}
