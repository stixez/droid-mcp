package io.droidmcp.core

import io.droidmcp.core.protocol.ToolSchemas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A concrete piece of device data a client can read by [uri] (MCP `resources/list`).
 *
 * @property uri Stable identifier passed back to `resources/read` (e.g. `content://media/external/images/media/42`).
 * @property name Short machine-friendly name (e.g. the file name).
 * @property title Optional human-friendly display name.
 * @property description Optional one-line description.
 * @property mimeType MIME type of the contents, when known.
 * @property size Size in bytes, when known.
 */
data class McpResource(
    val uri: String,
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
)

/**
 * A parameterised family of resources (MCP `resources/templates/list`), e.g.
 * `droidmcp://contacts/{id}`. [uriTemplate] follows RFC 6570.
 */
data class McpResourceTemplate(
    val uriTemplate: String,
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
)

/** The body of one resource returned by `resources/read`: either [Text] or base64 [Blob]. */
sealed class ResourceContents {
    abstract val uri: String
    abstract val mimeType: String?

    data class Text(override val uri: String, val text: String, override val mimeType: String? = "text/plain") : ResourceContents()

    data class Blob(override val uri: String, val base64: String, override val mimeType: String? = null) : ResourceContents()

    companion object {
        /** [data] (e.g. a tool's [ToolResult.data]) as an `application/json` text resource. */
        fun json(uri: String, data: Map<String, Any?>): Text =
            Text(uri, Json.encodeToString(JsonElement.serializer(), ToolSchemas.toJsonElement(data)), "application/json")
    }
}

/**
 * Supplies resources for one area of the device (photos, files, contacts, …). Registered
 * through [DroidMcp.Builder.addResourceProvider]; each module exposes its own, e.g.
 * `MediaTools.resources(context)`.
 *
 * Like [McpTool.execute], [list] and [read] run on `Dispatchers.IO` and must not throw for an
 * expected failure: return an empty list, or null from [read], instead.
 */
interface ResourceProvider {
    /**
     * Tools whose data this provider serves (e.g. `read_contact` for contacts). The provider is
     * hidden — not listed, not readable — unless all of them are registered, enabled and visible,
     * so gating a tool off also gates the same data as a resource.
     */
    val requiredTools: Set<String> get() = emptySet()

    /** Templates this provider resolves; advertised by `resources/templates/list`. */
    val templates: List<McpResourceTemplate> get() = emptyList()

    /** Concrete resources to advertise. Keep it bounded (e.g. the most recent items). */
    suspend fun list(): List<McpResource> = emptyList()

    /** Whether [uri] belongs to this provider. Checked before [read]; must be cheap. */
    fun handles(uri: String): Boolean

    /** The contents at [uri], or null when it doesn't exist or can't be read (e.g. a missing permission). */
    suspend fun read(uri: String): List<ResourceContents>?
}

/**
 * Runs provider code on `Dispatchers.IO`, bounded by [timeoutMs]: null on timeout, a failed
 * [Result] when the provider throws. Like [ToolRegistry.executeTool], a cancellation the provider
 * leaks (e.g. its own `withTimeout`) is a failure, while cancellation of the caller propagates.
 */
internal suspend fun <T> runProvider(timeoutMs: Long, block: suspend () -> T): Result<T>? =
    withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

/**
 * Thread-safe collection of [ResourceProvider]s. Changes are reported to [addChangeListener]
 * subscribers so the HTTP transport can emit `notifications/resources/list_changed`; providers
 * whose listing changes on their own call [notifyChanged].
 */
class ResourceRegistry {
    private val providers = CopyOnWriteArrayList<ResourceProvider>()
    private val listeners = ChangeListeners()

    fun register(provider: ResourceProvider) {
        providers += provider
        notifyChanged()
    }

    fun unregister(provider: ResourceProvider) {
        if (providers.remove(provider)) notifyChanged()
    }

    fun providers(): List<ResourceProvider> = providers.toList()

    /** Providers whose [ResourceProvider.requiredTools] are all in [visibleTools]. */
    fun visibleProviders(visibleTools: Set<String>): List<ResourceProvider> =
        providers.filter { visibleTools.containsAll(it.requiredTools) }

    /** The provider among [visibleTools]-visible ones that [handles][ResourceProvider.handles] [uri], or null. */
    fun providerFor(uri: String, visibleTools: Set<String>): ResourceProvider? =
        visibleProviders(visibleTools).firstOrNull { runCatching { it.handles(uri) }.getOrDefault(false) }

    /**
     * Every [visibleTools]-visible provider's resources with the provider that listed them, queried
     * in parallel. A provider that fails or overruns [timeoutMs] contributes nothing.
     */
    internal suspend fun listVisible(visibleTools: Set<String>, timeoutMs: Long): List<Pair<ResourceProvider, McpResource>> =
        coroutineScope {
            visibleProviders(visibleTools)
                .map { provider ->
                    async { runProvider(timeoutMs) { provider.list() }?.getOrNull().orEmpty().map { provider to it } }
                }
                .awaitAll()
                .flatten()
        }

    fun addChangeListener(listener: () -> Unit) = listeners.add(listener)

    fun removeChangeListener(listener: () -> Unit) = listeners.remove(listener)

    /** Tell subscribers the advertised resources changed. A failing listener can't affect the others. */
    fun notifyChanged() = listeners.notifyChanged()
}
