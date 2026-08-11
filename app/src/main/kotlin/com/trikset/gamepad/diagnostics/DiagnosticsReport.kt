package com.trikset.gamepad.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.trikset.gamepad.BuildConfig
import com.trikset.gamepad.ConnectionState
import com.trikset.gamepad.RobotPresetStore
import com.trikset.gamepad.SettingsFragment
import java.util.Locale

/**
 * Builds the one-file diagnostic "data block" users share with developers: app/device/display
 * identity, the live connection state, the full settings snapshot (non-defaults marked) and the
 * recent [AppLog] tail, optionally followed by a crash stack trace. Pure formatting — inputs are
 * passed in, so every section is unit-testable under Robolectric.
 */
object DiagnosticsReport {

  fun build(
      context: Context,
      prefs: SharedPreferences,
      connectionState: ConnectionState?,
      logTail: List<String>,
      crashTrace: String?,
  ): String {
    val resources = context.resources
    val metrics = resources.displayMetrics
    val configuration = resources.configuration
    val out = StringBuilder()
    out.appendLine("# TRIK Gamepad diagnostic report")
    out.appendLine()
    out.appendLine("## App")
    out.appendLine("- Version: ${BuildConfig.VERSION_NAME}")
    out.appendLine("- Version code: ${BuildConfig.VERSION_CODE}")
    out.appendLine("- Build type: ${BuildConfig.BUILD_TYPE}")
    out.appendLine()
    out.appendLine("## Device")
    out.appendLine("- Manufacturer: ${Build.MANUFACTURER}")
    out.appendLine("- Model: ${Build.MODEL}")
    out.appendLine("- Product: ${Build.PRODUCT}")
    out.appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    out.appendLine()
    out.appendLine("## Display")
    out.appendLine("- Resolution: ${metrics.widthPixels}x${metrics.heightPixels} px")
    out.appendLine("- Density: ${metrics.densityDpi} dpi; font scale ${configuration.fontScale}")
    out.appendLine("- Locale: ${Locale.getDefault()}")
    out.appendLine()
    out.appendLine("## Connection")
    out.appendLine("- ${connectionLine(connectionState)}")
    out.appendLine()
    out.appendLine("## Settings")
    appendSettings(out, prefs)
    out.appendLine()
    out.appendLine("## Recent log")
    out.appendLine("```text")
    if (logTail.isEmpty()) {
      out.appendLine("(no log entries)")
    } else {
      for (line in logTail) {
        out.appendLine(line)
      }
    }
    out.appendLine("```")
    if (crashTrace != null) {
      out.appendLine()
      out.appendLine("## Last crash")
      out.appendLine("```text")
      out.appendLine(crashTrace.trim())
      out.appendLine("```")
    }
    return out.toString()
  }

  private fun connectionLine(state: ConnectionState?): String =
      when (state) {
        null -> "State: not running (open the gamepad to capture the live state)"
        is ConnectionState.Connected -> "State: Connected"
        is ConnectionState.Connecting -> "State: Connecting"
        is ConnectionState.Disconnected -> "State: Disconnected (${state.reason})"
      }

  private fun appendSettings(out: StringBuilder, prefs: SharedPreferences) {
    out.appendLine(
        settingLine(prefs, "Robot IP address", SettingsFragment.SK_HOST_ADDRESS, "192.168.77.1")
    )
    out.appendLine(settingLine(prefs, "Robot TCP port", SettingsFragment.SK_HOST_PORT, "4444"))
    out.appendLine(settingLine(prefs, "Video stream URI", SettingsFragment.SK_VIDEO_URI, ""))
    out.appendLine(
        settingLine(prefs, "Keep-alive timeout, ms", SettingsFragment.SK_KEEPALIVE, "5000")
    )
    out.appendLine(settingLine(prefs, "Keep screen on", SettingsFragment.SK_KEEP_SCREEN_ON, "true"))
    out.appendLine(settingLine(prefs, "Arrow transparency", SettingsFragment.SK_SHOW_PADS, "100"))
    out.appendLine(
        settingLine(prefs, "Hide pads & buttons", SettingsFragment.SK_HIDE_CONTROLS, "false")
    )
    out.appendLine(settingLine(prefs, "Show FPS", SettingsFragment.SK_SHOW_FPS, "false"))
    out.appendLine(settingLine(prefs, "Wheel enabled", SettingsFragment.SK_WHEEL_ENABLED, "false"))
    out.appendLine(settingLine(prefs, "Wheel sensitivity", SettingsFragment.SK_WHEEL_STEP, "7"))
    out.appendLine(settingLine(prefs, "Swap sticks", SettingsFragment.SK_GAMEPAD_SWAP, "false"))
    out.appendLine(settingLine(prefs, "Magic buttons", SettingsFragment.SK_MAGIC_BUTTON_COUNT, "3"))
    for (n in 1..SettingsFragment.MAX_MAGIC_BUTTONS) {
      val stored = prefs.all[SettingsFragment.magicSymbolKey(n)]?.toString()
      if (stored != null) {
        out.appendLine("- Button $n symbol: $stored")
      }
    }
    out.appendLine(
        settingLine(prefs, "Diagnostics verbosity", SettingsFragment.SK_DIAG_LEVEL, "info")
    )
    out.appendLine(
        settingLine(
            prefs,
            "Share without editing",
            SettingsFragment.SK_SHARE_WITHOUT_EDITING,
            "false",
        )
    )
    val presets = RobotPresetStore(prefs).all().values.map { it.name }.sorted()
    out.appendLine(
        "- Robot presets: " + if (presets.isEmpty()) "none" else presets.joinToString(", ")
    )
  }

  private fun settingLine(
      prefs: SharedPreferences,
      label: String,
      key: String,
      default: String,
  ): String {
    val stored = prefs.all[key]?.toString()
    val value = stored ?: default
    val marker = if (stored == null || stored == default) " (default)" else ""
    return "- $label: $value$marker"
  }
}
