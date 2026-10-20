package io.droidmcp.files

import android.os.Environment
import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.ResourceContents
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/** `file:` URI handling in [FilesResourceProvider]. */
class FilesResourceProviderTest {

    private lateinit var base: File
    private lateinit var root: File
    private val provider = FilesResourceProvider()

    @BeforeEach
    fun setUp() {
        base = Files.createTempDirectory("files-res").toFile().canonicalFile
        root = File(base, "sdcard").apply { mkdirs() }
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns root
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        base.deleteRecursively()
    }

    /** A `file://` URI for [file] written the way a client would paste it: a raw path. */
    private fun rawUri(file: File) = "file://" + file.path.replace(File.separatorChar, '/').let { if (it.startsWith("/")) it else "/$it" }

    private suspend fun text(uri: String) = (provider.read(uri)?.single() as? ResourceContents.Text)?.text

    @Test
    fun `hash, question mark and space are part of the file name`() = runTest {
        File(root, "report").writeText("wrong file")
        val hash = File(root, "report#2.txt").apply { writeText("two") }
        // '?' can't appear in a Windows file name; the space still exercises the raw-path parsing.
        val question = if (File.separatorChar == '/') "?" else ""
        val spaced = File(root, "My Notes$question.txt").apply { writeText("notes") }
        assertThat(text(rawUri(hash))).isEqualTo("two")
        assertThat(text(rawUri(spaced))).isEqualTo("notes")
    }

    @Test
    fun `text comes back byte-for-byte, and oversized or non-UTF-8 text as a blob`() = runTest {
        val csv = "a,b\r\nc,d\r\n"
        val crlf = File(root, "data.csv").apply { writeText(csv) }
        assertThat(text(rawUri(crlf))).isEqualTo(csv)

        val big = File(root, "big.log").apply { writeText("x".repeat(1024 * 1024 + 1)) }
        assertThat(provider.read(rawUri(big))!!.single()).isInstanceOf(ResourceContents.Blob::class.java)

        val latin1 = File(root, "latin1.txt").apply { writeBytes(byteArrayOf(0x63, 0xE9.toByte())) }
        assertThat(provider.read(rawUri(latin1))!!.single()).isInstanceOf(ResourceContents.Blob::class.java)
    }

    @Test
    fun `percent escapes are decoded`() = runTest {
        val file = File(root, "a b#c.txt").apply { writeText("decoded") }
        val encoded = rawUri(file).replace(" ", "%20").replace("#", "%23")
        assertThat(text(encoded)).isEqualTo("decoded")
    }

    @Test
    fun `remote hosts, paths outside the sandbox and bad escapes are not found`() = runTest {
        val outside = File(base, "private.txt").apply { writeText("secret") }
        assertThat(provider.read(rawUri(outside))).isNull()
        assertThat(provider.read("file://example.com/sdcard/x.txt")).isNull()
        assertThat(provider.read(rawUri(File(root, "x")) + "%zz")).isNull()
    }
}
