package io.droidmcp.app

import com.google.common.truth.Truth.assertThat
import io.droidmcp.app.confirm.ApprovalPolicy
import io.droidmcp.app.confirm.ApprovalScope
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.PairingControl
import io.droidmcp.app.data.settings.PairedComputer
import io.droidmcp.app.feature.computers.ComputersIntent
import io.droidmcp.app.feature.computers.ComputersViewModel
import io.droidmcp.app.data.server.ServerStatus
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.settings.AppSettings
import io.droidmcp.app.data.settings.SettingsStore
import io.droidmcp.app.data.settings.ThemeMode
import io.droidmcp.app.data.update.UpdateChecker
import io.droidmcp.app.feature.activity.ActivityEffect
import io.droidmcp.app.feature.activity.ActivityFilter
import io.droidmcp.app.feature.activity.ActivityIntent
import io.droidmcp.app.feature.activity.ActivityLog
import io.droidmcp.app.feature.activity.ActivityViewModel
import io.droidmcp.app.feature.home.HomeEffect
import io.droidmcp.app.feature.home.HomeIntent
import io.droidmcp.app.feature.home.HomeViewModel
import io.droidmcp.app.feature.home.Route
import io.droidmcp.app.feature.settings.SettingsEffect
import io.droidmcp.app.feature.settings.SettingsIntent
import io.droidmcp.app.feature.settings.SettingsViewModel
import io.droidmcp.app.feature.setup.SetupEffect
import io.droidmcp.app.feature.setup.SetupIntent
import io.droidmcp.app.feature.setup.SetupViewModel
import io.droidmcp.app.feature.tools.ToolsIntent
import io.droidmcp.app.feature.tools.ToolsViewModel
import io.droidmcp.core.ToolCallAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.home(
        server: FakeServer = FakeServer(),
        settings: FakeSettingsStore = FakeSettingsStore(),
        network: LocalNetwork = LocalNetwork("192.168.1.7", onWifi = true),
        calls: List<ToolCallAudit> = emptyList(),
    ) = HomeViewModel(
        server = server,
        settings = settings,
        permissions = FakePermissions(setOf("notifications")),
        network = MutableStateFlow(network),
        recentCalls = MutableStateFlow(calls),
        essentials = listOf("notifications", "screen_control"),
        now = { 1_000_000_000_000L },
    ).also { advanceUntilIdle() }

    @Test fun `home generates a token and builds the command`() = runTest(dispatcher) {
        val vm = home()
        val state = vm.state.value
        assertThat(state.token).isEqualTo("token-0123456789abcdef")
        assertThat(state.configText).contains("http://192.168.1.7:8080/mcp")
        assertThat(state.missingEssentials).containsExactly("screen_control")
    }

    @Test fun `home toggles the server`() = runTest(dispatcher) {
        val server = FakeServer()
        val vm = home(server = server)
        vm.onIntent(HomeIntent.ToggleServer)
        advanceUntilIdle()
        assertThat(server.calls).containsExactly("start")
        assertThat(vm.state.value.status).isEqualTo(ServerStatus.Starting)
        vm.onIntent(HomeIntent.ToggleServer)
        advanceUntilIdle()
        assertThat(server.calls).containsExactly("start", "stop").inOrder()
    }

    @Test fun `home falls back to usb without a wifi address`() = runTest(dispatcher) {
        val vm = home(network = LocalNetwork(null, onWifi = false))
        assertThat(vm.state.value.route).isEqualTo(Route.Usb)
        assertThat(vm.state.value.connection?.host).isEqualTo("localhost")
    }

    @Test fun `a pause while closed offers background location once`() = runTest(dispatcher) {
        val server = FakeServer()
        val vm = home(server = server)
        advanceUntilIdle()
        assertThat(vm.state.value.pausedWhileClosed).isFalse()

        server.pausedWhileClosed.value = true
        advanceUntilIdle()
        assertThat(vm.state.value.pausedWhileClosed).isTrue()

        vm.onIntent(HomeIntent.AllowBackgroundLocation)
        val effect = vm.effects.first() as HomeEffect.LaunchGrant
        assertThat(effect.capability.id).isEqualTo(Capabilities.BACKGROUND_LOCATION.id)
        advanceUntilIdle()
        assertThat(vm.state.value.pausedWhileClosed).isFalse()
        // Setup never lists it.
        assertThat(Capabilities.all.map { it.id }).doesNotContain(Capabilities.BACKGROUND_LOCATION.id)
    }

    @Test fun `copy emits the full command`() = runTest(dispatcher) {
        val vm = home()
        vm.onIntent(HomeIntent.CopyConfig)
        val effect = vm.effects.first() as HomeEffect.Copy
        assertThat(effect.text).contains("Bearer token-0123456789abcdef")
        assertThat(effect.sensitive).isTrue()
    }

    @Test fun `home counts today's calls`() = runTest(dispatcher) {
        val now = 1_000_000_000_000L
        val calls = listOf(
            ToolCallAudit(now - 1_000, "read_calendar", "primary", null, true, null, 12),
            ToolCallAudit(now - 3 * 86_400_000L, "get_device_info", "primary", null, true, null, 3),
        )
        val vm = home(calls = calls)
        assertThat(vm.state.value.callsToday).isEqualTo(1)
        assertThat(vm.state.value.lastCall?.toolName).isEqualTo("read_calendar")
    }

    @Test fun `setup finish marks setup complete`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val vm = SetupViewModel(firstRun = true, permissions = FakePermissions(), settings = settings, background = dispatcher)
        advanceUntilIdle()
        vm.onIntent(SetupIntent.Finish)
        advanceUntilIdle()
        assertThat(settings.state.value.setupCompleted).isTrue()
        assertThat(vm.effects.first()).isEqualTo(SetupEffect.Finished)
    }

    @Test fun `setup refresh asks the permission source`() = runTest(dispatcher) {
        val permissions = FakePermissions()
        val vm = SetupViewModel(firstRun = false, permissions = permissions, settings = FakeSettingsStore(), background = dispatcher)
        vm.onIntent(SetupIntent.Refresh)
        assertThat(permissions.refreshes).isEqualTo(1)
    }

    @Test fun `settings rejects a bad port and saves a good one`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val vm = SettingsViewModel(settings, FakeServer(), "1.0")
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.EditPort("80"))
        vm.onIntent(SettingsIntent.SavePort)
        assertThat(vm.state.value.portError).isNotNull()
        vm.onIntent(SettingsIntent.EditPort("9a0b01"))
        assertThat(vm.state.value.portInput).isEqualTo("9001")
        vm.onIntent(SettingsIntent.SavePort)
        advanceUntilIdle()
        assertThat(settings.state.value.port).isEqualTo(9001)
        assertThat(vm.state.value.portChanged).isFalse()
    }

    @Test fun `settings theme choice is saved`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val vm = SettingsViewModel(settings, FakeServer(), "1.0")
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.SetThemeMode(ThemeMode.Dark))
        advanceUntilIdle()
        assertThat(settings.state.value.themeMode).isEqualTo(ThemeMode.Dark)
        assertThat(vm.state.value.themeMode).isEqualTo(ThemeMode.Dark)
    }

    @Test fun `home hint dismissal is remembered`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val vm = home(settings = settings)
        vm.onIntent(HomeIntent.DismissEssentialsHint)
        advanceUntilIdle()
        assertThat(settings.state.value.essentialsHintDismissed).isTrue()
        assertThat(vm.state.value.essentialsHintDismissed).isTrue()
    }

    @Test fun `settings rotate token replaces it`() = runTest(dispatcher) {
        val settings = FakeSettingsStore(AppSettings(token = "old-token-0123456789"))
        val server = FakeServer()
        val vm = SettingsViewModel(settings, server, "1.0")
        vm.onIntent(SettingsIntent.AskRotateToken)
        assertThat(vm.state.value.confirmRotate).isTrue()
        vm.onIntent(SettingsIntent.ConfirmRotateToken)
        advanceUntilIdle()
        assertThat(settings.state.value.token).isEqualTo("rotated-0123456789abcdef")
        assertThat(server.calls).containsExactly("restartIfRunning")
        assertThat(vm.state.value.confirmRotate).isFalse()
    }

    @Test fun `tools toggles go to the server and persist behaviour switches`() = runTest(dispatcher) {
        val server = FakeServer()
        val settings = FakeSettingsStore()
        val vm = ToolsViewModel(server, settings, FakePermissions(), resolve = { _, _ -> emptyList() }, resolveDispatcher = dispatcher)
        advanceUntilIdle()
        assertThat(vm.state.value.loading).isFalse()
        vm.onIntent(ToolsIntent.SetToolEnabled("send_message", false))
        assertThat(server.toggled).containsExactly(setOf("send_message") to false)
        vm.onIntent(ToolsIntent.SetReadOnly(true))
        advanceUntilIdle()
        assertThat(settings.state.value.readOnly).isTrue()
        assertThat(vm.state.value.readOnly).isTrue()
    }

    @Test fun `activity filters failures and clears`() = runTest(dispatcher) {
        val rows = MutableStateFlow(
            listOf(
                ToolCallAudit(2, "send_message", "primary", "{}", false, "declined", 5),
                ToolCallAudit(1, "read_calendar", "primary", "{}", true, null, 9),
            ),
        )
        val log = object : ActivityLog {
            override fun observe(limit: Int) = rows
            override suspend fun clear() { rows.value = emptyList() }
            override suspend fun exportJson() = "[]"
        }
        val vm = ActivityViewModel(log)
        advanceUntilIdle()
        assertThat(vm.state.value.failedCount).isEqualTo(1)
        vm.onIntent(ActivityIntent.SetFilter(ActivityFilter.Failed))
        assertThat(vm.state.value.visible.map { it.toolName }).containsExactly("send_message")
        vm.onIntent(ActivityIntent.AskClear)
        vm.onIntent(ActivityIntent.ConfirmClear)
        advanceUntilIdle()
        assertThat(vm.state.value.items).isEmpty()
        assertThat(vm.effects.take(2).toList())
            .containsExactly(ActivityEffect.DeleteExports, ActivityEffect.Message("Activity cleared")).inOrder()
    }

    @Test fun `a late settings emission doesn't undo a forget or an always`() = runTest(dispatcher) {
        val fake = FakeSettingsStore(AppSettings(alwaysAllowedTools = setOf("send_message")))
        // Writes that haven't landed yet: only older emissions reach the policy.
        val slowWrites = object : SettingsStore by fake {
            override suspend fun update(transform: (AppSettings) -> AppSettings) = awaitCancellation()
        }
        val policy = ApprovalPolicy(slowWrites, this)
        advanceUntilIdle()
        assertThat(policy.isPreApproved("send_message")).isTrue()

        policy.forgetAlways("send_message")
        policy.remember("vibrate", ApprovalScope.Always)
        fake.state.update { it.copy(port = 9000) } // unrelated change, still the old approvals
        advanceUntilIdle()
        assertThat(policy.isPreApproved("send_message")).isFalse()
        assertThat(policy.isPreApproved("vibrate")).isTrue()
        coroutineContext.cancelChildren()
    }

    @Test fun `approval policy remembers and forgets`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        var clock = 1_000L
        val policy = ApprovalPolicy(settings, this, now = { clock })
        advanceUntilIdle()
        assertThat(policy.isPreApproved("vibrate")).isFalse()
        policy.remember("vibrate", ApprovalScope.OneHour)
        assertThat(policy.isPreApproved("vibrate")).isTrue()
        clock += ApprovalPolicy.ONE_HOUR_MS + 1
        assertThat(policy.isPreApproved("vibrate")).isFalse()
        policy.remember("send_message", ApprovalScope.Always)
        advanceUntilIdle()
        assertThat(settings.state.value.alwaysAllowedTools).containsExactly("send_message")
        assertThat(policy.isPreApproved("send_message")).isTrue()
        policy.forgetAlways("send_message")
        advanceUntilIdle()
        assertThat(policy.isPreApproved("send_message")).isFalse()
        coroutineContext.cancelChildren()
    }

    @Test fun `trusting a network while turning the rule on`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val vm = SettingsViewModel(
            settings, FakeServer(), "1.0",
            network = MutableStateFlow(LocalNetwork("192.168.1.2", onWifi = true, ssid = "Home")),
        )
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.SetTrustedWifiOnly(true))
        advanceUntilIdle()
        assertThat(settings.state.value.trustedWifiOnly).isTrue()
        assertThat(settings.state.value.trustedNetworks).containsExactly("Home")
    }

    @Test fun `turning the rule on with the Wi-Fi name hidden asks for access, then trusts the network`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val network = MutableStateFlow(LocalNetwork("192.168.1.2", onWifi = true, ssid = null))
        val vm = SettingsViewModel(settings, FakeServer(), "1.0", network = network)
        advanceUntilIdle()
        val effect = async { vm.effects.first() }
        vm.onIntent(SettingsIntent.SetTrustedWifiOnly(true))
        advanceUntilIdle()
        assertThat((effect.await() as SettingsEffect.LaunchGrant).capability.id).isEqualTo("wifi")
        assertThat(settings.state.value.trustedNetworks).isEmpty()
        network.value = LocalNetwork("192.168.1.2", onWifi = true, ssid = "Home")  // access granted
        advanceUntilIdle()
        assertThat(settings.state.value.trustedNetworks).containsExactly("Home")
        network.value = LocalNetwork("10.0.0.5", onWifi = true, ssid = "Cafe")      // later networks aren't
        advanceUntilIdle()
        assertThat(settings.state.value.trustedNetworks).containsExactly("Home")
    }

    @Test fun `computers are added and revoked through pairing`() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val pairing = object : PairingControl {
            override suspend fun addComputer(name: String): PairedComputer {
                val c = PairedComputer(name, "token-for-$name-0123456789", 1)
                settings.update { it.copy(pairedComputers = it.pairedComputers + c) }
                return c
            }
            override suspend fun revokeComputer(name: String) {
                settings.update { s -> s.copy(pairedComputers = s.pairedComputers.filterNot { it.name == name }) }
            }
        }
        val vm = ComputersViewModel(pairing, settings, MutableStateFlow(LocalNetwork("10.0.0.2", onWifi = true)), MutableStateFlow(emptyList()))
        advanceUntilIdle()
        vm.onIntent(ComputersIntent.AskAdd)
        vm.onIntent(ComputersIntent.EditName("Laptop"))
        vm.onIntent(ComputersIntent.ConfirmAdd)
        advanceUntilIdle()
        assertThat(vm.state.value.computers.map { it.name }).containsExactly("Laptop")
        assertThat(vm.state.value.showingConnection?.claudeCodeCommand()).contains("token-for-Laptop")
        vm.onIntent(ComputersIntent.AskRevoke("Laptop"))
        vm.onIntent(ComputersIntent.ConfirmRevoke)
        advanceUntilIdle()
        assertThat(vm.state.value.computers).isEmpty()
    }

    @Test fun `computer commands use the running server's port, not unsaved settings`() = runTest(dispatcher) {
        // HTTPS switched on in Settings, but the running server still serves plain HTTP on 8080.
        val settings = FakeSettingsStore(AppSettings(tlsEnabled = true, pairedComputers = listOf(PairedComputer("Laptop", "t".repeat(32), 1))))
        val status = MutableStateFlow<ServerStatus>(ServerStatus.Running(port = 8080, tls = false, token = "x", toolCount = 1, startedAt = 0))
        val pairing = object : PairingControl {
            override suspend fun addComputer(name: String) = error("unused")
            override suspend fun revokeComputer(name: String) = Unit
        }
        val vm = ComputersViewModel(pairing, settings, MutableStateFlow(LocalNetwork("10.0.0.2", onWifi = true)), MutableStateFlow(emptyList()), status)
        advanceUntilIdle()
        vm.onIntent(ComputersIntent.Show("Laptop"))
        advanceUntilIdle()
        assertThat(vm.state.value.showingConnection?.claudeCodeCommand()).contains("http://10.0.0.2:8080/mcp")
        // Once stopped, the next start uses the saved settings.
        status.value = ServerStatus.Stopped
        advanceUntilIdle()
        assertThat(vm.state.value.showingConnection?.claudeCodeCommand()).contains("https://10.0.0.2:8443/mcp")
    }

    @Test fun `a failed update check waits an hour instead of every app open`() = runTest(dispatcher) {
        var clock = 10L * 24 * 60 * 60 * 1000
        var calls = 0
        val settings = FakeSettingsStore(AppSettings(checkForUpdates = true))
        val checker = UpdateChecker(settings, "0.13.0", fetchLatestTag = { calls++; null }, now = { clock })
        checker.checkIfDue()
        checker.checkIfDue()
        assertThat(calls).isEqualTo(1)
        clock += 61 * 60 * 1000
        checker.checkIfDue()
        assertThat(calls).isEqualTo(2)
    }
}
