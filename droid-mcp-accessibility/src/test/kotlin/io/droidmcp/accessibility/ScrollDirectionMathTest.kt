package io.droidmcp.accessibility

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Locks in the reading-direction-to-finger-physics mapping that EdgeClaw
 * relies on. If this test ever flips, scrolling will reveal the wrong half
 * of the content and the LLM's mental model breaks.
 */
class ScrollDirectionMathTest {

    private val width = 1080f
    private val height = 2400f
    private val cx = width / 2f
    private val cy = height / 2f
    private val dx = width * 0.35f
    private val dy = height * 0.35f

    @Test
    fun `down reveals content below — finger moves UP`() {
        val s = swipeCoordsFor("down", width, height)
        assertThat(s.startX).isEqualTo(cx)
        assertThat(s.endX).isEqualTo(cx)
        // Start Y is BELOW end Y → finger moves from larger Y to smaller Y → moves UP on screen.
        assertThat(s.startY).isGreaterThan(s.endY)
        assertThat(s.startY).isEqualTo(cy + dy)
        assertThat(s.endY).isEqualTo(cy - dy)
    }

    @Test
    fun `up reveals content above — finger moves DOWN`() {
        val s = swipeCoordsFor("up", width, height)
        assertThat(s.startX).isEqualTo(cx)
        assertThat(s.endX).isEqualTo(cx)
        // Start Y is ABOVE end Y → finger moves from smaller Y to larger Y → moves DOWN on screen.
        assertThat(s.startY).isLessThan(s.endY)
        assertThat(s.startY).isEqualTo(cy - dy)
        assertThat(s.endY).isEqualTo(cy + dy)
    }

    @Test
    fun `right reveals content to the right — finger moves LEFT`() {
        val s = swipeCoordsFor("right", width, height)
        assertThat(s.startY).isEqualTo(cy)
        assertThat(s.endY).isEqualTo(cy)
        // Start X is to the RIGHT of end X → finger moves from larger X to smaller X → moves LEFT.
        assertThat(s.startX).isGreaterThan(s.endX)
        assertThat(s.startX).isEqualTo(cx + dx)
        assertThat(s.endX).isEqualTo(cx - dx)
    }

    @Test
    fun `left reveals content to the left — finger moves RIGHT`() {
        val s = swipeCoordsFor("left", width, height)
        assertThat(s.startY).isEqualTo(cy)
        assertThat(s.endY).isEqualTo(cy)
        // Start X is to the LEFT of end X → finger moves from smaller X to larger X → moves RIGHT.
        assertThat(s.startX).isLessThan(s.endX)
        assertThat(s.startX).isEqualTo(cx - dx)
        assertThat(s.endX).isEqualTo(cx + dx)
    }

    @Test
    fun `swipe distance is 35 percent of the relevant dimension`() {
        val down = swipeCoordsFor("down", width, height)
        val downDistance = (down.startY - down.endY)
        assertThat(downDistance).isEqualTo(2 * dy)

        val right = swipeCoordsFor("right", width, height)
        val rightDistance = (right.startX - right.endX)
        assertThat(rightDistance).isEqualTo(2 * dx)
    }
}
