package io.droidmcp.app

import com.google.common.truth.Truth.assertThat
import io.droidmcp.app.confirm.ConfirmationCenter
import io.droidmcp.app.data.permissions.Capability
import io.droidmcp.app.data.permissions.CapabilitySection
import io.droidmcp.app.data.permissions.GrantMethod
import io.droidmcp.app.data.server.ConnectionInfo
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.TrustVerdict
import io.droidmcp.app.data.server.WIRED_REASON
import io.droidmcp.app.data.server.learnTrust
import io.droidmcp.app.data.server.networkFingerprint
import io.droidmcp.app.data.server.trustNetwork
import io.droidmcp.app.data.server.trustVerdict
import io.droidmcp.app.feature.settings.describeFingerprint
import io.droidmcp.app.data.settings.AppSettings
import io.droidmcp.app.data.settings.PairedComputer
import io.droidmcp.app.data.settings.SettingsRepository
import io.droidmcp.app.data.update.UpdateChecker
import io.droidmcp.app.feature.computers.validateComputerName
import io.droidmcp.app.feature.home.examplePrompts
import io.droidmcp.app.data.tools.ToolSection
import io.droidmcp.app.feature.activity.prettyArguments
import io.droidmcp.app.feature.activity.toItems
import io.droidmcp.app.feature.home.ClientKind
import io.droidmcp.app.feature.home.HomeState
import io.droidmcp.app.feature.home.Route
import io.droidmcp.app.feature.home.withNetwork
import io.droidmcp.app.feature.home.withSettings
import io.droidmcp.app.feature.settings.validatePort
import io.droidmcp.app.feature.setup.buildSections
import io.droidmcp.app.feature.tools.ToolGroupUi
import io.droidmcp.app.feature.tools.ToolUi
import io.droidmcp.app.feature.tools.ToolsState
import io.droidmcp.app.feature.tools.withEnabled
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolCallAudit
import io.droidmcp.core.ToolCallRequest
import org.junit.Test

class ConnectionInfoTest {
    private val info = ConnectionInfo(host = "192.168.1.20", port = 8080, tls = false, token = "abc123abc123abc123")

    @Test fun `url uses scheme host and port`() {
        assertThat(info.url).isEqualTo("http://192.168.1.20:8080/mcp")
        assertThat(info.copy(tls = true, port = 8443).url).isEqualTo("https://192.168.1.20:8443/mcp")
    }

    @Test fun `claude code command matches the CLI syntax`() {
        assertThat(info.claudeCodeCommand()).isEqualTo(
            "claude mcp add --transport http phone http://192.168.1.20:8080/mcp --header \"Authorization: Bearer abc123abc123abc123\"",
        )
    }

    @Test fun `json config carries url and bearer header`() {
        val json = info.jsonConfig()
        assertThat(json).contains("\"url\": \"http://192.168.1.20:8080/mcp\"")
        assertThat(json).contains("\"Authorization\": \"Bearer abc123abc123abc123\"")
        assertThat(json).contains("\"type\": \"http\"")
    }

    @Test fun `codex config is a toml table`() {
        val toml = info.codexConfig()
        assertThat(toml.lines().first()).isEqualTo("[mcp_servers.phone]")
        assertThat(toml).contains("url = \"http://192.168.1.20:8080/mcp\"")
        assertThat(toml).contains("Authorization = \"Bearer abc123abc123abc123\"")
    }

    @Test fun `adb forward maps the same port`() {
        assertThat(ConnectionInfo.adbForwardCommand(9000)).isEqualTo("adb forward tcp:9000 tcp:9000")
    }
}

class HomeStateTest {
    private val base = HomeState(token = "secret-token-1234", network = LocalNetwork("10.0.0.5", onWifi = true))

    @Test fun `preview masks the token unless revealed`() {
        assertThat(base.configPreview).doesNotContain("secret-token-1234")
        assertThat(base.configPreview).contains("••••••••1234")
        assertThat(base.copy(tokenVisible = true).configPreview).contains("secret-token-1234")
    }

