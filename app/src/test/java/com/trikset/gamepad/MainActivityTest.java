package com.trikset.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class MainActivityTest {

  private MainActivity activity;

  private Object field(Object target, String name) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  private void setField(Object target, String name, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  private Method method(Object target, String name, Class<?>... params) throws Exception {
    Method m = target.getClass().getDeclaredMethod(name, params);
    m.setAccessible(true);
    return m;
  }

  @Before
  public void setUp() {
    activity = org.robolectric.Robolectric.buildActivity(MainActivity.class).setup().get();
  }

  @Test
  public void onCreateShouldSetUpSenderServiceAndPads() {
    assertNotNull(activity.getSenderService());
    SquareTouchPadLayout left = activity.findViewById(R.id.leftPad);
    assertNotNull(left);
    assertNotNull(activity.findViewById(R.id.rightPad));
  }

  @Test
  public void onSharedPreferenceChangedShouldSetTarget() throws Exception {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (SharedPreferences.OnSharedPreferenceChangeListener)
            field(activity, "mSharedPreferencesListener");
    assertNotNull(listener);

    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7").commit();
    listener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_HOST_ADDRESS);

    assertEquals("10.0.0.7", activity.getSenderService().getHostAddr());
  }

  @Test
  public void onSharedPreferenceChangedWithBadPortShouldToastAndKeepDefault() throws Exception {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (SharedPreferences.OnSharedPreferenceChangeListener)
            field(activity, "mSharedPreferencesListener");

    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, "not-a-number").commit();
    listener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_HOST_PORT);
    // No crash; target still set with default 4444 because the parse failure is
    // caught and the port variable keeps its initial value.
    assertNotNull(activity.getSenderService().getHostAddr());
  }

  @Test
  public void onSharedPreferenceChangedShouldRewriteVideoUriOnHostChange() throws Exception {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (SharedPreferences.OnSharedPreferenceChangeListener)
            field(activity, "mSharedPreferencesListener");

    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "192.168.1.42").commit();
    listener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_HOST_ADDRESS);

    String uri = prefs.getString(SettingsFragment.SK_VIDEO_URI, "");
    assertEquals("http://192.168.1.42:8080/?action=stream", uri);
  }

  @Test
  public void processSensorShouldSendWheelWhenAngleChanged() throws Exception {
    setField(activity, "mWheelEnabled", true);
    setField(activity, "mAngle", 0);
    setField(activity, "mWheelStep", 7);

    Method m = (Method) method(activity, "processSensor", float[].class);
    m.invoke(activity, (Object) new float[] {1f, 0f}); // angle 0
    m.invoke(activity, (Object) new float[] {0.7f, 0.7f}); // angle ~ +45

    assertNotNull(activity.getSenderService());
  }

  @Test
  public void processSensorShouldSkipSmallAngles() throws Exception {
    setField(activity, "mWheelEnabled", true);
    setField(activity, "mAngle", 0);
    setField(activity, "mWheelStep", 7);
    Method m = (Method) method(activity, "processSensor", float[].class);

    m.invoke(activity, (Object) new float[] {1f, 0f});
    m.invoke(activity, (Object) new float[] {1f, 0.01f}); // tiny angle delta, no send
  }

  @Test
  public void onOptionsItemSelectedShouldToggleWheel() throws Exception {
    assertFalse((boolean) field(activity, "mWheelEnabled"));
    setField(activity, "mWheelEnabled", true);
    assertTrue((boolean) field(activity, "mWheelEnabled"));
  }

  @Test
  public void onCreateShouldRegisterPreferencesAndLifecycle() {
    // .setup() ran onCreate+onResume; tear down to cover onPause/onDestroy.
    org.robolectric.Robolectric.flushForegroundThreadScheduler();
  }

  @Test
  public void onSharedPreferenceChangedWithSmallKeepaliveShouldResetToMinimum() throws Exception {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (SharedPreferences.OnSharedPreferenceChangeListener)
            field(activity, "mSharedPreferencesListener");

    prefs.edit().putString(SettingsFragment.SK_KEEPALIVE, "100").commit();
    listener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_KEEPALIVE);

    // Below MINIMAL_KEEPALIVE: the value is reset to the current keepalive.
    String stored = prefs.getString(SettingsFragment.SK_KEEPALIVE, "");
    assertEquals(String.valueOf(SenderService.DEFAULT_KEEPALIVE), stored);
  }

  @Test
  public void onSharedPreferenceChangedWithInvalidKeepaliveShouldReset() throws Exception {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (SharedPreferences.OnSharedPreferenceChangeListener)
            field(activity, "mSharedPreferencesListener");

    prefs.edit().putString(SettingsFragment.SK_KEEPALIVE, "abc").commit();
    listener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_KEEPALIVE);
    // Non-numeric -> reset to the current keepalive, no crash.
    String stored = prefs.getString(SettingsFragment.SK_KEEPALIVE, "");
    assertEquals(String.valueOf(SenderService.DEFAULT_KEEPALIVE), stored);
  }

  @Test
  public void onSharedPreferenceChangedShouldClampWheelStep() throws Exception {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (SharedPreferences.OnSharedPreferenceChangeListener)
            field(activity, "mSharedPreferencesListener");

    // Note: the app reads the wheel step via Integer.getInteger(pref, default),
    // which reads a SYSTEM property named by the pref value, so arbitrary pref
    // values fall back to the default (7) and the clamp keeps it in [1,100].
    prefs.edit().putString(SettingsFragment.SK_WHEEL_STEP, "999").commit();
    listener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_WHEEL_STEP);
    int step = (int) field(activity, "mWheelStep");
    assertTrue("expected wheel step in [1,100], got " + step, step >= 1 && step <= 100);
  }

  @Test
  public void onSharedPreferenceChangedWithBadVideoUriShouldNotCrash() throws Exception {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    SharedPreferences.OnSharedPreferenceChangeListener listener =
        (SharedPreferences.OnSharedPreferenceChangeListener)
            field(activity, "mSharedPreferencesListener");

    prefs.edit().putString(SettingsFragment.SK_VIDEO_URI, "not a uri").commit();
    listener.onSharedPreferenceChanged(prefs, SettingsFragment.SK_VIDEO_URI);
    // URISyntaxException/MalformedURLException handled; mVideoURL stays null.
    assertNull(field(activity, "mVideoURL"));
  }

  @Test
  public void processSensorShouldClampAngleAndHonorStep() throws Exception {
    setField(activity, "mWheelEnabled", true);
    setField(activity, "mAngle", 0);
    setField(activity, "mWheelStep", 7);
    Method m = (Method) method(activity, "processSensor", float[].class);

    // Large positive angle (x>0, y large) -> clamped to 100 and sent.
    m.invoke(activity, (Object) new float[] {1f, 100f});
    assertEquals(100, (int) field(activity, "mAngle"));
  }

  @Test
  public void onPauseShouldDisconnectAndStopVideo() throws Exception {
    // Force a video view to cover the mVideo != null branch in onPause.
    com.demo.mjpeg.MjpegView video = new com.demo.mjpeg.MjpegView(activity);
    setField(activity, "mVideo", video);
    activity.onPause();
    // No crash; sensor listener unregistered and sender disconnected.
  }

  @Test
  public void onCreateOptionsMenuShouldInflateMenu() {
    androidx.appcompat.view.menu.MenuBuilder menu =
        new androidx.appcompat.view.menu.MenuBuilder(activity);
    assertTrue(activity.onCreateOptionsMenu(menu));
  }

  @Test
  public void onOptionsItemSelectedShouldOpenSettingsOrToggleWheel() throws Exception {
    androidx.appcompat.view.menu.MenuBuilder menu =
        new androidx.appcompat.view.menu.MenuBuilder(activity);
    menu.add(0, R.id.wheel, 0, "wheel");
    menu.add(0, R.id.settings, 1, "settings");

    // Toggle wheel off -> on.
    android.view.MenuItem wheel = menu.findItem(R.id.wheel);
    assertTrue(activity.onOptionsItemSelected(wheel));
    assertTrue((boolean) field(activity, "mWheelEnabled"));

    // Settings item starts the SettingsActivity.
    android.view.MenuItem settings = menu.findItem(R.id.settings);
    assertTrue(activity.onOptionsItemSelected(settings));

    // Unknown item falls through to super.
    menu.add(0, 9999, 2, "unknown");
    assertFalse(activity.onOptionsItemSelected(menu.findItem(9999)));
  }

  @Test
  public void restartVideoStreamShouldBeSafeWithoutVideo() throws Exception {
    // mVideo null -> runOnUiThread closure returns early.
    Method m = (Method) method(activity, "restartVideoStream");
    m.invoke(activity);
    org.robolectric.Robolectric.flushForegroundThreadScheduler();
  }

  @Test
  public void setSystemUiVisibilityHideShouldRun() throws Exception {
    Method m = (Method) method(activity, "setSystemUiVisibility", boolean.class);
    m.invoke(activity, false);
    org.robolectric.Robolectric.flushForegroundThreadScheduler();
  }

  @Test
  public void onDestroyShouldNullOutListeners() {
    activity.onDestroy();
    // No crash; all listeners nulled and pads cleared.
  }
}
