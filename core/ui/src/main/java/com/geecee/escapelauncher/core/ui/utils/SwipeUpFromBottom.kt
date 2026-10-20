package com.geecee.escapelauncher.core.ui.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Calls [onSwipeUp] when a swipe up starts in the bottom [zoneFraction] of this element.
 *
 * The zone stops above the system gesture / navigation bar area so the Android home gesture keeps working.
 * Swipes that start higher up, move sideways or move down are left untouched for the content underneath.
 * Once a swipe in the zone is clearly upwards it is consumed, so lists underneath don't also scroll.
 */
@Composable
fun Modifier.swipeUpFromBottom(
    enabled: Boolean,
    zoneFraction: Float = 0.25f,
    onSwipeUp: () -> Unit
): Modifier {
    if (!enabled) return this

    val density = LocalDensity.current
    val bottomExclusion = maxOf(
        WindowInsets.systemGestures.getBottom(density),
        WindowInsets.navigationBars.getBottom(density)
    )
    val triggerDistance = with(density) { 48.dp.toPx() }
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)

    return pointerInput(bottomExclusion, zoneFraction, triggerDistance) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val zoneTop = size.height * (1f - zoneFraction)
            val zoneBottom = (size.height - bottomExclusion).toFloat()
            if (down.position.y < zoneTop || down.position.y > zoneBottom) return@awaitEachGesture

            val touchSlop = viewConfiguration.touchSlop
            var drag = Offset.Zero
            var claimed = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                drag += change.positionChange()

                if (!claimed) {
                    val sideways = abs(drag.x) > touchSlop && abs(drag.x) >= abs(drag.y)
                    val downwards = drag.y > touchSlop
                    if (sideways || downwards) return@awaitEachGesture
                    claimed = -drag.y > touchSlop && -drag.y > abs(drag.x)
                }

                if (claimed) {
                    change.consume()
                    if (-drag.y >= triggerDistance) {
                        currentOnSwipeUp()
                        // Swallow the rest of this swipe
                        do {
                            val rest = awaitPointerEvent(PointerEventPass.Initial)
                            rest.changes.forEach { it.consume() }
                        } while (rest.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                }
            }
        }
    }
}
