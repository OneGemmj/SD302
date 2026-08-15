package com.seedream.app

import android.app.Application
import com.seedream.app.logging.AppCrashHandler
import com.seedream.app.logging.FileLogger
import com.seedream.app.logging.LogEventBus
import com.seedream.app.storage.SettingsStorage
import java.io.File

class SeedreamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStorage(this).let { settings ->
            if (settings.getSetting("loggingEnabled", "false") == "true") {
                enableLogging()
            }
        }
    }

    /**
     * Attaches the file logger, wires the crash handler and tails logcat.
     * Called at startup when the switch is on, and from the ViewModel when the
     * user flips the switch while the app is running.
     */
    fun enableLogging() {
        val logDir = File(filesDir, "logs")
        LogEventBus.attach(
            FileLogger(file = { File(logDir, "app.log") })
        )
        AppCrashHandler.install()
        AppCrashHandler.startLogcatReader()
    }

    fun disableLogging() {
        LogEventBus.detach()
    }
}
