package com.trikset.gamepad;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.preference.PreferenceManager;
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
}
