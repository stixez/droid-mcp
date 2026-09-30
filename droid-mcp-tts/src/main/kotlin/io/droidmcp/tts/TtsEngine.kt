package io.droidmcp.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** How long to wait for a [TextToSpeech] engine to bind and initialize. */
internal const val TTS_INIT_TIMEOUT_MS = 10_000L

/**
 * Creates a one-shot [TextToSpeech] engine, waits (at most [initTimeoutMs]) for its init callback,
 * then runs [block] with it from the calling coroutine — so any engine queries in [block] happen
 * off the init callback thread. The engine is shut down on every path: init error, init timeout,
 * normal completion, exceptions, and coroutine cancellation.
 *
 * @param onError maps an init failure message to the caller's result type.
 */
internal suspend fun <T> useTtsEngine(
    context: Context,
    initTimeoutMs: Long = TTS_INIT_TIMEOUT_MS,
    onError: (String) -> T,
    block: suspend (TextToSpeech) -> T,
): T {
    val initStatus = CompletableDeferred<Int>()
    val tts = TextToSpeech(context) { status -> initStatus.complete(status) }
    try {
        val status = withTimeoutOrNull(initTimeoutMs) { initStatus.await() }
            ?: return onError("TTS engine did not initialize within ${initTimeoutMs / 1000}s")
        if (status != TextToSpeech.SUCCESS) return onError("Failed to initialize TTS engine")
        return block(tts)
    } finally {
        tts.shutdown()
    }
}
