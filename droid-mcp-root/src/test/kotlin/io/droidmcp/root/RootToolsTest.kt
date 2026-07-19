package io.droidmcp.root

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.topjohnwu.superuser.Shell
import io.droidmcp.core.PermissionStatus
import io.droidmcp.shell.ShellTools
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * **Not safe for parallel test execution.** `mockkStatic(Shell::class)` is
 * a process-global static mock; concurrent tests in this class (or anywhere
 * else exercising libsu's `Shell` static API) would race on the mock state.
 * The current `build.gradle.kts` doesn't enable JUnit5 parallel mode; if you
 * ever do, fence these tests with `@Execution(SAME_THREAD)`.
 */
class RootToolsTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(Shell::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Shell::class)
    }

    @Test
    fun `provider exposes the 17 shell-core tools, names mirror Shizuku`() {
        every { Shell.isAppGrantedRoot() } returns true
        val names = RootTools.all(context).map { it.name }.toSet()
        assertThat(names).containsExactlyElementsIn(ShellTools.allNames())
    }

    @Test
    fun `supportedTools mirrors ShellTools allNames`() {
        every { Shell.isAppGrantedRoot() } returns true
        assertThat(RootTools.supportedTools(context)).isEqualTo(ShellTools.allNames())
    }

    @Test
    fun `permissionStatus reports NotGranted when libsu has not been queried`() {
        every { Shell.isAppGrantedRoot() } returns null
        val status = RootTools.permissionStatus(context)
        assertThat(status).isInstanceOf(PermissionStatus.NotGranted::class.java)
        assertThat(status.message).contains("not been checked")
    }

    @Test
    fun `permissionStatus reports NotGranted when libsu denied access`() {
        every { Shell.isAppGrantedRoot() } returns false
        val status = RootTools.permissionStatus(context)
        assertThat(status).isInstanceOf(PermissionStatus.NotGranted::class.java)
        assertThat(status.message).contains("denied by the superuser manager")
    }

    @Test
    fun `permissionStatus reports Granted when libsu granted access`() {
        every { Shell.isAppGrantedRoot() } returns true
        val status = RootTools.permissionStatus(context)
        assertThat(status).isInstanceOf(PermissionStatus.Granted::class.java)
        assertThat(status.granted).isTrue()
    }

    @Test
    fun `isRootAvailable returns true only when libsu reports granted=true`() {
        every { Shell.isAppGrantedRoot() } returns null
        assertThat(RootTools.isRootAvailable()).isFalse()

        every { Shell.isAppGrantedRoot() } returns false
        assertThat(RootTools.isRootAvailable()).isFalse()

        every { Shell.isAppGrantedRoot() } returns true
        assertThat(RootTools.isRootAvailable()).isTrue()
    }

    @Test
    fun `hasPermissions always returns true - convention method, runtime perms NA`() {
        assertThat(RootTools.hasPermissions(context)).isTrue()
    }

    @Test
    fun `requiredPermissions is empty - no manifest grants needed`() {
        assertThat(RootTools.requiredPermissions()).isEmpty()
    }
}
