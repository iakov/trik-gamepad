package com.trikset.gamepad2

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.net.URI
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
    fun setTargetChip(host: String)

    fun toast(text: String)

    fun animatePadsAlpha(alpha: Float, previousAlpha: Float)

    fun setVideoUrl(url: String?)

    var wheelStep: Int

    var wheelEnabled: Boolean

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

  fun onPreferenceChanged(sharedPreferences: SharedPreferences) {
    val addr =
        sharedPreferences.getString(SettingsFragment.SK_HOST_ADDRESS, DEFAULT_HOST_ADDRESS)
            ?: DEFAULT_HOST_ADDRESS
    var portNumber = DEFAULT_PORT
    val portStr =
        sharedPreferences.getString(SettingsFragment.SK_HOST_PORT, DEFAULT_HOST_PORT)
            ?: DEFAULT_HOST_PORT
    try {
      portNumber = portStr.toInt()
    } catch (e: NumberFormatException) {
      ui.toast(context.getString(R.string.port_number_incorrect, portStr))
    }
    sender.setTarget(addr, portNumber)

    // Command transport: the robot screen's Network category selects TCP or UDP (global setting;
    // per-preset transport is deferred until the robot supports UDP). A change disconnects and the
    // next command reconnects over the new transport.
    val transportKey =
        sharedPreferences.getString(SettingsFragment.SK_TRANSPORT, TRANSPORT_DEFAULT)
            ?: TRANSPORT_DEFAULT
    sender.transportMode =
        if (transportKey.equals(TRANSPORT_UDP, ignoreCase = true)) {
          TransportMode.UDP
        } else {
          TransportMode.TCP
        }

    val defAlpha = PADS_ALPHA_DEFAULT
    // SeekBarPreference stores Int; legacy String values are still honored.
    val padsAlpha =
        SettingsFragment.readSeekBarValue(
            sharedPreferences,
            SettingsFragment.SK_SHOW_PADS,
            defAlpha,
        )
    val alpha = Math.max(0, Math.min(ALPHA_MAX, padsAlpha)) / ALPHA_MAX.toFloat()
    ui.animatePadsAlpha(alpha, prevAlpha)
    prevAlpha = alpha

    // Empty host = video-only device: the URI default cannot be derived from the host, and the
    // malformed "http://:8080/..." would toast "Illegal video stream URL" on every register.
    // An empty effective URI -> setVideoUrl(null) -> the placeholder prompts the user to configure
    // one. The shared helper mirrors the settings row exactly (both show/use the effective value,
    // so an unset field never reads as "no stream" while the app streams the host-derived default).
    val videoStreamURI = SettingsFragment.effectiveVideoUri(sharedPreferences)

    // The top-left chip shows the robot target: the host when set; else the video stream's host
    // when a stream is configured (video-only mode); else a filler so the chip stays readable
    // instead of empty (DESIGN.md "Defaults are as useful as possible").
    val videoHost = runCatching { URI(videoStreamURI).host }.getOrNull()
    ui.setTargetChip(addr.ifBlank { videoHost?.takeIf { it.isNotBlank() } ?: TARGET_CHIP_EMPTY })

    // The URI flows as an opaque string: validation happens per video player at open time (MJPEG
    // parses the http/https URL, MediaPlayer accepts rtsp:// directly), so an rtsp:// stream never
    // trips URL-only parsing. An empty effective URI = video disabled (DESIGN.md "Empty-value
    // semantics").
    ui.setVideoUrl(videoStreamURI.ifEmpty { null })

    val wheelStep =
        SettingsFragment.readSeekBarValue(
            sharedPreferences,
            SettingsFragment.SK_WHEEL_STEP,
            ui.wheelStep,
        )
    ui.wheelStep = Math.max(WHEEL_STEP_MIN, Math.min(WHEEL_STEP_MAX, wheelStep))

    val wheelEnabled = sharedPreferences.getBoolean(SettingsFragment.SK_WHEEL_ENABLED, false)
    ui.wheelEnabled = wheelEnabled

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
              )
              ?.toInt() ?: SenderService.DEFAULT_KEEPALIVE
      if (timeout < SenderService.MINIMAL_KEEPALIVE) {
        ui.toast(
            String.format(
                Locale.ROOT,
                context.getString(R.string.keepalive_must_be_not_less),
                SenderService.MINIMAL_KEEPALIVE,
            )
        )
        sharedPreferences.edit {
          putString(SettingsFragment.SK_KEEPALIVE, sender.keepaliveTimeout.toString())
        }
      } else {
        sender.keepaliveTimeout = timeout
      }
    } catch (e: NumberFormatException) {
      ui.toast(context.getString(R.string.keepalive_must_be_positive_decimal))
      sharedPreferences.edit {
        putString(SettingsFragment.SK_KEEPALIVE, sender.keepaliveTimeout.toString())
      }
    }

    val customMessage = sharedPreferences.getString(SettingsFragment.SK_CUSTOM_MESSAGE, null)
    if (customMessage != null) {
      sender.send("custom $customMessage")
    }
  }

  internal companion object {
    const val TAG = "SettingsController"
    const val DEFAULT_HOST_ADDRESS = "192.168.77.1"
    const val DEFAULT_HOST_PORT = "4444"
    const val DEFAULT_PORT = 4444
    const val TRANSPORT_DEFAULT = "tcp"
    const val TRANSPORT_UDP = "udp"
    const val PADS_ALPHA_DEFAULT = 100
    const val ALPHA_MAX = 255
    const val WHEEL_STEP_MIN = 1
    const val WHEEL_STEP_MAX = 100
    const val DEFAULT_MAGIC_BUTTON_COUNT = 3
    // Shown in the top-left IP chip when neither a host nor a video stream is configured
    // (the chip stays readable instead of empty).
    const val TARGET_CHIP_EMPTY = "---.---.---.---"

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
