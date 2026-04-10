package io.droidmcp.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class SpeakTextTool(private val context: Context) : McpTool {

    override val name = "speak_text"
    override val description = "Speak text aloud using the device's text-to-speech engine"
    override val parameters = listOf(
        ToolParameter("text", "Text to speak aloud", ParameterType.STRING, required = true),
        ToolParameter("language", "BCP-47 language code (default: en)", ParameterType.STRING),
        ToolParameter("pitch", "Pitch of the speech (0.5-2.0, default: 1.0)", ParameterType.NUMBER),
        ToolParameter("speed", "Speech rate (0.5-2.0, default: 1.0)", ParameterType.NUMBER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val text = params["text"]?.toString()
            ?: return ToolResult.error("text is required")
        val language = params["language"]?.toString() ?: "en"
        val pitch = (params["pitch"] as? Number)?.toFloat()?.coerceIn(0.5f, 2.0f) ?: 1.0f
        val speed = (params["speed"] as? Number)?.toFloat()?.coerceIn(0.5f, 2.0f) ?: 1.0f

        return suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null

            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.ERROR) {
                    continuation.resume(ToolResult.error("Failed to initialize TTS engine"))
                    return@TextToSpeech
                }

                val engine = tts ?: run {
                    continuation.resume(ToolResult.error("TTS engine not available"))
                    return@TextToSpeech
                }

                val locale = Locale.forLanguageTag(language)
                val langResult = engine.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_NOT_SUPPORTED || langResult == TextToSpeech.LANG_MISSING_DATA) {
                    engine.setLanguage(Locale.ENGLISH)
                }

                engine.setPitch(pitch)
                engine.setSpeechRate(speed)

                val utteranceId = "droid-mcp-tts-${System.currentTimeMillis()}"

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        engine.shutdown()
                        if (continuation.isActive) {
                            continuation.resume(ToolResult.success(mapOf(
                                "success" to true,
                                "text_length" to text.length,
                                "language" to language,
                            )))
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        engine.shutdown()
                        if (continuation.isActive) {
                            continuation.resume(ToolResult.error("TTS playback error"))
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        engine.shutdown()
                        if (continuation.isActive) {
                            continuation.resume(ToolResult.error("TTS playback error (code: $errorCode)"))
                        }
                    }
                })

                val speakResult = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (speakResult == TextToSpeech.ERROR) {
                    engine.shutdown()
                    continuation.resume(ToolResult.error("TTS speak call failed"))
                }
            }

            continuation.invokeOnCancellation {
                tts?.shutdown()
            }
        }
    }
}
