package com.asl.wallet.core.common.validation

/**
 * Client-side validation for instant feedback only. The server must validate everything again;
 * nothing here is a security control.
 */
object Validators {

    private val mobileRegex = Regex("^[6-9]\\d{9}$")
    private val ifscRegex = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    private val panRegex = Regex("^[A-Z]{5}\\d{4}[A-Z]$")
    private val upiRegex = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")
    private val pincodeRegex = Regex("^[1-9]\\d{5}$")

    fun isMobile(v: String?): Boolean = v != null && mobileRegex.matches(v.trim())
    fun isIfsc(v: String?): Boolean = v != null && ifscRegex.matches(v.trim().uppercase())
    fun isPan(v: String?): Boolean = v != null && panRegex.matches(v.trim().uppercase())
    fun isUpiId(v: String?): Boolean = v != null && upiRegex.matches(v.trim())
    fun isPincode(v: String?): Boolean = v != null && pincodeRegex.matches(v.trim())

    fun isOtp(v: String?, length: Int): Boolean =
        v != null && v.length == length && v.all(Char::isDigit)

    /** 12 digits, not starting with 0 or 1, and passing the UIDAI Verhoeff checksum. */
    fun isAadhaar(v: String?): Boolean {
        val d = v.orEmpty().filter(Char::isDigit)
        if (d.length != 12 || d[0] == '0' || d[0] == '1') return false
        return Verhoeff.isValid(d)
    }

    /** Account numbers in India are 9–18 digits. */
    fun isAccountNumber(v: String?): Boolean {
        val d = v.orEmpty().trim()
        return d.length in 9..18 && d.all(Char::isDigit)
    }
}

/** Password strength shown under "new password" fields. */
enum class PasswordStrength { TooShort, Weak, Fair, Strong }

object PasswordRules {
    const val MIN_LENGTH = 8

    data class Checks(val length: Boolean, val digit: Boolean, val upper: Boolean, val lower: Boolean, val symbol: Boolean)

    fun checks(p: String) = Checks(
        length = p.length >= MIN_LENGTH,
        digit = p.any(Char::isDigit),
        upper = p.any(Char::isUpperCase),
        lower = p.any(Char::isLowerCase),
        symbol = p.any { !it.isLetterOrDigit() },
    )

    fun strength(p: String): PasswordStrength {
        val c = checks(p)
        if (!c.length) return PasswordStrength.TooShort
        val score = listOf(c.digit, c.upper, c.lower, c.symbol).count { it }
        return when {
            score >= 4 -> PasswordStrength.Strong
            score == 3 -> PasswordStrength.Fair
            else -> PasswordStrength.Weak
        }
    }
}

/** Verhoeff checksum, used by UIDAI for Aadhaar numbers. */
internal object Verhoeff {
    private val d = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6), intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8), intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2), intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4), intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
    )
    private val p = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2), intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0), intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5), intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
    )

    fun isValid(num: String): Boolean {
        var c = 0
        num.reversed().forEachIndexed { i, ch -> c = d[c][p[i % 8][ch - '0']] }
        return c == 0
    }
}
