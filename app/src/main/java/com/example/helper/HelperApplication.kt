package com.example.helper

import android.app.Application
import com.example.helper.coredata.AppContainer

// Hosts the manual-DI container (core-data card). Feature ViewModels read
// repositories from here, never from Room directly.
class HelperApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
