package io.droidmcp.shell

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Locks in `ListAppPermissionsTool`'s dumpsys-package parser. Without these
 * tests, the parser silently regresses to either:
 *  - dropping legitimate permissions whose lines have OEM trailing fields
 *  - or matching non-permission `key=value` lines (`enabled`, `userId`,
 *    `targetSdk`, …) as fake permissions
 */
class PermissionsParseTest {

    private val context: android.content.Context = mockk(relaxed = true)

    @Test
    fun `bare name in install permissions is auto-granted`() = runTest {
        val stdout = """
            install permissions:
              android.permission.INTERNET
              android.permission.ACCESS_NETWORK_STATE
        """.trimIndent()
        val perms = parse(stdout)
        assertThat(perms).hasSize(2)
        assertThat(perms[0]["name"]).isEqualTo("android.permission.INTERNET")
        assertThat(perms[0]["granted"]).isEqualTo(true)
        assertThat(perms[1]["granted"]).isEqualTo(true)
    }

    @Test
    fun `bare name in requested permissions stays granted=false`() = runTest {
        val stdout = """
            requested permissions:
              android.permission.READ_CONTACTS
              android.permission.WRITE_CONTACTS
        """.trimIndent()
        val perms = parse(stdout)
        assertThat(perms).hasSize(2)
        perms.forEach { assertThat(it["granted"]).isEqualTo(false) }
    }

    @Test
    fun `runtime permission with explicit granted=true captures granted and flags`() = runTest {
        val stdout = """
            runtime permissions:
              android.permission.READ_CONTACTS: granted=true, flags=[USER_SET]
        """.trimIndent()
        val perms = parse(stdout)
        assertThat(perms).hasSize(1)
        assertThat(perms[0]["name"]).isEqualTo("android.permission.READ_CONTACTS")
        assertThat(perms[0]["granted"]).isEqualTo(true)
        assertThat(perms[0]["flags"]).isEqualTo("USER_SET")
    }

    @Test
    fun `runtime permission with explicit granted=false reflects that`() = runTest {
        val stdout = """
            runtime permissions:
              android.permission.READ_SMS: granted=false, flags=[]
        """.trimIndent()
        val perms = parse(stdout)
        assertThat(perms).hasSize(1)
        assertThat(perms[0]["granted"]).isEqualTo(false)
    }

    @Test
    fun `OEM trailing fields don't break the line — granted and flags still captured`() = runTest {
        val stdout = """
            runtime permissions:
              android.permission.READ_CALL_LOG: granted=true, flags=[USER_SET], restricted=true, gids=[10001, 3003]
        """.trimIndent()
        val perms = parse(stdout)
        assertThat(perms).hasSize(1)
        assertThat(perms[0]["granted"]).isEqualTo(true)
        assertThat(perms[0]["flags"]).isEqualTo("USER_SET")
    }

    @Test
    fun `non-permission key=value lines outside permission sections are NOT matched`() = runTest {
        val stdout = """
            Package [com.example.app] (abc123):
              userId=10123
              firstInstallTime=2026-01-01 12:00:00
              lastUpdateTime=2026-05-20 09:00:00
              targetSdk=33
              enabled=true
            install permissions:
              android.permission.INTERNET
            runtime permissions:
              android.permission.READ_CONTACTS: granted=true, flags=[USER_SET]
        """.trimIndent()
        val perms = parse(stdout)
        // Only the two real permissions should appear — no userId/firstInstallTime/etc.
        assertThat(perms.map { it["name"] }).containsExactly(
            "android.permission.INTERNET",
            "android.permission.READ_CONTACTS",
        )
    }

    @Test
    fun `non-permissions section header clears currentSection`() = runTest {
        // After `runtime permissions:` we enter the runtime section. If a
        // subsequent unrelated section header (`usesLibraries:`) appears,
        // bare dotted identifiers within IT must NOT be picked up.
        val stdout = """
            runtime permissions:
              android.permission.READ_CONTACTS: granted=true
            usesLibraries:
              org.apache.http.legacy
              androidx.window.extensions
        """.trimIndent()
        val perms = parse(stdout)
        assertThat(perms.map { it["name"] }).containsExactly("android.permission.READ_CONTACTS")
    }

    @Test
    fun `bare names without a dot are rejected`() = runTest {
        // Defensive: even inside a permissions section, a bare name like
        // `enabled` shouldn't get picked up (real Android permissions are
        // always dotted).
        val stdout = """
            install permissions:
              enabled
              android.permission.INTERNET
        """.trimIndent()
        val perms = parse(stdout)
        assertThat(perms.map { it["name"] }).containsExactly("android.permission.INTERNET")
    }

    private suspend fun parse(stdout: String): List<Map<String, Any?>> {
        val shell = FakeShellBackend().apply {
            stub("dumpsys", listOf("package", "com.example.app"), ShellResult.ofText(0, stdout, ""))
        }
        val result = ListAppPermissionsTool(shell).execute(mapOf("package_name" to "com.example.app"))
        assertThat(result.isSuccess).isTrue()
        @Suppress("UNCHECKED_CAST")
        return result.data?.get("permissions") as List<Map<String, Any?>>
    }
}
