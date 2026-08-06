package com.trikset.gamepad;

import static android.content.Context.CLIPBOARD_SERVICE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.preference.PreferenceManager;
import androidx.preference.Preference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class SettingsActivityTest {

  @Test
  public void onCreateShouldAddSettingsFragment() {
    SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
    assertNotNull(activity.getSupportFragmentManager().findFragmentById(android.R.id.content));
  }

  @Test
  public void settingsFragmentShouldLoadPreferences() {
    SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
    SettingsFragment fragment =
        (SettingsFragment)
            activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
    assertNotNull(fragment);

    // The fragment reads the shared preferences when it builds its summaries;
    // verify the default preferences were registered by accessing them.
    assertTrue(PreferenceManager.getDefaultSharedPreferences(activity).contains("hostAddress"));
  }

  @Test
  public void aboutSystemClickShouldCopyToClipboard() {
    SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
    SettingsFragment fragment =
        (SettingsFragment)
            activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
    assertNotNull(fragment);

    Preference about = fragment.findPreference(SettingsFragment.SK_ABOUT_SYSTEM);
    assertNotNull(about);
    // Trigger the click listener set up by the fragment.
    about.getOnPreferenceClickListener().onPreferenceClick(about);

    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(CLIPBOARD_SERVICE);
    ClipData primary = clipboard.getPrimaryClip();
    assertNotNull(primary);
    assertTrue(primary.getItemAt(0).getText().length() > 0);
  }

  @Test
  public void dynamicSummaryShouldUpdateOnChange() {
    SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
    SettingsFragment fragment =
        (SettingsFragment)
            activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
    assertNotNull(fragment);

    Preference host = fragment.findPreference(SettingsFragment.SK_HOST_ADDRESS);
    assertNotNull(host);
    // The change listener sets the summary to the new value.
    host.getOnPreferenceChangeListener().onPreferenceChange(host, "10.0.0.9");
    assertTrue(String.valueOf(host.getSummary()).contains("10.0.0.9"));
  }
}
