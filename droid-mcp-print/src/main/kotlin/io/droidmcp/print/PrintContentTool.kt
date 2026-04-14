package io.droidmcp.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class PrintContentTool(private val context: Context) : McpTool {

    override val name = "print_content"
    override val description = "Send content to the system print dialog. Supports plain text (wrapped in HTML) and HTML content."
    override val parameters = listOf(
        ToolParameter("content", "Text or HTML content to print", ParameterType.STRING, required = true),
        ToolParameter("job_name", "Print job name (default: 'droid-mcp print')", ParameterType.STRING),
        ToolParameter("is_html", "Whether content is HTML (default: false, wraps text in basic HTML)", ParameterType.BOOLEAN),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val content = params["content"]?.toString()
            ?: return ToolResult.error("content is required")
        val jobName = params["job_name"]?.toString() ?: "droid-mcp print"
        val isHtml = params["is_html"] as? Boolean ?: false

        val htmlContent = if (isHtml) {
            content
        } else {
            "<html><body><pre style=\"font-family: monospace; white-space: pre-wrap;\">${
                content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            }</pre></body></html>"
        }

        return try {
            Handler(Looper.getMainLooper()).post {
                val webView = WebView(context)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val printAdapter = view.createPrintDocumentAdapter(jobName)
                        printManager.print(
                            jobName,
                            printAdapter,
                            PrintAttributes.Builder().build(),
                        )
                    }
                }

                webView.loadDataWithBaseURL(
                    null, htmlContent, "text/html", "UTF-8", null,
                )
            }

            ToolResult.success(mapOf(
                "success" to true,
                "job_name" to jobName,
                "content_length" to content.length,
                "message" to "Print dialog opened",
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to print: ${e.message}")
        }
    }
}
