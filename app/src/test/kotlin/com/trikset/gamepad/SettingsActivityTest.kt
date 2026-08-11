package com.trikset.gamepad

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
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

@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest : RobolectricTestBase() {

  private lateinit var activity: SettingsActivity
  private lateinit var fragment: SettingsFragment

  @Before
  fun setUp() {
    activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    fragment =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
  }

  @Test
  fun onCreateShouldAddSettingsFragment() {
    assertNotNull(fragment)
  }

  @Test
  fun settingsFragmentShouldLoadPreferences() {
    // The fragment reads the shared preferences when it builds its summaries;
    // verify the default preferences were registered by accessing them.
    assertTrue(PreferenceManager.getDefaultSharedPreferences(activity).contains("hostAddress"))
  }

  @Test
  fun aboutSystemClickShouldCopyToClipboard() {
    val about = fragment.findPreference<Preference>(SettingsFragment.SK_ABOUT_SYSTEM)
    assertNotNull(about)
    // Trigger the click listener set up by the fragment.
    about!!.onPreferenceClickListener!!.onPreferenceClick(about)

    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val primary = clipboard.primaryClip
    assertNotNull(primary)
    assertTrue(primary!!.getItemAt(0).text.length > 0)
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
  fun aboutSystemClickWithoutActivityShouldBeSafe() {
    // A fragment never attached to an activity -> initializeAboutSystemField
    // returns early via the `activity ?: return` guard.
    val fragment = SettingsFragment()
    val method = fragment.javaClass.getDeclaredMethod("initializeAboutSystemField")
    method.isAccessible = true
    method.invoke(fragment)
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

  private fun presetRows(): List<Preference> {
    val category = fragment.findPreference<PreferenceCategory>(SettingsFragment.SK_ROBOT_PRESETS)
    assertNotNull("robot presets category must exist in the nested Advanced screen", category)
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
  fun onPreferenceStartScreenShouldPushNestedFragment() {
    val manager = PreferenceManager(activity)
    val screen = manager.createPreferenceScreen(activity)
    screen.key = SettingsFragment.SK_ADVANCED
    assertTrue(activity.onPreferenceStartScreen(fragment, screen))
    activity.supportFragmentManager.executePendingTransactions()
    val top = activity.supportFragmentManager.fragments.last()
    assertTrue("the nested screen must push a new SettingsFragment", top is SettingsFragment)
    assertEquals(
        SettingsFragment.SK_ADVANCED,
        top.arguments?.getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT),
    )
  }

  @Test
  fun nestedFragmentWithAdvancedRootShouldLoadAdvancedPrefs() {
    // The sub-screen fragment runs onCreatePreferences with rootKey="advancedSettings", so its
    // tree is the Advanced subtree (About + keepalive) and the init helpers resolve within it.
    val sub = SettingsFragment()
    val args = Bundle()
    args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, SettingsFragment.SK_ADVANCED)
    sub.arguments = args
    activity.supportFragmentManager.beginTransaction().add(sub, "advanced-sub").commitNow()

    assertNotNull(
        "About must resolve inside the Advanced subtree",
        sub.findPreference<Preference>(SettingsFragment.SK_ABOUT_SYSTEM),
    )
    assertNotNull(
        "keepalive must resolve inside the Advanced subtree",
        sub.findPreference<Preference>(SettingsFragment.SK_KEEPALIVE),
    )
  }

  @Test
  fun magicSymbolsRowShouldShowResolvedGlyphSummary() {
    val symbols = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_SYMBOLS)
    assertNotNull(symbols)
    assertEquals("▲ ■ ● ✕ ◆", symbols!!.summary)
  }

  @Test
  fun magicSymbolsClickShouldOpenSymbolsDialog() {
    val symbols = fragment.findPreference<Preference>(SettingsFragment.SK_MAGIC_SYMBOLS)
    assertNotNull(symbols)
    symbols!!.onPreferenceClickListener!!.onPreferenceClick(symbols)
    assertNotNull(
        "tapping Button symbols must open the glyph dialog",
        org.robolectric.shadows.ShadowDialog.getLatestDialog(),
    )
  }

  @Test
  fun diagLevelSummaryShouldShowCurrentLabelOnChange() {
    val diag = fragment.findPreference<Preference>(SettingsFragment.SK_DIAG_LEVEL)
    assertNotNull(diag)
    diag!!.onPreferenceChangeListener!!.onPreferenceChange(diag, "verbose")
    assertTrue((diag.summary ?: "").toString().contains("Verbose"))
    assertTrue((diag.summary ?: "").toString().contains("How much detail"))
  }

  @Test
  fun videoUriSummaryShouldShowEmptyStateLabelWhenUnset() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs.edit().remove(SettingsFragment.SK_VIDEO_URI).commit()
    val videoUri = fragment.findPreference<Preference>(SettingsFragment.SK_VIDEO_URI)
    assertNotNull(videoUri)
    videoUri!!.onPreferenceChangeListener!!.onPreferenceChange(videoUri, "")
    assertEquals("No stream URI set", videoUri.summary)
  }

  @Test
  fun seekBarSummariesShouldShowValueAndDescription() {
    val wheel = fragment.findPreference<Preference>(SettingsFragment.SK_WHEEL_STEP)
    assertNotNull(wheel)
    wheel!!.onPreferenceChangeListener!!.onPreferenceChange(wheel, 12)
    assertEquals("12 · Smaller = more sensitive; 5..10 is typical", wheel.summary)
  }
}
