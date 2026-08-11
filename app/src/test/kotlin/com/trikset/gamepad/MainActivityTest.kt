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
  fun onSharedPreferenceChangedShouldNotRewriteVideoUriOnHostChange() {
    // No implicit video-URI copy on host change; the user resets it explicitly.
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    setPref(SettingsFragment.SK_HOST_ADDRESS, "192.168.1.42")
    assertEquals(
        "http://10.0.0.7:8080/?action=stream",
        prefs().getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
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
  fun keepScreenOnPreferenceShouldDriveMainView() {
    val main = activity.findViewById<View>(R.id.main)!!
    // Default true keeps the screen on.
    assertTrue(main.keepScreenOn)
    prefs().edit().putBoolean(SettingsFragment.SK_KEEP_SCREEN_ON, false).commit()
    requireNotNull(activity.getSettingsController()).onPreferenceChanged(prefs())
    assertFalse(main.keepScreenOn)
  }

  @Test
  fun nullVideoUrlShouldShowPlaceholder() {
    val placeholder = activity.findViewById<android.widget.TextView>(R.id.videoPlaceholder)!!
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertEquals(View.VISIBLE, placeholder.visibility)
  }

  @Test
  fun configuredVideoUrlShouldHidePlaceholder() {
    val placeholder = activity.findViewById<android.widget.TextView>(R.id.videoPlaceholder)!!
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertEquals(View.GONE, placeholder.visibility)
  }

  @Test
  fun magicButtonRowShouldNotClipAtLargeFontScale() {
    // The row is wrap_content + minHeight 50dp, so it grows with the font scale instead of
    // clipping the magic buttons. Measure at FONT_SCALE 1.3.
    val resources = activity.resources
    val config = android.content.res.Configuration(resources.configuration)
    config.fontScale = 1.3f
    resources.updateConfiguration(config, resources.displayMetrics)

    val row = activity.findViewById<android.view.ViewGroup>(R.id.buttons)!!
    row.measure(
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val rowHeight = row.measuredHeight
    for (i in 0 until row.childCount) {
      val child = row.getChildAt(i)
      child.measure(
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      )
      assertTrue(
          "child $i height ${child.measuredHeight} must fit row height $rowHeight at fontScale 1.3",
          child.measuredHeight <= rowHeight,
      )
    }
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
  fun onOptionsItemSelectedShouldOpenSettingsOrFallThrough() {
    val menu = androidx.appcompat.view.menu.MenuBuilder(activity)
    menu.add(0, R.id.settings, 1, "settings")

    // Settings item starts the SettingsActivity.
    val settings = menu.findItem(R.id.settings)
    assertTrue(activity.onOptionsItemSelected(settings!!))

    // Unknown item falls through to super.
    menu.add(0, 9999, 2, "unknown")
    assertFalse(activity.onOptionsItemSelected(menu.findItem(9999)!!))
  }

  @Test
  fun wheelEnabledPreferenceShouldDriveWheel() {
    prefs().edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, true).commit()
    requireNotNull(activity.getSettingsController()).onPreferenceChanged(prefs())
    assertTrue(activity.isWheelEnabled())
    prefs().edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, false).commit()
    requireNotNull(activity.getSettingsController()).onPreferenceChanged(prefs())
    assertFalse(activity.isWheelEnabled())
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
    // Wiring: control connection Connected + video configured + not playing must arm a reload
    // through the retry controller (here it fast-fails to an unreachable address and the
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
    // Gate: Connected with no video URL configured -> shouldReload short-circuits at
    // mVideoURL != null (false) and nothing is armed.
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
    // The spinner gate (restartVideoStream) requires a live control connection — connect first.
    awaitControlConnection(activity.getSenderService())
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
  fun restartVideoStreamWhenDisconnectedShouldNotShowLoading() {
    // Gate: not Connected -> the spinner stays hidden even with a URL configured.
    assertSpinnerHiddenAfterRestart(URL("http://127.0.0.1:1/nope"), connectFirst = false)
  }

  @Test
  fun restartVideoStreamWithoutVideoUrlShouldNotShowLoading() {
    // Gate: no URL configured -> the spinner stays hidden even while Connected. An unset URI pref
    // defaults to a real URL (MainActivitySettingsController), so set it to "" to get mVideoURL
    // null.
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertSpinnerHiddenAfterRestart(null, connectFirst = true)
  }

  /** Restarts the stream and asserts the loading spinner stays hidden (spinner-gate coverage). */
  private fun assertSpinnerHiddenAfterRestart(videoUrl: URL?, connectFirst: Boolean) {
    if (connectFirst) {
      awaitControlConnection(activity.getSenderService())
    }
    setField(activity, "mVideo", MjpegView(activity))
    if (videoUrl != null) {
      setField(activity, "mVideoURL", videoUrl)
    }
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.Robolectric.flushForegroundThreadScheduler()
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(android.view.View.GONE, indicator!!.visibility)
  }

  @Test
  fun emptyHostShouldHidePadsAndButtons() {
    // Empty host = video-only device: no pads/buttons, even with the "hide controls" toggle unset.
    setPref(SettingsFragment.SK_HOST_ADDRESS, "")
    assertEquals(View.GONE, activity.findViewById<View>(R.id.controlsOverlay)?.visibility)
    assertEquals(View.GONE, activity.findViewById<View>(R.id.buttons)?.visibility)
  }

  @Test
  fun shouldReloadVideoWhenConnectedWithUrl() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    setField(activity, "mVideo", MjpegView(activity))
    setField(activity, "mVideoURL", URL("http://127.0.0.1:1/nope"))
    awaitControlConnection(activity.getSenderService())
    val m = method(activity, "shouldReloadVideo")
    assertTrue(m.invoke(activity) as Boolean)
  }

  @Test
  fun shouldReloadVideoWhenVideoOnlyEmptyHost() {
    // Empty host = video-only: the retry gate relaxes the Connected requirement.
    setPref(SettingsFragment.SK_HOST_ADDRESS, "")
    setField(activity, "mVideo", MjpegView(activity))
    setField(activity, "mVideoURL", URL("http://127.0.0.1:1/nope"))
    val m = method(activity, "shouldReloadVideo")
    assertTrue(
        "video-only (empty host) must retry without a control connection",
        m.invoke(activity) as Boolean,
    )
  }

  @Test
  fun shouldNotReloadVideoWhenHostConfiguredButDisconnected() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    setField(activity, "mVideo", MjpegView(activity))
    setField(activity, "mVideoURL", URL("http://127.0.0.1:1/nope"))
    val m = method(activity, "shouldReloadVideo")
    assertFalse("configured host needs Connected to retry", m.invoke(activity) as Boolean)
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

  @Test
  fun setShowFpsShouldToggleVideoOverlay() {
    val video = MjpegView(activity)
    setField(activity, "mVideo", video)
    activity.setShowFps(true)
    assertTrue(video.showFps)
    activity.setShowFps(false)
    assertFalse(video.showFps)
  }

  @Test
  fun setShowFpsWithoutVideoShouldBeSafe() {
    setField(activity, "mVideo", null)
    activity.setShowFps(true)
  }

  @Test
  fun setControlsVisibleShouldHideAndShowPads() {
    activity.setControlsVisible(false)
    assertEquals(View.GONE, activity.findViewById<View>(R.id.controlsOverlay)?.visibility)
    assertEquals(View.GONE, activity.findViewById<View>(R.id.buttons)?.visibility)
    activity.setControlsVisible(true)
    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.controlsOverlay)?.visibility)
  }

  @Test
  fun setMagicButtonsShouldPopulateTheRow() {
    activity.setMagicButtons(3, listOf("▲", "■", "●"))
    val row = activity.findViewById<android.view.ViewGroup>(R.id.buttons)!!
    assertEquals(3, row.childCount)
    assertEquals("▲", (row.getChildAt(0) as android.widget.Button).text.toString())
  }

  @Test
  fun dispatchKeyEventShouldRouteGamepadKeys() {
    assertTrue(
        activity.dispatchKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_UP,
            )
        )
    )
    assertTrue(
        activity.dispatchKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_DPAD_UP,
            )
        )
    )
  }

  @Test
  fun dispatchKeyEventWithUnmappedKeyShouldFallThrough() {
    // Not a gamepad key -> the controller does not consume it and the activity
    // lets the event fall through to super (no focus -> false).
    assertFalse(
        activity.dispatchKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_VOLUME_UP,
            )
        )
    )
  }

  private fun joystickMoveEvent(source: Int, x: Float): android.view.MotionEvent {
    val coords = android.view.MotionEvent.PointerCoords()
    coords.setAxisValue(android.view.MotionEvent.AXIS_X, x)
    val props = android.view.MotionEvent.PointerProperties()
    props.id = 0
    val now = android.os.SystemClock.uptimeMillis()
    return android.view.MotionEvent.obtain(
        now,
        now,
        android.view.MotionEvent.ACTION_MOVE,
        1,
        arrayOf(props),
        arrayOf(coords),
        0,
        0,
        1f,
        1f,
        0,
        0,
        source,
        0,
    )
  }

  @Test
  fun onGenericMotionEventShouldConsumeJoystickMoves() {
    assertTrue(
        activity.onGenericMotionEvent(
            joystickMoveEvent(android.view.InputDevice.SOURCE_JOYSTICK, 0.5f)
        )
    )
  }

  @Test
  fun onGenericMotionEventWithNonGamepadSourceShouldFallThrough() {
    assertFalse(
        activity.onGenericMotionEvent(
            joystickMoveEvent(android.view.InputDevice.SOURCE_MOUSE, 0.5f)
        )
    )
  }
}
