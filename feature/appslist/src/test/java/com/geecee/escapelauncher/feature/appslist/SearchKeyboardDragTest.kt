package com.geecee.escapelauncher.feature.appslist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchKeyboardDragTest {

    private val hidden = 0f
    private val shown = 900f

    @Test
    fun `a fast flick up opens even from near the bottom`() {
        assertTrue(settlesOpen(current = 50f, hidden = hidden, shown = shown, velocityUp = 1500f))
    }

    @Test
    fun `a fast flick down closes even from near the top`() {
        assertFalse(settlesOpen(current = 850f, hidden = hidden, shown = shown, velocityUp = -1500f))
    }

    @Test
    fun `a slow release past halfway opens`() {
        assertTrue(settlesOpen(current = 500f, hidden = hidden, shown = shown, velocityUp = 0f))
    }

    @Test
    fun `a slow release before halfway closes`() {
        assertFalse(settlesOpen(current = 400f, hidden = hidden, shown = shown, velocityUp = 0f))
    }

    @Test
    fun `a release exactly at halfway closes`() {
        assertFalse(settlesOpen(current = 450f, hidden = hidden, shown = shown, velocityUp = 0f))
    }

    @Test
    fun `speeds below the flick threshold are judged by position`() {
        assertFalse(settlesOpen(current = 300f, hidden = hidden, shown = shown, velocityUp = 900f))
        assertTrue(settlesOpen(current = 600f, hidden = hidden, shown = shown, velocityUp = -900f))
    }
}
