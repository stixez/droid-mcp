package io.droidmcp.shell

/**
 * Input validators shared by the shell-based tools. Centralised so the error
 * codes (`invalid_package_name`, `invalid_permission`, etc.) stay consistent
 * across pm / am / settings / standby tools.
 */
internal object ShellValidation {

    /**
     * Android package names: letters / digits / underscores in dot-separated
     * segments, each segment starting with a letter. Reject `..`, leading
     * dots, spaces, shell metacharacters.
     */
    private val packageNamePattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*$""")

    /**
     * Android permission names: typically a fully-qualified dotted identifier
     * (e.g. `android.permission.READ_SMS`) or a system shorthand. Accept any
     * dot-separated identifier with uppercase + underscores allowed.
     */
    private val permissionPattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$""")

    /**
     * Settings keys: lowercase letters / digits / underscores. Some namespaces
     * use uppercase or mixed-case — be permissive about case, strict about
     * absence of metacharacters.
     */
    private val settingsKeyPattern = Regex("""^[A-Za-z][A-Za-z0-9_]*$""")

    fun requirePackageName(value: String?): Result<String> {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return failure("invalid_package_name", "package_name is required")
        if (!packageNamePattern.matches(v)) return failure("invalid_package_name", "package_name '$v' is not a valid Android package identifier")
        return Result.success(v)
    }

    fun requirePermission(value: String?): Result<String> {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return failure("invalid_permission", "permission is required")
        if (!permissionPattern.matches(v)) return failure("invalid_permission", "permission '$v' is not a valid identifier")
        return Result.success(v)
    }

    fun requireSettingsKey(value: String?): Result<String> {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return failure("invalid_settings_key", "key is required")
        if (!settingsKeyPattern.matches(v)) return failure("invalid_settings_key", "key '$v' contains invalid characters")
        return Result.success(v)
    }

    /**
     * Settings values are free-text but must not contain unescaped shell
     * metacharacters that could break out of the command. We pass values via
     * `ShellBackend.exec(command, args)` which keeps them as discrete argv
     * entries (no shell interpolation), so this just rejects null/oversize.
     */
    fun requireSettingsValue(value: String?, maxLength: Int = 4096): Result<String> {
        if (value == null) return failure("invalid_settings_value", "value is required")
        if (value.length > maxLength) return failure("invalid_settings_value", "value exceeds $maxLength characters")
        return Result.success(value)
    }

    private fun failure(code: String, detail: String): Result<String> =
        Result.failure(ShellValidationFailure(code, detail))
}

internal class ShellValidationFailure(val code: String, val detail: String) :
    Exception("$code: $detail")
