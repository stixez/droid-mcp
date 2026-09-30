package io.droidmcp.web

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * SSRF guard shared by the web tools. Unless the host opts in with `allowPrivateNetwork`, the
 * tools refuse to talk to anything that isn't a public unicast address: loopback, RFC 1918
 * private ranges (10/8, 172.16/12, 192.168/16), unique-local IPv6 (fc00::/7), link-local
 * (169.254/16 — incl. cloud metadata endpoints — and fe80::/10), CGNAT (100.64/10), the
 * unspecified address, `0/8`, broadcast, and multicast. IPv6 forms that carry an IPv4 address
 * (IPv4-mapped / -compatible, NAT64 `64:ff9b::/96`, 6to4 `2002::/16`, Teredo `2001::/32`) are
 * judged by the embedded IPv4.
 *
 * Enforced in two layers, both of which run again for every redirect hop:
 *  - [GuardedDns] filters hostname resolution *before* any socket is opened.
 *  - [GuardInterceptor] (a network interceptor) re-checks IP-literal hosts — which OkHttp never
 *    passes through [Dns] — and the address actually connected to, before any request bytes are
 *    sent.
 *
 * Caveat: when the device routes traffic through an HTTP proxy, the *proxy* resolves the target
 * hostname, so only IP-literal targets can be checked in that configuration.
 */
internal object NetworkGuard {

    /** Builds the OkHttp client used by the web tools: 15 s connect/read, 30 s overall call timeout. */
    fun newClient(allowPrivateNetwork: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
        if (!allowPrivateNetwork) {
            builder.dns(GuardedDns)
            builder.addNetworkInterceptor(GuardInterceptor)
        }
        return builder.build()
    }

    /** True if [address] is not a public unicast address (see the class KDoc for the ranges). */
    fun isBlocked(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return true
        }
        val b = address.address
        return when (address) {
            is Inet4Address -> isBlockedV4(b)
            is Inet6Address -> {
                val b0 = b[0].toInt() and 0xff
                val b1 = b[1].toInt() and 0xff
                when {
                    (b0 and 0xfe) == 0xfc -> true // fc00::/7 unique local
                    b0 == 0xfe && (b1 and 0xc0) == 0x80 -> true // fe80::/10 link local
                    // IPv4-compatible (::a.b.c.d) and NAT64 (64:ff9b::/96): judge the embedded v4.
                    b.copyOfRange(0, 12).all { it.toInt() == 0 } -> isBlockedV4(b.copyOfRange(12, 16))
                    b0 == 0x00 && b1 == 0x64 && (b[2].toInt() and 0xff) == 0xff && (b[3].toInt() and 0xff) == 0x9b &&
                        b.copyOfRange(4, 12).all { it.toInt() == 0 } -> isBlockedV4(b.copyOfRange(12, 16))
                    // 6to4 (2002:a.b.c.d::/48): the v4 sits in bytes 2..5.
                    b0 == 0x20 && b1 == 0x02 -> isBlockedV4(b.copyOfRange(2, 6))
                    // Teredo (2001:0000::/32): server v4 in bytes 4..7, client v4 bit-inverted in 12..15.
                    b0 == 0x20 && b1 == 0x01 && b[2].toInt() == 0 && b[3].toInt() == 0 ->
                        isBlockedV4(b.copyOfRange(4, 8)) ||
                            isBlockedV4(ByteArray(4) { (b[12 + it].toInt() xor 0xff).toByte() })
                    else -> false
                }
            }
            else -> true
        }
    }

    private fun isBlockedV4(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xff
        val b1 = b[1].toInt() and 0xff
        return b0 == 0 || // 0.0.0.0/8 "this network"
            b0 == 10 ||
            b0 == 127 ||
            (b0 == 100 && b1 in 64..127) || // CGNAT 100.64/10
            (b0 == 169 && b1 == 254) ||
            (b0 == 172 && b1 in 16..31) ||
            (b0 == 192 && b1 == 168) ||
            b0 >= 224 // multicast 224/4, reserved 240/4, broadcast
    }

    /** [Dns] that drops blocked addresses and fails the lookup if nothing public remains. */
    private object GuardedDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val allowed = all.filterNot { isBlocked(it) }
            if (allowed.isEmpty()) {
                throw UnknownHostException(
                    "Blocked: $hostname resolves only to private/loopback/link-local addresses"
                )
            }
            return allowed
        }
    }

    /** Network interceptor: re-validates IP-literal hosts and the connected socket address on every hop. */
    private object GuardInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val host = chain.request().url.host
            literalAddress(host)?.let { literal ->
                if (isBlocked(literal)) throw IOException("Blocked: $host is a private/loopback/link-local address")
            }
            val route = chain.connection()?.route()
            if (route != null && route.proxy.type() == Proxy.Type.DIRECT) {
                val connected = route.socketAddress.address
                if (connected != null && isBlocked(connected)) {
                    throw IOException("Blocked: $host connected to non-public address ${connected.hostAddress}")
                }
            }
            return chain.proceed(chain.request())
        }

        /** Parses [host] as an IP literal without any DNS lookup; null for hostnames. */
        private fun literalAddress(host: String): InetAddress? {
            val isV4 = host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
            val isV6 = host.contains(':')
            if (!isV4 && !isV6) return null
            return try {
                InetAddress.getByName(host) // literal → no resolution
            } catch (_: Exception) {
                null
            }
        }
    }
}
