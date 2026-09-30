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
 *    Allowlist matching is against the split tokens.
 *
 * **Allowlist enforcement**: the request's argv — the whitespace-split command
 * line in string form, or `[command] + args` verbatim in argv form — is checked
 * against [ShellAllowlist.isAllowed], a token-by-token prefix match against the
 * host-registered entries (see [ShellAllowlist]). If it doesn't match (including
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
 * `stdout_truncated`, and `stderr_truncated`. The `*_truncated` flags are also
 * set when the backend itself killed the process for exceeding its capture cap
 * (then `exit_code` is `-1`). Note the raw process `exit_code`
 * is reported as data: a non-zero exit is still a successful tool call.
 */
class RunShellTool(private val shell: ShellBackend) : McpTool {

    override val name = "run_shell"
    override val description = "Run an arbitrary shell command via the backend. **Host-gated** — refuses unless the host has allowlisted a matching command prefix via `ShellAllowlist.set(...)`. Prefer the argv form (`args` array) for anything containing whitespace or quotes; the legacy string form whitespace-splits naively."
    override val parameters = listOf(
        ToolParameter("command", "Bin name (argv form) or full command line (string form). The host allowlist is matched token-by-token against the leading argv entries: in string form the whitespace-split command line, in argv form `command` followed by `args` verbatim (an arg containing a space never matches an allowlist token).", ParameterType.STRING, required = true),
        ToolParameter("args", "Optional argv array. When set, each entry is passed as a discrete argument with no shell tokenisation (so quoted strings, paths with spaces, etc. just work).", ParameterType.ARRAY, required = false),
        ToolParameter("max_stdout_bytes", "Truncate stdout above this many bytes (1024-65536, default 8192). Same cap is applied to stderr (bytes, not characters).", ParameterType.INTEGER, required = false, minimum = 1024.0, maximum = 65536.0),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val rawCommand = params["command"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ToolResult.error("invalid_args", "command is required")

        val argvParam = params["args"] as? List<*>
        val (binName, argv, allowlistArgv) = if (argvParam != null) {
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
            Triple(rawCommand, args, listOf(rawCommand) + args)
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
            Triple(bin, args, listOf(bin) + args)
        }

        if (!ShellAllowlist.isAllowed(allowlistArgv)) {
            return ToolResult.error(
                "run_shell_not_enabled",
                "host has not allowlisted this command prefix; current allowlist: ${ShellAllowlist.snapshot().joinToString(prefix = "[", postfix = "]")}",
            )
        }

        val cap = (params["max_stdout_bytes"] as? Number)?.toInt()?.coerceIn(1024, 65_536) ?: 8192

        return shell.gatedExec(binName, argv) { result ->
            val outBytes = result.stdoutBytes
            // outputTruncated: the backend hit its own capture cap and killed the process.
            val truncatedOut = outBytes.size > cap || result.outputTruncated
            val outString = if (outBytes.size > cap) {
                outBytes.copyOf(cap).toString(Charsets.UTF_8) + "\n…[truncated ${outBytes.size - cap} bytes]"
            } else {
                result.stdout
            }
            // Apply the same byte-cap to stderr so the units stay consistent.
            val errBytes = result.stderr.toByteArray(Charsets.UTF_8)
            val truncatedErr = errBytes.size > cap || result.outputTruncated
            val errString = if (errBytes.size > cap) {
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
 * Process-global allowlist for `run_shell`. The host sets a set of command
 * prefixes (e.g. `"pm list packages"`, `"dumpsys battery"`, `"settings get global"`)
 * at startup; the tool refuses any command whose argv doesn't start with one of
 * them.
 *
 * **Token matching.** Each entry is whitespace-tokenized; it matches a request
 * only if its tokens are exactly equal to the request's first N argv entries
 * (N = entry token count). `"pm list"` matches argv `[pm, list, packages]` but
 * NOT `[pmx, ...]`, `[pm, listx]` or `[pm, "list packages"]`. Entries therefore
 * can't express an argument containing whitespace. Trailing arguments beyond the
 * entry are unrestricted — so allowlist the narrowest prefix that works.
 *
 * **Interpreter entries are rejected.** An entry whose first token (or its
 * basename, e.g. `/system/bin/sh`) is in [FORBIDDEN_LEADING_COMMANDS] — shells,
 * `toybox`/`busybox`, `su`, `app_process`, `env`, `xargs`, `nohup`, `timeout`,
 * etc. — would let any command run as the shell/root UID, defeating the
 * allowlist. [set] throws [IllegalArgumentException] for such entries (and for
 * blank entries). Commands that can themselves spawn subprocesses given the right
 * arguments (`find -exec`, `awk 'BEGIN{system(...)}'`, `am`-started
 * instrumentation) are not all enumerable — review each entry with that in mind.
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

    /**
     * Leading commands that are refused as allowlist entries because they execute
     * arbitrary other commands (or a script) from their arguments.
     */
    val FORBIDDEN_LEADING_COMMANDS: Set<String> = setOf(
        // shells
        "sh", "bash", "zsh", "ash", "dash", "mksh", "ksh", "csh", "tcsh", "fish",
        // multi-call binaries (`toybox sh`, `busybox sh`)
        "toybox", "toolbox", "busybox",
        // privilege / identity / namespace changers
        "su", "sudo", "run-as", "runcon", "chroot", "nsenter", "unshare", "setsid", "magisk",
        // Java / dex launchers
        "app_process", "app_process32", "app_process64", "dalvikvm", "dalvikvm32", "dalvikvm64",
        // wrappers that exec their argv
        "env", "nohup", "xargs", "eval", "exec", "command", "builtin", "nice", "ionice", "chrt",
        "taskset", "timeout", "time", "stdbuf", "watch", "flock", "strace", "ltrace", "script",
        // script interpreters
        "awk", "gawk", "mawk", "nawk", "perl", "python", "python2", "python3", "lua", "ruby", "php", "node",
    )

    @Volatile
    private var prefixes: Set<String> = emptySet()

    @Volatile
    private var tokenized: List<List<String>> = emptyList()

    /**
     * Replace the allowlist.
     *
     * @throws IllegalArgumentException if any entry is blank or starts with a command
     *   in [FORBIDDEN_LEADING_COMMANDS]. The previous allowlist is kept in that case.
     */
    fun set(allowed: Set<String>) {
        val parsed = allowed.map { entry ->
            val tokens = tokenize(entry)
            require(tokens.isNotEmpty()) { "ShellAllowlist entry must not be blank" }
            val lead = tokens.first().substringAfterLast('/')
            require(lead !in FORBIDDEN_LEADING_COMMANDS) {
                "ShellAllowlist entry '$entry' starts with '$lead', which executes arbitrary commands and would defeat run_shell gating"
            }
            tokens
        }
        // Publish both together-ish: `tokenized` is what isAllowed consults.
        tokenized = parsed
        prefixes = allowed.toSet()
    }

    fun snapshot(): Set<String> = prefixes

    /** String form: whitespace-tokenizes [command] and delegates to the argv overload. */
    fun isAllowed(command: String): Boolean = isAllowed(tokenize(command))

    /**
     * True if some entry's tokens equal the first N entries of [argv] (argv[0] is the
     * bin name). Always false for an empty allowlist.
     */
    fun isAllowed(argv: List<String>): Boolean {
        val entries = tokenized
        if (entries.isEmpty() || argv.isEmpty()) return false
        return entries.any { entry ->
            entry.size <= argv.size && entry.indices.all { i -> entry[i] == argv[i] }
        }
    }

    private fun tokenize(value: String): List<String> =
        value.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    private val WHITESPACE = Regex("""\s+""")
}
