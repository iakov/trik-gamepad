package com.trikset.gamepad

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuItemCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.trikset.gamepad.mjpeg.MjpegView
import java.net.URL

/**
 * The gamepad: two touch pads, five magic buttons, a sensor-driven wheel and the MJPEG video
 * stream, all sending commands through [SenderService].
 */
class MainActivity :
    AppCompatActivity(), SensorEventListener, MainActivitySettingsController.SettingsUi {

  private var hideRunnable: HideRunnable? = null
  private var mSensorManager: SensorManager? = null
  private var mAngle = 0 // -100% .. +100%
  private var mWheelEnabled = false
  private var mWheelStep = WHEEL_STEP_DEFAULT
  private var mSender: SenderService? = null
  private var mVideo: MjpegView? = null
  private var mVideoURL: URL? = null
  private var mSettingsController: MainActivitySettingsController? = null
  private val wheelController = WheelController()

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
    hideRunnable = HideRunnable()
    setSystemUiVisibility(false)
    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowHomeEnabled(true)
      actionBar.setDisplayUseLogoEnabled(false)
      actionBar.setLogo(R.drawable.trik_gamepad_logo_512x512)
      actionBar.setDisplayShowTitleEnabled(true)
    }

    mSender = SenderService()
    mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

    mVideo = findViewById(R.id.video)

    recreateMagicButtons(MAGIC_BUTTON_COUNT)

    getSenderService().setOnDisconnectedListener { toast("Disconnected." + it) }
    getSenderService().setShowTextCallback { toast(it) }

    val btnSettings = findViewById<Button>(R.id.btnSettings)
    if (btnSettings != null) {
      btnSettings.setOnClickListener {
        val actionBar1 = supportActionBar
        if (actionBar1 != null) {
          setSystemUiVisibility(!actionBar1.isShowing)
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
    val video = mVideo
    if (video != null) {
      video.stopPlayback()
      video.setOnStreamErrorListener(null)
    }
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    val video = mVideo
    if (video != null) {
      // Reconnect-on-error: the render thread reports a dead stream and we
      // drop the HTTP connection and restart it (see .PLAN.md R12).
      video.setOnStreamErrorListener { restartVideoStream() }
      restartVideoStream()
    }
    val sensorManager = mSensorManager
    if (sensorManager != null) {
      sensorManager.registerListener(
          this,
          sensorManager.getDefaultSensor(Sensor.TYPE_ALL),
          SensorManager.SENSOR_DELAY_NORMAL,
      )
    }
  }

  private fun restartVideoStream() {
    // The error listener may fire from the render thread; always hop to the
    // main thread before touching the view hierarchy / opening the stream.
    runOnUiThread {
      val video = mVideo ?: return@runOnUiThread
      VideoStreamLoader(video).load(mVideoURL)
    }
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
      processSensor(event.values)
    } else {
      Log.i("Sensor", event.sensor.type.toString())
    }
  }

  private fun processSensor(values: FloatArray) {
    val angle =
        wheelController.nextAngle(values[0], values[1], mAngle, mWheelStep, mWheelEnabled) ?: return
    mAngle = angle
    getSenderService().send("wheel $mAngle")
  }

  private fun recreateMagicButtons(count: Int) {
    val buttonsView = findViewById<ViewGroup>(R.id.buttons) ?: return
    buttonsView.removeAllViews()
    for (num in 1..count) {
      val btn = Button(this)
      btn.isHapticFeedbackEnabled = true
      btn.gravity = Gravity.CENTER
      btn.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      val name = num.toString()
      btn.text = name
      btn.setBackgroundResource(R.drawable.button_shape)
      btn.setOnClickListener {
        val sender = getSenderService()
        if (sender != null) {
          sender.send("btn $name down")
          btn.performHapticFeedback(
              HapticFeedbackConstants.LONG_PRESS,
              HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
          )
        }
      }
      buttonsView.addView(btn)
    }
  }

  private fun setSystemUiVisibility(show: Boolean) {
    val mainView = findViewById<View>(R.id.main) ?: return
    val controller = WindowCompat.getInsetsController(window, mainView) ?: return
    if (show) {
      controller.show(WindowInsetsCompat.Type.systemBars())
      val actionBar = supportActionBar
      if (actionBar != null) {
        actionBar.show()
      }
    } else {
      controller.hide(WindowInsetsCompat.Type.systemBars())
      controller.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      )
    }
    val hideRunnable = getHideRunnable()
    if (hideRunnable != null) {
      mainView.removeCallbacks(hideRunnable)
      mainView.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }
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

  fun getSenderService(): SenderService {
    return mSender!!
  }

  fun getSettingsController(): MainActivitySettingsController? = mSettingsController

  fun setSenderService(sender: SenderService?) {
    mSender = sender
  }

  private fun getHideRunnable(): HideRunnable? = hideRunnable

  private inner class HideRunnable : Runnable {
    override fun run() {
      setSystemUiVisibility(false)
    }
  }

  override fun onDestroy() {
    mSensorManager?.unregisterListener(this)
    val video = mVideo
    if (video != null) {
      video.stopPlayback()
      video.setOnStreamErrorListener(null)
      mVideo = null
    }
    val mainView = findViewById<View>(R.id.main)
    getHideRunnable()?.let { mainView.removeCallbacks(it) }
    val buttonsView = findViewById<ViewGroup>(R.id.buttons)
    if (buttonsView != null) {
      for (i in 0 until buttonsView.childCount) {
        buttonsView.getChildAt(i).setOnClickListener(null)
      }
    }
    findViewById<Button>(R.id.btnSettings)?.setOnClickListener(null)
    findViewById<SquareTouchPadLayout>(R.id.leftPad)?.setSender(null)
    findViewById<SquareTouchPadLayout>(R.id.rightPad)?.setSender(null)
    mSettingsController?.unregister()
    mSettingsController = null
    getSenderService().setOnDisconnectedListener(null)
    getSenderService().setShowTextCallback(null)
    mSender = null
    hideRunnable = null
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
