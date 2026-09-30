package io.droidmcp.files

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** `ReadFileTool.readBounded`: line-break normalization, `max_lines` / `max_chars` truncation. */
class ReadBoundedTest {

    @TempDir
    lateinit var dir: File

    private val tool = ReadFileTool(mockk(relaxed = true))
    private val maxChars = 1_048_576

    private fun read(text: String, maxLines: Int = 100): ReadFileTool.BoundedRead {
        val file = File(dir, "f.txt").apply { writeText(text) }
        return tool.readBounded(file, maxLines)
    }

    private fun ReadFileTool.BoundedRead.assert(content: String, lines: Int, truncatedBy: String? = null) {
        assertThat(this.content).isEqualTo(content)
        assertThat(this.lines).isEqualTo(lines)
        assertThat(this.truncatedBy).isEqualTo(truncatedBy)
    }

    @Test
    fun `empty file`() = read("").assert("", 0)

    @Test
    fun `lf, crlf and bare cr are all normalized to lf`() {
        read("a\nb\nc").assert("a\nb\nc", 3)
        read("a\r\nb\r\nc").assert("a\nb\nc", 3)
        read("a\rb\rc").assert("a\nb\nc", 3)
        read("a\r\nb\rc\nd").assert("a\nb\nc\nd", 4)
    }

    @Test
    fun `a single trailing line break does not add an empty line`() {
        read("a\n").assert("a", 1)
        read("a\r\n").assert("a", 1)
        read("a\r").assert("a", 1)
        read("a\nb\n").assert("a\nb", 2)
    }

    @Test
    fun `blank lines are preserved`() {
        read("a\n\nb").assert("a\n\nb", 3)
        read("a\r\n\r\nb").assert("a\n\nb", 3)
        read("a\r\rb").assert("a\n\nb", 3)
        read("a\n\n").assert("a\n", 2)
        read("\n").assert("", 1)
    }

    @Test
    fun `cr immediately followed by lf counts once but lf followed by cr counts twice`() {
        read("a\r\nb").assert("a\nb", 2)
        read("a\n\rb").assert("a\n\nb", 3)
    }

    @Test
    fun `truncates at max_lines and reports it only when more content follows`() {
        read("1\n2\n3\n4", maxLines = 2).assert("1\n2", 2, "max_lines")
        read("1\n2\n\n", maxLines = 2).assert("1\n2", 2, "max_lines")
        read("1\n2", maxLines = 2).assert("1\n2", 2)
        read("1\n2\n", maxLines = 2).assert("1\n2", 2)
        read("1\r\n2\r\n", maxLines = 2).assert("1\n2", 2)
        read("1\r2\r", maxLines = 2).assert("1\n2", 2)
        read("only", maxLines = 1).assert("only", 1)
        read("only\nnext", maxLines = 1).assert("only", 1, "max_lines")
    }

    @Test
    fun `crlf split across the 8 KB read buffer is still one break`() {
        val head = "x".repeat(8191)
        read("$head\r\ny").assert("$head\ny", 2)
        read("$head\r\n", maxLines = 1).assert(head, 1)
    }

    @Test
    fun `a single huge line is capped at max_chars`() {
        read("a".repeat(maxChars + 10)).assert("a".repeat(maxChars), 1, "max_chars")
    }

    @Test
    fun `exactly max_chars is not truncated`() {
        read("a".repeat(maxChars)).assert("a".repeat(maxChars), 1)
        read("a".repeat(maxChars) + "\n").assert("a".repeat(maxChars), 1)
    }

    @Test
    fun `max_chars applies across many lines`() {
        val line = "b".repeat(1023)
        val result = read((line + "\n").repeat(2000), maxLines = 1000)
        // 1000 lines * 1024 chars (incl. breaks) is under the cap, so max_lines wins here.
        assertThat(result.truncatedBy).isEqualTo("max_lines")
        assertThat(result.lines).isEqualTo(1000)

        val big = "c".repeat(4095)
        val capped = read((big + "\n").repeat(300), maxLines = 1000)
        assertThat(capped.truncatedBy).isEqualTo("max_chars")
        assertThat(capped.content.length).isAtMost(maxChars + 1)
    }
}
