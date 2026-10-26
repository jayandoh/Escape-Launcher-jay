package com.geecee.escapelauncher.feature.appslist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geecee.escapelauncher.core.common.SwipeUpArea
import com.geecee.escapelauncher.core.ui.R
import kotlin.math.roundToInt

/**
 * Shown over the real home screen to adjust where swipe up to search starts.
 *
 * The area above Android's gesture strip is tinted and its top edge follows a vertical drag anywhere on
 * the screen. Done saves it ([onDone] gets the height in dp, or null for the default), Reset goes back to
 * the default, and Back cancels ([onCancel]). Everything under it is paused while it's open.
 */
@Composable
fun SwipeUpAreaEditor(
    savedHeightDp: Float?,
    onDone: (heightDp: Float?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    hapticFeedbackEnabled: Boolean = true
) {
    BackHandler(onBack = onCancel)

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val gestureStrip = maxOf(
        WindowInsets.systemGestures.getBottom(density),
        WindowInsets.navigationBars.getBottom(density)
    ).toFloat()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight.toFloat()
        val minPx = SwipeUpArea.clampPx(0f, screenHeight, gestureStrip, density.density)
        val maxPx = SwipeUpArea.clampPx(Float.MAX_VALUE, screenHeight, gestureStrip, density.density)
        fun defaultPx() = SwipeUpArea.heightPx(null, screenHeight, gestureStrip, density.density)

        // Null height means "the default", which keeps following the screen size
        var useDefault by remember(savedHeightDp) { mutableStateOf(savedHeightDp == null) }
        var heightPx by remember(screenHeight, gestureStrip, savedHeightDp) {
            mutableFloatStateOf(SwipeUpArea.heightPx(savedHeightDp, screenHeight, gestureStrip, density.density))
        }

        fun setHeight(wanted: Float) {
            val clamped = wanted.coerceIn(minPx, maxPx)
            // A soft tick when the line reaches a limit
            if (clamped != wanted && heightPx != clamped && hapticFeedbackEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            heightPx = clamped
            useDefault = false
        }

        val areaTop = screenHeight - gestureStrip - heightPx
        val heightDp = heightPx / density.density
        // Shown the same way as the old "bottom quarter": the share of the screen it covers, strip included
        val percent = ((heightPx + gestureStrip) / screenHeight * 100).roundToInt()
        val primary = MaterialTheme.colorScheme.primary
        val tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
        val strip = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        val lineWidth = with(density) { 2.dp.toPx() }

        // Takes every touch, so the home screen underneath is paused, and moves the line on a vertical drag
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(screenHeight, gestureStrip) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        setHeight(heightPx - dragAmount)
                    }
                }
        ) {
            drawRect(scrim, size = Size(size.width, areaTop))
            drawRect(tint, topLeft = Offset(0f, areaTop), size = Size(size.width, heightPx))
            if (gestureStrip > 0f) {
                drawRect(strip, topLeft = Offset(0f, size.height - gestureStrip), size = Size(size.width, gestureStrip))
            }
            drawRect(primary, topLeft = Offset(0f, areaTop - lineWidth / 2), size = Size(size.width, lineWidth))
        }

        // The handle on the line, also the accessible control
        val label = stringResource(R.string.swipe_up_area_height)
        val taller = stringResource(R.string.swipe_up_area_taller)
        val shorter = stringResource(R.string.swipe_up_area_shorter)
        val step = SwipeUpArea.STEP_DP * density.density
        var handleHeight by remember { mutableFloatStateOf(0f) }
        Surface(
            shape = RoundedCornerShape(50),
            color = primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (areaTop - handleHeight / 2).roundToInt()) }
                .onSizeChanged { handleHeight = it.height.toFloat() }
                .semantics {
                    contentDescription = label
                    stateDescription = "$percent%"
                    progressBarRangeInfo = ProgressBarRangeInfo(heightPx, minPx..maxPx)
                    setProgress { target -> setHeight(target); true }
                    customActions = listOf(
                        CustomAccessibilityAction(taller) { setHeight(heightPx + step); true },
                        CustomAccessibilityAction(shorter) { setHeight(heightPx - step); true }
                    )
                }
        ) {
            Text(
                text = "↕ $percent%",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // What the tinted area does, centred in it
        Text(
            text = stringResource(R.string.swipe_up_area_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp),
            color = primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (areaTop + heightPx / 2).roundToInt()) }
                .padding(horizontal = 30.dp)
        )

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalButton(onClick = {
                    heightPx = defaultPx()
                    useDefault = true
                }) { Text(stringResource(R.string.reset), style = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp)) }
                Button(
                    onClick = { onDone(if (useDefault) null else heightDp) },
                    colors = ButtonDefaults.buttonColors()
                ) { Text(stringResource(R.string.done), style = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp)) }
            }
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.swipe_up_area_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.swipe_up_area_note),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
