package com.trikset.gamepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.trikset.gamepad.diagnostics.AppLog
import com.trikset.gamepad.diagnostics.CrashLogStore
import com.trikset.gamepad.diagnostics.DiagLevel
import com.trikset.gamepad.diagnostics.DiagnosticsReport
import com.trikset.gamepad.diagnostics.ReportSharer
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
    const val SK_HIDE_CONTROLS = "hideControls"
    const val SK_SHOW_FPS = "showFps"
    const val SK_GAMEPAD_SWAP = "gamepadSwap"
    const val SK_ADVANCED = "advancedSettings"
    const val SK_MAGIC_BUTTON_COUNT = "magicButtonCount"
    const val SK_SAVE_PRESET = "saveRobotPreset"
    const val SK_DELETE_PRESET = "deleteRobotPreset"
    const val SK_ROBOT_PRESETS = "robotPresets"
    const val SK_DIAG_LEVEL = "diagLevel"
    const val SK_SHARE_WITHOUT_EDITING = "shareWithoutEditing"
    const val SK_REPORT_ISSUE = "reportIssue"
    const val SK_COPY_REPORT = "copyReport"
    const val SK_VIEW_LOG = "viewLog"
    const val MAX_MAGIC_BUTTONS = 5
    private const val DEFAULT_HOST_ADDRESS = "192.168.77.1"
    private const val DEFAULT_HOST_PORT = "4444"
    private const val LOG_DIALOG_MAX_LINES = 200

    fun magicSymbolKey(buttonNumber: Int): String = "magicSymbol$buttonNumber"
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
    // Copying the full diagnostic report to the clipboard on click (the summary
    // above stays the short hardware spec preview).
    aboutSystem.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val clipboard = myActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText(getString(R.string.about_system), buildDiagnosticsReport())
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

  /**
   * "Report an issue": opens the report file in a text editor for review (or a share sheet when
   * "Share without editing" is set / no editor exists).
   */
  private fun initializeReportIssueField() {
    val myActivity = activity ?: return
    val report = findPreference<Preference>(SK_REPORT_ISSUE) ?: return
    report.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      ReportSharer.share(myActivity, buildDiagnosticsReport())
      true
    }
  }

  /** "Copy report": clipboard copy of the full report text. */
  private fun initializeCopyReportField() {
    val myActivity = activity ?: return
    val copy = findPreference<Preference>(SK_COPY_REPORT) ?: return
    copy.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val clipboard = myActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(
          ClipData.newPlainText(getString(R.string.copy_report), buildDiagnosticsReport())
      )
      Toast.makeText(
              myActivity.applicationContext,
              getString(R.string.copied_to_clipboard),
              Toast.LENGTH_SHORT,
          )
          .show()
      true
    }
  }

  /** "View log": in-app read-only dialog with the recent AppLog tail for self-diagnosis. */
  private fun initializeViewLogField() {
    val myActivity = activity ?: return
    val viewLog = findPreference<Preference>(SK_VIEW_LOG) ?: return
    viewLog.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val lines = AppLog.tail(LOG_DIALOG_MAX_LINES)
      val message =
          if (lines.isEmpty()) getString(R.string.view_log_empty) else lines.joinToString("\n")
      androidx.appcompat.app.AlertDialog.Builder(myActivity)
          .setTitle(R.string.view_log_title)
          .setMessage(message)
          .setPositiveButton(R.string.dismiss, null)
          .show()
      true
    }
  }

  private fun buildDiagnosticsReport(): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
    val crash = CrashLogStore(requireContext()).latest()
    return DiagnosticsReport.build(
        requireContext(),
        prefs,
        null,
        AppLog.tail(AppLog.BUFFER_CAPACITY),
        crash?.stackTrace,
    )
  }

  private fun initializeDynamicPreferenceSummary() {
    val listener = Preference.OnPreferenceChangeListener { preference, value ->
      preference.summary = value.toString()
      true
    }

    // Root screen: host/port/keepalive all resolve; the Advanced sub-screen only contains
    // keepalive, so missing prefs are skipped (findPreference is null-safe).
    for (preferenceKey in arrayOf(SK_HOST_ADDRESS, SK_HOST_PORT, SK_KEEPALIVE)) {
      val preference = findPreference<Preference>(preferenceKey) ?: continue
      preference.summary = requireNotNull(preference.sharedPreferences).getString(preferenceKey, "")
      preference.onPreferenceChangeListener = listener
    }
  }

  /**
   * Wires the Robot-presets category: "Save current robot as preset" stores the current host/port/
   * video URI under a name; "Delete a preset" lists the saved names in a dialog; dynamic rows (one
   * per saved preset) apply the preset on tap. Dynamic rows are rebuilt on save/delete.
   */
  private fun initializeRobotPresets() {
    val category = findPreference<PreferenceCategory>(SK_ROBOT_PRESETS) ?: return
    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
    val store = RobotPresetStore(prefs)

    val save = findPreference<EditTextPreference>(SK_SAVE_PRESET)
    save?.setOnPreferenceChangeListener { _, newValue ->
      val name = newValue?.toString()?.trim().orEmpty()
      if (name.isEmpty()) {
        Toast.makeText(requireContext(), R.string.preset_name_required, Toast.LENGTH_SHORT).show()
        false
      } else {
        val host = prefs.getString(SK_HOST_ADDRESS, DEFAULT_HOST_ADDRESS) ?: DEFAULT_HOST_ADDRESS
        val port = prefs.getString(SK_HOST_PORT, DEFAULT_HOST_PORT) ?: DEFAULT_HOST_PORT
        val videoUri = prefs.getString(SK_VIDEO_URI, "") ?: ""
        store.save(name, host, port, videoUri)
        refreshPresetRows(category, store)
        Toast.makeText(
                requireContext(),
                getString(R.string.preset_saved, name),
                Toast.LENGTH_SHORT,
            )
            .show()
        true
      }
    }

    val delete = findPreference<Preference>(SK_DELETE_PRESET)
    delete?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val myActivity = activity ?: return@OnPreferenceClickListener true
      val names = store.all().values.map { it.name }.sorted()
      if (names.isEmpty()) {
        Toast.makeText(requireContext(), R.string.no_presets_saved, Toast.LENGTH_SHORT).show()
      } else {
        androidx.appcompat.app.AlertDialog.Builder(myActivity)
            .setTitle(R.string.delete_preset_dialog_title)
            .setItems(names.toTypedArray()) { _, which ->
              store.delete(names[which])
              refreshPresetRows(category, store)
              Toast.makeText(
                      requireContext(),
                      getString(R.string.preset_deleted, names[which]),
                      Toast.LENGTH_SHORT,
                  )
                  .show()
            }
            .show()
      }
      true
    }

    refreshPresetRows(category, store)
  }

  /** Rebuilds the dynamic "apply preset" rows; the static Save/Delete prefs stay in place. */
  private fun refreshPresetRows(category: PreferenceCategory, store: RobotPresetStore) {
    for (i in category.preferenceCount - 1 downTo 0) {
      val pref = category.getPreference(i)
      if (pref.key != SK_SAVE_PRESET && pref.key != SK_DELETE_PRESET) {
        category.removePreference(pref)
      }
    }
    val myActivity = activity ?: return
    for (preset in store.all().values.sortedBy { it.name }) {
      val row = Preference(myActivity)
      row.title = preset.name
      row.summary = getString(R.string.preset_row_summary, preset.host, preset.port)
      row.isPersistent = false
      row.onPreferenceClickListener = Preference.OnPreferenceClickListener {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit {
          putString(SK_HOST_ADDRESS, preset.host)
          putString(SK_HOST_PORT, preset.port)
          putString(SK_VIDEO_URI, preset.videoUri)
        }
        initializeDynamicPreferenceSummary()
        Toast.makeText(
                requireContext(),
                getString(R.string.preset_applied, preset.name),
                Toast.LENGTH_SHORT,
            )
            .show()
        true
      }
      category.addPreference(row)
    }
  }

  /**
   * Applies the "Diagnostics verbosity" setting to [AppLog.minBufferLevel] when the fragment is
   * created and whenever it changes. Applied here (the settings owner) rather than in
   * MainActivity's controller so it works even when the gamepad activity was never opened; [App]
   * re-applies it at process start.
   */
  private fun initializeDiagnosticsLevelField() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
    AppLog.minBufferLevel = DiagLevel.toBufferLevel(prefs.getString(SK_DIAG_LEVEL, null))
    val preference = findPreference<ListPreference>(SK_DIAG_LEVEL) ?: return
    preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
      AppLog.minBufferLevel = DiagLevel.toBufferLevel(newValue as? String)
      true
    }
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.pref_general, rootKey)

    initializeAboutSystemField()
    initializeDynamicPreferenceSummary()
    initializeResetVideoUriField()
    initializeCopyRobotIpField()
    initializeRobotPresets()
    initializeDiagnosticsLevelField()
    initializeReportIssueField()
    initializeCopyReportField()
    initializeViewLogField()
  }
}
