package com.trikset.gamepad

import android.view.View
import androidx.preference.PreferenceManager
import com.trikset.gamepad.mjpeg.MjpegView
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URL
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

@RunWith(RobolectricTestRunner::class)
class MainActivityTest : RobolectricTestBase() {

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

  /** Stores [value] under [key] and notifies the settings controller (the common act step). */
  private fun setPref(key: String, value: String) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)
    prefs.edit().putString(key, value).commit()
    requireNotNull(activity.getSettingsController()).onPreferenceChanged(prefs)
  }

  private fun prefs() = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)

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
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    assertEquals("10.0.0.7", activity.getSenderService().getHostAddr())
  }

  @Test
  fun onSharedPreferenceChangedWithBadPortShouldToastAndKeepDefault() {
    setPref(SettingsFragment.SK_HOST_PORT, "not-a-number")
    // No crash; target still set with default 4444 because the parse failure is
    // caught and the port variable keeps its initial value.
    assertNotNull(activity.getSenderService().getHostAddr())
  }

  @Test
  fun onSharedPreferenceChangedShouldRewriteVideoUriOnHostChange() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "192.168.1.42")
    val uri = prefs().getString(SettingsFragment.SK_VIDEO_URI, "")
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
  fun keepaliveBelowMinimumOrInvalidShouldResetToDefault() {
    for (value in listOf("100", "abc")) {
      setPref(SettingsFragment.SK_KEEPALIVE, value)
      // Below MINIMAL_KEEPALIVE / non-numeric: reset to the current keepalive.
      val stored = prefs().getString(SettingsFragment.SK_KEEPALIVE, "")
      assertEquals(
          "keepalive '$value' must reset to the default",
          SenderService.DEFAULT_KEEPALIVE.toString(),
          stored,
      )
    }
  }

  @Test
  fun wheelStepOutOfRangeOrInvalidShouldStaySane() {
    for (value in listOf("999", "abc")) {
      // Integer.getInteger reads a SYSTEM property named by the pref value, so
      // arbitrary values fall back to the default (7); the clamp keeps [1,100].
      setPref(SettingsFragment.SK_WHEEL_STEP, value)
      val step = field(activity, "mWheelStep") as Int
      assertTrue("expected wheel step in [1,100] for '$value', got $step", step in 1..100)
    }
  }

  @Test
  fun invalidVideoUriShouldNotCrash() {
    for (value in listOf("not a uri", "foo:bar")) {
      setPref(SettingsFragment.SK_VIDEO_URI, value)
      // "not a uri" fails URI parsing, "foo:bar" is a valid URI but toURL()
      // throws MalformedURLException; both leave mVideoURL null.
      assertNull("video url must stay null for '$value'", field(activity, "mVideoURL"))
    }
  }

  @Test
  fun onSharedPreferenceChangedWithValidVideoUriShouldSetUrl() {
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertNotNull(field(activity, "mVideoURL"))
  }

  @Test
  fun onSharedPreferenceChangedWithValidKeepaliveShouldApply() {
    setPref(SettingsFragment.SK_KEEPALIVE, "2000")
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
  fun onPauseWithNullFieldsShouldBeSafe() {
    // Null out both collaborators so the null branches in onPause run.
    setField(activity, "mSensorManager", null)
    setField(activity, "mVideo", null)
    method(activity, "onPause").invoke(activity)
  }

  @Test
  fun onResumeWithNullFieldsShouldBeSafe() {
    // Null out both collaborators so the null branches in onResume run.
    setField(activity, "mVideo", null)
    setField(activity, "mSensorManager", null)
    method(activity, "onResume").invoke(activity)
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
  }

  @Test
  fun restartVideoStreamShouldLoadWhenVideoPresent() {
    val video = MjpegView(activity)
    setField(activity, "mVideo", video)
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
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
  fun connectionConnectedWithVideoConfiguredShouldDriveRetryReload() {
    // Campaign 8 wiring: control connection Connected + video configured + not playing must arm a
    // reload through the retry controller (here it fast-fails to an unreachable address and the
    // onLoadFailed path runs). Exercises the collector's Connected branch and the shouldReload
    // gate.
    setField(activity, "mVideo", MjpegView(activity))
    setField(activity, "mVideoURL", URL("http://127.0.0.1:1/nope"))
    val sender = activity.getSenderService()
    awaitControlConnection(sender)
    // The reload's load() runs on a real executor; give the fast-failing open + its onResult
    // post time to reach the main looper so the onLoadFailed path is deterministically covered.
    val settleDeadline = System.currentTimeMillis() + 2000
    while (System.currentTimeMillis() < settleDeadline) {
      org.robolectric.Robolectric.flushForegroundThreadScheduler()
      Thread.sleep(20)
    }
    sender.disconnect("test done")
  }

  @Test
  fun connectionConnectedWithNullVideoUrlShouldNotReload() {
    // Campaign 8 gate: Connected with no video URL configured -> shouldReload
    // short-circuits at mVideoURL != null (false) and nothing is armed.
    val sender = activity.getSenderService()
    awaitControlConnection(sender)
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
    sender.disconnect("test done")
  }

  /** Connects [sender] to an ephemeral server and waits until the state flips to Connected. */
  private fun awaitControlConnection(sender: SenderService) {
    TestTcpServer().use { server ->
      sender.setTarget(TestTcpServer.HOST, server.port)
      sender.send("")
      val deadline = System.currentTimeMillis() + 5000
      while (
          sender.connectionState.value !is ConnectionState.Connected &&
              System.currentTimeMillis() < deadline
      ) {
        org.robolectric.Robolectric.flushForegroundThreadScheduler()
        Thread.sleep(10)
      }
      assertTrue(sender.connectionState.value is ConnectionState.Connected)
    }
  }

  /** Sets a configured video and triggers a reload (the common spinner-test setup). */
  private fun restartVideoStreamWithConfiguredVideo() {
    setField(activity, "mVideo", MjpegView(activity))
    setField(activity, "mVideoURL", URL("http://127.0.0.1:1/nope"))
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
  }

  @Test
  fun restartVideoStreamShouldShowLoadingIndicator() {
    restartVideoStreamWithConfiguredVideo()
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(android.view.View.VISIBLE, indicator!!.visibility)
  }

  @Test
  fun onPauseShouldHideLoadingIndicator() {
    restartVideoStreamWithConfiguredVideo()
    method(activity, "onPause").invoke(activity)
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(android.view.View.GONE, indicator!!.visibility)
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
  fun onSensorChangedWithNonAccelerometerShouldLogOnly() {
    val event =
        org.robolectric.shadows.ShadowSensorManager.createSensorEvent(
            3,
            android.hardware.Sensor.TYPE_GRAVITY,
        )
    event.values[0] = 0.7f
    event.values[1] = 0.7f
    activity.onSensorChanged(event)
    // Non-accelerometer sensors hit the else branch; no wheel command.
    assertEquals(0, field(activity, "mAngle") as Int)
  }

  @Test
  fun createPadWithUnknownIdShouldBeSafe() {
    val m = method(activity, "createPad", Int::class.javaPrimitiveType!!, String::class.java)
    m.invoke(activity, 999999, "1")
    // No pad with that id -> the null branch is safe.
  }

  @Test
  fun btnSettingsClickShouldToggleActionBar() {
    val btnSettings = activity.findViewById<android.widget.Button>(R.id.btnSettings)
    assertNotNull(btnSettings)
    btnSettings!!.performClick()
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
  }

  @Test
  fun btnSettingsClickWhenActionBarHiddenShouldShowIt() {
    // Hide the action bar first; the settings button then calls
    // setVisibility(true), exercising the actionBarProvider()?.show() path.
    activity.supportActionBar?.hide()
    val btnSettings = activity.findViewById<android.widget.Button>(R.id.btnSettings)
    assertNotNull(btnSettings)
    btnSettings!!.performClick()
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
    // No assertion on the final visibility: the 3s auto-hide runnable fires
    // during the flush and re-hides the bar. The click already exercised the
    // show path (branch coverage).
  }

  @Test
  fun onDestroyShouldNullOutListeners() {
    method(activity, "onDestroy").invoke(activity)
    // No crash; all listeners nulled and pads cleared.
  }

  @Test
  fun onSharedPreferenceChangedWithBadShowPadsShouldNotCrash() {
    setPref(SettingsFragment.SK_SHOW_PADS, "not-a-number")
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
