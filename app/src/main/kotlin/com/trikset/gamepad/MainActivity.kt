package com.trikset.gamepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.trikset.gamepad.diagnostics.AppLog
import com.trikset.gamepad.diagnostics.CrashLogStore
import com.trikset.gamepad.diagnostics.DiagnosticsReport
import com.trikset.gamepad.diagnostics.ReportSharer
import com.trikset.gamepad.mjpeg.MjpegView
import java.net.URL
import kotlinx.coroutines.launch

/**
 * The gamepad: two touch pads, five magic buttons, a sensor-driven wheel and the MJPEG video
 * stream, all sending commands through [SenderService].
 */
class MainActivity :
    AppCompatActivity(), SensorEventListener, MainActivitySettingsController.SettingsUi {

  private var mSensorManager: SensorManager? = null
  private var mAngle = 0 // -100% .. +100%
  private var mWheelEnabled = false
  private var mWheelStep = WHEEL_STEP_DEFAULT
  private var mVideo: MjpegView? = null
  private var mVideoURL: URL? = null
  private var mSettingsController: MainActivitySettingsController? = null
  private var videoRetryController: VideoRetryController? = null
  private val senderViewModel: SenderViewModel by viewModels()
  private val wheelController = WheelController()
  private val magicButtons = MagicButtonPanel(this) { getSenderService().send(it) }
  // Lazy: `window` is only assigned during Activity.attach(), which runs after
  // construction — a field initializer touching it would NPE/throw in Robolectric.
  private val systemUiController: SystemUiController by lazy {
    SystemUiController(
        window,
        mainViewProvider = { findViewById(R.id.main) },
        actionBarProvider = { supportActionBar },
        hideDelayMs = HIDE_DELAY_MS,
    )
  }
  private val connectionFeedback: ConnectionFeedback by lazy {
    ConnectionFeedback(
        context = this,
        settingsButtonProvider = { findViewById(R.id.btnSettings) },
        rootViewProvider = { findViewById(R.id.main) },
        statusTextProvider = { findViewById(R.id.connectionStatus) },
        targetConfiguredProvider = { !getSenderService().getHostAddr().isNullOrBlank() },
        connectAction = { getSenderService().connect() },
    )
  }
  private val hardwareGamepadController: HardwareGamepadController by lazy {
    HardwareGamepadController(
        send = { getSenderService().send(it) },
        settings = {
          val prefs = PreferenceManager.getDefaultSharedPreferences(this)
          HardwareGamepadController.Settings(
              swapSticks = prefs.getBoolean(SettingsFragment.SK_GAMEPAD_SWAP, false),
              magicButtonCount = MainActivitySettingsController.readMagicButtonCount(prefs),
          )
        },
    )
  }
  private val videoStreamErrorNotifier = VideoStreamErrorNotifier()

  private fun createPad(id: Int, strId: String) {
    val pad = findViewById<SquareTouchPadLayout>(id)
    if (pad != null) {
      pad.setPadName("pad $strId")
      pad.setSender(getSenderService())
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // no-op, kept for the SensorEventListener contract
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    // Edge-to-edge (required on API 35+; enforced for targetSdk 36): draw
    // behind the system bars instead of using the removed FLAG_FULLSCREEN.
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    systemUiController.setVisibility(false)
    connectionFeedback.attach()
    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowHomeEnabled(true)
      actionBar.setDisplayUseLogoEnabled(false)
      actionBar.setLogo(R.mipmap.trik_gamepad_logo_512x512)
      actionBar.setDisplayShowTitleEnabled(true)
    }

    mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

    mVideo = findViewById(R.id.video)

    getSenderService().setShowTextCallback { message ->
      // The gear border is the persistent status; surface only connection *errors* as feedback,
      // using a Snackbar (Material) instead of a transient Toast.
      if (message.endsWith(ERROR_SUFFIX)) {
        runOnUiThread { connectionFeedback.error(message) }
      }
    }

    val btnSettings = findViewById<Button>(R.id.btnSettings)
    if (btnSettings != null) {
      btnSettings.setOnClickListener {
        val actionBar1 = supportActionBar
        if (actionBar1 != null) {
          systemUiController.setVisibility(!actionBar1.isShowing)
        }
      }
    }

    val controlsOverlay = findViewById<View>(R.id.controlsOverlay)
    if (controlsOverlay != null) {
      controlsOverlay.bringToFront()
    }

    createPad(R.id.leftPad, "1")
    createPad(R.id.rightPad, "2")

    mSettingsController = MainActivitySettingsController(this, getSenderService(), this)
    mSettingsController?.register()

    // Bounded video-stream retry, gated on the control connection's keepalive (connectionState is
    // Connected == the same robot is reachable). The gate relaxes for an empty host: a video-only
    // device (no control target) must still auto-recover its stream, so retries run on the URL +
    // not-playing checks alone there. Reloads only while the view is not playing, so a healthy
    // stream is never disturbed; a failed open / silent stall / foldable surface recreation all
    // leave the view not-playing and are recovered by the 5 s tick or by the control-Connected
    // edge (see VideoRetryController).
    videoRetryController =
        VideoRetryController(
            shouldReload = { shouldReloadVideo() },
            reload = { restartVideoStream() },
        )

    // Observe the TCP connection state for the activity's lifetime; repeatOnLifecycle
    // (not the deprecated launchWhenX) stops the collection on STOP and restarts it
    // fresh on each START, so no stale emissions are collected across pauses.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        senderViewModel.connectionState.collect { state ->
          connectionFeedback.update(state)
          if (
              state is ConnectionState.Disconnected &&
                  state.reason.isNotEmpty() &&
                  state.reason != PAUSE_DISCONNECT_REASON
          ) {
            connectionFeedback.error("Disconnected." + state.reason)
          }
          if (state is ConnectionState.Connected) {
            // Edge trigger: robot reachable again -> reload the video right away if it is dead.
            videoRetryController?.onControlConnected()
          }
        }
      }
    }

    maybeShowCrashReportDialog()
  }

  /**
   * Surfaces a captured crash exactly once: offers to share the report via the text-editor flow (or
   * the direct share sheet when "Share without editing" is set), copy it, or dismiss. The report
   * carries the live connection state and current log tail next to the crash trace.
   */
  private fun maybeShowCrashReportDialog() {
    val store = CrashLogStore(this)
    if (!store.shouldPrompt()) {
      return
    }
    store.markPrompted()
    val crash = store.latest() ?: return
    val shareWithoutEditing =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(SettingsFragment.SK_SHARE_WITHOUT_EDITING, false)
    val reportText = buildCrashReport(store, crash.stackTrace)
    AlertDialog.Builder(this)
        .setTitle(R.string.crash_dialog_title)
        .setMessage(R.string.crash_dialog_message)
        .setPositiveButton(
            if (shareWithoutEditing) R.string.share else R.string.review_and_share
        ) { _, _ ->
          ReportSharer.share(this, reportText)
        }
        .setNeutralButton(R.string.copy) { _, _ -> copyReport(reportText) }
        .setNegativeButton(R.string.dismiss, null)
        .show()
  }

  private fun buildCrashReport(store: CrashLogStore, crashTrace: String): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    return DiagnosticsReport.build(
        this,
        prefs,
        senderViewModel.connectionState.value,
        AppLog.tail(AppLog.BUFFER_CAPACITY),
        crashTrace,
    )
  }

  private fun copyReport(reportText: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.report_subject), reportText))
    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean =
      when (item.itemId) {
        R.id.settings -> {
          startActivity(Intent(this, SettingsActivity::class.java))
          true
        }
        else -> super.onOptionsItemSelected(item)
      }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // A hardware gamepad maps to pad/button commands; unmapped keys fall through.
    val handled =
        when (event.action) {
          KeyEvent.ACTION_DOWN ->
              hardwareGamepadController.onKeyDown(event.keyCode, event.repeatCount)
          KeyEvent.ACTION_UP -> hardwareGamepadController.onKeyUp(event.keyCode)
          else -> false
        }
    return handled || super.dispatchKeyEvent(event)
  }

  override fun onGenericMotionEvent(event: MotionEvent): Boolean =
      hardwareGamepadController.onMotionEvent(event) || super.onGenericMotionEvent(event)

  override fun onPause() {
    mSensorManager?.unregisterListener(this)
    getSenderService().disconnect(PAUSE_DISCONNECT_REASON)
    videoRetryController?.onPause()
    setVideoLoading(false)
    val video = mVideo
    if (video != null) {
      video.stopPlayback()
      video.setOnStreamErrorListener(null)
      video.setOnFirstFrameListener(null)
    }
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    videoRetryController?.onResume()
    val video = mVideo
    if (video != null) {
      // Reconnect-on-error: the render thread reports a dead stream and we
      // drop the HTTP connection and restart it (R12; see DECISIONS.md
      // "MJPEG: reconnect-on-error").
      video.setOnStreamErrorListener { videoRetryController?.onStreamError() }
      // Hide the loading indicator once the first frame of this playback cycle renders.
      video.setOnFirstFrameListener { runOnUiThread { setVideoLoading(false) } }
      restartVideoStream()
    }
    val sensorManager = mSensorManager
    if (sensorManager != null) {
      sensorManager.registerListener(
          this,
          sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
          SensorManager.SENSOR_DELAY_NORMAL,
      )
    }
  }

  /**
   * Retry gate for the bounded video-stream reload. Requires a configured URL and a not-playing
   * view; the control connection must be Connected, unless the host is blank — a video-only device
   * (no control target) still needs its stream to auto-recover.
   */
  private fun shouldReloadVideo(): Boolean {
    val hostConfigured = !getSenderService().getHostAddr().isNullOrBlank()
    return (!hostConfigured ||
        senderViewModel.connectionState.value is ConnectionState.Connected) &&
        mVideoURL != null &&
        mVideo?.isPlaying() == false
  }

  private fun restartVideoStream() {
    // The error listener may fire from the render thread; always hop to the
    // main thread before touching the view hierarchy / opening the stream.
    runOnUiThread {
      val video = mVideo ?: return@runOnUiThread
      // Show the loading indicator only while a URL is configured AND the control connection is
      // up (no spinner when disconnected / no stream URL); it stays up until the first frame
      // renders (robot video disabled -> keeps cycling).
      if (mVideoURL != null && senderViewModel.connectionState.value is ConnectionState.Connected) {
        setVideoLoading(true)
      }
      // Feed the load outcome back into the retry controller: a failed open arms the bounded
      // retry loop, a success disarms it. A failure also surfaces a throttled "video stream
      // unavailable" Snackbar (once per failure episode, not per 5 s retry tick).
      VideoStreamLoader(video).load(mVideoURL) { ok ->
        if (ok) {
          videoRetryController?.onLoadSuccess()
        } else {
          videoRetryController?.onLoadFailed()
          if (videoStreamErrorNotifier.shouldNotify()) {
            connectionFeedback.error(getString(R.string.video_stream_unavailable))
          }
        }
      }
    }
  }

  private fun setVideoLoading(visible: Boolean) {
    findViewById<android.widget.ProgressBar>(R.id.videoLoading)?.visibility =
        if (visible) View.VISIBLE else View.GONE
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
      processSensor(event.values)
    }
  }

  private fun processSensor(values: FloatArray) {
    val angle =
        wheelController.nextAngle(values[0], values[1], mAngle, mWheelStep, mWheelEnabled) ?: return
    mAngle = angle
    getSenderService().send("wheel $mAngle")
  }

  override fun setActionBarTitle(title: String): Boolean {
    val actionBar = supportActionBar
    return if (actionBar != null) {
      actionBar.title = title
      true
    } else {
      false
    }
  }

  override fun toast(text: String) {
    runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
  }

  override fun animatePadsAlpha(alpha: Float, previousAlpha: Float) {
    val alphaUp = AlphaAnimation(previousAlpha, alpha)
    alphaUp.setFillAfter(true)
    alphaUp.setDuration(ALPHA_ANIMATION_MS)
    findViewById<View>(R.id.controlsOverlay)?.startAnimation(alphaUp)
    findViewById<View>(R.id.buttons)?.startAnimation(alphaUp)
  }

  override fun setVideoUrl(url: URL?) {
    mVideoURL = url
    // No URL -> show a hint instead of a silent black area. The placeholder stays hidden while a
    // URL is configured, even if the stream is down (the loading spinner + gear border convey that
    // state).
    findViewById<android.widget.TextView>(R.id.videoPlaceholder)?.visibility =
        if (url == null) View.VISIBLE else View.GONE
  }

  override fun setKeepScreenOn(enabled: Boolean) {
    findViewById<View>(R.id.main)?.keepScreenOn = enabled
  }

  override fun setMagicButtons(count: Int, symbols: List<String>) {
    val buttonsView = findViewById<ViewGroup>(R.id.buttons) ?: return
    magicButtons.populate(buttonsView, count, symbols)
  }

  override fun setControlsVisible(visible: Boolean) {
    // GONE removes the pads/buttons from the layout entirely (no hit-testing).
    val visibility = if (visible) View.VISIBLE else View.GONE
    findViewById<View>(R.id.controlsOverlay)?.visibility = visibility
    findViewById<View>(R.id.buttons)?.visibility = visibility
  }

  override fun setShowFps(enabled: Boolean) {
    mVideo?.showFps = enabled
  }

  override fun getWheelStep(): Int = mWheelStep

  override fun setWheelStep(step: Int) {
    mWheelStep = step
  }

  override fun isWheelEnabled(): Boolean = mWheelEnabled

  override fun setWheelEnabled(enabled: Boolean) {
    mWheelEnabled = enabled
  }

  fun getSenderService(): SenderService = senderViewModel.sender

  fun getSettingsController(): MainActivitySettingsController? = mSettingsController

  fun setSenderService(sender: SenderService?) {
    if (sender != null) {
      senderViewModel.sender = sender
    }
  }

  override fun onDestroy() {
    mSensorManager?.unregisterListener(this)
    setVideoLoading(false)
    val video = mVideo
    if (video != null) {
      video.stopPlayback()
      video.setOnStreamErrorListener(null)
      video.setOnFirstFrameListener(null)
      mVideo = null
    }
    systemUiController.detach()
    val buttonsView = findViewById<ViewGroup>(R.id.buttons)
    if (buttonsView != null) {
      magicButtons.clearListeners(buttonsView)
    }
    findViewById<Button>(R.id.btnSettings)?.setOnClickListener(null)
    findViewById<SquareTouchPadLayout>(R.id.leftPad)?.setSender(null)
    findViewById<SquareTouchPadLayout>(R.id.rightPad)?.setSender(null)
    mSettingsController?.unregister()
    mSettingsController = null
    getSenderService().setShowTextCallback(null)
    super.onDestroy()
  }

  private companion object {
    const val TAG = "MainActivity"
    const val HIDE_DELAY_MS = 3000L
    const val ALPHA_ANIMATION_MS = 2000L
    const val WHEEL_STEP_DEFAULT = 7
    const val PAUSE_DISCONNECT_REASON = "Inactive gamepad"
    const val ERROR_SUFFIX = " error."
  }
}
