package com.trikset.gamepad

import android.view.View
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionFeedbackTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()

  private fun feedback(
      statusTextProvider: () -> TextView?,
      connectAction: () -> Unit = {},
  ): ConnectionFeedback =
      ConnectionFeedback(
          context = context,
          settingsButtonProvider = { null },
          rootViewProvider = { null },
          statusTextProvider = statusTextProvider,
          connectAction = connectAction,
      )

  /** Builds a feedback whose border paints a real [btn] (or skips when null). */
  private fun feedbackWith(
      btn: Button?,
      status: TextView?,
  ): ConnectionFeedback =
      ConnectionFeedback(
          context = context,
          settingsButtonProvider = { btn },
          rootViewProvider = { null },
          statusTextProvider = { status },
          connectAction = {},
      )

  @Test
  fun updateShouldBeSafeWhenButtonMissing() {
    val feedback = feedback({ null })
    feedback.update(ConnectionState.Connected)
    feedback.error("boom")
  }

  @Test
  fun updateShouldBeSafeWhenBackgroundIsNotLayerDrawable() {
    feedbackWith(Button(context), null).update(ConnectionState.Connected)
  }

  @Test
  fun updateShouldBeSafeWhenLayerHasNoSettingsBackground() {
    // A LayerDrawable without the @+id/settingsButtonBg layer -> the shape lookup returns
    // null and update must no-op (the gear keeps its XML stroke color).
    val btn = Button(context)
    btn.background =
        android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable()))
    feedbackWith(btn, null).update(ConnectionState.Connected)
  }

  @Test
  fun updateShouldPaintGearBorder() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val feedback =
        ConnectionFeedback(
            context = activity,
            settingsButtonProvider = { activity.findViewById(R.id.btnSettings) },
            rootViewProvider = { activity.findViewById(R.id.main) },
            statusTextProvider = { activity.findViewById(R.id.connectionStatus) },
            connectAction = {},
        )
    val app = org.robolectric.RuntimeEnvironment.getApplication()
    feedback.update(ConnectionState.Connected)
    assertEquals(app.getColor(R.color.greendark), borderStrokeColor(activity))
    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals(app.getColor(R.color.red), borderStrokeColor(activity))
    feedback.update(ConnectionState.Connecting)
    assertEquals(app.getColor(R.color.amber), borderStrokeColor(activity))
  }

  @Test
  fun updateShouldRenderConnectionStatusText() {
    // update() returns early when the settings button is missing, so use a real
    // button with the gear background (border + status render together).
    val btn = Button(context)
    btn.setBackgroundResource(R.drawable.btn_settings)
    val status = TextView(context)
    val feedback = feedbackWith(btn, status)

    feedback.update(ConnectionState.Connecting)
    assertEquals("Connecting…", status.text.toString())
    assertEquals(View.VISIBLE, status.visibility)

    feedback.update(ConnectionState.Connected)
    // Connected -> the pill is hidden; the video / gear border conveys the state.
    assertEquals(View.GONE, status.visibility)

    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals("Tap to connect…", status.text.toString())
    assertEquals(View.VISIBLE, status.visibility)
  }

  @Test
  fun attachShouldWireTapToConnect() {
    val status = TextView(context)
    var connected = false
    val feedback = feedback({ status }, connectAction = { connected = true })

    feedback.attach()
    status.performClick()
    assertTrue("tapping the status line must trigger the connect action", connected)
  }

  @Test
  fun attachShouldBeSafeWithoutStatusView() {
    feedback({ null }).attach()
    // No crash when the status line is not in the hierarchy.
  }

  private fun borderStrokeColor(activity: MainActivity): Int {
    val btn = activity.findViewById<Button>(R.id.btnSettings)!!
    val bg = btn.background as android.graphics.drawable.LayerDrawable
    val shape =
        bg.findDrawableByLayerId(R.id.settingsButtonBg)
            as android.graphics.drawable.GradientDrawable
    return org.robolectric.Shadows.shadowOf(shape).strokeColor
  }
}
