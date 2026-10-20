package io.droidmcp.files

import android.content.Context
import android.os.Environment
import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ToolResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/** [WriteFileTool], [CreateDirectoryTool], [MoveFileTool] and [DeleteFileTool] against a temp sandbox. */
class FileWriteToolsTest {

    private lateinit var base: File
    private lateinit var root: File
    private lateinit var outside: File

    @BeforeEach
    fun setUp() {
        base = Files.createTempDirectory("files-write").toFile().canonicalFile
        root = File(base, "sdcard").apply { mkdirs() }
        File(root, "Download").mkdirs()
        outside = File(base, "private").apply { mkdirs() }
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns root
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        base.deleteRecursively()
    }

    private val download get() = File(root, "Download")

    /** Runs [tool] and, on success, checks the result against its declared output schema. */
    private suspend fun McpTool.run(vararg params: Pair<String, Any>): ToolResult {
        val result = execute(mapOf(*params))
        if (result.isSuccess) assertThat(OutputSchema.violations(outputSchema!!, result.data)).isEmpty()
        return result
    }

    // write_file

    @Test
    fun `write creates a new file`() = runTest {
        val target = File(download, "note.txt")
        val result = WriteFileTool().run("path" to target.path, "content" to "héllo")
        assertThat(result.isSuccess).isTrue()
        assertThat(target.readText()).isEqualTo("héllo")
        assertThat(result.data!!["created"]).isEqualTo(true)
        assertThat(result.data!!["bytes_written"]).isEqualTo(6)
    }

    @Test
    fun `write refuses an existing file in create mode`() = runTest {
        val target = File(download, "note.txt").apply { writeText("old") }
        val result = WriteFileTool().run("path" to target.path, "content" to "new")
        assertThat(result.isSuccess).isFalse()
        assertThat(target.readText()).isEqualTo("old")
    }

    @Test
    fun `write overwrites and appends`() = runTest {
        val target = File(download, "note.txt").apply { writeText("a") }
        assertThat(WriteFileTool().run("path" to target.path, "content" to "b", "mode" to "append").isSuccess).isTrue()
        assertThat(target.readText()).isEqualTo("ab")
        val overwritten = WriteFileTool().run("path" to target.path, "content" to "c", "mode" to "overwrite")
        assertThat(target.readText()).isEqualTo("c")
        assertThat(overwritten.data!!["created"]).isEqualTo(false)
    }

    @Test
    fun `write creates parents only when asked`() = runTest {
        val target = File(download, "a/b/note.txt")
        assertThat(WriteFileTool().run("path" to target.path, "content" to "x").isSuccess).isFalse()
        assertThat(WriteFileTool().run("path" to target.path, "content" to "x", "create_dirs" to true).isSuccess).isTrue()
        assertThat(target.readText()).isEqualTo("x")
    }

