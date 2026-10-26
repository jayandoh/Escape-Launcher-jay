package com.geecee.escapelauncher.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeUpAreaTest {

    // A Pixel 6a-like screen: 2400 px tall, 2.625 px per dp, 63 px gesture strip
    private val screen = 2400f
    private val strip = 63f
    private val density = 2.625f

    @Test
    fun `without a saved height it matches the bottom quarter of the screen`() {
        assertEquals(600f - strip, SwipeUpArea.heightPx(null, screen, strip, density), 0.01f)
    }

    @Test
    fun `a saved height is a distance in dp above the gesture strip`() {
        assertEquals(262.5f, SwipeUpArea.heightPx(100f, screen, strip, density), 0.01f)
    }

    @Test
    fun `it is never shorter than the minimum`() {
        assertEquals(48f * density, SwipeUpArea.heightPx(10f, screen, strip, density), 0.01f)
    }

    @Test
    fun `it never reaches above the middle of the screen`() {
        assertEquals(screen / 2f - strip, SwipeUpArea.heightPx(1000f, screen, strip, density), 0.01f)
    }

    @Test
    fun `the minimum wins on a screen too short for both limits`() {
        assertEquals(48f * density, SwipeUpArea.heightPx(100f, 200f, strip, density), 0.01f)
    }

    @Test
    fun `clamping a dragged height uses the same limits`() {
        assertEquals(48f * density, SwipeUpArea.clampPx(0f, screen, strip, density), 0.01f)
        assertEquals(screen / 2f - strip, SwipeUpArea.clampPx(5000f, screen, strip, density), 0.01f)
        assertEquals(400f, SwipeUpArea.clampPx(400f, screen, strip, density), 0.01f)
    }
}
