package com.geecee.escapelauncher.feature.appslist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.CancellationSignal
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Upward release speed, in px/s, past which a swipe counts as a flick */
private const val FLING_VELOCITY = 1000f

/**
 * Where the keyboard should come to rest when the finger lifts, given where it is ([current]) between
 * [hidden] and [shown]: open on a flick up, closed on a flick down, otherwise whichever end is nearer.
 */
internal fun settlesOpen(current: Float, hidden: Float, shown: Float, velocityUp: Float): Boolean = when {
    velocityUp > FLING_VELOCITY -> true
    velocityUp < -FLING_VELOCITY -> false
    else -> current > (hidden + shown) / 2f
}

/**
 * The one owner of the swipe up search's keyboard while a finger moves it, so the keyboard follows
 * the finger frame by frame.
 *
 * A swipe from outside the search calls [begin], [drag] and [release]; drags on the open search come in
 * through [nestedScrollConnection], which the search's list uses. [begin] can come at any time, including
 * while an earlier swipe is still settling: the keyboard is then caught where it is and follows the new
 * finger from there.
 * Only one request for control of the keyboard is ever outstanding, and callbacks from an older request
 * are ignored, so rapid swipes can't leave it in a half state.
 *
 * Control of the keyboard needs the search field focused, so the overlay calls [onFieldFocused] once it
 * is, and [onFieldGone] when it leaves the screen. Apps can only move the keyboard like this on Android 11
 * and later ([isSupported]). Before that, or if Android refuses control, [failed] is set and the keyboard
 * is shown the usual way.
 */
class SearchKeyboardDrag(private val view: View, private val scope: CoroutineScope) {
    /** Whether the search overlay is shown, and whether it's still animating in or out */
    val overlayState = MutableTransitionState(false)

    /** True from the start of a swipe until the keyboard comes to rest */
    var active by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    /** Bumped on every swipe, so the overlay can reset its per-swipe state */
    var generation by mutableIntStateOf(0)
        private set
    /** The keyboard's full height once known, in px */
    var shownHeight by mutableIntStateOf(0)
        private set
    var fingerDown = false
        private set

    /** While the search is moving or fading out, any vertical drag anywhere takes it over */
    val takesOverAnyDrag: Boolean
        get() = active || (overlayState.currentState && !overlayState.targetState)

    private var fieldFocused = false
    private var controller: WindowInsetsAnimationControllerCompat? = null
    private var requestId = 0
    private var requestPending = false
    private var settleJob: Job? = null
    private var startOffset = 0f
    private var distance = 0f
    private var releaseVelocity: Float? = null
    private var opening = true
    private var nestedDragging = false
    private var nestedDistance = 0f

