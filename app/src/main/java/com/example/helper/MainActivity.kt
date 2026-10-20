package com.example.helper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.helper.scaffold.AppScaffold
import com.example.helper.theme.HelperTheme

// Single activity; app-shell owns the whole composition root (theme →
// scaffold → nav). See modules/app-shell.md.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelperTheme {
                AppScaffold()
            }
        }
    }
}
