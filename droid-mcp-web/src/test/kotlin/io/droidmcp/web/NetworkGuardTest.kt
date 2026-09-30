package io.droidmcp.web

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException

/**
 * SSRF guard coverage. Every address here is an IP literal (`getByName` on a literal and
 * `getByAddress` never touch DNS), so the tests are hermetic.
 */
class NetworkGuardTest {

    private fun blocked(literal: String) = NetworkGuard.isBlocked(InetAddress.getByName(literal))

    private fun assertAllBlocked(vararg literals: String) = literals.forEach {
        assertWithMessage("$it should be blocked").that(blocked(it)).isTrue()
    }

    private fun assertAllAllowed(vararg literals: String) = literals.forEach {
        assertWithMessage("$it should be allowed").that(blocked(it)).isFalse()
    }

    @Test
    fun `loopback and unspecified are blocked`() =
        assertAllBlocked("127.0.0.1", "127.255.255.254", "::1", "0.0.0.0", "::")

    @Test
    fun `this-network 0 slash 8 is blocked`() = assertAllBlocked("0.1.2.3", "0.255.255.255")

    @Test
    fun `RFC 1918 ranges are blocked including their edges`() = assertAllBlocked(
        "10.0.0.0", "10.255.255.255",
        "172.16.0.0", "172.31.255.255",
        "192.168.0.0", "192.168.255.255",
    )

    @Test
    fun `addresses just outside RFC 1918 ranges are allowed`() = assertAllAllowed(
        "11.0.0.1", "9.255.255.255",
        "172.15.255.255", "172.32.0.0",
        "192.167.255.255", "192.169.0.0",
    )

    @Test
    fun `link-local incl cloud metadata endpoint is blocked`() =
        assertAllBlocked("169.254.169.254", "169.254.0.1", "fe80::1", "febf:ffff::1")

    @Test
    fun `just outside link-local is allowed`() = assertAllAllowed("169.253.255.255", "169.255.0.1", "fe7f::1")

    @Test
    fun `CGNAT 100_64 slash 10 is blocked, neighbours are not`() {
        assertAllBlocked("100.64.0.0", "100.100.100.100", "100.127.255.255")
        assertAllAllowed("100.63.255.255", "100.128.0.0")
    }

    @Test
    fun `unique-local fc00 slash 7 is blocked`() = assertAllBlocked("fc00::1", "fd12:3456:789a::1", "fdff:ffff::1")

    @Test
    fun `multicast, reserved and broadcast are blocked`() =
        assertAllBlocked("224.0.0.1", "239.255.255.250", "240.0.0.1", "255.255.255.255", "ff02::1", "ff0e::1")

    @Test
    fun `public unicast addresses are allowed`() = assertAllAllowed(
        "8.8.8.8", "1.1.1.1", "93.184.216.34", "223.255.255.255",
        "2001:4860:4860::8888", "2606:4700:4700::1111",
    )

    @Test
    fun `IPv4-mapped IPv6 is judged by the embedded IPv4`() {
        // Java collapses ::ffff:a.b.c.d to an Inet4Address — confirm, then check both directions.
        assertThat(InetAddress.getByName("::ffff:10.0.0.1")).isInstanceOf(Inet4Address::class.java)
        assertAllBlocked("::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254", "::ffff:7f00:1")
        assertAllAllowed("::ffff:8.8.8.8")

        val mapped = ByteArray(16).also {
            it[10] = 0xff.toByte(); it[11] = 0xff.toByte()
            it[12] = 192.toByte(); it[13] = 168.toByte(); it[14] = 1; it[15] = 1
        }
        assertThat(NetworkGuard.isBlocked(InetAddress.getByAddress(mapped))).isTrue()
    }

    @Test
    fun `IPv4-compatible IPv6 is judged by the embedded IPv4`() {
        assertAllBlocked("::10.0.0.1", "::192.168.1.1", "::169.254.169.254", "::100.64.0.1")
        assertAllAllowed("::8.8.8.8")
    }

    @Test
    fun `NAT64 64_ff9b slash 96 is judged by the embedded IPv4`() {
        assertAllBlocked("64:ff9b::10.0.0.1", "64:ff9b::7f00:1", "64:ff9b::a9fe:a9fe")
        assertAllAllowed("64:ff9b::8.8.8.8")
        // Outside the /96 (non-zero bytes 4..11) the NAT64 rule does not apply.
        assertAllAllowed("64:ff9b:1::a00:1")
    }

    @Test
    fun `6to4 2002 slash 16 is judged by the embedded IPv4`() {
        // 2002:c0a8:0101:: = 192.168.1.1, 2002:7f00:0001:: = 127.0.0.1, 2002:a9fe:a9fe:: = 169.254.169.254
        assertAllBlocked("2002:c0a8:101::1", "2002:7f00:1::1", "2002:a9fe:a9fe::1")
        assertAllAllowed("2002:808:808::1") // 8.8.8.8
    }

