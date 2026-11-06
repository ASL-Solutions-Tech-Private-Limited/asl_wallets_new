package com.asl.wallet.core.common

import com.asl.wallet.core.common.format.AmountInWords
import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.common.format.Money
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.common.validation.PasswordRules
import com.asl.wallet.core.common.validation.PasswordStrength
import com.asl.wallet.core.common.validation.Validators
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
    @Test fun `formats with Indian grouping`() {
        assertThat(Money.format(BigDecimal("48250.75"))).isEqualTo("₹ 48,250.75")
        assertThat(Money.format(BigDecimal("12345678.5"))).isEqualTo("₹ 1,23,45,678.50")
        assertThat(Money.format(BigDecimal("999"))).isEqualTo("₹ 999.00")
        assertThat(Money.format(BigDecimal("100000"), showPaise = false)).isEqualTo("₹ 1,00,000")
        assertThat(Money.format(5025L, withSymbol = false)).isEqualTo("5,025")
    }

    @Test fun `negative amounts use a real minus sign`() {
        assertThat(Money.format(BigDecimal("-299"))).isEqualTo("−₹ 299.00")
    }

    @Test fun `parses common inputs and rejects junk`() {
        assertThat(Money.parse("₹ 5,000")).isEqualTo(BigDecimal("5000.00"))
        assertThat(Money.parse("10")).isEqualTo(BigDecimal("10.00"))
        assertThat(Money.parse("7297.164000000025")).isEqualTo(BigDecimal("7297.16"))
        assertThat(Money.parse("")).isNull()
        assertThat(Money.parse("1.2.3")).isNull()
        assertThat(Money.parse("abc")).isNull()
    }
}

class AmountInWordsTest {
    @Test fun `uses lakh and crore`() {
        assertThat(AmountInWords.english(BigDecimal("5025"))).isEqualTo("Five thousand twenty-five rupees")
        assertThat(AmountInWords.english(BigDecimal("200000"))).isEqualTo("Two lakh rupees")
        assertThat(AmountInWords.english(BigDecimal("12345678"))).isEqualTo(
            "One crore twenty-three lakh forty-five thousand six hundred seventy-eight rupees",
        )
    }

    @Test fun `handles paise, one and zero`() {
        assertThat(AmountInWords.english(BigDecimal("1"))).isEqualTo("One rupee")
        assertThat(AmountInWords.english(BigDecimal("12.50"))).isEqualTo("Twelve rupees and fifty paise")
        assertThat(AmountInWords.english(BigDecimal.ZERO)).isEqualTo("Zero rupees")
    }
}

class MaskingTest {
    @Test fun `masks email`() {
        assertThat(Masking.email("ajay.asltech@gmail.com")).isEqualTo("aj•••@gmail.com")
        assertThat(Masking.email("a@b.co")).isEqualTo("a•••@b.co")
        assertThat(Masking.email("not-an-email")).isEqualTo("•••")
        assertThat(Masking.email(null)).isEqualTo("•••")
    }

    @Test fun `masks personal data`() {
        assertThat(Masking.aadhaar("2345 6789 0124")).isEqualTo("XXXX XXXX 0124")
        assertThat(Masking.aadhaar("123")).isEqualTo("XXXX XXXX XXXX")
        assertThat(Masking.mobile("9876543210")).isEqualTo("98XXX XX210")
        assertThat(Masking.account("302918444417")).isEqualTo("••••4417")
        assertThat(Masking.pan("abcps1234k")).isEqualTo("ABCXXXXX4K")
    }
}

class ValidatorsTest {
    @Test fun `aadhaar uses Verhoeff checksum`() {
        assertThat(Validators.isAadhaar("234567890124")).isTrue()
        assertThat(Validators.isAadhaar("2345 6789 0124")).isTrue()
        assertThat(Validators.isAadhaar("999941057058")).isTrue()
        assertThat(Validators.isAadhaar("234567890125")).isFalse() // one digit changed
        assertThat(Validators.isAadhaar("134567890124")).isFalse() // cannot start with 1
        assertThat(Validators.isAadhaar("23456789012")).isFalse()  // 11 digits
    }

    @Test fun `mobile ifsc pan upi`() {
        assertThat(Validators.isMobile("9876543210")).isTrue()
        assertThat(Validators.isMobile("5876543210")).isFalse()
        assertThat(Validators.isIfsc("SBIN0001234")).isTrue()
        assertThat(Validators.isIfsc("SBIN1001234")).isFalse()
        assertThat(Validators.isPan("ABCPS1234K")).isTrue()
        assertThat(Validators.isPan("ABC1234K")).isFalse()
        assertThat(Validators.isUpiId("anil.das@ybl")).isTrue()
        assertThat(Validators.isUpiId("anil.das")).isFalse()
        assertThat(Validators.isOtp("4721", 4)).isTrue()
        assertThat(Validators.isOtp("47a1", 4)).isFalse()
        assertThat(Validators.isAccountNumber("302918444417")).isTrue()
        assertThat(Validators.isAccountNumber("1234")).isFalse()
    }

    @Test fun `password strength`() {
        assertThat(PasswordRules.strength("abc")).isEqualTo(PasswordStrength.TooShort)
        assertThat(PasswordRules.strength("abcdefgh")).isEqualTo(PasswordStrength.Weak)
        assertThat(PasswordRules.strength("Abcdefg1")).isEqualTo(PasswordStrength.Fair)
        assertThat(PasswordRules.strength("Abcdef1!")).isEqualTo(PasswordStrength.Strong)
    }
}

class IdempotencyKeyTest {
    @Test fun `keys are unique uuids`() {
        val a = IdempotencyKey.new()
        val b = IdempotencyKey.new()
        assertThat(a).isNotEqualTo(b)
        assertThat(a.value).matches("[0-9a-f-]{36}")
    }
}
