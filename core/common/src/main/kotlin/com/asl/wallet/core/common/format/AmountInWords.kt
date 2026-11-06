package com.asl.wallet.core.common.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts rupee amounts to words using the Indian numbering system (thousand, lakh, crore),
 * e.g. 5025 -> "Five thousand twenty-five rupees". Shown under every amount the retailer types,
 * so a wrong extra zero is obvious before money moves.
 */
object AmountInWords {

    private val ones = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun english(amount: BigDecimal): String {
        val scaled = amount.abs().setScale(2, RoundingMode.HALF_UP)
        val rupees = scaled.toBigInteger().toLong()
        val paise = scaled.remainder(BigDecimal.ONE).movePointRight(2).toInt()

        if (rupees == 0L && paise == 0) return "Zero rupees"

        val parts = mutableListOf<String>()
        if (rupees > 0) parts += words(rupees) + if (rupees == 1L) " rupee" else " rupees"
        if (paise > 0) parts += belowHundred(paise) + if (paise == 1) " paisa" else " paise"
        return parts.joinToString(" and ").replaceFirstChar { it.uppercase() }
    }

    private fun words(n: Long): String {
        require(n in 0..999_999_999_999L) { "Amount too large" }
        if (n == 0L) return ""
        val crore = n / 10_000_000
        val lakh = (n / 100_000) % 100
        val thousand = (n / 1_000) % 100
        val hundredBlock = (n % 1_000).toInt()

        val out = mutableListOf<String>()
        if (crore > 0) out += words(crore) + " crore"
        if (lakh > 0) out += belowHundred(lakh.toInt()) + " lakh"
        if (thousand > 0) out += belowHundred(thousand.toInt()) + " thousand"
        if (hundredBlock > 0) out += belowThousand(hundredBlock)
        return out.joinToString(" ")
    }

    private fun belowThousand(n: Int): String {
        val h = n / 100
        val r = n % 100
        return listOfNotNull(
            if (h > 0) ones[h] + " hundred" else null,
            if (r > 0) belowHundred(r) else null,
        ).joinToString(" ")
    }

    private fun belowHundred(n: Int): String = when {
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + "-" + ones[n % 10]
    }
}
