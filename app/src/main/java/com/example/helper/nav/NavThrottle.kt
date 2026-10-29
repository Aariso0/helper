package com.example.helper.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun rememberNavThrottle(minIntervalMs: Long = 300L): (()->Unit)->() -> Unit {
    var lastTime by remember { mutableLongStateOf(0L) }
    return { action: () -> Unit ->
        {
            val now = System.currentTimeMillis()
            if (now - lastTime >= minIntervalMs) {
                lastTime = now
                action()
            }
        }
    }
}
