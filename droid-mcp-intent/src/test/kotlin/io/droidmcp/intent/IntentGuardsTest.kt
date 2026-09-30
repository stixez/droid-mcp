package io.droidmcp.intent

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `android.net.Uri` is a stub in JVM tests, so `Uri.parse` is replaced with a fake that
 * reproduces Android's `StringUri.parseScheme` rule: the scheme is everything before the first
 * `:`, or null if there is no `:`. `normalizeScheme()` lowercases the scheme, as on Android.
 */
class IntentGuardsTest {

    private class FakeUri(val raw: String) {
        val scheme: String? = raw.indexOf(':').let { if (it < 0) null else raw.substring(0, it) }
    }

    private fun mockUri(raw: String): Uri {
        val fake = FakeUri(raw)
        val normalized = mockk<Uri> {
            every { scheme } returns fake.scheme?.lowercase()
            every { this@mockk.toString() } returns
                if (fake.scheme == null) raw else fake.scheme.lowercase() + raw.substring(fake.scheme.length)
        }
        return mockk {
            every { scheme } returns fake.scheme
            every { normalizeScheme() } returns normalized
            every { this@mockk.toString() } returns raw
        }
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        val raw = slot<String>()
        every { Uri.parse(capture(raw)) } answers { mockUri(raw.captured) }
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun check(raw: String) = IntentGuards.parseAllowedUri(raw)

    @Test
    fun `every allowlisted scheme is accepted`() {
        listOf(
            "http://example.com", "https://example.com/a?b=c", "geo:0,0?q=coffee", "tel:+15551234567",
            "mailto:a@example.com", "sms:+15551234567", "smsto:5551234", "mms:5551234", "mmsto:5551234",
            "market://details?id=com.example",
        ).forEach { raw ->
            assertWithMessage(raw).that(check(raw).isSuccess).isTrue()
        }
    }

    @Test
    fun `allowlist is exactly the documented set`() {
        assertThat(IntentGuards.ALLOWED_SCHEMES).containsExactly(
            "http", "https", "geo", "tel", "mailto", "sms", "smsto", "mms", "mmsto", "market",
        )
    }

    @Test
    fun `dangerous schemes are rejected`() {
        listOf(
            "file:///sdcard/secret.txt",
            "content://com.host.provider/private",
            "intent://scan/#Intent;scheme=zxing;package=com.evil;end",
            "android-app://com.evil/https/example.com",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "myapp://transfer?to=attacker",
            "ftp://example.com/x",
        ).forEach { raw ->
            val result = check(raw)
            assertWithMessage(raw).that(result.isFailure).isTrue()
            assertWithMessage(raw).that(result.exceptionOrNull()!!.message).contains("is not permitted")
        }
    }

    @Test
    fun `scheme matching is case-insensitive in both directions`() {
        assertThat(check("HTTPS://example.com").isSuccess).isTrue()
        assertThat(check("Tel:123").isSuccess).isTrue()
        assertThat(check("JavaScript:alert(1)").isFailure).isTrue()
        assertThat(check("FILE:///etc/hosts").isFailure).isTrue()
        assertThat(check("Content://x/y").exceptionOrNull()!!.message).contains("'content'")
    }

    @Test
    fun `accepted uri is returned with a normalized lowercase scheme`() {
        val uri = check("HTTPS://Example.com/Path").getOrThrow()
        assertThat(uri.scheme).isEqualTo("https")
        assertThat(uri.toString()).isEqualTo("https://Example.com/Path")
    }

    @Test
    fun `missing scheme is rejected with a dedicated message`() {
        listOf("www.example.com", "/sdcard/file.txt", "", "   ").forEach { raw ->
            val result = check(raw)
            assertWithMessage("'$raw'").that(result.isFailure).isTrue()
            assertWithMessage("'$raw'").that(result.exceptionOrNull()!!.message).contains("URI has no scheme")
        }
    }

    @Test
    fun `empty scheme is rejected`() {
        assertThat(check(":foo").isFailure).isTrue()
    }

    @Test
    fun `surrounding whitespace cannot smuggle a scheme past the check`() {
        assertThat(check("  https://example.com  ").isSuccess).isTrue()
        assertThat(check("  javascript:alert(1)").isFailure).isTrue()
        assertThat(check("\tfile:///etc/hosts\n").isFailure).isTrue()
    }

    @Test
    fun `error message lists the allowed schemes`() {
        val message = check("file:///x").exceptionOrNull()!!.message!!
        assertThat(message).startsWith("uri_scheme_not_allowed:")
        assertThat(message).contains("geo, http, https, mailto, market, mms, mmsto, sms, smsto, tel")
    }
}
