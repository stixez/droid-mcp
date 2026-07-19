package io.droidmcp.shell

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.ToolAnnotations
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ShellToolsTest {

    private val context: android.content.Context = mockk(relaxed = true)

    @Test
    fun `provider exposes all 17 shell tools`() {
        val shell = FakeShellBackend()
        val names = ShellTools.all(context, shell).map { it.name }.toSet()
        assertThat(names).containsExactlyElementsIn(ShellTools.allNames())
        assertThat(names).hasSize(17)
    }

    @Test
    fun `shell tools error with shell_unavailable when backend is not available`() = runTest {
        val shell = FakeShellBackend(available = false)
        val result = ForceStopAppTool(shell).execute(mapOf("package_name" to "com.x"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("shell_unavailable")
    }

    @Test
    fun `force_stop_app validates package name`() = runTest {
        val shell = FakeShellBackend()
        val result = ForceStopAppTool(shell).execute(mapOf("package_name" to "not a valid pkg!"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("invalid_package_name")
    }

    @Test
    fun `force_stop_app sends correct argv`() = runTest {
        val shell = FakeShellBackend()
        val result = ForceStopAppTool(shell).execute(mapOf("package_name" to "com.example.app"))
        assertThat(result.isSuccess).isTrue()
        assertThat(shell.invocations).containsExactly("am" to listOf("force-stop", "com.example.app"))
    }

    @Test
    fun `uninstall_app default is replace=false equivalent (no -r)`() = runTest {
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("Success") }
        UninstallAppTool(shell).execute(mapOf("package_name" to "com.x"))
        assertThat(shell.invocations.single()).isEqualTo("pm" to listOf("uninstall", "com.x"))
    }

    @Test
    fun `uninstall_app with keep_data passes -k`() = runTest {
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("Success") }
        UninstallAppTool(shell).execute(mapOf("package_name" to "com.x", "keep_data" to true))
        assertThat(shell.invocations.single()).isEqualTo("pm" to listOf("uninstall", "-k", "com.x"))
    }

    @Test
    fun `install_apk default is replace=true (adds -r)`() = runTest {
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("Success: \n") }
        InstallApkTool(shell).execute(mapOf("path" to "/sdcard/test.apk"))
        assertThat(shell.invocations.single()).isEqualTo("pm" to listOf("install", "-r", "/sdcard/test.apk"))
    }

    @Test
    fun `put_secure_setting builds correct argv`() = runTest {
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("") }
        PutSecureSettingTool(shell).execute(mapOf("key" to "mock_location", "value" to "1"))
        assertThat(shell.invocations.single()).isEqualTo("settings" to listOf("put", "secure", "mock_location", "1"))
    }

    @Test
    fun `put_global_setting rejects shell-meta in key`() = runTest {
        val shell = FakeShellBackend()
        val result = PutGlobalSettingTool(shell).execute(mapOf("key" to "evil; rm -rf /", "value" to "1"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("invalid_settings_key")
    }

    @Test
    fun `grant_permission validates both args`() = runTest {
        val shell = FakeShellBackend()
        val bad1 = GrantPermissionTool(shell).execute(mapOf("package_name" to "bad pkg", "permission" to "android.permission.X"))
        assertThat(bad1.errorMessage).contains("invalid_package_name")
        val bad2 = GrantPermissionTool(shell).execute(mapOf("package_name" to "com.x", "permission" to "bad perm"))
        assertThat(bad2.errorMessage).contains("invalid_permission")
    }

    @Test
    fun `set_app_standby_bucket validates bucket name`() = runTest {
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("") }
        val ok = SetAppStandbyBucketTool(shell).execute(mapOf("package_name" to "com.x", "bucket" to "rare"))
        assertThat(ok.isSuccess).isTrue()
        assertThat(shell.invocations.single()).isEqualTo("am" to listOf("set-standby-bucket", "com.x", "rare"))

        val bad = SetAppStandbyBucketTool(shell).execute(mapOf("package_name" to "com.x", "bucket" to "invalid"))
        assertThat(bad.isSuccess).isFalse()
        assertThat(bad.errorMessage).contains("invalid_args")
    }

    @Test
    fun `annotations are correct across the tool set`() {
        val shell = FakeShellBackend()
        val tools = ShellTools.all(context, shell).associateBy { it.name }

        // Read tools
        assertThat(tools["list_app_permissions"]?.annotations).isEqualTo(
            ToolAnnotations(readOnlyHint = true, idempotentHint = true)
        )
        assertThat(tools["get_top_window"]?.annotations).isEqualTo(
            ToolAnnotations(readOnlyHint = true, idempotentHint = true)
        )
        assertThat(tools["capture_screen_quiet"]?.annotations).isEqualTo(
            ToolAnnotations(readOnlyHint = true)
        )

        // Idempotent destructive
        assertThat(tools["enable_app"]?.annotations).isEqualTo(
            ToolAnnotations(destructiveHint = true, idempotentHint = true)
        )
        assertThat(tools["grant_permission"]?.annotations).isEqualTo(
            ToolAnnotations(destructiveHint = true, idempotentHint = true)
        )
        assertThat(tools["put_secure_setting"]?.annotations).isEqualTo(
            ToolAnnotations(destructiveHint = true, idempotentHint = true)
        )

        // Pure destructive
        assertThat(tools["force_stop_app"]?.annotations).isEqualTo(ToolAnnotations(destructiveHint = true))
        assertThat(tools["install_apk"]?.annotations).isEqualTo(ToolAnnotations(destructiveHint = true))
        assertThat(tools["uninstall_app"]?.annotations).isEqualTo(ToolAnnotations(destructiveHint = true))
        assertThat(tools["clear_app_data"]?.annotations).isEqualTo(ToolAnnotations(destructiveHint = true))
        assertThat(tools["disable_app"]?.annotations).isEqualTo(
            ToolAnnotations(destructiveHint = true, idempotentHint = true)
        )
        assertThat(tools["run_shell"]?.annotations).isEqualTo(ToolAnnotations(destructiveHint = true))
    }
}
