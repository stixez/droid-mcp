package io.droidmcp.print

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Renders text or HTML content in an off-screen [WebView] and hands it to [PrintManager.print],
 * which opens the system print UI. Plain text is HTML-escaped and wrapped in a monospace `<pre>`
 * block; pass `is_html = true` to print raw HTML as-is.
 *
 * No permissions required, but an [Activity] is: `PrintManager.print()` must be called with an
 * Activity context. The Activity comes from [activityProvider] (see [PrintTools.all]) or from
 * [context] if it is itself an Activity; if neither yields a live Activity the tool returns an
 * error. The WebView is created and loaded on the main thread; the call suspends until the page
 * has loaded and the print job has been handed to the system (or [LOAD_TIMEOUT_MS] elapses).
 * The WebView is strongly held by the print adapter wrapper and destroyed when the print flow
 * finishes (or immediately on failure/timeout).
 *
 * `success` indicates the print dialog was launched, not that anything was printed.
 *
 * Output keys: `success`, `job_name`, `content_length`, `message`, `job_id` (may be null).
 */
class PrintContentTool(
    private val context: Context,
    private val activityProvider: () -> Activity? = { null },
) : McpTool {

    override val name = "print_content"
    override val description = "Send content to the system print dialog. Supports plain text (wrapped in HTML) and HTML content. Requires the host app to have a foreground Activity."
    override val parameters = listOf(
        ToolParameter("content", "Text or HTML content to print", ParameterType.STRING, required = true),
        ToolParameter("job_name", "Print job name (default: 'droid-mcp print')", ParameterType.STRING),
        ToolParameter("is_html", "Whether content is HTML (default: false, wraps text in basic HTML)", ParameterType.BOOLEAN),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val content = params["content"]?.toString()
            ?: return ToolResult.error("content is required")
        val jobName = params["job_name"]?.toString() ?: "droid-mcp print"
        val isHtml = params["is_html"] as? Boolean ?: false

        val activity = activityProvider() ?: (context as? Activity)
            ?: return ToolResult.error(
                "No foreground Activity available — printing requires an Activity context. " +
                    "Configure PrintTools.all(context, activityProvider) with the host's current Activity.",
            )
        if (activity.isFinishing || activity.isDestroyed) {
            return ToolResult.error("The host Activity is finishing or destroyed; cannot open the print dialog")
        }

        val htmlContent = if (isHtml) {
            content
        } else {
            "<html><body><pre style=\"font-family: monospace; white-space: pre-wrap;\">${
                content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            }</pre></body></html>"
        }

        return try {
            val jobId = withContext(Dispatchers.Main) {
                withTimeout(LOAD_TIMEOUT_MS) { printOnMain(activity, htmlContent, jobName) }
            }
            ToolResult.success(mapOf(
                "success" to true,
                "job_name" to jobName,
                "content_length" to content.length,
                "message" to "Print dialog opened",
                "job_id" to jobId,
            ))
        } catch (e: TimeoutCancellationException) {
            ToolResult.error("Timed out after ${LOAD_TIMEOUT_MS}ms waiting for the content to render")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.error("Failed to print: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Must run on the main thread. Returns the print job id (if the system reported one). */
    private suspend fun printOnMain(activity: Activity, html: String, jobName: String): String? =
        suspendCancellableCoroutine { cont ->
            val webView = WebView(activity)
            var handedOff = false
            cont.invokeOnCancellation {
                // Cancellation from withTimeout on Dispatchers.Main runs on the main thread.
                if (!handedOff) webView.post { webView.destroy() }
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (!cont.isActive || handedOff) return
                    try {
                        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                            ?: throw IllegalStateException("PrintManager not available")
                        val adapter = DestroyingAdapter(view.createPrintDocumentAdapter(jobName), view)
                        val job = printManager.print(jobName, adapter, PrintAttributes.Builder().build())
                        handedOff = true
                        cont.resume(job.id?.toString())
                    } catch (e: Exception) {
                        view.destroy()
                        handedOff = true
                        cont.resumeWith(Result.failure(e))
                    }
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }

    /**
     * Delegating adapter that keeps a strong reference to the backing [WebView] for the lifetime
     * of the print flow and destroys it in [onFinish].
     */
    private class DestroyingAdapter(
        private val delegate: PrintDocumentAdapter,
        private var webView: WebView?,
    ) : PrintDocumentAdapter() {
        override fun onStart() = delegate.onStart()

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?,
        ) = delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?,
        ) = delegate.onWrite(pages, destination, cancellationSignal, callback)

        override fun onFinish() {
            try {
                delegate.onFinish()
            } finally {
                webView?.destroy()
                webView = null
            }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
