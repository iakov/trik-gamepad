package com.trikset.gamepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {

  companion object {
    const val SK_HOST_ADDRESS = "hostAddress"
    const val SK_HOST_PORT = "hostPort"
    const val SK_SHOW_PADS = "showPads"
    const val SK_VIDEO_URI = "videoURI"
    const val SK_WHEEL_STEP = "wheelSens"
    const val SK_ABOUT_SYSTEM = "aboutSystem"
    const val SK_KEEPALIVE = "keepaliveTimeout"
    const val SK_RESET_VIDEO_URI = "resetVideoURI"
    const val SK_COPY_ROBOT_IP = "copyRobotIp"
    const val SK_WHEEL_ENABLED = "wheelEnabled"
    const val SK_KEEP_SCREEN_ON = "keepScreenOn"
    private const val DEFAULT_HOST_ADDRESS = "192.168.77.1"
  }

  /** One-tap copy of the configured robot IP (debugging convenience). */
  private fun initializeCopyRobotIpField() {
    val myActivity = activity ?: return
    val copy = findPreference<Preference>(SK_COPY_ROBOT_IP) ?: return
    copy.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val prefs = copy.sharedPreferences
      val host = prefs?.getString(SK_HOST_ADDRESS, DEFAULT_HOST_ADDRESS) ?: DEFAULT_HOST_ADDRESS
      val clipboard = myActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText(SK_HOST_ADDRESS, host))
      Toast.makeText(
              myActivity.applicationContext,
              getString(R.string.copied_to_clipboard),
              Toast.LENGTH_SHORT,
          )
          .show()
      true
    }
  }

  /** Fills the video URI from the configured robot host; explicit, never implicit. */
  private fun initializeResetVideoUriField() {
    val myActivity = activity ?: return
    val reset = findPreference<Preference>(SK_RESET_VIDEO_URI) ?: return
    reset.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val prefs = reset.sharedPreferences
      val host = prefs?.getString(SK_HOST_ADDRESS, DEFAULT_HOST_ADDRESS) ?: DEFAULT_HOST_ADDRESS
      val uri = "http://$host:8080/?action=stream"
      prefs?.edit { putString(SK_VIDEO_URI, uri) }
      reset.summary = uri
      Toast.makeText(
              myActivity.applicationContext,
              getString(R.string.video_uri_reset),
              Toast.LENGTH_SHORT,
          )
          .show()
      true
    }
  }

  private fun initializeAboutSystemField() {
    val myActivity = activity ?: return
    val displayMetrics = DisplayMetrics()
    myActivity.windowManager.defaultDisplay.getMetrics(displayMetrics)
    val systemInfo =
        String.format(
            Locale.ENGLISH,
            "Version:%s; Android %s; SDK %d; Resolution %dx%d; PPI %dx%d",
            BuildConfig.VERSION_NAME,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            displayMetrics.heightPixels,
            displayMetrics.widthPixels,
            displayMetrics.ydpi.toInt(),
            displayMetrics.xdpi.toInt(),
        )
    val aboutSystem = requireNotNull(findPreference<Preference>(SK_ABOUT_SYSTEM))
    aboutSystem.summary = getString(R.string.tap_to_copy) + ":" + systemInfo
    // Copying system info to the clipboard on click
    aboutSystem.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val clipboard = myActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText(getString(R.string.about_system), systemInfo)
      clipboard.setPrimaryClip(clip)

      Toast.makeText(
              myActivity.applicationContext,
              getString(R.string.copied_to_clipboard),
              Toast.LENGTH_SHORT,
          )
          .show()

      true
    }
  }

  private fun initializeDynamicPreferenceSummary() {
    val listener = Preference.OnPreferenceChangeListener { preference, value ->
      preference.summary = value.toString()
      true
    }

    for (preferenceKey in arrayOf(SK_HOST_ADDRESS, SK_HOST_PORT, SK_KEEPALIVE)) {
      val preference = requireNotNull(findPreference<Preference>(preferenceKey))
      preference.summary = requireNotNull(preference.sharedPreferences).getString(preferenceKey, "")
      preference.onPreferenceChangeListener = listener
    }
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.pref_general, rootKey)

    initializeAboutSystemField()
    initializeDynamicPreferenceSummary()
    initializeResetVideoUriField()
    initializeCopyRobotIpField()
  }
}
