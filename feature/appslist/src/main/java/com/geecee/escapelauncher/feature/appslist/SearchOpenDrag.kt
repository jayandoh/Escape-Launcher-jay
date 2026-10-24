package com.geecee.escapelauncher.feature.appslist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * SPIKE: the one owner of the swipe up search's keyboard while a finger drives it (Android 11+).
 *
 * A swipe calls [begin], [drag] and [release]. [begin] can come at any time, including while an earlier
 * swipe is still settling: the keyboard is then caught where it is and follows the new finger from there.
 * Only one request for control of the keyboard is ever outstanding, and callbacks from an older request
 * are ignored, so rapid swipes can't leave it in a half state.
 *
 * Control of the keyboard needs the search field focused, so the overlay calls [onFieldFocused] once it
 * is, and [onFieldGone] when it leaves the screen. Before Android 11, or if Android refuses control,
 * [failed] is set and the keyboard should be shown the usual way.
 */
class SearchOpenDrag(private val view: View, private val scope: CoroutineScope) {
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
    private var cancellation: CancellationSignal? = null
    private var settleJob: Job? = null
    private var startOffset = 0f
    private var distance = 0f
    private var releaseVelocity: Float? = null

    fun begin() {
        generation++
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
        log("begin generation=$generation startOffset=$startOffset haveController=${c != null}")
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

    /** Drops control of the keyboard, e.g. because the search closed for another reason */
    fun cancel() {
        log("cancel")
        settleJob?.cancel()
        settleJob = null
        requestId++
        requestPending = false
        cancellation?.cancel()
        cancellation = null
        controller = null
        fingerDown = false
        active = false
    }

    private fun requestControl() {
        if (!active || controller != null || requestPending || !fieldFocused) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return fail()
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
            val signal = CancellationSignal().also { cancellation = it }
            log("request control id=$id")
            WindowCompat.getInsetsController(activity.window, view).controlWindowInsetsAnimation(
                WindowInsetsCompat.Type.ime(), -1, LinearInterpolator(), signal,
                object : WindowInsetsAnimationControlListenerCompat {
                    override fun onReady(controller: WindowInsetsAnimationControllerCompat, types: Int) {
                        if (id != requestId || !active) {
                            log("onReady stale id=$id")
                            controller.finish(currentImeBottom() > 0)
                            return
                        }
                        requestPending = false
                        this@SearchOpenDrag.controller = controller
                        shownHeight = controller.shownStateInsets.bottom
                        log("onReady id=$id hidden=${controller.hiddenStateInsets.bottom} shown=$shownHeight current=${controller.currentInsets.bottom}")
                        applyDistance()
                        releaseVelocity?.let { settle(it) }
                    }

                    override fun onFinished(controller: WindowInsetsAnimationControllerCompat) {
                        log("onFinished id=$id")
                        if (this@SearchOpenDrag.controller === controller) this@SearchOpenDrag.controller = null
                    }

                    override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
                        log("onCancelled id=$id current=${id == requestId}")
                        if (id != requestId) return
                        val wasReady = this@SearchOpenDrag.controller != null
                        this@SearchOpenDrag.controller = null
                        requestPending = false
                        when {
                            // Something else took the keyboard mid-swipe: ask again so the finger keeps it
                            fingerDown -> requestControl()
                            wasReady -> active = false
                            else -> fail()
                        }
                    }
                })
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

    /** Finish on a flick, otherwise go to whichever end is nearer */
    private fun settle(velocityUp: Float) {
        val c = controller ?: return
        val hidden = c.hiddenStateInsets.bottom.toFloat()
        val shown = c.shownStateInsets.bottom.toFloat()
        val current = c.currentInsets.bottom.toFloat()
        val open = when {
            velocityUp > FLING_VELOCITY -> true
            velocityUp < -FLING_VELOCITY -> false
            else -> current > (hidden + shown) / 2f
        }
        log("settle velocityUp=$velocityUp open=$open")
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
        log("fail")
        requestPending = false
        failed = true
        active = false
    }

    private fun log(message: String) = Log.d("SpikeDrag", message)

    private companion object {
        const val FLING_VELOCITY = 1000f
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
