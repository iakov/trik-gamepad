package com.trikset.gamepad

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
      addressProvider: () -> String? = { null },
      connectAction: () -> Unit = {},
  ): ConnectionFeedback =
      ConnectionFeedback(
          context = context,
          settingsButtonProvider = { null },
          rootViewProvider = { null },
          statusTextProvider = statusTextProvider,
          addressProvider = addressProvider,
          connectAction = connectAction,
      )

  /** Builds a feedback whose border paints a real [btn] (or skips when null). */
  private fun feedbackWith(
      btn: Button?,
      status: TextView?,
      addressProvider: () -> String? = { null },
  ): ConnectionFeedback =
      ConnectionFeedback(
          context = context,
          settingsButtonProvider = { btn },
          rootViewProvider = { null },
          statusTextProvider = { status },
          addressProvider = addressProvider,
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
            addressProvider = { "192.168.77.1:4444" },
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
    val feedback = feedbackWith(btn, status, addressProvider = { "10.0.0.7:4444" })

    feedback.update(ConnectionState.Connecting)
    assertEquals("Connecting…", status.text.toString())

    feedback.update(ConnectionState.Connected)
    assertEquals("Connected to 10.0.0.7:4444", status.text.toString())

    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals("Disconnected — tap to connect", status.text.toString())
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

  @Test
  fun connectedStatusShouldTolerateMissingAddress() {
    val btn = Button(context)
    btn.setBackgroundResource(R.drawable.btn_settings)
    val status = TextView(context)
    feedbackWith(btn, status).update(ConnectionState.Connected)
    // No address -> the format argument is empty rather than "null".
    assertEquals("Connected to ", status.text.toString())
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
