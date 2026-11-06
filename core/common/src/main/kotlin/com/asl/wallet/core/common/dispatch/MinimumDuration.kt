package com.asl.wallet.core.common.dispatch

import kotlinx.coroutines.delay

/** How long a money "processing" screen stays up at least, so the retailer sees what's happening. */
const val MIN_PROCESSING_MS = 2_000L

/**
 * Runs [block] and, if it finished faster than [minMs], waits for the rest. A slow server adds no extra wait:
 * a 5-second answer shows after 5 seconds, an instant one after [minMs].
 */
suspend fun <T> atLeast(minMs: Long = MIN_PROCESSING_MS, block: suspend () -> T): T {
    val start = System.nanoTime()
    val result = block()
    val left = minMs - (System.nanoTime() - start) / 1_000_000
    if (left > 0) delay(left)
    return result
}
