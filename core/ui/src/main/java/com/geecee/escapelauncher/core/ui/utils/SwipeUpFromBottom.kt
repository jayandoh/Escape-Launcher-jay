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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/**
 * Follows a swipe up that starts in the bottom [zoneFraction] of this element, and while
 * [takesOverAnyDrag] is true, any vertical drag anywhere, up or down.
 *
 * Put it on a parent of everything the swipe opens, so it sees every touch first. Both conditions are
 * checked when a finger touches down, so nothing switches off in the middle of a swipe.
 *
 * [onSwipeStart] is called once the swipe is clearly vertical, then [onSwipe] with how far the finger has
 * moved up since it touched down (negative is down), and [onSwipeEnd] with the upward speed (px/s) when
 * the finger lifts.
 *
 * The zone stops above the system gesture / navigation bar area so the Android home gesture keeps working.
 * Touches that aren't taken are left untouched for the content underneath. Once a swipe is taken it is
 * consumed, so lists underneath don't also scroll.
 */
@Composable
fun Modifier.swipeUpFromBottom(
    enabled: Boolean,
    zoneFraction: Float = 0.25f,
    canStartFromZone: () -> Boolean,
    takesOverAnyDrag: () -> Boolean,
    onSwipeStart: () -> Unit,
    onSwipe: (distanceUp: Float) -> Unit,
    onSwipeEnd: (velocityUp: Float) -> Unit
): Modifier {
    if (!enabled) return this

    val density = LocalDensity.current
    val bottomExclusion = maxOf(
        WindowInsets.systemGestures.getBottom(density),
        WindowInsets.navigationBars.getBottom(density)
    )
    val currentCanStartFromZone by rememberUpdatedState(canStartFromZone)
    val currentTakesOverAnyDrag by rememberUpdatedState(takesOverAnyDrag)
    val currentOnSwipeStart by rememberUpdatedState(onSwipeStart)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnSwipeEnd by rememberUpdatedState(onSwipeEnd)

    return pointerInput(bottomExclusion, zoneFraction) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val takeOver = currentTakesOverAnyDrag()
            if (!takeOver) {
                val zoneTop = size.height * (1f - zoneFraction)
                val zoneBottom = (size.height - bottomExclusion).toFloat()
                val inZone = down.position.y in zoneTop..zoneBottom
                if (!inZone || !currentCanStartFromZone()) return@awaitEachGesture
            }

            val touchSlop = viewConfiguration.touchSlop
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var drag = Offset.Zero
            var claimed = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    if (claimed) {
                        change?.let {
                            velocityTracker.addPosition(it.uptimeMillis, it.position)
                            it.consume()
                        }
                        currentOnSwipeEnd(-velocityTracker.calculateVelocity().y)
                    }
                    return@awaitEachGesture
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                drag += change.positionChange()

                if (!claimed) {
                    val sideways = abs(drag.x) > touchSlop && abs(drag.x) >= abs(drag.y)
                    val downwards = !takeOver && drag.y > touchSlop
                    if (sideways || downwards) return@awaitEachGesture
                    val vertical = if (takeOver) abs(drag.y) else -drag.y
                    claimed = vertical > touchSlop && vertical > abs(drag.x)
                    if (claimed) currentOnSwipeStart()
                }

                if (claimed) {
                    change.consume()
                    currentOnSwipe(-drag.y)
                }
            }
        }
    }
}
