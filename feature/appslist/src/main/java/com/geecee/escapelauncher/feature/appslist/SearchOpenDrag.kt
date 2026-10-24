package com.geecee.escapelauncher.feature.appslist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.os.CancellationSignal
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * SPIKE: opens the swipe up search with the keyboard following the finger, frame by frame.
 *
 * The swipe calls [begin], [drag] and [release]. Once the search field has focus (without the keyboard
 * popping up on its own), the overlay calls [attach], which takes control of the keyboard's position.
 * Before Android 11, or if Android refuses control, [failed] is set and the keyboard should be shown normally.
 */
class SearchOpenDrag(private val view: View, private val scope: CoroutineScope) {
    /** True from the start of the swipe until the keyboard comes to rest */
    var active by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    /** The keyboard's full height once known, in px */
    var shownHeight by mutableIntStateOf(0)
        private set

    private var controller: WindowInsetsAnimationControllerCompat? = null
    private var cancellation: CancellationSignal? = null
    private var distance = 0f
    private var releaseVelocity: Float? = null
    private var settling = false

    fun begin() {
        cancel()
        active = true
        failed = false
        distance = 0f
        releaseVelocity = null
        settling = false
    }

    fun drag(distanceUp: Float) {
        distance = distanceUp
        applyDistance()
    }

    fun release(velocityUp: Float) {
        releaseVelocity = velocityUp
        if (controller != null) settle(velocityUp)
    }

    /** Called once the search field has focus, so the keyboard has something to type into */
    fun attach() {
        Log.d("SpikeDrag", "attach active=$active controller=$controller")
        if (!active || controller != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return fail()
        val activity = view.context.findActivity() ?: return fail()
        scope.launch {
            // Give the text field a couple of frames to connect to the keyboard
            withFrameNanos {}
            withFrameNanos {}
            if (!active) return@launch
            val signal = CancellationSignal().also { cancellation = it }
            WindowCompat.getInsetsController(activity.window, view).controlWindowInsetsAnimation(
                WindowInsetsCompat.Type.ime(), -1, LinearInterpolator(), signal,
                object : WindowInsetsAnimationControlListenerCompat {
                    override fun onReady(controller: WindowInsetsAnimationControllerCompat, types: Int) {
                        Log.d("SpikeDrag", "onReady hidden=${controller.hiddenStateInsets.bottom} shown=${controller.shownStateInsets.bottom} current=${controller.currentInsets.bottom} distance=$distance")
                        this@SearchOpenDrag.controller = controller
                        shownHeight = controller.shownStateInsets.bottom
                        applyDistance()
                        releaseVelocity?.let { settle(it) }
                    }

                    override fun onFinished(controller: WindowInsetsAnimationControllerCompat) {
                        Log.d("SpikeDrag", "onFinished")
                        this@SearchOpenDrag.controller = null
                        active = false
                    }

                    override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
                        val wasReady = this@SearchOpenDrag.controller != null
                        Log.d("SpikeDrag", "onCancelled wasReady=$wasReady")
                        this@SearchOpenDrag.controller = null
                        if (!wasReady) fail() else active = false
                    }
                })
        }
    }

    fun cancel() {
        cancellation?.cancel()
        cancellation = null
        controller = null
        active = false
    }

    private fun applyDistance() {
        val c = controller ?: return
        if (settling) return
        val hidden = c.hiddenStateInsets.bottom
        val shown = c.shownStateInsets.bottom
        setBottom(c, (hidden + distance).coerceIn(hidden.toFloat(), shown.toFloat()))
    }

    private fun setBottom(c: WindowInsetsAnimationControllerCompat, bottom: Float) {
        val hidden = c.hiddenStateInsets.bottom
        val shown = c.shownStateInsets.bottom
        val fraction = if (shown > hidden) (bottom - hidden) / (shown - hidden) else 1f
        c.setInsetsAndAlpha(Insets.of(0, 0, 0, bottom.roundToInt()), 1f, fraction.coerceIn(0f, 1f))
    }

    /** Finish opening on a flick up or past halfway, otherwise spring back down */
    private fun settle(velocityUp: Float) {
        Log.d("SpikeDrag", "settle velocityUp=$velocityUp")
        val c = controller ?: return
        settling = true
        val hidden = c.hiddenStateInsets.bottom.toFloat()
        val shown = c.shownStateInsets.bottom.toFloat()
        val current = c.currentInsets.bottom.toFloat()
        val open = when {
            velocityUp > FLING_VELOCITY -> true
            velocityUp < -FLING_VELOCITY -> false
            else -> current > (hidden + shown) / 2f
        }
        scope.launch {
            Animatable(current).animateTo(
                targetValue = if (open) shown else hidden,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = velocityUp
            ) {
                if (!c.isFinished && !c.isCancelled) setBottom(c, value.coerceIn(hidden, shown))
            }
            if (!c.isFinished && !c.isCancelled) c.finish(open)
        }
    }

    private fun fail() {
        failed = true
        active = false
    }

    private companion object {
        const val FLING_VELOCITY = 1000f
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
