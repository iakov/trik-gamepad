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
 * rewrites the video URI when the host changes, animates pad opacity, parses the video URL, clamps
 * the wheel step and validates the keepalive timeout. Extracted from MainActivity's inline listener
 * (ROADMAP Phase 2-E) so the logic is directly testable without reflection.
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
  }

  private val context = context.applicationContext
  private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
  private var prevAlpha = 0f

  val listener: SharedPreferences.OnSharedPreferenceChangeListener =
      SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        onPreferenceChanged(prefs)
      }

  fun register() {
    onPreferenceChanged(preferences)
    preferences.registerOnSharedPreferenceChangeListener(listener)
  }

  fun unregister() {
    preferences.unregisterOnSharedPreferenceChangeListener(listener)
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
    val oldAddr = sender.getHostAddr()
    sender.setTarget(addr, portNumber)

    if (!ui.setActionBarTitle(addr)) {
      ui.toast("Can not change title, not a problem")
    }

    if (!addr.equals(oldAddr, ignoreCase = true)) {
      // update video stream URI when target addr changed
      sharedPreferences.edit {
        putString(SettingsFragment.SK_VIDEO_URI, "http://" + addr.trim() + ":8080/?action=stream")
      }
    }

    val defAlpha = PADS_ALPHA_DEFAULT
    var padsAlpha = defAlpha
    try {
      padsAlpha =
          sharedPreferences.getString(SettingsFragment.SK_SHOW_PADS, defAlpha.toString())!!.toInt()
    } catch (nfe: NumberFormatException) {
      // unchanged
    }
    val alpha = Math.max(0, Math.min(ALPHA_MAX, padsAlpha)) / ALPHA_MAX.toFloat()
    ui.animatePadsAlpha(alpha, prevAlpha)
    prevAlpha = alpha

    val videoStreamURI =
        sharedPreferences.getString(
            SettingsFragment.SK_VIDEO_URI,
            "http://$addr:8080/?action=stream",
        )!!
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

    val wheelStep =
        sharedPreferences
            .getString(SettingsFragment.SK_WHEEL_STEP, ui.getWheelStep().toString())
            ?.toIntOrNull() ?: ui.getWheelStep()
    ui.setWheelStep(Math.max(WHEEL_STEP_MIN, Math.min(WHEEL_STEP_MAX, wheelStep)))

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
                Locale.US,
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

  private companion object {
    const val TAG = "SettingsController"
    const val DEFAULT_HOST_ADDRESS = "192.168.77.1"
    const val DEFAULT_HOST_PORT = "4444"
    const val DEFAULT_PORT = 4444
    const val PADS_ALPHA_DEFAULT = 100
    const val ALPHA_MAX = 255
    const val WHEEL_STEP_MIN = 1
    const val WHEEL_STEP_MAX = 100
  }
}
