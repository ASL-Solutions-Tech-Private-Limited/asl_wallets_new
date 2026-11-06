package com.asl.wallet.core.network

/** What a screen can say about a failed call. Server text is passed on only when it is safe to show. */
sealed interface UiError {
    data class Server(val message: String) : UiError
    data object NoInternet : UiError
    data object Timeout : UiError
    data object Insecure : UiError
    data object Unknown : UiError
}

/**
 * Returns the server's message only if it reads like a message for people. Database/ORM text, stack
 * traces and very long messages are dropped (the backend currently leaks Prisma errors on 500s).
 */
fun safeServerMessage(message: String?): String? {
    val m = message?.trim().orEmpty()
    val internal = listOf(
        "prisma", "invocation", "column", "database", "sql", "exception", "stack", "undefined", "validation error:", "`",
        // Provider internals passed through by the backend (recharge vendor, 4 Nov): never shown to retailers.
        "login failed", "loginfailed", "api not responding", "mock response", "whitelist", "vendor", "<?xml",
    )
    if (m.isEmpty() || m.length > 160 || internal.any { m.contains(it, ignoreCase = true) } || IP_ADDRESS.containsMatchIn(m)) return null
    return m
}

/** Server IP addresses in a message mean it's an internal log line (VAPT: never show infrastructure details). */
private val IP_ADDRESS = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")

fun NetworkResult.Failure.toUiError(): UiError = when (this) {
    is NetworkResult.Failure.Api -> safeServerMessage(message)?.let { UiError.Server(it) } ?: UiError.Unknown
    NetworkResult.Failure.NoInternet -> UiError.NoInternet
    NetworkResult.Failure.Timeout -> UiError.Timeout
    NetworkResult.Failure.InsecureConnection -> UiError.Insecure
    NetworkResult.Failure.Unauthorized, is NetworkResult.Failure.Unexpected -> UiError.Unknown
}
