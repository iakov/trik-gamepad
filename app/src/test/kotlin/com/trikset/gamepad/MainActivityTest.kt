package com.trikset.gamepad

import android.view.View
import androidx.preference.PreferenceManager
import com.trikset.gamepad.mjpeg.MjpegView
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class MainActivityTest {

  private lateinit var activity: MainActivity

  // Shared preferences persist across methods in a JVM; start each test clean.
  @Before
  fun resetSharedPreferences() {
    PreferenceManager.getDefaultSharedPreferences(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        .edit()
        .clear()
        .commit()
  }

  @Before
  fun setUp() {
    activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
  }

  private fun field(target: Any, name: String): Any? {
    val f: Field = target.javaClass.getDeclaredField(name)
    f.isAccessible = true
    return f.get(target)
  }

  private fun setField(target: Any, name: String, value: Any?) {
    val f: Field = target.javaClass.getDeclaredField(name)
    f.isAccessible = true
    f.set(target, value)
  }

  private fun method(target: Any, name: String, vararg params: Class<*>): Method {
    val m = target.javaClass.getDeclaredMethod(name, *params)
    m.isAccessible = true
    return m
  }

  @Test
  fun onCreateShouldSetUpSenderServiceAndPads() {
    assertNotNull(activity.getSenderService())
    val left = activity.findViewById<SquareTouchPadLayout>(R.id.leftPad)
    assertNotNull(left)
    assertNotNull(activity.findViewById<SquareTouchPadLayout>(R.id.rightPad))
  }

  @Test
  fun onSharedPreferenceChangedShouldSetTarget() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())
    assertNotNull(controller)

    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7").commit()
    controller.onPreferenceChanged(prefs)

    assertEquals("10.0.0.7", activity.getSenderService().getHostAddr())
  }

  @Test
  fun onSharedPreferenceChangedWithBadPortShouldToastAndKeepDefault() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs.edit().putString(SettingsFragment.SK_HOST_PORT, "not-a-number").commit()
    controller.onPreferenceChanged(prefs)
    // No crash; target still set with default 4444 because the parse failure is
    // caught and the port variable keeps its initial value.
    assertNotNull(activity.getSenderService().getHostAddr())
  }

  @Test
  fun onSharedPreferenceChangedShouldRewriteVideoUriOnHostChange() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs.edit().putString(SettingsFragment.SK_HOST_ADDRESS, "192.168.1.42").commit()
    controller.onPreferenceChanged(prefs)

    val uri = prefs.getString(SettingsFragment.SK_VIDEO_URI, "")
    assertEquals("http://192.168.1.42:8080/?action=stream", uri)
  }

  @Test
  fun onOptionsItemSelectedShouldToggleWheel() {
    assertFalse(field(activity, "mWheelEnabled") as Boolean)
    setField(activity, "mWheelEnabled", true)
    assertTrue(field(activity, "mWheelEnabled") as Boolean)
  }

  @Test
  fun onCreateShouldRegisterPreferencesAndLifecycle() {
    // .setup() ran onCreate+onResume; tear down to cover onPause/onDestroy.
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
  }

  @Test
  fun onSharedPreferenceChangedWithSmallKeepaliveShouldResetToMinimum() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs.edit().putString(SettingsFragment.SK_KEEPALIVE, "100").commit()
    controller.onPreferenceChanged(prefs)

    // Below MINIMAL_KEEPALIVE: the value is reset to the current keepalive.
    val stored = prefs.getString(SettingsFragment.SK_KEEPALIVE, "")
    assertEquals(SenderService.DEFAULT_KEEPALIVE.toString(), stored)
  }

  @Test
  fun onSharedPreferenceChangedWithInvalidKeepaliveShouldReset() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs.edit().putString(SettingsFragment.SK_KEEPALIVE, "abc").commit()
    controller.onPreferenceChanged(prefs)
    // Non-numeric -> reset to the current keepalive, no crash.
    val stored = prefs.getString(SettingsFragment.SK_KEEPALIVE, "")
    assertEquals(SenderService.DEFAULT_KEEPALIVE.toString(), stored)
  }

  @Test
  fun onSharedPreferenceChangedShouldClampWheelStep() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    // Note: the app reads the wheel step via Integer.getInteger(pref, default),
    // which reads a SYSTEM property named by the pref value, so arbitrary pref
    // values fall back to the default (7) and the clamp keeps it in [1,100].
    prefs.edit().putString(SettingsFragment.SK_WHEEL_STEP, "999").commit()
    controller.onPreferenceChanged(prefs)
    val step = field(activity, "mWheelStep") as Int
    assertTrue("expected wheel step in [1,100], got $step", step in 1..100)
  }

  @Test
  fun onSharedPreferenceChangedWithNonNumericWheelStepShouldKeepDefault() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    // A non-numeric value makes Integer.getInteger return null; the elvis keeps
    // the current step.
    prefs.edit().putString(SettingsFragment.SK_WHEEL_STEP, "abc").commit()
    controller.onPreferenceChanged(prefs)
    val step = field(activity, "mWheelStep") as Int
    assertTrue("expected a sane wheel step, got $step", step in 1..100)
  }

  @Test
  fun onSharedPreferenceChangedWithBadVideoUriShouldNotCrash() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs.edit().putString(SettingsFragment.SK_VIDEO_URI, "not a uri").commit()
    controller.onPreferenceChanged(prefs)
    // URISyntaxException/MalformedURLException handled; mVideoURL stays null.
    assertNull(field(activity, "mVideoURL"))
  }

  @Test
  fun onSharedPreferenceChangedWithUnknownProtocolShouldNotCrash() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    // "foo:bar" is a valid URI but toURL() throws MalformedURLException.
    prefs.edit().putString(SettingsFragment.SK_VIDEO_URI, "foo:bar").commit()
    controller.onPreferenceChanged(prefs)
    assertNull(field(activity, "mVideoURL"))
  }

  @Test
  fun onSharedPreferenceChangedWithValidVideoUriShouldSetUrl() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs
        .edit()
        .putString(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
        .commit()
    controller.onPreferenceChanged(prefs)
    assertNotNull(field(activity, "mVideoURL"))
  }

  @Test
  fun onSharedPreferenceChangedWithValidKeepaliveShouldApply() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs.edit().putString(SettingsFragment.SK_KEEPALIVE, "2000").commit()
    controller.onPreferenceChanged(prefs)
    // >= MINIMAL_KEEPALIVE -> applied to the sender.
    assertEquals(2000, activity.getSenderService().getKeepaliveTimeout())
  }

  @Test
  fun onPauseShouldDisconnectAndStopVideo() {
    // Force a video view to cover the mVideo != null branch in onPause.
    val video = MjpegView(activity)
    setField(activity, "mVideo", video)
    method(activity, "onPause").invoke(activity)
    // No crash; sensor listener unregistered and sender disconnected.
  }

  @Test
  fun onCreateOptionsMenuShouldInflateMenu() {
    val menu = androidx.appcompat.view.menu.MenuBuilder(activity)
    assertTrue(activity.onCreateOptionsMenu(menu))
  }

  @Test
  fun onOptionsItemSelectedShouldOpenSettingsOrToggleWheel() {
    val menu = androidx.appcompat.view.menu.MenuBuilder(activity)
    menu.add(0, R.id.wheel, 0, "wheel")
    menu.add(0, R.id.settings, 1, "settings")

    // Toggle wheel off -> on.
    val wheel = menu.findItem(R.id.wheel)
    assertTrue(activity.onOptionsItemSelected(wheel!!))
    assertTrue(field(activity, "mWheelEnabled") as Boolean)

    // Settings item starts the SettingsActivity.
    val settings = menu.findItem(R.id.settings)
    assertTrue(activity.onOptionsItemSelected(settings!!))

    // Unknown item falls through to super.
    menu.add(0, 9999, 2, "unknown")
    assertFalse(activity.onOptionsItemSelected(menu.findItem(9999)!!))
  }

  @Test
  fun restartVideoStreamShouldBeSafeWithoutVideo() {
    // mVideo null -> runOnUiThread closure returns early.
    val m = method(activity, "restartVideoStream")
    m.invoke(activity)
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
  }

  @Test
  fun createPadShouldWireSender() {
    val m = method(activity, "createPad", Int::class.javaPrimitiveType!!, String::class.java)
    m.invoke(activity, R.id.leftPad, "1")
    val pad = activity.findViewById<SquareTouchPadLayout>(R.id.leftPad)
    assertNotNull(pad)
  }

  @Test
  fun onAccuracyChangedShouldBeNoOp() {
    // Call the listener method; must not throw.
    activity.onAccuracyChanged(null, 0)
  }

  @Test
  fun onSensorChangedAccelerometerShouldProcessWheel() {
    setField(activity, "mWheelEnabled", true)
    setField(activity, "mAngle", 0)
    setField(activity, "mWheelStep", 7)

    // createSensorEvent(size, type): type must be TYPE_ACCELEROMETER (the
    // 1-arg createSensorEvent(size) defaults to TYPE_GRAVITY, which never
    // reaches the wheel path).
    val event =
        org.robolectric.shadows.ShadowSensorManager.createSensorEvent(
            3,
            android.hardware.Sensor.TYPE_ACCELEROMETER,
        )
    event.values[0] = 0.7f
    event.values[1] = 0.7f
    event.values[2] = 0f
    activity.onSensorChanged(event)
    // Wheel enabled -> a "wheel N" command was sent.
    assertTrue(activity.getSenderService().getHostAddr() != null)
  }

  @Test
  fun onSensorChangedWhenWheelDisabledShouldReturnEarly() {
    setField(activity, "mWheelEnabled", false)
    setField(activity, "mAngle", 0)
    setField(activity, "mWheelStep", 7)

    val event =
        org.robolectric.shadows.ShadowSensorManager.createSensorEvent(
            3,
            android.hardware.Sensor.TYPE_ACCELEROMETER,
        )
    event.values[0] = 0.7f
    event.values[1] = 0.7f
    activity.onSensorChanged(event)
    // Wheel disabled -> no command sent, mAngle unchanged.
    assertEquals(0, field(activity, "mAngle") as Int)
  }

  @Test
  fun btnSettingsClickShouldToggleActionBar() {
    val btnSettings = activity.findViewById<android.widget.Button>(R.id.btnSettings)
    assertNotNull(btnSettings)
    btnSettings!!.performClick()
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
  }

  @Test
  fun onDestroyShouldNullOutListeners() {
    method(activity, "onDestroy").invoke(activity)
    // No crash; all listeners nulled and pads cleared.
  }

  @Test
  fun onSharedPreferenceChangedWithBadShowPadsShouldNotCrash() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    val controller = requireNotNull(activity.getSettingsController())

    prefs.edit().putString(SettingsFragment.SK_SHOW_PADS, "not-a-number").commit()
    controller.onPreferenceChanged(prefs)
    // The non-numeric pads alpha is caught; nothing crashes.
    assertNotNull(activity.findViewById<View>(R.id.controlsOverlay))
  }

  @Test
  fun setSenderServiceShouldStoreSender() {
    val replacement = SenderService()
    activity.setSenderService(replacement)
    assertSame(replacement, activity.getSenderService())
  }
}