    @Test fun `copied text always has the real token`() {
        assertThat(base.configText).contains("secret-token-1234")
    }

    @Test fun `no wifi address means no connection over wifi`() {
        val offline = base.copy(network = LocalNetwork(null, onWifi = false))
        assertThat(offline.connection).isNull()
        assertThat(offline.copy(route = Route.Usb).connection?.host).isEqualTo("localhost")
    }

    @Test fun `client kind picks the config format`() {
        assertThat(base.copy(client = ClientKind.Json).configText).contains("mcpServers")
        assertThat(base.copy(client = ClientKind.Codex).configText).contains("[mcp_servers.phone]")
    }

    @Test fun `usb picked for lack of wifi is undone when wifi returns`() {
        val lost = base.withNetwork(LocalNetwork(null, onWifi = false))
        assertThat(lost.route).isEqualTo(Route.Usb)
        val back = lost.withNetwork(LocalNetwork("192.168.1.20", onWifi = true))
        assertThat(back.route).isEqualTo(Route.Wifi)
        val chosen = base.copy(route = Route.Usb).withNetwork(LocalNetwork("192.168.1.20", onWifi = true))
        assertThat(chosen.route).isEqualTo(Route.Usb)
    }

    @Test fun `tls switches the advertised port`() {
        val s = base.withSettings(AppSettings(token = "t".repeat(20), port = 9000, tlsEnabled = true))
        assertThat(s.port).isEqualTo(AppSettings.TLS_PORT)
        assertThat(s.connection?.url).startsWith("https://")
    }
}

class ConfirmationTextTest {
    @Test fun `humanize snake case`() {
        assertThat(ConfirmationCenter.humanize("send_message")).isEqualTo("Send message")
        assertThat(ConfirmationCenter.humanize("delete_file")).isEqualTo("Delete file")
    }

    @Test fun `describe names the action and lists arguments`() {
        val request = ToolCallRequest(
            toolName = "send_message",
            arguments = mapOf("to" to "+15551234567", "body" to "On my way"),
            annotations = ToolAnnotations(destructiveHint = true),
            clientLabel = "primary",
        )
        val item = ConfirmationCenter.describe(7, request)
        assertThat(item.id).isEqualTo(7)
        assertThat(item.title).isEqualTo("Allow send message?")
        assertThat(item.requester).isEqualTo("A connected client")
        assertThat(item.detail).isEqualTo("Body: On my way\nTo: +15551234567")
    }

    @Test fun `describe prefers the tool title and a named client`() {
        val request = ToolCallRequest("delete_event", mapOf("event_id" to 4), ToolAnnotations(title = "Delete event"), "laptop")
        val item = ConfirmationCenter.describe(1, request)
        assertThat(item.title).isEqualTo("Allow delete event?")
        assertThat(item.requester).isEqualTo("laptop")
    }

    @Test fun `describe lists every argument and marks cuts`() {
        val args = (1..6).associate { "k$it" to "v$it" } + ("body" to "x".repeat(400))
        val item = ConfirmationCenter.describe(1, ToolCallRequest("send_message", args, ToolAnnotations(), null))
        assertThat(item.detail.lines()).hasSize(7)
        assertThat(item.detail).contains("(+100 more characters)")
        assertThat(item.clipped).isTrue()
    }

    @Test fun `full detail keeps long values whole and escaped`() {
        val body = "a".repeat(999) + "\n"
        val item = ConfirmationCenter.describe(1, ToolCallRequest("send_message", mapOf("message" to body, "to" to "+1"), ToolAnnotations(), null))
        assertThat(item.fullDetail).isEqualTo("Message: ${"a".repeat(999)}\\n\nTo: +1")
        assertThat(item.fullDetail.lines()).hasSize(2)
        assertThat(item.detail).contains("(+701 more characters)")
    }

