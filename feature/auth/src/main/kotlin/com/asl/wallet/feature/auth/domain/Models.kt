package com.asl.wallet.feature.auth.domain

import com.asl.wallet.core.common.validation.Validators
import kotlinx.serialization.Serializable

/** The signed-in retailer, as the app needs it. No Aadhaar, PAN or secrets. */
@Serializable
data class User(
    val id: Long,
    val regno: Long,
    val name: String,
    val shopName: String,
    val phone: String,
    val email: String,
    val userType: String,
    val kycDone: Boolean,
) {
    val firstName: String get() = name.substringBefore(' ').ifBlank { name }
    val initials: String get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "U" }
}

/** What the retailer typed in "Mobile number or email". */
sealed interface LoginId {
    val raw: String
    data class Mobile(override val raw: String) : LoginId
    data class Email(override val raw: String) : LoginId

    companion object {
        private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        /** Returns null while the input is not a valid 10-digit Indian mobile or an email. */
        fun parse(input: String): LoginId? {
            val t = input.trim()
            if (t.contains('@')) return if (emailRegex.matches(t)) Email(t.lowercase()) else null
            val digits = t.filter(Char::isDigit).takeLast(10)
            return if (t.all { it.isDigit() || it == ' ' || it == '+' } && Validators.isMobile(digits)) Mobile(digits) else null
        }
    }
}

sealed interface LoginOutcome {
    data class SignedIn(val user: User) : LoginOutcome
    /** The server wants a 4-digit OTP before issuing tokens. */
    data object OtpRequired : LoginOutcome
    data class Locked(val message: String) : LoginOutcome
    data class Failed(val error: AuthError) : LoginOutcome
}

sealed interface AuthError {
    /** Message from the server, already checked to be safe to show. */
    data class Server(val message: String) : AuthError
    data object NoInternet : AuthError
    data object Timeout : AuthError
    data object Insecure : AuthError
    data object Unknown : AuthError
    /** The OTP step lost its pending login (for example after the app was killed). */
    data object OtpSessionExpired : AuthError
}

sealed interface ActionResult {
    data object Ok : ActionResult
    data class Failed(val error: AuthError) : ActionResult
}
