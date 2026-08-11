package com.trikset.gamepad

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.Locale

/**
 * Owns [MainActivity]'s preference-change handling: retargets the [sender] on host/port changes,
 * parses the video URL, animates pad opacity, clamps the wheel step and validates the keepalive
 * timeout. Extracted from MainActivity's inline listener so the logic is directly testable without
 * reflection.
 */
class MainActivitySettingsController(
    context: Context,
    private val sender: SenderService,
    private val ui: SettingsUi,
) {
  /**
   * UI-side callbacks implemented by [MainActivity] (views, title, toast, video URL, wheel step).
   */
  interface SettingsUi {
    fun setActionBarTitle(title: String): Boolean

    fun toast(text: String)

    fun animatePadsAlpha(alpha: Float, previousAlpha: Float)

    fun setVideoUrl(url: URL?)

    fun getWheelStep(): Int

    fun setWheelStep(step: Int)

    fun isWheelEnabled(): Boolean

    fun setWheelEnabled(enabled: Boolean)

    fun setKeepScreenOn(enabled: Boolean)

    fun setMagicButtons(count: Int, symbols: List<String>)

    fun setControlsVisible(visible: Boolean)

    fun setShowFps(enabled: Boolean)
  }

  private val context = context.applicationContext
  private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
  private var prevAlpha = 0f
  private var registered = false

  // Private so the listener cannot be registered anywhere else (a leak footgun);
  // register()/unregister() are the only entry points and are idempotent.
  private val listener: SharedPreferences.OnSharedPreferenceChangeListener =
      SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        onPreferenceChanged(prefs)
      }

  fun register() {
    if (registered) {
      return
    }
    registered = true
    onPreferenceChanged(preferences)
    preferences.registerOnSharedPreferenceChangeListener(listener)
  }

  fun unregister() {
    if (!registered) {
      return
    }
    registered = false
    preferences.unregisterOnSharedPreferenceChangeListener(listener)
  }

  /**
   * Reads an int preference; SeekBarPreference stores Int, legacy EditTextPreference stored String.
   */
  private fun readInt(prefs: SharedPreferences, key: String, default: Int): Int =
      when (val value = prefs.all[key]) {
        is Int -> value
        is String -> value.toIntOrNull() ?: default
        else -> default
      }

  fun onPreferenceChanged(sharedPreferences: SharedPreferences) {
    val addr = sharedPreferences.getString(SettingsFragment.SK_HOST_ADDRESS, DEFAULT_HOST_ADDRESS)!!
    var portNumber = DEFAULT_PORT
    val portStr = sharedPreferences.getString(SettingsFragment.SK_HOST_PORT, DEFAULT_HOST_PORT)!!
    try {
      portNumber = portStr.toInt()
    } catch (e: NumberFormatException) {
      ui.toast("Port number '$portStr' is incorrect.")
    }
    sender.setTarget(addr, portNumber)

    if (!ui.setActionBarTitle(addr)) {
      ui.toast("Can not change title, not a problem")
    }

    val defAlpha = PADS_ALPHA_DEFAULT
    // SeekBarPreference stores Int; legacy String values are still honored.
    val padsAlpha = readInt(sharedPreferences, SettingsFragment.SK_SHOW_PADS, defAlpha)
    val alpha = Math.max(0, Math.min(ALPHA_MAX, padsAlpha)) / ALPHA_MAX.toFloat()
    ui.animatePadsAlpha(alpha, prevAlpha)
    prevAlpha = alpha

    // Empty host = video-only device: the URI default cannot be derived from the host, and the
    // malformed "http://:8080/..." would toast "Illegal video stream URL" on every register.
    // An empty default -> setVideoUrl(null) -> the placeholder prompts the user to configure one.
    val defaultVideoUri = if (addr.isBlank()) "" else "http://$addr:8080/?action=stream"
    val videoStreamURI =
        sharedPreferences.getString(SettingsFragment.SK_VIDEO_URI, defaultVideoUri)!!
    try {
      ui.setVideoUrl(if (videoStreamURI.isEmpty()) null else URI(videoStreamURI).toURL())
    } catch (e: URISyntaxException) {
      ui.toast("Illegal video stream URL")
      Log.e(TAG, "onPreferenceChanged: ", e)
      ui.setVideoUrl(null)
    } catch (e: MalformedURLException) {
      ui.toast("Illegal video stream URL")
      Log.e(TAG, "onPreferenceChanged: ", e)
      ui.setVideoUrl(null)
    }

    val wheelStep = readInt(sharedPreferences, SettingsFragment.SK_WHEEL_STEP, ui.getWheelStep())
    ui.setWheelStep(Math.max(WHEEL_STEP_MIN, Math.min(WHEEL_STEP_MAX, wheelStep)))

    val wheelEnabled = sharedPreferences.getBoolean(SettingsFragment.SK_WHEEL_ENABLED, false)
    ui.setWheelEnabled(wheelEnabled)

    val keepScreenOn = sharedPreferences.getBoolean(SettingsFragment.SK_KEEP_SCREEN_ON, true)
    ui.setKeepScreenOn(keepScreenOn)

    // Magic buttons: count (0 hides the row; capped at the maximum) + a display glyph per button
    // (defaults ▲ ■ ● ✕ ◆); the protocol command stays numeric `btn N down`.
    val magicCount = readMagicButtonCount(sharedPreferences)
    val symbols =
        (1..SettingsFragment.MAX_MAGIC_BUTTONS).map { n ->
          MagicButtonSymbols.resolve(
              n,
              sharedPreferences.getString(SettingsFragment.magicSymbolKey(n), null),
          )
        }
    ui.setMagicButtons(magicCount, symbols)

    val hideControls = sharedPreferences.getBoolean(SettingsFragment.SK_HIDE_CONTROLS, false)
    // Empty host = video-only device: no pads/buttons to tap regardless of the toggle.
    ui.setControlsVisible(!hideControls && addr.isNotBlank())

    val showFps = sharedPreferences.getBoolean(SettingsFragment.SK_SHOW_FPS, false)
    ui.setShowFps(showFps)

    try {
      val timeout =
          sharedPreferences
              .getString(
                  SettingsFragment.SK_KEEPALIVE,
                  SenderService.DEFAULT_KEEPALIVE.toString(),
              )!!
              .toInt()
      if (timeout < SenderService.MINIMAL_KEEPALIVE) {
        ui.toast(
            String.format(
                Locale.ROOT,
                context.getString(R.string.keepalive_must_be_not_less),
                SenderService.MINIMAL_KEEPALIVE,
            )
        )
        sharedPreferences.edit {
          putString(SettingsFragment.SK_KEEPALIVE, sender.getKeepaliveTimeout().toString())
        }
      } else {
        sender.setKeepaliveTimeout(timeout)
      }
    } catch (e: NumberFormatException) {
      ui.toast(context.getString(R.string.keepalive_must_be_positive_decimal))
      sharedPreferences.edit {
        putString(SettingsFragment.SK_KEEPALIVE, sender.getKeepaliveTimeout().toString())
      }
    }
  }

  internal companion object {
    const val TAG = "SettingsController"
    const val DEFAULT_HOST_ADDRESS = "192.168.77.1"
    const val DEFAULT_HOST_PORT = "4444"
    const val DEFAULT_PORT = 4444
    const val PADS_ALPHA_DEFAULT = 100
    const val ALPHA_MAX = 255
    const val WHEEL_STEP_MIN = 1
    const val WHEEL_STEP_MAX = 100
    const val DEFAULT_MAGIC_BUTTON_COUNT = 3

    /** Reads the configured magic-button count (0..MAX), honoring both Int and String storage. */
    fun readMagicButtonCount(prefs: SharedPreferences): Int {
      val parsed =
          when (val value = prefs.all[SettingsFragment.SK_MAGIC_BUTTON_COUNT]) {
            is Int -> value
            is String -> value.toIntOrNull()
            else -> null
          }
      return (parsed ?: DEFAULT_MAGIC_BUTTON_COUNT).coerceIn(0, SettingsFragment.MAX_MAGIC_BUTTONS)
    }
  }
}