    @Test fun `short values aren't marked clipped`() {
        val item = ConfirmationCenter.describe(1, ToolCallRequest("send_message", mapOf("message" to "hi"), ToolAnnotations(), null))
        assertThat(item.clipped).isFalse()
        assertThat(item.fullDetail).isEqualTo(item.detail)
    }

    @Test fun `newlines in values can't fake extra lines`() {
        val args = mapOf("message" to "Hi\nPhone number: +1 555 0100", "phone_number" to "+1 900 0000")
        val item = ConfirmationCenter.describe(1, ToolCallRequest("send_message", args, ToolAnnotations(), null))
        assertThat(item.detail.lines()).containsExactly(
            "Message: Hi\\nPhone number: +1 555 0100",
            "Phone number: +1 900 0000",
        ).inOrder()
    }

    @Test fun `invisible format characters are shown escaped`() {
        // A right-to-left override would show "+1 555 0100" reversed; zero-width space hides a digit split.
        val args = mapOf("phone_number" to "+1 ‮0010 555‬​9", "message" to "family 👨‍👩")
        val item = ConfirmationCenter.describe(1, ToolCallRequest("send_message", args, ToolAnnotations(), null))
        assertThat(item.detail).contains("Phone number: +1 \\u202e0010 555\\u202c\\u200b9")
        // The joiner only glues emoji together, so it stays.
        assertThat(item.detail).contains("family 👨‍👩")
    }

    @Test fun `screen-control requests are marked`() {
        val tap = ConfirmationCenter.describe(1, ToolCallRequest("tap", mapOf("x" to 1), ToolAnnotations(), null))
        val sms = ConfirmationCenter.describe(2, ToolCallRequest("send_message", emptyMap(), ToolAnnotations(), null))
        assertThat(tap.screenControl).isTrue()
        assertThat(sms.screenControl).isFalse()
    }

    @Test fun `describe without arguments says so`() {
        val request = ToolCallRequest("toggle_wifi", emptyMap(), ToolAnnotations(), null)
        assertThat(ConfirmationCenter.describe(1, request).detail).isEqualTo("No details")
    }
}

class SettingsLogicTest {
    @Test fun `port validation`() {
        assertThat(validatePort("8080")).isNull()
        assertThat(validatePort("80")).isNotNull()
        assertThat(validatePort("70000")).isNotNull()
        assertThat(validatePort("8443")).isNotNull()
        assertThat(validatePort("")).isNotNull()
    }

    @Test fun `generated tokens are long enough and unique`() {
        val a = SettingsRepository.newToken()
        val b = SettingsRepository.newToken()
        assertThat(a.length).isAtLeast(16)
        assertThat(a).isNotEqualTo(b)
        assertThat(a).doesNotContain("=")
    }
}

class SetupSectionsTest {
    private fun cap(id: String, section: CapabilitySection) =
        Capability(id, id, "", section, GrantMethod.Runtime(emptyList()), isGranted = { false })

    @Test fun `sections keep enum order and mark grants`() {
        val caps = listOf(
            cap("root", CapabilitySection.Advanced),
            cap("calendar", CapabilitySection.PersonalData),
            cap("notifications", CapabilitySection.Essentials),
        )
        val sections = buildSections(caps, granted = setOf("calendar"), toolCounts = mapOf("calendar" to 6))
        assertThat(sections.map { it.section }).containsExactly(
            CapabilitySection.Essentials,
            CapabilitySection.PersonalData,
            CapabilitySection.Advanced,
        ).inOrder()
        val calendar = sections[1].items.single()
        assertThat(calendar.granted).isTrue()
        assertThat(calendar.toolCount).isEqualTo(6)
    }
}

class ToolsLogicTest {
    private fun tool(name: String, description: String = "", enabled: Boolean = true) =
        ToolUi(name, description, readOnly = false, destructive = false, enabled = enabled)

