package com.trikset.gamepad

import android.app.Application
import androidx.preference.PreferenceManager
import com.trikset.gamepad.diagnostics.AppLog
import com.trikset.gamepad.diagnostics.CrashHandler
import com.trikset.gamepad.diagnostics.CrashLogStore
import com.trikset.gamepad.diagnostics.DiagLevel

/**
 * App-wide initialisation: applies the persisted diagnostics-verbosity setting to [AppLog] before
 * anything logs, and installs the chaining uncaught-exception handler so a crash is captured for
 * the next-launch report dialog. Kept minimal and dependency-free so it is safe under Robolectric.
 */
class App : Application() {

  override fun onCreate() {
    super.onCreate()
    AppLog.minBufferLevel =
        DiagLevel.toBufferLevel(
            PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SettingsFragment.SK_DIAG_LEVEL, null)
        )
    Thread.setDefaultUncaughtExceptionHandler(
        CrashHandler(CrashLogStore(this), Thread.getDefaultUncaughtExceptionHandler())
    )
  }
}
