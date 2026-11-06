package com.asl.wallet.core.network

/**
 * Per-flavor network settings, provided by the app module from BuildConfig.
 *
 * @param baseUrl must be https and end with "/"
 * @param certificatePins host -> list of "sha256/<base64 SPKI hash>". At least two pins per host
 *   (current key + backup) so a certificate renewal can't lock users out.
 * @param isDebug enables header logging (never bodies) and relaxes nothing else.
 */
data class NetworkConfig(
    val baseUrl: String,
    val certificatePins: Map<String, List<String>>,
    val isDebug: Boolean,
    val appVersion: String,
    val flavor: String,
) {
    init {
        require(baseUrl.startsWith("https://")) { "Base URL must use HTTPS" }
        require(baseUrl.endsWith("/")) { "Base URL must end with /" }
        if (!isDebug) {
            require(certificatePins.isNotEmpty()) { "Release builds must pin certificates" }
            certificatePins.forEach { (host, pins) ->
                require(pins.size >= 2) { "Host $host needs a primary and a backup pin" }
                require(pins.all { it.startsWith("sha256/") }) { "Pins must be sha256/ SPKI hashes" }
            }
        }
    }
}