    /**
     * Drags on the open search, through its list: with the keyboard up, dragging down moves it with the
     * finger. With results listed and the keyboard down, the list scrolls as usual, and dragging up once the
     * list can't scroll any further brings the keyboard back up.
     */
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!canDragOnSearch(source)) return Offset.Zero
            if (!nestedDragging) {
                // Only a drag down while the keyboard is up starts moving it before the list
                if (available.y <= 0f || currentImeBottom() == 0) return Offset.Zero
                startNestedDrag()
            }
            return moveByNestedDrag(available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!canDragOnSearch(source)) return Offset.Zero
            // Dragging up with nothing left for the list to scroll brings the keyboard up
            if (available.y >= 0f) return Offset.Zero
            if (!nestedDragging) startNestedDrag()
            return moveByNestedDrag(available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!nestedDragging) return Velocity.Zero
            nestedDragging = false
            release(-available.y)
            return available
        }
    }

    /**
     * A finger drag on the search, unless the search is already closing: a drag that lands on it while it fades
     * out would otherwise bring the keyboard back up with no search above it
     */
    private fun canDragOnSearch(source: NestedScrollSource): Boolean =
        source == NestedScrollSource.UserInput && isSupported && (nestedDragging || overlayState.targetState)

    private fun startNestedDrag() {
        begin(opening = false)
        nestedDragging = true
        nestedDistance = 0f
    }

    private fun moveByNestedDrag(deltaY: Float): Offset {
        nestedDistance -= deltaY
        drag(nestedDistance)
        return Offset(0f, deltaY)
    }

    /** [opening] is false for a drag on the search that's already open */
    fun begin(opening: Boolean = true) {
        generation++
        if (!isSupported) return
        this.opening = opening
        nestedDragging = false
        active = true
        failed = false
        fingerDown = true
        releaseVelocity = null
        distance = 0f
        settleJob?.cancel()
        settleJob = null

        val c = controller?.takeIf { it.isReady && !it.isFinished && !it.isCancelled }
        startOffset = if (c != null) {
            // Catch the keyboard where the last swipe left it
            (c.currentInsets.bottom - c.hiddenStateInsets.bottom).toFloat()
        } else {
            controller = null
            currentImeBottom().toFloat()
        }
        if (c == null) requestControl()
    }

    /** [distanceUp] is how far the finger has moved up since this swipe started; negative is down */
    fun drag(distanceUp: Float) {
        distance = distanceUp
        applyDistance()
    }

    fun release(velocityUp: Float) {
        fingerDown = false
        releaseVelocity = velocityUp
        if (controller != null) settle(velocityUp)
    }

    fun onFieldFocused() {
        fieldFocused = true
        if (active) requestControl()
    }

    fun onFieldGone() {
        fieldFocused = false
    }

    /**
     * The search closed for another reason (Back, Home, an app opening...): leave the keyboard hidden.
     *
     * Control is finished as hidden rather than cancelled, because cancelling puts the keyboard back the way
     * it was before the swipe took it, which can be fully up even though the search has closed.
     * A request still waiting for Android is finished as hidden when it arrives.
     */
    fun close() {
        settleJob?.cancel()
        settleJob = null
        requestId++
        requestPending = false
        val c = controller
        controller = null
        if (c != null && !c.isFinished && !c.isCancelled) c.finish(false)
        nestedDragging = false
        fingerDown = false
        active = false
    }

    private fun requestControl() {
        if (!active || controller != null || requestPending || !fieldFocused) return
        val activity = view.context.findActivity() ?: return fail()
        val id = ++requestId
        requestPending = true
        scope.launch {
            // Give the text field a couple of frames to connect to the keyboard
            withFrameNanos {}
            withFrameNanos {}
            if (id != requestId || !active) {
                if (id == requestId) requestPending = false
                return@launch
            }
            val signal = CancellationSignal()
            WindowCompat.getInsetsController(activity.window, view).controlWindowInsetsAnimation(
                WindowInsetsCompat.Type.ime(), -1, LinearInterpolator(), signal,
                object : WindowInsetsAnimationControlListenerCompat {
                    override fun onReady(controller: WindowInsetsAnimationControllerCompat, types: Int) {
                        if (id != requestId || !active) {
                            // The search closed while this request was waiting: keep the keyboard hidden
                            controller.finish(false)
                            return
                        }
                        requestPending = false
                        this@SearchKeyboardDrag.controller = controller
                        shownHeight = controller.shownStateInsets.bottom
                        applyDistance()
                        releaseVelocity?.let { settle(it) }
                    }

                    override fun onFinished(controller: WindowInsetsAnimationControllerCompat) {
                        if (this@SearchKeyboardDrag.controller === controller) this@SearchKeyboardDrag.controller = null
                    }

                    override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
                        if (id != requestId) return
                        val wasReady = this@SearchKeyboardDrag.controller != null
                        this@SearchKeyboardDrag.controller = null
                        requestPending = false
                        when {
                            // Something else took the keyboard mid-swipe: ask again so the finger keeps it
                            fingerDown -> requestControl()
                            wasReady -> active = false
                            else -> fail()
                        }
                    }
                })
            // Android can sit on a request for up to 2 s, for example while the keyboard app gets ready again
            // after a while unused. Rather than show nothing for that long, bring the keyboard up the usual way.
            delay(CONTROL_WAIT)
            if (id == requestId && requestPending) {
                requestId++
                signal.cancel()
                fail()
            }
        }
    }

    private fun applyDistance() {
        val c = controller ?: return
        if (settleJob != null) return
        val hidden = c.hiddenStateInsets.bottom.toFloat()
        val shown = c.shownStateInsets.bottom.toFloat()
        setBottom(c, (hidden + startOffset + distance).coerceIn(hidden, shown))
    }

    private fun setBottom(c: WindowInsetsAnimationControllerCompat, bottom: Float) {
        if (c.isFinished || c.isCancelled) return
        val hidden = c.hiddenStateInsets.bottom
        val shown = c.shownStateInsets.bottom
        val fraction = if (shown > hidden) (bottom - hidden) / (shown - hidden) else 1f
        c.setInsetsAndAlpha(Insets.of(0, 0, 0, bottom.roundToInt()), 1f, fraction.coerceIn(0f, 1f))
    }

    private fun settle(velocityUp: Float) {
        val c = controller ?: return
        val hidden = c.hiddenStateInsets.bottom.toFloat()
        val shown = c.shownStateInsets.bottom.toFloat()
        val current = c.currentInsets.bottom.toFloat()
        val open = settlesOpen(current, hidden, shown, velocityUp)
        settleJob?.cancel()
        settleJob = scope.launch {
            Animatable(current).animateTo(
                targetValue = if (open) shown else hidden,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = velocityUp
            ) {
                setBottom(c, value.coerceIn(hidden, shown))
            }
            // Only reached if no new swipe took over in the meantime
            if (controller === c) {
                controller = null
                settleJob = null
                if (!c.isFinished && !c.isCancelled) c.finish(open)
                active = false
            }
        }
    }

    private fun currentImeBottom(): Int =
        ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

    private fun fail() {
        requestPending = false
        // Only an opening needs the keyboard shown the usual way; a drag on the open search just stops
        if (opening) failed = true
        active = false
    }

    companion object {
        /** Android usually hands the keyboard over within about 200 ms */
        private val CONTROL_WAIT = 400.milliseconds

        /** Apps can move the keyboard with the finger from Android 11 */
        val isSupported: Boolean
            @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
