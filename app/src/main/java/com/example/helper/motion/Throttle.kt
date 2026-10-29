package com.example.helper.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun rememberThrottledClick(minIntervalMs: Long = 300L): (() -> Unit) -> () -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    return { onClick: () -> Unit ->
        {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= minIntervalMs) {
                lastClickTime = currentTime
                onClick()
            }
        }
    }
}