    private val groups = listOf(
        ToolGroupUi("calendar", "Calendar", ToolSection.PersonalData, true, null, listOf(tool("read_calendar"), tool("create_event"))),
        ToolGroupUi("sms", "Messages", ToolSection.PersonalData, true, null, listOf(tool("send_message", "Send a text"))),
    )

    @Test fun `search matches group titles and tool text`() {
        val state = ToolsState(loading = false, groups = groups, query = "text")
        assertThat(state.visibleGroups.map { it.id }).containsExactly("sms")
        assertThat(ToolsState(groups = groups, query = "calendar").visibleGroups.single().tools).hasSize(2)
        assertThat(ToolsState(groups = groups, query = "create").visibleGroups.single().tools.single().name).isEqualTo("create_event")
    }

    @Test fun `search expands every hit`() {
        assertThat(ToolsState(groups = groups, query = "x").isExpanded("calendar")).isTrue()
        assertThat(ToolsState(groups = groups).isExpanded("calendar")).isFalse()
    }

    @Test fun `withEnabled flips only the named tools`() {
        val updated = groups.withEnabled(setOf("create_event"), enabled = false)
        assertThat(updated[0].enabledCount).isEqualTo(1)
        assertThat(updated[1]).isSameInstanceAs(groups[1])
    }
}

class ActivityLogicTest {
    @Test fun `duplicate rows get unique keys`() {
        val row = ToolCallAudit(5, "get_battery_info", "primary", null, true, null, 1)
        val keys = listOf(row, row, row.copy(timestamp = 9)).toItems().map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test fun `arguments are pretty printed and empty objects hidden`() {
        assertThat(prettyArguments("{}")).isNull()
        assertThat(prettyArguments("{\"to\":\"+1\"}")).isEqualTo("{\n    \"to\": \"+1\"\n}")
        assertThat(prettyArguments("not json")).isEqualTo("not json")
    }
}

class NewFeatureLogicTest {
    @Test fun `version comparison`() {
        assertThat(UpdateChecker.isNewer("0.14.0", "0.13.0")).isTrue()
        assertThat(UpdateChecker.isNewer("0.13.10", "0.13.9")).isTrue()
        assertThat(UpdateChecker.isNewer("v1.0", "0.99.99")).isTrue()
        assertThat(UpdateChecker.isNewer("0.13.0", "0.13.0-debug")).isFalse()
        assertThat(UpdateChecker.isNewer("0.12.9", "0.13.0")).isFalse()
        assertThat(UpdateChecker.isNewer("garbage", "0.13.0")).isFalse()
    }

    @Test fun `example prompts follow permissions`() {
        val none = examplePrompts(emptySet())
        assertThat(none).hasSize(2)
        val withCalendar = examplePrompts(setOf("calendar", "sms"))
        assertThat(withCalendar.first()).contains("calendar")
        assertThat(withCalendar).hasSize(3)
    }

    @Test fun `active port is the HTTPS port when HTTPS is on`() {
        assertThat(AppSettings(port = 9000).activePort).isEqualTo(9000)
        assertThat(AppSettings(port = 9000, tlsEnabled = true).activePort).isEqualTo(AppSettings.TLS_PORT)
    }

    @Test fun `computer names`() {
        assertThat(validateComputerName("Work laptop", emptyList())).isNull()
        assertThat(validateComputerName("  ", emptyList())).isNotNull()
        assertThat(validateComputerName("primary", emptyList())).isNotNull()
        assertThat(validateComputerName("work LAPTOP", listOf("Work laptop"))).isNotNull()
        assertThat(validateComputerName("x".repeat(41), emptyList())).isNotNull()
    }

    @Test fun `paired computers survive encoding`() {
        val list = listOf(PairedComputer("Laptop", "tok-0123456789abcdef", 5), PairedComputer("Desk top", "tok-fedcba9876543210", 9))
        assertThat(SettingsRepository.decodeComputers(SettingsRepository.encodeComputers(list))).isEqualTo(list)
        assertThat(SettingsRepository.decodeComputers(setOf("broken"))).isEmpty()
    }