    @Test
    fun `Teredo 2001 slash 32 is judged by its server and bit-inverted client IPv4`() {
        // Server 65.54.227.120 (4136:e378) is public; client f7f7:f7f7 inverts to 8.8.8.8.
        assertAllAllowed("2001:0:4136:e378:8000:63bf:f7f7:f7f7")
        // Client 3f57:fefe inverts to 192.168.1.1.
        assertAllBlocked("2001:0:4136:e378:8000:63bf:3f57:fefe")
        // Server 10.0.0.1 (a00:1) is private.
        assertAllBlocked("2001:0:a00:1:8000:63bf:f7f7:f7f7")
        // Other 2001::/16 space (e.g. 2001:db8 docs, 2001:4860 Google) isn't Teredo.
        assertAllAllowed("2001:4860:4860::8888")
    }

    // ---- client wiring / allowPrivateNetwork opt-in -------------------------------------------

    @Test
    fun `default client installs guarded DNS and a network interceptor`() {
        val client = NetworkGuard.newClient(allowPrivateNetwork = false)
        assertThat(client.dns).isNotSameInstanceAs(Dns.SYSTEM)
        assertThat(client.networkInterceptors).hasSize(1)
    }

    @Test
    fun `allowPrivateNetwork opt-in disables both guard layers`() {
        val client = NetworkGuard.newClient(allowPrivateNetwork = true)
        assertThat(client.dns).isSameInstanceAs(Dns.SYSTEM)
        assertThat(client.networkInterceptors).isEmpty()
    }

    @Test
    fun `guarded DNS rejects private literals and passes public ones`() {
        val dns = NetworkGuard.newClient(false).dns
        listOf("127.0.0.1", "10.1.2.3", "169.254.169.254", "::1").forEach { host ->
            assertThrows<UnknownHostException>("$host should be rejected") { dns.lookup(host) }
        }
        assertThat(dns.lookup("8.8.8.8").map { it.hostAddress }).containsExactly("8.8.8.8")
    }

    private fun chainFor(url: String, route: Route? = null): Interceptor.Chain {
        val request = Request.Builder().url(url).build()
        val connection = route?.let { r -> mockk<Connection> { every { route() } returns r } }
        return mockk {
            every { request() } returns request
            every { connection() } returns connection
            every { proceed(any()) } returns mockk<Response>()
        }
    }

    private fun routeTo(ip: String, proxy: Proxy = Proxy.NO_PROXY): Route = mockk {
        every { this@mockk.proxy } returns proxy
        every { socketAddress } returns InetSocketAddress(InetAddress.getByName(ip), 443)
    }

    @Test
    fun `interceptor blocks private IP-literal hosts incl bracketed IPv6`() {
        val interceptor = NetworkGuard.newClient(false).networkInterceptors.single()
        listOf(
            "http://127.0.0.1/", "https://10.0.0.1:8443/x", "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://[fd00::1]/",
            "http://[::ffff:127.0.0.1]/",
        ).forEach { url ->
            val chain = chainFor(url)
            assertThrows<IOException>("$url should be blocked") { interceptor.intercept(chain) }
            verify(exactly = 0) { chain.proceed(any()) }
        }
    }

    @Test
    fun `interceptor lets public IP literals through`() {
        val interceptor = NetworkGuard.newClient(false).networkInterceptors.single()
        val chain = chainFor("http://8.8.8.8/")
        interceptor.intercept(chain)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun `interceptor blocks a hostname whose direct connection landed on a private address`() {
        val interceptor = NetworkGuard.newClient(false).networkInterceptors.single()
        val chain = chainFor("https://rebind.example/", routeTo("10.0.0.5"))
        assertThrows<IOException> { interceptor.intercept(chain) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `interceptor allows a hostname connected to a public address`() {
        val interceptor = NetworkGuard.newClient(false).networkInterceptors.single()
        val chain = chainFor("https://example.com/", routeTo("93.184.216.34"))
        interceptor.intercept(chain)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun `interceptor skips connected-address check when going through an HTTP proxy`() {
        val interceptor = NetworkGuard.newClient(false).networkInterceptors.single()
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.local", 3128))
        val chain = chainFor("https://example.com/", routeTo("10.0.0.5", proxy))
        interceptor.intercept(chain)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    // ---- fetch_webpage URL scheme check (returns before any network I/O) ----------------------

    private fun fetch(url: String) = runBlocking { FetchWebpageTool().execute(mapOf("url" to url)) }

    @Test
    fun `fetch_webpage rejects non-http schemes with the http-only error`() {
        listOf("file:///etc/hosts", "ftp://example.com/x", "gopher://127.0.0.1:70/", "content://foo/bar").forEach { url ->
            val result = fetch(url)
            assertWithMessage(url).that(result.isSuccess).isFalse()
            assertWithMessage(url).that(result.errorMessage).contains("only http and https URLs are supported")
        }
    }

    @Test
    fun `fetch_webpage rejects scheme-less and malformed URLs as invalid`() {
        listOf("javascript:alert(1)", "example.com", "http//missing-colon", "").forEach { url ->
            val result = fetch(url)
            assertWithMessage(url).that(result.isSuccess).isFalse()
            assertWithMessage(url).that(result.errorMessage).startsWith("Invalid URL")
        }
    }

    @Test
    fun `fetch_webpage requires url`() {
        val result = runBlocking { FetchWebpageTool().execute(emptyMap()) }
        assertThat(result.errorMessage).isEqualTo("url is required")
    }
}
