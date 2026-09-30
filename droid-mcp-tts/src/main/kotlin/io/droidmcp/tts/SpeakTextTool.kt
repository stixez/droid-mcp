package io.droidmcp.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Speaks text aloud through the device [TextToSpeech] engine, suspending until playback completes.
 * No permissions required.
 *
 * Initializes a one-shot engine (init bounded by [TTS_INIT_TIMEOUT_MS]), applies pitch/speed (each
 * clamped to 0.5–2.0) and language (falls back to English if the requested BCP-47 tag is
 * unsupported or missing data), then speaks. Text longer than
 * [TextToSpeech.getMaxSpeechInputLength] is split into chunks (at whitespace where possible) and
 * queued with `QUEUE_ADD` after an initial `QUEUE_FLUSH`; the call completes when the last chunk
 * finishes. Playback is bounded by a timeout that scales with text length and speed (roughly
 * twice the estimated speaking time plus slack, capped at [MAX_PLAYBACK_TIMEOUT_MS]). The engine
 * is shut down on every path — completion, error, timeout, and coroutine cancellation (which
 * therefore also stops playback).
 *
 * Output map (on completion): `success` (true), `text_length` (Int), `language` (the requested tag,
 * not necessarily the one actually used after fallback), `chunk_count` (Int).
 */
class SpeakTextTool(private val context: Context) : McpTool {

    override val name = "speak_text"
    override val description = "Speak text aloud using the device's text-to-speech engine"
    override val parameters = listOf(
        ToolParameter("text", "Text to speak aloud", ParameterType.STRING, required = true),
        ToolParameter("language", "BCP-47 language code (default: en)", ParameterType.STRING),
        ToolParameter("pitch", "Pitch of the speech (0.5-2.0, default: 1.0)", ParameterType.NUMBER, minimum = 0.5, maximum = 2.0),
        ToolParameter("speed", "Speech rate (0.5-2.0, default: 1.0)", ParameterType.NUMBER, minimum = 0.5, maximum = 2.0),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val text = params["text"]?.toString()
            ?: return ToolResult.error("text is required")
        if (text.isBlank()) return ToolResult.error("text must not be blank")
        val language = params["language"]?.toString() ?: "en"
        val pitch = (params["pitch"] as? Number)?.toFloat()?.coerceIn(0.5f, 2.0f) ?: 1.0f
        val speed = (params["speed"] as? Number)?.toFloat()?.coerceIn(0.5f, 2.0f) ?: 1.0f

        return useTtsEngine(context, onError = { ToolResult.error(it) }) { engine ->
            val locale = Locale.forLanguageTag(language)
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_NOT_SUPPORTED || langResult == TextToSpeech.LANG_MISSING_DATA) {
                engine.setLanguage(Locale.ENGLISH)
            }
            engine.setPitch(pitch)
            engine.setSpeechRate(speed)

            val chunks = chunkText(text, TextToSpeech.getMaxSpeechInputLength())
            val idPrefix = "droid-mcp-tts-${System.nanoTime()}-"
            val lastId = idPrefix + (chunks.size - 1)
            val outcome = CompletableDeferred<ToolResult>()

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == lastId) {
                        outcome.complete(ToolResult.success(mapOf(
                            "success" to true,
                            "text_length" to text.length,
                            "language" to language,
                            "chunk_count" to chunks.size,
                        )))
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    outcome.complete(ToolResult.error("TTS playback error"))
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    outcome.complete(ToolResult.error("TTS playback error (code: $errorCode)"))
                }
            })

            chunks.forEachIndexed { i, chunk ->
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (engine.speak(chunk, mode, null, idPrefix + i) == TextToSpeech.ERROR) {
                    return@useTtsEngine ToolResult.error("TTS speak call failed (chunk ${i + 1} of ${chunks.size})")
                }
            }

            val timeoutMs = playbackTimeoutMs(text.length, speed)
            withTimeoutOrNull(timeoutMs) { outcome.await() }
                ?: ToolResult.error("TTS playback timed out after ${timeoutMs / 1000}s")
        }
    }

    companion object {
        /** Upper bound on how long a single speak_text call may play. */
        private const val MAX_PLAYBACK_TIMEOUT_MS = 5 * 60_000L

        /** Fixed slack added to the estimate (engine warm-up, audio focus, etc.). */
        private const val PLAYBACK_SLACK_MS = 15_000L

        /** Conservative speaking-rate estimate at speed 1.0. */
        private const val CHARS_PER_SECOND = 12f

        internal fun playbackTimeoutMs(length: Int, speed: Float): Long {
            val estimateMs = (length / (CHARS_PER_SECOND * speed) * 1000f).toLong()
            return (PLAYBACK_SLACK_MS + 2 * estimateMs).coerceAtMost(MAX_PLAYBACK_TIMEOUT_MS)
        }

        /** Splits [text] into pieces of at most [maxLen] chars, preferring whitespace boundaries. */
        internal fun chunkText(text: String, maxLen: Int): List<String> {
            if (text.length <= maxLen) return listOf(text)
            val chunks = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                var end = minOf(start + maxLen, text.length)
                if (end < text.length) {
                    val ws = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
                    if (ws > start) end = ws + 1
                }
                chunks.add(text.substring(start, end))
                start = end
            }
            return chunks
        }
    }
}