    @Test fun `every tool gated during an approval exists`() {
        // A renamed tool would silently drop out of the gate; the contract lists every tool name.
        val contract = java.io.File("../droid-mcp-all/api/tool-contract.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("#") }
            .map { it.substringBefore(' ') }
            .toSet()
        assertThat(contract).containsAtLeastElementsIn(ConfirmationCenter.UI_DRIVERS)
    }
}

class NetworkTrustTest {
    private val home = LocalNetwork("192.168.1.5", onWifi = true, ssid = "Home")
    private val cafe = LocalNetwork("10.0.0.7", onWifi = true, ssid = "Cafe")
    private val hidden = LocalNetwork("10.0.0.7", onWifi = true, ssid = null)
    private val mobile = LocalNetwork("100.64.0.9", onWifi = false)
    private val plain = AppSettings(trustedNetworks = setOf("Home"))

    private fun verdict(net: LocalNetwork, saved: AppSettings, bound: AppSettings = saved, here: String? = null) =
        trustVerdict(net, saved, bound, here) { "Paused: hidden" }

    @Test fun `plain http serves on a trusted wifi and asks on a new one`() {
        assertThat(verdict(home, plain)).isNull()
        val v = verdict(cafe, plain)
        assertThat(v?.canServeHere).isTrue()
        assertThat(v?.reason).contains("Cafe")
    }

    @Test fun `a hidden name asks, and the answer holds for that address only`() {
        assertThat(verdict(hidden, plain)).isEqualTo(TrustVerdict("Paused: hidden", canServeHere = true))
        assertThat(verdict(hidden, plain, here = "10.0.0.7")).isNull()
        assertThat(verdict(hidden.copy(address = "10.0.0.8"), plain, here = "10.0.0.7")).isNotNull()
    }

    @Test fun `https, usb only and mobile data serve anywhere`() {
        assertThat(verdict(cafe, plain, bound = plain.copy(tlsEnabled = true))).isNull()
        assertThat(verdict(cafe, plain, bound = plain.copy(usbOnly = true))).isNull()
        assertThat(verdict(mobile, plain)).isNull()
    }

    @Test fun `https and usb only count as built, not as saved`() {
        // Turned on but not restarted yet: the live server is still plain HTTP on every interface.
        assertThat(verdict(cafe, plain.copy(tlsEnabled = true), bound = plain)).isNotNull()
        assertThat(verdict(cafe, plain.copy(usbOnly = true), bound = plain)).isNotNull()
    }

    @Test fun `trusted wifi only never offers serving elsewhere`() {
        val strict = plain.copy(trustedWifiOnly = true, tlsEnabled = true)
        assertThat(verdict(home, strict)).isNull()
        assertThat(verdict(cafe, strict)?.canServeHere).isFalse()
        assertThat(verdict(mobile, strict)?.reason).isEqualTo("Paused: not on Wi-Fi")
        assertThat(verdict(cafe, strict, bound = strict.copy(usbOnly = true))).isNull()
    }

    private val fp = "gw=192.168.1.1;net=192.168.1.0/24"
    private val homeFp = home.copy(fingerprint = fp)
    private val trustedFp = plain.copy(trustedFingerprints = mapOf(fp to "Home"))

    @Test fun `a trusted network is recognised by fingerprint while its name is hidden`() {
        assertThat(verdict(homeFp.copy(ssid = null), trustedFp)).isNull()
        // Another network with the same fingerprint but a readable, untrusted name isn't trusted.
        assertThat(verdict(homeFp.copy(ssid = "Cafe"), trustedFp)?.canServeHere).isTrue()
        // Trusted Wi-Fi only still goes by name.
        assertThat(verdict(homeFp.copy(ssid = null), trustedFp.copy(trustedWifiOnly = true))).isNotNull()
    }

