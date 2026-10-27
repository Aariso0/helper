package com.example.helper.scaffold

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Long-press that works on a Material chip (app-shell's shared UI seam).
 *
 * A `FilterChip` handles its own taps internally, so a `combinedClickable` on
 * a `Box` wrapped *around* the chip never sees the press: the chip consumes
 * the pointer first. Long-press therefore has to sit on the chip's **own**
 * modifier and read the events before the chip does — the `Initial` pass goes
 * outer-to-inner, so this modifier sees the down before `FilterChip`'s
 * clickable.
 *
 * Taps are deliberately left alone: this only claims the gesture once it has
 * outlived `longPressTimeoutMillis` inside the touch slop. A short press, or
 * a drag, is never consumed, so the chip's click and any parent scroll behave
 * exactly as before — while a long press swallows the release so the chip
 * does not also fire its click.
 */
fun Modifier.onLongPress(onLongClick: () -> Unit): Modifier = composed {
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val view = LocalView.current
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!awaitLongPressOrRelease()) continue
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                currentOnLongClick()
                // The finger is still down. Consume the rest of this gesture
                // (release included) so the chip cannot read it as a tap.
                var event = awaitPointerEvent(PointerEventPass.Initial)
                while (event.changes.any { it.pressed }) {
                    event.changes.forEach { it.consume() }
                    event = awaitPointerEvent(PointerEventPass.Initial)
                }
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * Waits out the long-press timeout. Returns true when the press is still
 * within the touch slop after that time (a long press); false when the finger
 * lifted or moved first — in which case the gesture belongs to the chip.
 */
private suspend fun AwaitPointerEventScope.awaitLongPressOrRelease(): Boolean {
    val timeout = viewConfiguration.longPressTimeoutMillis
    val slop = viewConfiguration.touchSlop
    return withTimeoutOrNull(timeout) {
        var stillPressed = true
        while (stillPressed) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val moved = event.changes.any { it.positionChange().getDistance() > slop }
            stillPressed = !moved && event.changes.any { it.pressed }
        }
        false
    } ?: true
}
