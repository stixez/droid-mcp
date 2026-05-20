package io.droidmcp.shell

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/** `pm grant <pkg> <perm>` — grant a runtime permission without prompting. Idempotent. */
class GrantPermissionTool(private val shell: ShellBackend) : McpTool {
    override val name = "grant_permission"
    override val description = "Grant a runtime permission to an app via `pm grant`, bypassing the user prompt. Idempotent. Permission must be one the app declared in its manifest."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
        ToolParameter("permission", "Fully-qualified permission name, e.g. 'android.permission.READ_CONTACTS'.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        val perm = ShellValidation.requirePermission(params["permission"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("pm", listOf("grant", pkg, perm)) { result ->
            if (result.isSuccess && result.stderr.isEmpty()) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg, "permission" to perm))
            } else {
                // `pm grant` prints to stderr on failure (not declared / not runtime / etc.)
                ToolResult.error("grant_failed", result.stderr.lineSequence().firstOrNull()?.take(200) ?: "unknown")
            }
        }
    }
}

/** `pm revoke <pkg> <perm>` — revoke a runtime permission. Idempotent. */
class RevokePermissionTool(private val shell: ShellBackend) : McpTool {
    override val name = "revoke_permission"
    override val description = "Revoke a runtime permission via `pm revoke`. Idempotent. The app sees the permission as denied next time it queries."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
        ToolParameter("permission", "Fully-qualified permission name.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        val perm = ShellValidation.requirePermission(params["permission"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("pm", listOf("revoke", pkg, perm)) { result ->
            if (result.isSuccess && result.stderr.isEmpty()) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg, "permission" to perm))
            } else {
                ToolResult.error("revoke_failed", result.stderr.lineSequence().firstOrNull()?.take(200) ?: "unknown")
            }
        }
    }
}

/** `dumpsys package <pkg>` parsed for granted permissions. */
class ListAppPermissionsTool(private val shell: ShellBackend) : McpTool {
    override val name = "list_app_permissions"
    override val description = "List all runtime, normal, and dangerous permissions held (or requested-but-not-granted) by an app. Parsed from `dumpsys package <pkg>`."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("dumpsys", listOf("package", pkg)) { result ->
            if (!result.isSuccess) {
                return@gatedExec ToolResult.error("dumpsys_failed", "dumpsys package $pkg exited ${result.exitCode}")
            }
            val perms = parseDumpsysPermissions(result.stdout)
            ToolResult.success(mapOf(
                "package_name" to pkg,
                "permissions" to perms,
                "count" to perms.size,
            ))
        }
    }

    /**
     * Pull permission lines out of `dumpsys package` output. Relevant sections:
     *
     *   requested permissions:
     *     android.permission.INTERNET
     *   install permissions:
     *     android.permission.INTERNET: granted=true
     *   runtime permissions:
     *     android.permission.READ_CONTACTS: granted=true, flags=[USER_SET]
     *
     * Some API levels / OEM skins emit trailing fields (`gids=[...]`,
     * `restricted=true`) on the same line. We use anchored `find` (not
     * `matchEntire`) so those don't silently skip the line.
     *
     * Install permissions appearing in bare form (no `granted=` tail) are
     * auto-granted — we default `granted = true` in that case so the LLM
     * doesn't see a misleading `granted=false`.
     */
    private fun parseDumpsysPermissions(stdout: String): List<Map<String, Any?>> {
        val byName = linkedMapOf<String, MutableMap<String, Any?>>()
        var currentSection: Section? = null
        for (raw in stdout.lineSequence()) {
            val line = raw.trim()
            // Section header detection: any line ending in `:` resets the
            // section. If it's a permissions-flavoured header, set to one of
            // our three; otherwise clear (so non-permissions `key=value`
            // lines emitted after e.g. `usesLibraries:` aren't matched as
            // permissions).
            if (line.endsWith(":")) {
                currentSection = when {
                    line.startsWith("install permissions") -> Section.INSTALL
                    line.startsWith("runtime permissions") -> Section.RUNTIME
                    line.startsWith("requested permissions") -> Section.REQUESTED
                    else -> null
                }
                continue
            }
            // Only attempt permission matching INSIDE a permissions section —
            // and require a dotted identifier so bare `enabled` / `userId` /
            // `targetSdk` are rejected.
            if (currentSection == null) continue
            val nameMatch = PERM_NAME.find(line) ?: continue
            val permName = nameMatch.groupValues[1]
            val grantedMatch = PERM_GRANTED.find(line)
            val flagsMatch = PERM_FLAGS.find(line)

            val entry = byName.getOrPut(permName) {
                mutableMapOf(
                    "name" to permName,
                    "granted" to false,
                    "flags" to null,
                )
            }

            when {
                // Explicit `granted=true|false` overrides default. (`REQUESTED`
                // section just lists names without grant state — skip its
                // grant updates so install/runtime sections remain
                // authoritative.)
                grantedMatch != null && currentSection != Section.REQUESTED ->
                    entry["granted"] = grantedMatch.groupValues[1].toBooleanStrictOrNull() ?: false
                // Bare permission name inside `install permissions:` is auto-granted.
                grantedMatch == null && currentSection == Section.INSTALL ->
                    entry["granted"] = true
                // Bare name in REQUESTED / RUNTIME without explicit grant — leave default false.
                else -> Unit
            }
            if (flagsMatch != null) entry["flags"] = flagsMatch.groupValues[1]
        }
        return byName.values.toList()
    }

    private enum class Section { REQUESTED, INSTALL, RUNTIME }

    private companion object {
        // Permission names are always dotted: at least one `.` separating an
        // initial identifier from one or more sub-identifiers. This rejects
        // bare `userId`, `enabled`, `targetSdk` etc. emitted by dumpsys
        // outside permissions sections. Examples accepted:
        //   "android.permission.INTERNET"
        //   "com.vendor.X.Y"
        //   "android.permission.READ_CONTACTS: granted=true, flags=[USER_SET]"
        private val PERM_NAME = Regex("""^([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)+)""")
        private val PERM_GRANTED = Regex("""granted=(true|false)""")
        private val PERM_FLAGS = Regex("""flags=\[([^\]]*)\]""")
    }
}
