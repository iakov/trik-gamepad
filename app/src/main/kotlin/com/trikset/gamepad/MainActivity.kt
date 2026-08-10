package com.trikset.gamepad

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuItemCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowHomeEnabled(true)
      actionBar.setDisplayUseLogoEnabled(false)
      actionBar.setLogo(R.mipmap.trik_gamepad_logo_512x512)
      actionBar.setDisplayShowTitleEnabled(true)
    }

    mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

    mVideo = findViewById(R.id.video)

    val buttonsView = findViewById<ViewGroup>(R.id.buttons)
    if (buttonsView != null) {
      magicButtons.populate(buttonsView, MAGIC_BUTTON_COUNT)
    }

    getSenderService().setShowTextCallback { toast(it) }

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

    // Campaign 8: bounded video-stream retry, gated on the control connection's keepalive
    // (connectionState is Connected == the same robot is reachable). Reloads only while the view is
    // not playing, so a healthy stream is never disturbed; a failed open / silent stall / foldable
    // surface recreation all leave the view not-playing and are recovered by the 5 s tick or by the
    // control-Connected edge (see VideoRetryController).
    videoRetryController =
        VideoRetryController(
            shouldReload = {
              senderViewModel.connectionState.value is ConnectionState.Connected &&
                  mVideoURL != null &&
                  mVideo?.isPlaying() == false
            },
            reload = { restartVideoStream() },
        )

    // Observe the TCP connection state for the activity's lifetime; repeatOnLifecycle
    // (not the deprecated launchWhenX) stops the collection on STOP and restarts it
    // fresh on each START, so no stale emissions are collected across pauses.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        senderViewModel.connectionState.collect { state ->
          if (state is ConnectionState.Disconnected && state.reason.isNotEmpty()) {
            toast("Disconnected." + state.reason)
          }
          if (state is ConnectionState.Connected) {
            // Edge trigger: robot reachable again -> reload the video right away if it is dead.
            videoRetryController?.onControlConnected()
          }
        }
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu, menu)
    val wheel = MenuItemCompat.getActionView(menu.findItem(R.id.wheel)) as CheckBox
    wheel.text = getString(R.string.menu_wheel)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean =
      when (item.itemId) {
        R.id.settings -> {
          startActivity(Intent(this, SettingsActivity::class.java))
          true
        }
        R.id.wheel -> {
          mWheelEnabled = !mWheelEnabled
          item.isChecked = mWheelEnabled
          true
        }
        else -> super.onOptionsItemSelected(item)
      }

  override fun onPause() {
    mSensorManager?.unregisterListener(this)
    getSenderService().disconnect("Inactive gamepad")
    videoRetryController?.onPause()
    hideVideoLoading()
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
      video.setOnFirstFrameListener { runOnUiThread { hideVideoLoading() } }
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

  private fun restartVideoStream() {
    // The error listener may fire from the render thread; always hop to the
    // main thread before touching the view hierarchy / opening the stream.
    runOnUiThread {
      val video = mVideo ?: return@runOnUiThread
      // Show the loading indicator while the stream opens/reconnects; it stays
      // up until the first frame renders (robot video disabled -> keeps cycling).
      showVideoLoading()
      // Feed the load outcome back into the retry controller (Campaign 8): a failed open arms
      // the bounded retry loop, a success disarms it.
      VideoStreamLoader(video).load(mVideoURL) { ok ->
        if (ok) videoRetryController?.onLoadSuccess() else videoRetryController?.onLoadFailed()
      }
    }
  }

  private fun showVideoLoading() {
    findViewById<android.widget.ProgressBar>(R.id.videoLoading)?.visibility = View.VISIBLE
  }

  private fun hideVideoLoading() {
    findViewById<android.widget.ProgressBar>(R.id.videoLoading)?.visibility = View.GONE
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
  }

  override fun getWheelStep(): Int = mWheelStep

  override fun setWheelStep(step: Int) {
    mWheelStep = step
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
    hideVideoLoading()
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
    const val MAGIC_BUTTON_COUNT = 5
    const val HIDE_DELAY_MS = 3000L
    const val ALPHA_ANIMATION_MS = 2000L
    const val WHEEL_STEP_DEFAULT = 7
  }
}
