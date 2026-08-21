package com.trikset.gamepad2

import android.content.ClipboardManager
import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Direct tests for the robot/target settings screen ([RobotSettingsActivity] + pref_robot.xml):
 * host/port/keepalive summaries, video-URI reset, copy-IP and the presets category.
 */
@RunWith(RobolectricTestRunner::class)
class RobotSettingsActivityTest : RobolectricTestBase() {

  private lateinit var activity: RobotSettingsActivity
  private lateinit var fragment: SettingsFragment

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(RobotSettingsActivity::class.java).setup().get()
    fragment =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
  }

  @Test
  fun onCreateShouldAddSettingsFragment() {
    assertNotNull(fragment)
  }

  @Test
  fun settingsFragmentShouldLoadRobotPreferences() {
    assertTrue(PreferenceManager.getDefaultSharedPreferences(activity).contains("hostAddress"))
  }

  @Test
  fun dynamicSummaryShouldUpdateOnChange() {
    val host = fragment.findPreference<Preference>(SettingsFragment.SK_HOST_ADDRESS)
    assertNotNull(host)
    // The change listener sets the summary to the new value.
    host!!.onPreferenceChangeListener!!.onPreferenceChange(host, "10.0.0.9")
    assertTrue((host.summary ?: "").toString().contains("10.0.0.9"))
  }

  @Test
  fun hostPortAndKeepaliveSummariesShouldUpdateOnChange() {
    for (key in arrayOf(SettingsFragment.SK_HOST_PORT, SettingsFragment.SK_KEEPALIVE)) {
      val pref = fragment.findPreference<Preference>(key)
      assertNotNull(pref)
      pref!!.onPreferenceChangeListener!!.onPreferenceChange(pref, "7777")
      assertTrue((pref.summary ?: "").toString().contains("7777"))
    }
  }

  @Test
  fun resetVideoUriClickShouldFillFromHost() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    val reset = fragment.findPreference<Preference>(SettingsFragment.SK_RESET_VIDEO_URI)
    assertNotNull(reset)
    reset!!.onPreferenceClickListener!!.onPreferenceClick(reset)

    assertEquals(
        "http://10.0.0.9:8080/?action=stream",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
    // The action row's summary stays static; the videoURI row carries the current value.
    assertEquals(
        "Fill the URI from the robot IP address above",
        reset.summary,
    )
    val videoUri = fragment.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    assertTrue((videoUri!!.summary ?: "").toString().contains("http://10.0.0.9:8080"))
  }

  @Test
  fun copyRobotIpClickShouldCopyHostToClipboard() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    val copy = fragment.findPreference<Preference>(SettingsFragment.SK_COPY_ROBOT_IP)
    assertNotNull(copy)
    copy!!.onPreferenceClickListener!!.onPreferenceClick(copy)

    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val primary = clipboard.primaryClip
    assertNotNull(primary)
    assertEquals("10.0.0.9", primary!!.getItemAt(0).text.toString())
  }

  @Test
  fun resetVideoUriClickWithoutActivityShouldBeSafe() {
    val fragment = SettingsFragment()
    val method = fragment.javaClass.getDeclaredMethod("initializeResetVideoUriField")
    method.isAccessible = true
    method.invoke(fragment)
  }

  @Test
  fun copyRobotIpClickWithoutActivityShouldBeSafe() {
    val fragment = SettingsFragment()
    val method = fragment.javaClass.getDeclaredMethod("initializeCopyRobotIpField")
    method.isAccessible = true
    method.invoke(fragment)
  }

  @Test
  fun videoUriSummaryShouldShowEmptyStateLabelWhenExplicitlyEmpty() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_VIDEO_URI, "").commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "").commit()
    // Rebuild so the summary reflects the (empty) prefs at setup time.
    val rebuilt = buildFragment()
    val videoUri = rebuilt.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    // An explicitly-empty URI (with no host to derive from) is a genuine "video disabled" state.
    assertEquals("No stream URI set", videoUri!!.summary)
  }

  @Test
  fun videoUriSummaryShouldShowDerivedUrlWhenUnsetButHostConfigured() {
    // An unset URI with a configured host: the app streams the host-derived default, so the row
    // must show that URL (never "No stream URI set" while the app actually streams).
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().remove(SettingsFragment.SK_VIDEO_URI).commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    val rebuilt = buildFragment()
    val videoUri = rebuilt.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    assertEquals("http://10.0.0.9:8080/?action=stream", videoUri!!.summary)
  }

  @Test
  fun videoUriSummaryShouldRefreshOnHostChange() {
    // The derived video-URI summary follows the host: editing the host updates the row.
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().remove(SettingsFragment.SK_VIDEO_URI).commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    val rebuilt = buildFragment()
    val host = rebuilt.findPreference<Preference>(SettingsFragment.SK_HOST_ADDRESS)
    assertNotNull(host)
    host!!.onPreferenceChangeListener!!.onPreferenceChange(host, "10.0.0.7")

    val videoUri = rebuilt.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    assertEquals("http://10.0.0.7:8080/?action=stream", videoUri!!.summary)
  }

  /** Builds a fresh [RobotSettingsActivity] + fragment (prefs are read at setup). */
  private fun buildFragment(): SettingsFragment {
    val rebuilt = Robolectric.buildActivity(RobotSettingsActivity::class.java).setup().get()
    return rebuilt.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
  }

  private fun presetRows(): List<Preference> {
    val category = fragment.findPreference<PreferenceCategory>(SettingsFragment.SK_ROBOT_PRESETS)
    assertNotNull("robot presets category must exist in the robot screen", category)
    return (0 until category!!.preferenceCount).map { category.getPreference(it) }
  }

  @Test
  fun savePresetShouldStoreAndRefreshRows() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, "4444").commit()
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)
    assertNotNull(save)
    assertTrue(save!!.onPreferenceChangeListener!!.onPreferenceChange(save, "workshop"))

    assertTrue(
        "a dynamic row named 'workshop' must appear",
        presetRows().any { it.title.toString() == "workshop" },
    )
    assertEquals("4444", RobotPresetStore(prefs).all()["workshop"]?.port)
  }

  @Test
  fun savePresetWithEmptyNameShouldReject() {
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)
    assertNotNull(save)
    val accepted = save!!.onPreferenceChangeListener!!.onPreferenceChange(save, "   ")
    assertFalse("blank names must be rejected", accepted)
    assertTrue(presetRows().none { it.title.toString() == "" })
  }

  @Test
  fun applyPresetShouldWriteHostPortAndUri() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.9").commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, "4444").commit()
    prefs
        .edit()
        .putString(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.9:8080/?action=stream")
        .commit()
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)!!
    save.onPreferenceChangeListener!!.onPreferenceChange(save, "workshop")

    // Change the live settings, then apply the preset back.
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "0.0.0.0").commit()
    val row = presetRows().first { it.title.toString() == "workshop" }
    row.onPreferenceClickListener!!.onPreferenceClick(row)

    assertEquals("10.0.0.9", prefs.getString(SettingsFragment.SK_HOST_ADDRESS, ""))
    assertEquals("4444", prefs.getString(SettingsFragment.SK_HOST_PORT, ""))
    assertEquals(
        "http://10.0.0.9:8080/?action=stream",
        prefs.getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun deletePresetWithNoPresetsShouldBeSafe() {
    val delete = fragment.findPreference<Preference>(SettingsFragment.SK_DELETE_PRESET)
    assertNotNull(delete)
    // With no presets saved the delete tap shows a toast and does not open a dialog.
    delete!!.onPreferenceClickListener!!.onPreferenceClick(delete)
  }

  @Test
  fun deletePresetWithExistingPresetsShouldShowDialogAndDeleteOnItemTap() {
    seedHostAndPort()
    val save = fragment.findPreference<EditTextPreference>(SettingsFragment.SK_SAVE_PRESET)!!
    save.onPreferenceChangeListener!!.onPreferenceChange(save, "workshop")

    val delete = fragment.findPreference<Preference>(SettingsFragment.SK_DELETE_PRESET)!!
    delete.onPreferenceClickListener!!.onPreferenceClick(delete)
    val dialog =
        org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    val list =
        dialogViews(dialog) { it is android.widget.ListView }.first() as android.widget.ListView
    list.performItemClick(list.adapter.getView(0, null, list), 0, list.adapter.getItemId(0))

    assertTrue(
        "the preset must be deleted after tapping its row in the dialog",
        RobotPresetStore(PreferenceManager.getDefaultSharedPreferences(activity)).all().isEmpty(),
    )
  }

  @Test
  fun openAppSettingsRowShouldLaunchAppSettings() {
    val row = fragment.findPreference<Preference>(SettingsFragment.SK_OPEN_APP_SETTINGS)
    assertNotNull("the cross-link row must exist in the robot screen", row)
    row!!.onPreferenceClickListener!!.onPreferenceClick(row)
    val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
    assertEquals(SettingsActivity::class.java.name, intent?.component?.className)
  }

  /** Seeds the robot host/port prefs used by the preset save/delete tests. */
  private fun seedHostAndPort(host: String = "10.0.0.9", port: String = "4444") {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, host).commit()
    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, port).commit()
  }
}
