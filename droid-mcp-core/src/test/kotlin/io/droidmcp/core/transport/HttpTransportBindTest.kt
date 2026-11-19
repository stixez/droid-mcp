package io.droidmcp.core.transport

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.ToolRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Which interfaces a real Netty server listens on. */
class HttpTransportBindTest {

    private val port = ServerSocket(0).use { it.localPort }
    private var transport: HttpTransport? = null

    @AfterEach
    fun tearDown() {
        transport?.stop()
    }

    private fun start(host: String) {
        transport = HttpTransport(ToolRegistry(), port = port, bearerToken = "test-token-0123456789", host = host)
            .also { it.start() }
    }

    private fun reachable(address: String): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(address, port), 1_000) }
        true
    } catch (_: Exception) {
        false
    }

    /** An IPv4 address of this machine that isn't loopback, or null on a host without one. */
    private fun lanAddress(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address }
        ?.hostAddress

    @Test
    @Timeout(20, unit = TimeUnit.SECONDS)
    fun `loopback is reachable only from this machine`() {
        val lan = lanAddress()
        assumeTrue(lan != null, "no non-loopback IPv4 address to test against")
        start(HttpTransport.LOOPBACK)
        assertThat(reachable("127.0.0.1")).isTrue()
        assertThat(reachable(lan!!)).isFalse()
    }

    @Test
    @Timeout(20, unit = TimeUnit.SECONDS)
    fun `the default listens on every interface`() {
        val lan = lanAddress()
        assumeTrue(lan != null, "no non-loopback IPv4 address to test against")
        start(HttpTransport.ALL_INTERFACES)
        assertThat(reachable("127.0.0.1")).isTrue()
        assertThat(reachable(lan!!)).isTrue()
    }
}
