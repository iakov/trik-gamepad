package com.trikset.gamepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
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
    const val SK_MAGIC_SYMBOLS = "magicSymbols"
    const val SK_SAVE_PRESET = "saveRobotPreset"
    const val SK_DELETE_PRESET = "deleteRobotPreset"
    const val SK_ROBOT_PRESETS = "robotPresets"
    const val SK_DIAG_LEVEL = "diagLevel"
    const val SK_SHARE_WITHOUT_EDITING = "shareWithoutEditing"
    const val SK_REPORT_ISSUE = "reportIssue"
    const val SK_COPY_REPORT = "copyReport"
    const val SK_VIEW_LOG = "viewLog"
    const val SK_OPEN_SOURCE_LICENSES = "openSourceLicenses"
    // Cross-link rows between the app-settings and robot-settings screens.
    const val SK_OPEN_ROBOT_SETTINGS = "openRobotSettings"
    const val SK_OPEN_APP_SETTINGS = "openAppSettings"
    const val MAX_MAGIC_BUTTONS = 5
    /** Preference XML loaded by this fragment: app or robot/target settings. */
    const val ARG_PREFERENCE_XML = "preferenceXml"
    private const val DEFAULT_HOST_ADDRESS = "192.168.77.1"
    private const val DEFAULT_HOST_PORT = "4444"
    private const val LOG_DIALOG_MAX_LINES = 200
    // XML defaults for the seekbar settings (mirror pref_app.xml defaultValue).
    private const val DEFAULT_WHEEL_STEP = 7
    private const val DEFAULT_PADS_ALPHA = 100
    private const val DEFAULT_MAGIC_BUTTON_COUNT = 3

    fun magicSymbolKey(buttonNumber: Int): String = "magicSymbol$buttonNumber"

    fun effectiveVideoUri(prefs: SharedPreferences): String = effectiveVideoUri(prefs, null)

    /**
     * The effective video-stream URI: the stored value, else the URI derived from the configured
     * host (`http://<host>:8080/?action=stream`). Empty = video disabled. Shared by the settings
     * row (shows what will actually be used) and [MainActivitySettingsController] (uses it) so the
     * two can never disagree — an "unset" field must not read as "no stream" while the app streams
     * the derived default. [hostOverride] carries a freshly edited host value that is not yet
     * persisted (the change listener fires before prefs are saved).
     */
    fun effectiveVideoUri(prefs: SharedPreferences, hostOverride: String?): String {
      val host =
          hostOverride
              ?: prefs.getString(SK_HOST_ADDRESS, DEFAULT_HOST_ADDRESS)
              ?: DEFAULT_HOST_ADDRESS
      val defaultUri = if (host.isBlank()) "" else "http://$host:8080/?action=stream"
      return prefs.getString(SK_VIDEO_URI, defaultUri) ?: defaultUri
    }

    /** Builds a fragment for the app settings (pref_app.xml) or robot settings (pref_robot.xml). */
    fun newInstance(preferenceXml: Int): SettingsFragment =
        SettingsFragment().apply {
          arguments = Bundle().apply { putInt(ARG_PREFERENCE_XML, preferenceXml) }
        }

    /**
     * Reads a SeekBarPreference value (Int storage, honoring legacy String values), else [default].
     * Shared with MainActivitySettingsController, which has no fragment instance.
     */
    fun readSeekBarValue(prefs: SharedPreferences, key: String, default: Int): Int =
        when (val value = prefs.all[key]) {
          is Int -> value
          is String -> value.toIntOrNull() ?: default
          else -> default
        }
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
      // The videoURI row shows the current value in its summary; refresh it so the reset is
      // immediately visible (the row itself carries the value, not this action row).
      findPreference<Preference>(SK_VIDEO_URI)?.summary = uri
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
    // Only present in the app-settings screen (not the robot/target screen).
    val aboutSystem = findPreference<Preference>(SK_ABOUT_SYSTEM) ?: return
    // Resources.getDisplayMetrics() is the non-deprecated source of the default
    // display metrics on every supported API level (the
    // windowManager.defaultDisplay.getMetrics() chain is deprecated since API 30).
    val displayMetrics = myActivity.resources.displayMetrics
    val systemInfo =
        String.format(
            Locale.ENGLISH,
            getString(R.string.about_system_info_format),
            BuildConfig.VERSION_NAME,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            displayMetrics.heightPixels,
            displayMetrics.widthPixels,
            displayMetrics.ydpi.toInt(),
            displayMetrics.xdpi.toInt(),
        )
    aboutSystem.summary =
        getString(R.string.about_system_summary, getString(R.string.tap_to_copy), systemInfo)
    // "About system" copies the SHORT device spec (the summary preview). "Copy report" stays the
    // row that copies the full diagnostics report (see DESIGN.md "About system vs Copy report").
    aboutSystem.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val clipboard = myActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.about_system), systemInfo))

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

  /** "Open-source licenses": read-only dialog with the bundled font/license texts (res/raw). */
  private fun initializeOpenSourceLicensesField() {
    val myActivity = activity ?: return
    val licenses = findPreference<Preference>(SK_OPEN_SOURCE_LICENSES) ?: return
    licenses.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      val texts =
          listOf(R.raw.symbol_font_vera_license, R.raw.symbol_font_iec_license).mapNotNull { rawRes
            ->
            resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
          }
      androidx.appcompat.app.AlertDialog.Builder(myActivity)
          .setTitle(R.string.open_source_licenses)
          .setMessage(texts.joinToString("\n\n---\n\n"))
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
    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

    // Root screen: host/port/keepalive all resolve; the Advanced sub-screen only contains
    // keepalive, so missing prefs are skipped (findPreference is null-safe).
    for (preferenceKey in arrayOf(SK_HOST_ADDRESS, SK_HOST_PORT, SK_KEEPALIVE)) {
      val preference = findPreference<Preference>(preferenceKey) ?: continue
      preference.summary = prefs.getString(preferenceKey, "").orEmpty()
      preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, value ->
        pref.summary = value.toString()
        // The video-URI row derives its default from the host: a host change must refresh it
        // (the row shows the effective value, not just the stored one). The change listener fires
        // before the value is persisted, so pass the new host explicitly.
        if (preferenceKey == SK_HOST_ADDRESS) {
          refreshVideoUriSummary(hostOverride = value.toString())
        }
        true
      }
    }

    refreshVideoUriSummary()

    // The video-URI row's own change must also reflect the typed value immediately (the shared
    // helper reads prefs, which are only persisted after this listener returns true).
    val videoUri = findPreference<Preference>(SK_VIDEO_URI)
    videoUri?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, value ->
      pref.summary =
          (value as? String).orEmpty().ifEmpty { getString(R.string.video_uri_summary_empty) }
      true
    }

    // SeekBars: "<current value> · <description>" (every value-bearing setting shows its value).
    // The fallback is each preference's XML default so a fresh install shows the real value,
    // not a fabricated 0.
    val seekBarFormats =
        mapOf(
            SK_WHEEL_STEP to (R.string.pref_wheel_sens_summary to DEFAULT_WHEEL_STEP),
            SK_SHOW_PADS to (R.string.pref_show_pads_summary to DEFAULT_PADS_ALPHA),
            SK_MAGIC_BUTTON_COUNT to
                (R.string.pref_magic_count_summary to DEFAULT_MAGIC_BUTTON_COUNT),
        )
    for ((preferenceKey, pair) in seekBarFormats) {
      val preference = findPreference<Preference>(preferenceKey) ?: continue
      val (formatRes, default) = pair
      preference.summary = getString(formatRes, readSeekBarValue(prefs, preferenceKey, default))
      preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, value ->
        pref.summary = getString(formatRes, value)
        true
      }
    }
  }

  /**
   * Shows the *effective* video-URI value on the row: the stored URI, else the one derived from the
   * host (`http://<host>:8080/?action=stream`), else the empty-state label when video is genuinely
   * disabled. The row must never say "No stream URI set" while the app streams the host-derived
   * default (DESIGN.md "Every setting shows its current value"). [hostOverride] carries a freshly
   * edited host value that is not yet persisted (the change listener fires before prefs are saved).
   */
  private fun refreshVideoUriSummary(hostOverride: String? = null) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
    val videoUri = findPreference<Preference>(SK_VIDEO_URI) ?: return
    videoUri.summary =
        SettingsFragment.effectiveVideoUri(prefs, hostOverride).ifEmpty {
          getString(R.string.video_uri_summary_empty)
        }
  }

  /** "Button symbols…": dialog to edit the 5 glyphs; summary = the resolved glyphs joined. */
  private fun initializeMagicSymbolsField() {
    val myActivity = activity ?: return
    val symbols = findPreference<Preference>(SK_MAGIC_SYMBOLS) ?: return
    val store = MagicSymbolsStore(PreferenceManager.getDefaultSharedPreferences(requireContext()))
    symbols.summary = store.readAll().joinToString(" ")
    symbols.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      MagicSymbolsDialog.show(myActivity, store) {
        symbols.summary = store.readAll().joinToString(" ")
      }
      true
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
    preference.summary = diagLevelSummary(prefs.getString(SK_DIAG_LEVEL, null))
    preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
      AppLog.minBufferLevel = DiagLevel.toBufferLevel(newValue as? String)
      preference.summary = diagLevelSummary(newValue as? String)
      true
    }
  }

  /** "<current label> · How much detail…" — the current verbosity is always shown. */
  private fun diagLevelSummary(value: String?): String {
    val entryIndex =
        resources.getStringArray(R.array.diag_level_values).indexOfFirst { it == value }
    val label =
        if (entryIndex >= 0) {
          resources.getStringArray(R.array.diag_level_labels)[entryIndex]
        } else {
          getString(R.string.diag_level_label_info)
        }
    return getString(R.string.diag_level_summary, label)
  }

  /**
   * Cross-links the two settings screens: the app screen gets a "Robot settings" row, the robot
   * screen gets an "App settings" row (the gamepad's IP chip is the other robot-settings entry).
   */
  private fun initializeScreenLinks() {
    val myActivity = activity ?: return
    val openRobot = findPreference<Preference>(SK_OPEN_ROBOT_SETTINGS)
    openRobot?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      myActivity.startActivity(
          android.content.Intent(myActivity, RobotSettingsActivity::class.java)
      )
      true
    }
    val openApp = findPreference<Preference>(SK_OPEN_APP_SETTINGS)
    openApp?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
      myActivity.startActivity(android.content.Intent(myActivity, SettingsActivity::class.java))
      true
    }
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    val preferenceXml = arguments?.getInt(ARG_PREFERENCE_XML, R.xml.pref_app) ?: R.xml.pref_app
    setPreferencesFromResource(preferenceXml, rootKey)

    initializeAboutSystemField()
    initializeDynamicPreferenceSummary()
    initializeMagicSymbolsField()
    initializeResetVideoUriField()
    initializeCopyRobotIpField()
    initializeRobotPresets()
    initializeDiagnosticsLevelField()
    initializeReportIssueField()
    initializeCopyReportField()
    initializeViewLogField()
    initializeOpenSourceLicensesField()
    initializeScreenLinks()
  }
}
