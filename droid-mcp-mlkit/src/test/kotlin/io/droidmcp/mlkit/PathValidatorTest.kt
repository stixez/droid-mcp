package io.droidmcp.mlkit

import android.os.Environment
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * `Environment.getExternalStorageDirectory()` is pointed at a temp dir before [PathValidator]'s
 * lazy root is first read. The root is deliberately given via a non-canonical path (`x/./sdcard`)
 * to check the validator canonicalizes the root as well as the candidate.
 */
class PathValidatorTest {

    companion object {
        private lateinit var base: File
        private lateinit var root: File

        @JvmStatic
        @BeforeAll
        fun setUp() {
            base = Files.createTempDirectory("mlkit-sandbox").toFile().canonicalFile
            root = File(base, "sdcard").apply { mkdirs() }
            File(root, "Download").mkdirs()
            File(base, "sdcard2").mkdirs()
            File(base, "private").mkdirs()
            mockkStatic(Environment::class)
            every { Environment.getExternalStorageDirectory() } returns File(base, "./sdcard")
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            unmockkAll()
            base.deleteRecursively()
        }
    }

    private fun assertAllowed(vararg paths: String) = paths.forEach {
        assertWithMessage("$it should be allowed").that(PathValidator.isAllowed(it)).isTrue()
    }

    private fun assertDenied(vararg paths: String) = paths.forEach {
        assertWithMessage("$it should be denied").that(PathValidator.isAllowed(it)).isFalse()
    }

    @Test
    fun `root itself and descendants are allowed`() = assertAllowed(
        root.path, "${root.path}/", "${root.path}/Download", "${root.path}/Download/a.txt",
        "${root.path}/does/not/exist/yet.txt",
    )

    @Test
    fun `non-canonical spellings that stay inside are allowed`() = assertAllowed(
        "${root.path}/./Download/a.txt", "${root.path}/Download/../Download/a.txt", "${root.path}//Download",
    )

    @Test
    fun `dot-dot traversal out of the root is denied`() = assertDenied(
        "${root.path}/..", "${root.path}/../private/secret", "${root.path}/Download/../../private",
        "${root.path}/Download/../../../../../../etc/passwd",
    )

    @Test
    fun `prefix sibling of the root is denied`() = assertDenied(
        "${base.path}/sdcard2", "${base.path}/sdcard2/x.txt", "${root.path}2/x.txt", "${root.path}-backup",
    )

    @Test
    fun `unrelated absolute paths are denied`() = assertDenied("/etc/hosts", "/", base.path, "/data/data/com.host.app")

    @Test
    fun `relative paths resolve against the working directory and are denied`() =
        assertDenied("Download/a.txt", "../sdcard/Download")

    @Test
    fun `symlink inside the root pointing outside is denied`() {
        val link = File(root, "escape")
        Files.deleteIfExists(link.toPath())
        Files.createSymbolicLink(link.toPath(), File(base, "private").toPath())
        try {
            assertDenied(link.path, "${link.path}/secret")
        } finally {
            Files.deleteIfExists(link.toPath())
        }
    }

    @Test
    fun `symlink outside pointing into the root is allowed`() {
        val link = File(base, "into-root")
        Files.deleteIfExists(link.toPath())
        Files.createSymbolicLink(link.toPath(), File(root, "Download").toPath())
        try {
            assertThat(PathValidator.isAllowed("${link.path}/a.txt")).isTrue()
        } finally {
            Files.deleteIfExists(link.toPath())
        }
    }
}
