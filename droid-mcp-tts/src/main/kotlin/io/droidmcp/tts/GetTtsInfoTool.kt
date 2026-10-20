package io.droidmcp.tts

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports the default [android.speech.tts.TextToSpeech] engine and its supported languages. No
 * permissions required. Initializes a one-shot engine (bounded by [TTS_INIT_TIMEOUT_MS]), reads
 * its default engine and available languages (as BCP-47 tags) with a single
 * `getAvailableLanguages()` call made from the tool coroutine rather than the init callback, then
 * shuts it down — on every path, including init error, timeout, and cancellation (see
 * [useTtsEngine]).
 *
 * Output map: `default_engine` (String package, "unknown" if unavailable), `available_languages`
 * (sorted `List<String>`), `language_count` (Int).
 */
class GetTtsInfoTool(private val context: Context) : McpTool {

    override val name = "get_tts_info"
    override val description = "Get the text-to-speech engine and the languages it can speak."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        string("default_engine")
        arrayOf("available_languages", OutputSchema.string())
        integer("language_count")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult =
        useTtsEngine(context, onError = { ToolResult.error(it) }) { engine ->
            val defaultEngine = engine.defaultEngine ?: "unknown"

            val availableLanguages = try {
                engine.availableLanguages
                    ?.map { locale -> locale.toLanguageTag() }
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            ToolResult.success(mapOf(
                "default_engine" to defaultEngine,
                "available_languages" to availableLanguages,
                "language_count" to availableLanguages.size,
            ))
        }
}
