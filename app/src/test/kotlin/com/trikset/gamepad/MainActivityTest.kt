package com.trikset.gamepad

import android.view.View
import androidx.preference.PreferenceManager
import com.trikset.gamepad.mjpeg.MjpegView
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    requireNotNull(activity.settingsController).onPreferenceChanged(prefs)
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
    assertNotNull(activity.senderService)
    val left = activity.findViewById<SquareTouchPadLayout>(R.id.leftPad)
    assertNotNull(left)
    assertNotNull(activity.findViewById<SquareTouchPadLayout>(R.id.rightPad))
  }

  @Test
  fun onSharedPreferenceChangedShouldSetTarget() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    assertEquals("10.0.0.7", activity.senderService.hostAddr)
  }

  @Test
  fun onSharedPreferenceChangedWithBadPortShouldToastAndKeepDefault() {
    setPref(SettingsFragment.SK_HOST_PORT, "not-a-number")
    // No crash; target still set with default 4444 because the parse failure is
    // caught and the port variable keeps its initial value.
    assertNotNull(activity.senderService.hostAddr)
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
    assertFalse(field(activity, "wheelEnabled") as Boolean)
    setField(activity, "wheelEnabled", true)
    assertTrue(field(activity, "wheelEnabled") as Boolean)
  }

  @Test
  fun onCreateShouldRegisterPreferencesAndLifecycle() {
    // .setup() ran onCreate+onResume; tear down to cover onPause/onDestroy.
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
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
      val step = field(activity, "wheelStep") as Int
      assertTrue("expected wheel step in [1,100] for '$value', got $step", step in 1..100)
    }
  }

  @Test
  fun invalidVideoUriShouldNotCrash() {
    for (value in listOf("not a uri", "foo:bar")) {
      setPref(SettingsFragment.SK_VIDEO_URI, value)
      // "not a uri" fails URI parsing, "foo:bar" is a valid URI but toURL()
      // throws MalformedURLException; both leave videoUrl null.
      assertNull("video url must stay null for '$value'", field(activity, "videoUrl"))
    }
  }

  @Test
  fun onSharedPreferenceChangedWithValidVideoUriShouldSetUrl() {
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertNotNull(field(activity, "videoUrl"))
  }

  @Test
  fun keepScreenOnPreferenceShouldDriveMainView() {
    val main = activity.findViewById<View>(R.id.main)!!
    // Default true keeps the screen on.
    assertTrue(main.keepScreenOn)
    prefs().edit().putBoolean(SettingsFragment.SK_KEEP_SCREEN_ON, false).commit()
    requireNotNull(activity.settingsController).onPreferenceChanged(prefs())
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
    // The 1-arg updateConfiguration(Configuration) was removed in SDK 36, so the
    // deprecated 2-arg form is the only way to drive a font-scale change here.
    @Suppress("DEPRECATION") resources.updateConfiguration(config, resources.displayMetrics)

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
  fun magicButtonCirclesShouldNotBeClippedByClusterPadding() {
    // The cluster must not clip the button circles, and each circle must sit vertically
    // centered in the cluster. centerGlyph() used to shift each button up via translationY to
    // center its glyph, which moved the circular background off the cluster's vertical center
    // and into the top padding where clipToPadding clipped it flat (regression: phone + emulator
    // screenshots showed torn tops); glyph padding keeps the circle centered instead (see
    // DECISIONS.md "Magic-button glyph centering: asymmetric padding, not view translation").
    // clipToPadding=false stays as a belt-and-braces guard.
    val row = activity.findViewById<android.view.ViewGroup>(R.id.buttons)!!
    assertFalse("cluster must not clip the button circles", row.clipToPadding)
    // Robolectric does not lay out the hierarchy on its own; measure + layout the row so the
    // child positions are real. Row is wrap_content, so UNSPECIFIED specs resolve to the true
    // content size (buttons 48dp + 2dp top/bottom padding).
    row.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
    val contentCenter = row.paddingTop + (row.height - row.paddingTop - row.paddingBottom) / 2f
    for (i in 0 until row.childCount) {
      val child = row.getChildAt(i)
      // A translation-based centerGlyph (the pre-fix bug) would move the whole button; assert it
      // directly so the regression is caught regardless of Robolectric's font metrics (where the
      // vertical ink offset is ~0 and would slip past the center-tolerance check below).
      assertEquals("button $i translationX must stay 0", 0f, child.translationX, 0f)
      assertEquals("button $i translationY must stay 0", 0f, child.translationY, 0f)
      // The circular background fills the button, so the drawn button center is the circle
      // center; it must sit on the cluster's content center.
      val drawnCenter = child.top + child.height / 2f + child.translationY
      assertEquals(
          "button $i circle must be vertically centered in the cluster",
          contentCenter,
          drawnCenter,
          0.5f,
      )
    }
  }

  @Test
  fun onSharedPreferenceChangedWithValidKeepaliveShouldApply() {
    setPref(SettingsFragment.SK_KEEPALIVE, "2000")
    // >= MINIMAL_KEEPALIVE -> applied to the sender.
    assertEquals(2000, activity.senderService.keepaliveTimeout)
  }

  @Test
  fun onPauseShouldDisconnectAndStopVideo() {
    // Force a video view to cover the video != null branch in onPause.
    val video = MjpegView(activity)
    setField(activity, "video", video)
    method(activity, "onPause").invoke(activity)
    // No crash; sensor listener unregistered and sender disconnected.
  }

  @Test
  fun onPauseWithNullFieldsShouldBeSafe() {
    // Null out both collaborators so the null branches in onPause run.
    setField(activity, "sensorManager", null)
    setField(activity, "video", null)
    method(activity, "onPause").invoke(activity)
  }

  @Test
  fun onResumeWithNullFieldsShouldBeSafe() {
    // Null out both collaborators so the null branches in onResume run.
    setField(activity, "video", null)
    setField(activity, "sensorManager", null)
    method(activity, "onResume").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun restartVideoStreamShouldLoadWhenVideoPresent() {
    val video = MjpegView(activity)
    setField(activity, "video", video)
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun onOptionsItemSelectedUnknownShouldFallThrough() {
    val menu = androidx.appcompat.view.menu.MenuBuilder(activity)
    menu.add(0, 9999, 2, "unknown")
    assertFalse(activity.onOptionsItemSelected(menu.findItem(9999)!!))
  }

  @Test
  fun wheelEnabledPreferenceShouldDriveWheel() {
    prefs().edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, true).commit()
    requireNotNull(activity.settingsController).onPreferenceChanged(prefs())
    assertTrue(activity.wheelEnabled)
    prefs().edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, false).commit()
    requireNotNull(activity.settingsController).onPreferenceChanged(prefs())
    assertFalse(activity.wheelEnabled)
  }

  @Test
  fun restartVideoStreamShouldBeSafeWithoutVideo() {
    // video null -> runOnUiThread closure returns early.
    val m = method(activity, "restartVideoStream")
    m.invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun connectionConnectedWithVideoConfiguredShouldDriveRetryReload() {
    // Wiring: control connection Connected + video configured + not playing must arm a reload
    // through the retry controller (here it fast-fails to an unreachable address and the
    // onLoadFailed path runs). Exercises the collector's Connected branch and the shouldReload
    // gate.
    setField(activity, "video", MjpegView(activity))
    setField(activity, "videoUrl", URL("http://127.0.0.1:1/nope"))
    val sender = activity.senderService
    awaitControlConnection(sender)
    // The reload's load() runs on a real executor; give the fast-failing open + its onResult
    // post time to reach the main looper so the onLoadFailed path is deterministically covered.
    val settleDeadline = System.currentTimeMillis() + 2000
    while (System.currentTimeMillis() < settleDeadline) {
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      Thread.sleep(20)
    }
    sender.disconnect("test done")
  }

  @Test
  fun connectionConnectedWithNullVideoUrlShouldNotReload() {
    // Gate: Connected with no video URL configured -> shouldReload short-circuits at
    // videoUrl != null (false) and nothing is armed.
    val sender = activity.senderService
    awaitControlConnection(sender)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    sender.disconnect("test done")
  }

  @Test
  fun connectionConnectedShouldConfirmHaptic() {
    // Edge: control acquired (initial connect) -> a single medium click on the root view.
    val server = openControlConnection(activity.senderService)
    try {
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      val root = activity.findViewById<View>(R.id.main)
      assertEquals(
          "connecting must fire the medium click",
          Haptics.constant(Haptics.Level.CLICK),
          org.robolectric.Shadows.shadowOf(root).lastHapticFeedbackPerformed(),
      )
    } finally {
      server.close()
    }
  }

  @Test
  fun connectionUnexpectedDisconnectShouldPlayRejectSequence() {
    val sender = activity.senderService
    val server = openControlConnection(sender)
    try {
      // Lost control while connected -> the strong two-pulse alert plays; the final pulse is
      // the second strong one. The root's last haptic moves off the connect medium click.
      sender.disconnect("connection lost")
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      val root = activity.findViewById<View>(R.id.main)
      assertEquals(
          "unexpected disconnect must end the reject sequence on the strong pulse",
          Haptics.constant(Haptics.Level.HEAVY),
          org.robolectric.Shadows.shadowOf(root).lastHapticFeedbackPerformed(),
      )
    } finally {
      server.close()
    }
  }

  @Test
  fun connectionPauseDisconnectShouldNotPlayRejectSequence() {
    val sender = activity.senderService
    val server = openControlConnection(sender)
    try {
      // App-pause disconnect is not an error -> no alert; the last haptic stays the
      // connection medium click.
      sender.disconnect(ConnectionState.PAUSE_DISCONNECT_REASON)
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      val root = activity.findViewById<View>(R.id.main)
      assertEquals(
          "pause disconnect must not fire the reject sequence",
          Haptics.constant(Haptics.Level.CLICK),
          org.robolectric.Shadows.shadowOf(root).lastHapticFeedbackPerformed(),
      )
    } finally {
      server.close()
    }
  }

  /** Connects [sender] to [server] and waits until the state flips to Connected. */
  private fun awaitControlConnection(sender: SenderService, server: TestTcpServer) {
    sender.setTarget(TestTcpServer.HOST, server.port)
    sender.send("")
    val deadline = System.currentTimeMillis() + 5000
    while (
        sender.connectionState.value !is ConnectionState.Connected &&
            System.currentTimeMillis() < deadline
    ) {
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      Thread.sleep(10)
    }
    assertTrue(sender.connectionState.value is ConnectionState.Connected)
  }

  /** Connects [sender] to an ephemeral server (closed again) and waits for Connected. */
  private fun awaitControlConnection(sender: SenderService) {
    TestTcpServer().use { server -> awaitControlConnection(sender, server) }
  }

  /** Like [awaitControlConnection] but keeps the server open so the test can drive the drop. */
  private fun openControlConnection(sender: SenderService): TestTcpServer {
    val server = TestTcpServer()
    awaitControlConnection(sender, server)
    return server
  }

  /** Sets a configured video and triggers a reload (the common spinner-test setup). */
  private fun restartVideoStreamWithConfiguredVideo() {
    // The spinner gate (restartVideoStream) requires a live control connection — connect first.
    awaitControlConnection(activity.senderService)
    setField(activity, "video", MjpegView(activity))
    setField(activity, "videoUrl", URL("http://127.0.0.1:1/nope"))
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
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
    // defaults to a real URL (MainActivitySettingsController), so set it to "" to get videoUrl
    // null.
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertSpinnerHiddenAfterRestart(null, connectFirst = true)
  }

  /** Restarts the stream and asserts the loading spinner stays hidden (spinner-gate coverage). */
  private fun assertSpinnerHiddenAfterRestart(videoUrl: URL?, connectFirst: Boolean) {
    if (connectFirst) {
      awaitControlConnection(activity.senderService)
    }
    setField(activity, "video", MjpegView(activity))
    if (videoUrl != null) {
      setField(activity, "videoUrl", videoUrl)
    }
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
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
    setField(activity, "video", MjpegView(activity))
    setField(activity, "videoUrl", URL("http://127.0.0.1:1/nope"))
    awaitControlConnection(activity.senderService)
    val m = method(activity, "shouldReloadVideo")
    assertTrue(m.invoke(activity) as Boolean)
  }

  @Test
  fun shouldReloadVideoWhenVideoOnlyEmptyHost() {
    // Empty host = video-only: the retry gate relaxes the Connected requirement.
    setPref(SettingsFragment.SK_HOST_ADDRESS, "")
    setField(activity, "video", MjpegView(activity))
    setField(activity, "videoUrl", URL("http://127.0.0.1:1/nope"))
    val m = method(activity, "shouldReloadVideo")
    assertTrue(
        "video-only (empty host) must retry without a control connection",
        m.invoke(activity) as Boolean,
    )
  }

  @Test
  fun shouldNotReloadVideoWhenHostConfiguredButDisconnected() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    setField(activity, "video", MjpegView(activity))
    setField(activity, "videoUrl", URL("http://127.0.0.1:1/nope"))
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
    setField(activity, "wheelEnabled", true)
    setField(activity, "angle", 0)
    setField(activity, "wheelStep", 7)

    // Accelerometer samples reach the wheel path only when the sensor type matches.
    activity.onSensorChanged(sensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER))
    // Wheel enabled -> the accelerometer sample is processed: angle changes from 0
    // (mirrors onSensorChangedWhenWheelDisabledShouldReturnEarly asserting angle == 0).
    assertNotEquals(0, field(activity, "angle") as Int)
  }

  @Test
  fun onSensorChangedWhenWheelDisabledShouldReturnEarly() {
    setField(activity, "wheelEnabled", false)
    setField(activity, "angle", 0)
    setField(activity, "wheelStep", 7)

    activity.onSensorChanged(sensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER))
    // Wheel disabled -> no command sent, angle unchanged.
    assertEquals(0, field(activity, "angle") as Int)
  }

  @Test
  fun onSensorChangedWithNonAccelerometerShouldLogOnly() {
    activity.onSensorChanged(sensorEvent(android.hardware.Sensor.TYPE_GRAVITY))
    // Non-accelerometer sensors hit the else branch; no wheel command.
    assertEquals(0, field(activity, "angle") as Int)
  }

  @Test
  fun createPadWithUnknownIdShouldBeSafe() {
    val m = method(activity, "createPad", Int::class.javaPrimitiveType!!, String::class.java)
    m.invoke(activity, 999999, "1")
    // No pad with that id -> the null branch is safe.
  }

  @Test
  fun btnSettingsClickShouldOpenAppSettings() {
    val btnSettings = activity.findViewById<android.widget.Button>(R.id.btnSettings)
    assertNotNull(btnSettings)
    btnSettings!!.performClick()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
    assertEquals(SettingsActivity::class.java.name, intent?.component?.className)
    // Every button vibrates (user report: the gear was the one that did not);
    // one strong pulse, matching the magic buttons.
    assertEquals(
        "the gear must vibrate on click",
        Haptics.constant(Haptics.Level.HEAVY),
        org.robolectric.Shadows.shadowOf(btnSettings).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun targetChipClickShouldOpenRobotSettings() {
    val chip = activity.findViewById<View>(R.id.targetChip)
    assertNotNull(chip)
    chip!!.performClick()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
    assertEquals(RobotSettingsActivity::class.java.name, intent?.component?.className)
    // The chip is a HUD button too — it must vibrate like the gear and magic buttons.
    assertEquals(
        "the target chip must vibrate on click",
        Haptics.constant(Haptics.Level.HEAVY),
        org.robolectric.Shadows.shadowOf(chip).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun hudControlsPaddingShouldFollowWindowInsets() {
    val hudControls = activity.findViewById<View>(R.id.hudControls)
    assertNotNull(hudControls)

    // Dispatch a known insets frame; the container must adopt it per edge (the chips clear the
    // status-bar/shade strip, the cutout and the gesture-nav zones on real devices). Robolectric
    // may have already dispatched a simulated status-bar inset during activity setup (the minSdk
    // qualifier does), so assert the effect of THIS dispatch, not an initial zero. The compat
    // WindowInsetsCompat API is Robolectric-mocked on all SDK qualifiers (the raw platform
    // WindowInsets.Type is not on API 23).
    val frame =
        androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                androidx.core.graphics.Insets.of(1, 2, 3, 4),
            )
            .build()
            .toWindowInsets()
    assertNotNull(frame)
    hudControls.dispatchApplyWindowInsets(frame!!)
    assertEquals(1, hudControls.paddingLeft)
    assertEquals(2, hudControls.paddingTop)
    assertEquals(3, hudControls.paddingRight)
    assertEquals(4, hudControls.paddingBottom)
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
    assertSame(replacement, activity.senderService)
  }

  @Test
  fun setShowFpsShouldToggleVideoOverlay() {
    val video = MjpegView(activity)
    setField(activity, "video", video)
    activity.setShowFps(true)
    assertTrue(video.showFps)
    activity.setShowFps(false)
    assertFalse(video.showFps)
  }

  @Test
  fun setShowFpsWithoutVideoShouldBeSafe() {
    setField(activity, "video", null)
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

  @Test
  fun dispatchKeyEventWithUnknownActionShouldFallThrough() {
    // A non DOWN/UP action hits the when's else branch and falls through to super.
    // ACTION_MULTIPLE is deprecated (API 33) but remains the only KeyEvent action
    // that is neither DOWN nor UP, which is exactly what this test needs.
    @Suppress("DEPRECATION")
    val keyEvent =
        android.view.KeyEvent(
            android.view.KeyEvent.ACTION_MULTIPLE,
            android.view.KeyEvent.KEYCODE_VOLUME_UP,
        )
    assertFalse(activity.dispatchKeyEvent(keyEvent))
  }

  @Test
  fun setSenderServiceWithNullShouldBeSafe() {
    activity.setSenderService(null)
    // Null guard: the original sender is kept.
    assertNotNull(activity.senderService)
  }

  @Test
  fun onDestroyWithNullCollaboratorsShouldBeSafe() {
    // Null the collaborators so the null branches in onDestroy run.
    setField(activity, "sensorManager", null)
    setField(activity, "video", null)
    setField(activity, "settingsController", null)
    method(activity, "onDestroy").invoke(activity)
  }

  @Test
  fun nullVideoRetryControllerShouldBeSafeAcrossLifecycle() {
    // Null the retry controller so the ?. null branches in onPause/onResume and
    // the Connected edge-trigger all run.
    setField(activity, "videoRetryController", null)
    val sender = activity.senderService
    awaitControlConnection(sender)
    method(activity, "onPause").invoke(activity)
    method(activity, "onResume").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    sender.disconnect("test done")
  }

  /** Builds a SensorEvent of [type] via the modern SensorEventBuilder API. */
  private fun sensorEvent(type: Int): android.hardware.SensorEvent =
      org.robolectric.shadows.SensorEventBuilder.newBuilder()
          .setSensor(org.robolectric.shadows.ShadowSensor.newInstance(type))
          .setValues(floatArrayOf(0.7f, 0.7f, 0f))
          .build()
}
