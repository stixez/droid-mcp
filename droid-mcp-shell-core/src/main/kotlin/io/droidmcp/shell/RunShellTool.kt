package io.droidmcp.shell

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Escape hatch — run an arbitrary shell command via the backend.
 *
 * **Default-deny**: the tool refuses unless the host registers an explicit
 * allowlist of command prefixes via [ShellAllowlist]. The host's choice is
 * the security boundary; the LLM cannot bypass it through prompting.
 *
 * Two parameter forms:
 *  - **Argv form** (preferred): pass `command` (the bin name) plus `args`
 *    (an array of argv strings). No tokenisation, quoting just works.
 *  - **String form** (legacy / convenience): pass only `command` containing
 *    the full command line. The tool whitespace-splits — quotes are NOT
 *    honoured, so this form is unsuitable for arguments containing spaces.
 *    Allowlist matching is against the full string.
 *
 * **Allowlist enforcement**: a candidate "allowlist key" is built from the
 * request — the raw command line in string form, or `command` + space-joined
 * `args` in argv form — and checked against [ShellAllowlist.isAllowed] (a
 * prefix match against the host-registered set). If it doesn't match (including
 * the default empty allowlist, which disables the tool entirely), `execute`
 * short-circuits with a `run_shell_not_enabled` [ToolResult.error] whose detail
 * lists the current allowlist snapshot — the command is never spawned.
 *
 * Privilege: requires a working [ShellBackend] AND a host-configured
 * [ShellAllowlist].
 *
 * Params: `command` (required), `args` (optional argv array), `max_stdout_bytes`
 * (optional, clamped 1024–65536, default 8192; the same byte cap is applied to
 * stderr).
 *
 * On success the result map carries `exit_code`, `stdout`, `stderr`,
 * `stdout_truncated`, and `stderr_truncated`. Note the raw process `exit_code`
 * is reported as data: a non-zero exit is still a successful tool call.
 */
class RunShellTool(private val shell: ShellBackend) : McpTool {

    override val name = "run_shell"
    override val description = "Run an arbitrary shell command via the backend. **Host-gated** — refuses unless the host has allowlisted a matching command prefix via `ShellAllowlist.set(...)`. Prefer the argv form (`args` array) for anything containing whitespace or quotes; the legacy string form whitespace-splits naively."
    override val parameters = listOf(
        ToolParameter("command", "Bin name (argv form) or full command line (string form). String form: first token is matched against the host's allowlist. Argv form: allowlist is matched against `command` + space-joined `args` — note that spaces inside arg values are flattened into the reconstruction, so a host using long prefixes like `\"settings put global X \"` sees `args` joined with single spaces and cannot distinguish arg-internal spaces from argv separators.", ParameterType.STRING, required = true),
        ToolParameter("args", "Optional argv array. When set, each entry is passed as a discrete argument with no shell tokenisation (so quoted strings, paths with spaces, etc. just work).", ParameterType.ARRAY, required = false),
        ToolParameter("max_stdout_bytes", "Truncate stdout above this many bytes (1024-65536, default 8192). Same cap is applied to stderr (bytes, not characters).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val rawCommand = params["command"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ToolResult.error("invalid_args", "command is required")

        val argvParam = params["args"] as? List<*>
        val (binName, argv, allowlistKey) = if (argvParam != null) {
            // Argv form: `command` must be a single bin name, not a full command
            // line — a whitespace-containing "bin name" would let the allowlist
            // check see one string while the backend's shell sees several tokens
            // (e.g. shell metacharacters), letting a single allowlisted prefix
            // authorize an arbitrary trailing command.
            if (rawCommand.any { it.isWhitespace() }) {
                return ToolResult.error("invalid_args", "command must be a single bin name (no whitespace) when args is supplied; put the rest in args")
            }
            // Argv form: validate every entry is a string, no tokenisation.
            val args = argvParam.mapIndexed { i, entry ->
                entry as? String ?: return ToolResult.error("invalid_args", "args[$i] is not a string")
            }
            Triple(rawCommand, args, "$rawCommand ${args.joinToString(" ")}")
        } else {
            // String form: whitespace-split (legacy, quote-unaware).
            val firstSpace = rawCommand.indexOf(' ')
            val (bin, args) = if (firstSpace < 0) {
                rawCommand to emptyList()
            } else {
                rawCommand.substring(0, firstSpace) to
                    rawCommand.substring(firstSpace + 1)
                        .split(Regex("""\s+"""))
                        .filter { it.isNotEmpty() }
            }
            Triple(bin, args, rawCommand)
        }

        if (!ShellAllowlist.isAllowed(allowlistKey)) {
            return ToolResult.error(
                "run_shell_not_enabled",
                "host has not allowlisted this command prefix; current allowlist: ${ShellAllowlist.snapshot().joinToString(prefix = "[", postfix = "]")}",
            )
        }

        val cap = (params["max_stdout_bytes"] as? Number)?.toInt()?.coerceIn(1024, 65_536) ?: 8192

        return shell.gatedExec(binName, argv) { result ->
            val outBytes = result.stdoutBytes
            val truncatedOut = outBytes.size > cap
            val outString = if (truncatedOut) {
                outBytes.copyOf(cap).toString(Charsets.UTF_8) + "\n…[truncated ${outBytes.size - cap} bytes]"
            } else {
                result.stdout
            }
            // Apply the same byte-cap to stderr so the units stay consistent.
            val errBytes = result.stderr.toByteArray(Charsets.UTF_8)
            val truncatedErr = errBytes.size > cap
            val errString = if (truncatedErr) {
                errBytes.copyOf(cap).toString(Charsets.UTF_8) + "\n…[truncated ${errBytes.size - cap} bytes]"
            } else {
                result.stderr
            }
            ToolResult.success(mapOf(
                "exit_code" to result.exitCode,
                "stdout" to outString,
                "stderr" to errString,
                "stdout_truncated" to truncatedOut,
                "stderr_truncated" to truncatedErr,
            ))
        }
    }
}

/**
 * Process-global allowlist for `run_shell`. The host sets a set of command-line
 * prefixes (e.g. `"pm "`, `"am "`, `"settings put global "`) at startup; the
 * tool refuses any command not starting with one of those prefixes.
 *
 * Conservative by design: empty allowlist = `run_shell` is disabled. The LLM
 * cannot extend the allowlist; only host code can.
 *
 * **Not safe for parallel test execution.** Tests mutate the global via
 * `set(emptySet())` in `@BeforeEach`/`@AfterEach`. If JUnit5 parallel test
 * execution is enabled in this module, concurrent tests would race on
 * `prefixes`. The current `build.gradle.kts` doesn't enable parallel mode;
 * if you ever do, fence these tests with `@Execution(SAME_THREAD)` or
 * refactor the allowlist out of process-global state.
 */
object ShellAllowlist {

    @Volatile
    private var prefixes: Set<String> = emptySet()

    fun set(allowed: Set<String>) {
        prefixes = allowed.toSet()
    }

    fun snapshot(): Set<String> = prefixes

    fun isAllowed(command: String): Boolean {
        if (prefixes.isEmpty()) return false
        return prefixes.any { command.startsWith(it) }
    }
}
