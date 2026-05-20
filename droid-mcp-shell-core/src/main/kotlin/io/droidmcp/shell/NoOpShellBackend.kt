package io.droidmcp.shell

/**
 * [ShellBackend] that's always unavailable. Useful as a fallback in the
 * "use root if available, Shizuku otherwise" dispatch pattern documented
 * in `docs/ROOT.md` — when neither tier is reachable on the current device,
 * every tool errors with `shell_unavailable: NoOp` so the LLM sees a
 * structured failure instead of unexpected behaviour.
 *
 * Example:
 * ```kotlin
 * val backend: ShellBackend = when {
 *     RootShellBackend().isAvailable() -> RootShellBackend()
 *     ShizukuShellBackend().isAvailable() -> ShizukuShellBackend()
 *     else -> NoOpShellBackend
 * }
 * val tools = ShellTools.all(context, backend)
 * ```
 */
object NoOpShellBackend : ShellBackend {

    override val name: String = "NoOp"

    override fun isAvailable(): Boolean = false

    override suspend fun exec(command: String, args: List<String>): ShellResult =
        throw ShellException.NotAvailable("no shell backend configured (host registered NoOpShellBackend)")
}
