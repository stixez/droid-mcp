package io.droidmcp.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class GetTtsInfoTool(private val context: Context) : McpTool {

    override val name = "get_tts_info"
    override val description = "Get information about the available TTS engines and supported languages"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
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

                val defaultEngine = engine.defaultEngine ?: "unknown"

                val availableLanguages = try {
                    engine.availableLanguages
                        ?.map { locale -> locale.toLanguageTag() }
                        ?.sorted()
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                engine.shutdown()

                if (continuation.isActive) {
                    continuation.resume(ToolResult.success(mapOf(
                        "default_engine" to defaultEngine,
                        "available_languages" to availableLanguages,
                        "language_count" to availableLanguages.size,
                    )))
                }
            }

            continuation.invokeOnCancellation {
                tts?.shutdown()
            }
        }
    }
}
