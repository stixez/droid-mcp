package io.droidmcp.qr

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Environment
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files

/**
 * `Environment` and `Uri` are stubs on the JVM. `Environment.getExternalStorageDirectory()` is
 * pointed at a temp dir (once — the validator caches it in a `lazy`), and `Uri.parse` /
 * `Uri.fromFile` are replaced by a fake that follows Android's `StringUri` rules: scheme = text
 * before the first `:`, authority = text after `//` up to the next `/?#`, path percent-decoded.
 */
class ImageUriValidatorTest {

    companion object {
        private lateinit var base: File
        private lateinit var root: File
        private lateinit var sibling: File
        private lateinit var outside: File

        private fun fakeUri(raw: String): Uri {
            val colon = raw.indexOf(':')
            val scheme = if (colon < 0) null else raw.substring(0, colon)
            val rest = if (colon < 0) raw else raw.substring(colon + 1)
            val noFragment = rest.substringBefore('#').substringBefore('?')
            val authority: String?
            val encodedPath: String
            if (noFragment.startsWith("//")) {
                val afterSlashes = noFragment.substring(2)
                val end = afterSlashes.indexOf('/').let { if (it < 0) afterSlashes.length else it }
                authority = afterSlashes.substring(0, end).ifEmpty { null }
                encodedPath = afterSlashes.substring(end)
            } else {
                authority = null
                encodedPath = noFragment
            }
            val path = encodedPath.ifEmpty { null }?.let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
            return mockk {
                every { this@mockk.scheme } returns scheme
                every { this@mockk.authority } returns authority
                every { this@mockk.path } returns path
                every { this@mockk.toString() } returns raw
            }
        }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            base = Files.createTempDirectory("qr-sandbox").toFile().canonicalFile
            root = File(base, "sdcard").apply { mkdirs() }
            sibling = File(base, "sdcard2").apply { mkdirs() }
            outside = File(base, "private").apply { mkdirs() }
            File(root, "DCIM").mkdirs()
            File(root, "DCIM/qr.png").writeText("png")
            File(sibling, "qr.png").writeText("png")
            File(outside, "secret.png").writeText("png")

            mockkStatic(Environment::class, Uri::class)
            every { Environment.getExternalStorageDirectory() } returns root
            val raw = slot<String>()
            every { Uri.parse(capture(raw)) } answers { fakeUri(raw.captured) }
            val file = slot<File>()
            every { Uri.fromFile(capture(file)) } answers { fakeUri("file://" + file.captured.path) }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            unmockkAll()
            base.deleteRecursively()
        }
    }

    private val hostPackage = "com.host.app"

    private fun context(providerOwners: Map<String, String?> = emptyMap(), throwOnResolve: Boolean = false): Context {
        val pm = mockk<PackageManager> {
            every { resolveContentProvider(any(), any<Int>()) } answers {
                if (throwOnResolve) throw SecurityException("nope")
                providerOwners[firstArg()]?.let { owner -> ProviderInfo().apply { packageName = owner } }
            }
        }
        return mockk {
            every { packageName } returns hostPackage
            every { packageManager } returns pm
        }
    }

    private fun validate(raw: String, ctx: Context = context()) = ImageUriValidator.validate(ctx, raw)

    private fun assertDenied(raw: String, messagePart: String, ctx: Context = context()) {
        val result = validate(raw, ctx)
        assertWithMessage(raw).that(result.isFailure).isTrue()
        assertWithMessage(raw).that(result.exceptionOrNull()!!.message).contains(messagePart)
    }

    // ---- file:// and bare paths ---------------------------------------------------------------

    @Test
    fun `file uri inside the external root is accepted and canonicalized`() {
        val result = validate("file://${root.path}/DCIM/qr.png")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().path).isEqualTo(File(root, "DCIM/qr.png").canonicalPath)
    }

    @Test
    fun `bare absolute path inside the external root is accepted`() {
        assertThat(validate("${root.path}/DCIM/qr.png").isSuccess).isTrue()
        assertThat(validate("${root.path}/DCIM/qr.png  ").isSuccess).isTrue()
    }

    @Test
    fun `uppercase FILE scheme is treated like file`() {
        assertThat(validate("FILE://${root.path}/DCIM/qr.png").isSuccess).isTrue()
        assertDenied("FILE://${outside.path}/secret.png", "outside allowed storage")
    }

    @Test
    fun `paths outside the root are denied for both forms`() {
        assertDenied("file://${outside.path}/secret.png", "outside allowed storage")
        assertDenied("${outside.path}/secret.png", "outside allowed storage")
        assertDenied("file:///etc/hosts", "outside allowed storage")
    }

    @Test
    fun `dot-dot traversal out of the root is denied`() {
        assertDenied("file://${root.path}/../private/secret.png", "outside allowed storage")
        assertDenied("${root.path}/DCIM/../../private/secret.png", "outside allowed storage")
        // Percent-encoded dots decode to `..` before canonicalization.
        assertDenied("file://${root.path}/%2e%2e/private/secret.png", "outside allowed storage")
    }

    @Test
    fun `dot-dot that stays inside the root is fine`() {
        assertThat(validate("file://${root.path}/DCIM/../DCIM/qr.png").isSuccess).isTrue()
    }

    @Test
    fun `sibling directory sharing the root prefix is denied`() {
        // /tmp/x/sdcard2/qr.png starts with "/tmp/x/sdcard" as a string but is not inside it.
        assertDenied("file://${sibling.path}/qr.png", "outside allowed storage")
        assertDenied("${sibling.path}/qr.png", "outside allowed storage")
    }

    @Test
    fun `symlink inside the root pointing outside is denied`() {
        val link = File(root, "escape.png")
        Files.deleteIfExists(link.toPath())
        Files.createSymbolicLink(link.toPath(), File(outside, "secret.png").toPath())
        try {
            assertDenied("file://${link.path}", "outside allowed storage")
        } finally {
            Files.deleteIfExists(link.toPath())
        }
    }

    @Test
    fun `missing file and directories inside the root are reported as not found`() {
        assertDenied("file://${root.path}/DCIM/missing.png", "Image file not found")
        assertDenied("file://${root.path}/DCIM", "Image file not found")
        assertDenied("file://${root.path}", "Image file not found")
    }

    @Test
    fun `file uri without a path is denied`() {
        // Exact message depends on whether Uri.getPath() yields null or "" (-> cwd, outside root).
        assertThat(validate("file://").isFailure).isTrue()
    }

    @Test
    fun `relative path without a scheme is denied`() {
        assertDenied("DCIM/qr.png", "must be a file:// or content:// URI")
    }

    @Test
    fun `bare path with leading whitespace is handled like the trimmed path`() {
        assertThat(validate("  ${root.path}/DCIM/qr.png").isSuccess).isTrue()
    }

    // ---- content:// ---------------------------------------------------------------------------

    @Test
    fun `content uri from another app's provider is accepted`() {
        val ctx = context(mapOf("media" to "com.android.providers.media.module"))
        val result = validate("content://media/external/images/media/42", ctx)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().toString()).isEqualTo("content://media/external/images/media/42")
    }

    @Test
    fun `content uri served by the host app itself is denied`() {
        val ctx = context(mapOf("com.host.app.fileprovider" to hostPackage))
        assertDenied("content://com.host.app.fileprovider/root/data/data/com.host.app/db", "served by the host app", ctx)
        assertDenied("CONTENT://com.host.app.fileprovider/x", "served by the host app", ctx)
    }

    @Test
    fun `a user-id prefixed authority can't sneak past the host-provider check`() {
        val ctx = context(mapOf("com.host.app.fileprovider" to hostPackage))
        assertDenied("content://0@com.host.app.fileprovider/root/data/data/com.host.app/db", "user id", ctx)
        assertDenied("content://10@media/external/images/media/1", "user id", ctx)
    }

    @Test
    fun `content uri with an unknown authority is passed through`() {
        assertThat(validate("content://com.unknown.provider/img").isSuccess).isTrue()
    }

    @Test
    fun `content uri without authority is denied`() {
        assertDenied("content:/no/authority", "image_uri has no authority")
    }

    // ---- other schemes ------------------------------------------------------------------------

    @Test
    fun `unsupported schemes are denied`() {
        listOf(
            "http://example.com/qr.png", "https://example.com/qr.png", "android.resource://com.host.app/raw/x",
            "data:image/png;base64,AAAA", "ftp://example.com/qr.png", "jar:file:///x!/y",
        ).forEach { raw -> assertDenied(raw, "is not supported") }
    }
}