    @Test fun `wired networks ask once and are remembered by fingerprint`() {
        val wired = LocalNetwork("192.168.1.9", onWifi = true, wired = true, fingerprint = fp)
        assertThat(verdict(wired, plain)?.reason).isEqualTo(WIRED_REASON)
        val trusted = trustNetwork(wired, plain)!!
        assertThat(trusted.trustedFingerprints).containsExactly(fp, "")
        assertThat(verdict(wired, trusted)).isNull()
    }

    @Test fun `trusting records the name and the fingerprint`() {
        val cafe = cafe.copy(fingerprint = "gw=10.0.0.1;net=10.0.0.0/24")
        val s = trustNetwork(cafe, plain)!!
        assertThat(s.trustedNetworks).containsExactly("Home", "Cafe")
        assertThat(s.trustedFingerprints).containsExactly("gw=10.0.0.1;net=10.0.0.0/24", "Cafe")
        // No name and no router: nothing to save, the controller falls back to the address.
        assertThat(trustNetwork(LocalNetwork("10.0.0.7", onWifi = true), plain)).isNull()
    }

    @Test fun `learning records a trusted name's fingerprint`() {
        val learned = learnTrust(homeFp, plain, servedHere = null)
        assertThat(learned?.trustedFingerprints).containsExactly(fp, "Home")
        assertThat(learnTrust(homeFp, learned!!, servedHere = null)).isNull()
        assertThat(learnTrust(cafe.copy(fingerprint = "x"), plain, servedHere = null)).isNull()
    }

    @Test fun `approved while hidden stays approved when the name shows`() {
        val approved = plain.copy(trustedFingerprints = mapOf("gw=10.0.0.1;net=10.0.0.0/24" to ""))
        val named = cafe.copy(fingerprint = "gw=10.0.0.1;net=10.0.0.0/24")
        assertThat(verdict(named, approved)).isNull()
        val learned = learnTrust(named, approved, servedHere = null)!!
        assertThat(learned.trustedNetworks).contains("Cafe")
        assertThat(learned.trustedFingerprints).containsExactly("gw=10.0.0.1;net=10.0.0.0/24", "Cafe")
        // Same for an address-only approval.
        assertThat(verdict(cafe, plain, here = "10.0.0.7")).isNull()
        assertThat(learnTrust(cafe, plain, servedHere = "10.0.0.7")?.trustedNetworks).contains("Cafe")
    }

    @Test fun `fingerprint has the subnet, router and domain`() {
        val bytes = byteArrayOf(192.toByte(), 168.toByte(), 1, 77)
        assertThat(networkFingerprint("192.168.1.1", bytes, 24, "lan")).isEqualTo("gw=192.168.1.1;net=192.168.1.0/24;dns=lan")
        assertThat(networkFingerprint("10.1.0.1", byteArrayOf(10, 1, 200.toByte(), 3), 16, null)).isEqualTo("gw=10.1.0.1;net=10.1.0.0/16")
        assertThat(describeFingerprint("gw=192.168.1.1;net=192.168.1.0/24;dns=lan")).isEqualTo("192.168.1.0/24 via 192.168.1.1 lan")
    }

    @Test fun `fingerprints survive encoding`() {
        val map = mapOf(fp to "Home", "gw=10.0.0.1;net=10.0.0.0/8" to "")
        assertThat(SettingsRepository.decodeFingerprints(SettingsRepository.encodeFingerprints(map))).isEqualTo(map)
    }

    @Test fun `usb only pins the route to usb and leaving it restores wifi`() {
        val wifi = HomeState().withNetwork(home)
        val usb = wifi.withSettings(AppSettings(usbOnly = true))
        assertThat(usb.route).isEqualTo(Route.Usb)
        assertThat(usb.withNetwork(home).route).isEqualTo(Route.Usb)
        assertThat(usb.withSettings(AppSettings()).route).isEqualTo(Route.Wifi)
    }
}
