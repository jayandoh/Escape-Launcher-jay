package com.geecee.escapelauncher.feature.appslist

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The swipe up search's field. It looks like the expanded AnimatedPillSearchBar, but types into an EditText.
 *
 * Compose's text fields can't connect to the keyboard without showing it: the value-based one shows it
 * whenever it gains focus, and the state-based one doesn't connect at all until tapped when told not to.
 * A keyboard shown that way races the swipe for the keyboard, and depending on how fast the keyboard app is,
 * the search either waits up to 2 s for it or sits above a keyboard that then changes height.
 * An EditText connects on focus and, with showSoftInputOnFocus off, leaves raising the keyboard to the swipe.
 */
@Composable
internal fun SearchOverlayField(
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    onSearchDone: (SoftwareKeyboardController?) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentSearchText by rememberUpdatedState(searchText)
    val currentOnSearchTextChanged by rememberUpdatedState(onSearchTextChanged)
    val currentOnSearchDone by rememberUpdatedState(onSearchDone)
    val currentOnFocused by rememberUpdatedState(onFocused)

    val textStyle = MaterialTheme.typography.bodyMedium
    val textColor = MaterialTheme.colorScheme.onSecondary.toArgb()
    val typeface = LocalFontFamilyResolver.current
        .resolve(textStyle.fontFamily, textStyle.fontWeight ?: FontWeight.Normal)
        .value as? Typeface
    val cursorWidth = with(LocalDensity.current) { 2.dp.roundToPx() }
    var field by remember { mutableStateOf<EditText?>(null) }

    // Focus it once it's on screen. Where the keyboard can follow the finger, the swipe raises it;
    // elsewhere it opens the usual way, as the pill search does.
    LaunchedEffect(field) {
        val editText = field ?: return@LaunchedEffect
        if (SearchKeyboardDrag.isSupported) withFrameNanos {} else delay(150.milliseconds)
        editText.requestFocus()
        if (!SearchKeyboardDrag.isSupported) editText.showKeyboard()
        currentOnFocused()
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .width(280.dp)
            .height(56.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(24.dp)
            )
            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        background = null
                        setPadding(0, 0, 0, 0)
                        includeFontPadding = false
                        isSingleLine = true
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
                        showSoftInputOnFocus = !SearchKeyboardDrag.isSupported
                        setText(currentSearchText)
                        setSelection(text.length)
                        doAfterTextChanged { text ->
                            val value = text.toString()
                            if (value != currentSearchText) currentOnSearchTextChanged(value)
                        }
                        setOnEditorActionListener { _, actionId, event ->
                            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                                currentOnSearchDone(keyboardController)
                                true
                            } else {
                                false
                            }
                        }
                        // With showSoftInputOnFocus off a tap doesn't bring the keyboard back by itself
                        setOnClickListener { showKeyboard() }
                        field = this
                    }
                },
                update = { editText ->
                    editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textStyle.fontSize.value)
                    editText.letterSpacing = textStyle.letterSpacing.value / textStyle.fontSize.value
                    editText.setTextColor(textColor)
                    if (typeface != null) editText.typeface = typeface
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        editText.textCursorDrawable = GradientDrawable().apply {
                            setColor(textColor)
                            setSize(cursorWidth, 0)
                        }
                    }
                    if (editText.text.toString() != searchText) {
                        editText.setText(searchText)
                        editText.setSelection(searchText.length)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 16.dp)
            )
        }
    }
}

private fun EditText.showKeyboard() {
    context.getSystemService(InputMethodManager::class.java).showSoftInput(this, 0)
}