    @Test
    fun `write outside the sandbox is denied`() = runTest {
        val target = File(outside, "escape.txt")
        val traversal = File(download, "../../private/escape.txt")
        for (path in listOf(target.path, traversal.path)) {
            val result = WriteFileTool().run("path" to path, "content" to "x")
            assertThat(result.errorMessage).contains("Access denied")
        }
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun `write rejects a bad mode and a directory path`() = runTest {
        assertThat(WriteFileTool().run("path" to File(download, "x").path, "content" to "x", "mode" to "truncate").isSuccess).isFalse()
        assertThat(WriteFileTool().run("path" to download.path, "content" to "x", "mode" to "overwrite").isSuccess).isFalse()
    }

    // create_directory

    @Test
    fun `create directory is idempotent`() = runTest {
        val dir = File(download, "x/y")
        assertThat(CreateDirectoryTool().run("path" to dir.path).data!!["created"]).isEqualTo(true)
        assertThat(CreateDirectoryTool().run("path" to dir.path).data!!["created"]).isEqualTo(false)
        assertThat(dir.isDirectory).isTrue()
    }

    @Test
    fun `create directory fails where a file exists`() = runTest {
        val file = File(download, "taken").apply { writeText("") }
        assertThat(CreateDirectoryTool().run("path" to file.path).isSuccess).isFalse()
    }

    // move_file

    @Test
    fun `move renames a file`() = runTest {
        val source = File(download, "a.txt").apply { writeText("a") }
        val destination = File(download, "b.txt")
        val result = MoveFileTool().run("source" to source.path, "destination" to destination.path)
        assertThat(result.isSuccess).isTrue()
        assertThat(source.exists()).isFalse()
        assertThat(destination.readText()).isEqualTo("a")
    }

    @Test
    fun `move needs overwrite to replace a file`() = runTest {
        val source = File(download, "a.txt").apply { writeText("a") }
        val destination = File(download, "b.txt").apply { writeText("b") }
        assertThat(MoveFileTool().run("source" to source.path, "destination" to destination.path).isSuccess).isFalse()
        assertThat(destination.readText()).isEqualTo("b")
        assertThat(MoveFileTool().run("source" to source.path, "destination" to destination.path, "overwrite" to true).isSuccess).isTrue()
        assertThat(destination.readText()).isEqualTo("a")
        // The replaced file was set aside during the move and removed afterwards.
        assertThat(download.list()!!.toList()).containsExactly("b.txt")
    }

    @Test
    fun `move with overwrite won't replace a top-level folder`() = runTest {
        val movies = File(root, "Movies").apply { mkdirs() }
        val source = File(download, "a.txt").apply { writeText("a") }
        val result = MoveFileTool().run("source" to source.path, "destination" to movies.path, "overwrite" to true)
        assertThat(result.errorMessage).contains("top-level")
        assertThat(movies.isDirectory).isTrue()
        assertThat(source.exists()).isTrue()
    }

    @Test
    fun `move can change only the letter case`() = runTest {
        val source = File(download, "IMG.JPG").apply { writeText("pixels") }
        val result = MoveFileTool().run("source" to source.path, "destination" to File(download, "IMG.jpg").path)
        assertThat(result.isSuccess).isTrue()
        assertThat(download.list()!!.toList()).containsExactly("IMG.jpg")
        assertThat(File(download, "IMG.jpg").readText()).isEqualTo("pixels")
    }

    @Test
    fun `writes through a dangling link are refused`() = runTest {
        val link = File(download, "x")
        val linked = runCatching { Files.createSymbolicLink(link.toPath(), File(outside, "missing").toPath()) }.isSuccess
        assumeTrue(linked, "symbolic links are not available on this host")
        val result = WriteFileTool().run("path" to link.path, "content" to "x")
        assertThat(result.errorMessage).contains("broken symbolic link")
        assertThat(File(outside, "missing").exists()).isFalse()
    }

    @Test
    fun `write tools are opt-in`() {
        val context = mockk<Context>(relaxed = true)
        assertThat(FilesTools.all(context).map { it.name }).containsNoneOf("write_file", "create_directory", "move_file", "delete_file")
    }

    @Test
    fun `files directly in the root can be deleted and moved, Android is off-limits`() = runTest {
        val loose = File(root, "notes.txt").apply { writeText("n") }
        assertThat(MoveFileTool().run("source" to loose.path, "destination" to File(root, "renamed.txt").path).isSuccess).isTrue()
        assertThat(DeleteFileTool().run("path" to File(root, "renamed.txt").path).isSuccess).isTrue()

        val otherApp = File(root, "Android/data/com.other.app").apply { mkdirs() }
        File(otherApp, "save.dat").writeText("save")
        assertThat(DeleteFileTool().run("path" to otherApp.path, "recursive" to true).errorMessage).contains("Android/")
        assertThat(WriteFileTool().run("path" to File(otherApp, "x.txt").path, "content" to "x").errorMessage).contains("Android/")
        val source = File(download, "a.txt").apply { writeText("a") }
        assertThat(MoveFileTool().run("source" to source.path, "destination" to File(otherApp, "a.txt").path).errorMessage).contains("Android/")
        assertThat(File(otherApp, "save.dat").exists()).isTrue()
    }

    @Test
    fun `move refuses top-level folders`() = runTest {
        val result = MoveFileTool().run("source" to download.path, "destination" to File(root, "Elsewhere").path)
        assertThat(result.errorMessage).contains("top-level")
        assertThat(download.isDirectory).isTrue()
    }

    @Test
    fun `move refuses the root, itself, and leaving the sandbox`() = runTest {
        val dir = File(download, "d").apply { mkdirs() }
        assertThat(MoveFileTool().run("source" to root.path, "destination" to File(download, "r").path).isSuccess).isFalse()
        assertThat(MoveFileTool().run("source" to dir.path, "destination" to File(dir, "inner").path).isSuccess).isFalse()
        assertThat(MoveFileTool().run("source" to dir.path, "destination" to File(outside, "d").path).errorMessage).contains("Access denied")
        assertThat(dir.isDirectory).isTrue()
    }

    // delete_file

    @Test
    fun `delete removes a file and an empty directory`() = runTest {
        val file = File(download, "a.txt").apply { writeText("a") }
        val dir = File(download, "empty").apply { mkdirs() }
        assertThat(DeleteFileTool().run("path" to file.path).data!!["deleted_count"]).isEqualTo(1)
        assertThat(DeleteFileTool().run("path" to dir.path).isSuccess).isTrue()
        assertThat(file.exists() || dir.exists()).isFalse()
    }

    @Test
    fun `delete of a non-empty directory needs recursive`() = runTest {
        val dir = File(download, "tree").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.txt").writeText("b")
        assertThat(DeleteFileTool().run("path" to dir.path).isSuccess).isFalse()
        assertThat(dir.exists()).isTrue()
        val result = DeleteFileTool().run("path" to dir.path, "recursive" to true)
        assertThat(result.data!!["deleted_count"]).isEqualTo(4)
        assertThat(dir.exists()).isFalse()
    }

    @Test
    fun `delete refuses the root and top-level folders`() = runTest {
        assertThat(DeleteFileTool().run("path" to root.path, "recursive" to true).isSuccess).isFalse()
        assertThat(DeleteFileTool().run("path" to download.path, "recursive" to true).isSuccess).isFalse()
        assertThat(download.isDirectory).isTrue()
    }

    @Test
    fun `delete removes a link but not its target, and won't recurse through links`() = runTest {
        val secret = File(outside, "keep.txt").apply { writeText("keep") }
        val tree = File(download, "tree").apply { mkdirs() }
        val link = File(tree, "escape")
        val linked = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        assumeTrue(linked, "symbolic links are not available on this host")

        assertThat(DeleteFileTool().run("path" to tree.path, "recursive" to true).isSuccess).isFalse()
        assertThat(secret.exists()).isTrue()

        val inside = File(download, "inner").apply { mkdirs() }
        val innerLink = File(download, "inner-link")
        Files.createSymbolicLink(innerLink.toPath(), inside.toPath())
        assertThat(DeleteFileTool().run("path" to innerLink.path).isSuccess).isTrue()
        assertThat(inside.isDirectory).isTrue()
    }
}
