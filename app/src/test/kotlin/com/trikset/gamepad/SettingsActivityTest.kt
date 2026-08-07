package com.trikset.gamepad

import android.content.ClipboardManager
import android.content.Context
import android.preference.PreferenceManager
import androidx.preference.Preference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class SettingsActivityTest {

  @Test
  fun onCreateShouldAddSettingsFragment() {
    val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    assertNotNull(activity.supportFragmentManager.findFragmentById(android.R.id.content))
  }

  @Test
  fun settingsFragmentShouldLoadPreferences() {
    val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    val fragment =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
    assertNotNull(fragment)

    // The fragment reads the shared preferences when it builds its summaries;
    // verify the default preferences were registered by accessing them.
    assertTrue(PreferenceManager.getDefaultSharedPreferences(activity).contains("hostAddress"))
  }

  @Test
  fun aboutSystemClickShouldCopyToClipboard() {
    val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    val fragment =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
    assertNotNull(fragment)

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
    val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    val fragment =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
    assertNotNull(fragment)

    val host = fragment.findPreference<Preference>(SettingsFragment.SK_HOST_ADDRESS)
    assertNotNull(host)
    // The change listener sets the summary to the new value.
    host!!.onPreferenceChangeListener!!.onPreferenceChange(host, "10.0.0.9")
    assertTrue((host.summary ?: "").toString().contains("10.0.0.9"))
  }
}
