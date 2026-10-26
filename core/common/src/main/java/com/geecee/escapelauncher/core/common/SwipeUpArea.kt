package com.geecee.escapelauncher.core.common

/**
 * The area at the bottom of the launcher where a swipe up opens search.
 *
 * It sits just above Android's gesture strip (or 3-button bar), which the launcher never receives touches
 * from. Its height is saved as a distance in dp so it gives the same thumb reach on any phone. Until one
 * is saved, it covers the bottom fifth of the screen.
 */
object SwipeUpArea {
    /** Thinner than a fingertip is too hard to hit on purpose */
    const val MIN_HEIGHT_DP = 48f

    /** Taller would take too much scrolling away from the apps list */
    const val MAX_SCREEN_FRACTION = 0.5f

    /** Used until a height is saved */
    const val DEFAULT_SCREEN_FRACTION = 0.2f

    /** How much one accessibility increase / decrease step changes it */
    const val STEP_DP = 8f

    /**
     * The area's height above the gesture strip, in px.
     *
     * @param savedHeightDp the saved height, or null to use the default
     */
    fun heightPx(savedHeightDp: Float?, screenHeightPx: Float, gestureStripPx: Float, density: Float): Float {
        val wanted = savedHeightDp?.let { it * density } ?: (screenHeightPx * DEFAULT_SCREEN_FRACTION - gestureStripPx)
        return clampPx(wanted, screenHeightPx, gestureStripPx, density)
    }

    /** Keeps a height in px between the minimum and the middle of the screen; the minimum wins if both can't fit */
    fun clampPx(heightPx: Float, screenHeightPx: Float, gestureStripPx: Float, density: Float): Float =
        heightPx
            .coerceAtMost(screenHeightPx * MAX_SCREEN_FRACTION - gestureStripPx)
            .coerceAtLeast(MIN_HEIGHT_DP * density)
}
