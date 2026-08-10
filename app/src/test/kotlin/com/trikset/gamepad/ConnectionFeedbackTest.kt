package com.trikset.gamepad

import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionFeedbackTest : RobolectricTestBase() {

  @Test
  fun updateShouldBeSafeWhenButtonMissing() {
    val feedback =
        ConnectionFeedback(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            settingsButtonProvider = { null },
            rootViewProvider = { null },
        )
    feedback.update(ConnectionState.Connected)
    feedback.error("boom")
  }

  @Test
  fun updateShouldBeSafeWhenBackgroundIsNotLayerDrawable() {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val btn = Button(context)
    val feedback =
        ConnectionFeedback(
            context = context,
            settingsButtonProvider = { btn },
            rootViewProvider = { null },
        )
    feedback.update(ConnectionState.Connected)
  }

  @Test
  fun updateShouldBeSafeWhenLayerHasNoSettingsBackground() {
    // A LayerDrawable without the @+id/settingsButtonBg layer -> the shape lookup returns
    // null and update must no-op (the gear keeps its XML stroke color).
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val btn = Button(context)
    btn.background =
        android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable()))
    val feedback =
        ConnectionFeedback(
            context = context,
            settingsButtonProvider = { btn },
            rootViewProvider = { null },
        )
    feedback.update(ConnectionState.Connected)
  }

  @Test
  fun updateShouldPaintGearBorder() {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val feedback =
        ConnectionFeedback(
            context = activity,
            settingsButtonProvider = { activity.findViewById(R.id.btnSettings) },
            rootViewProvider = { activity.findViewById(R.id.main) },
        )
    val app = org.robolectric.RuntimeEnvironment.getApplication()
    feedback.update(ConnectionState.Connected)
    assertEquals(app.getColor(R.color.greendark), borderStrokeColor(activity))
    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals(app.getColor(R.color.red), borderStrokeColor(activity))
    feedback.update(ConnectionState.Connecting)
    assertEquals(app.getColor(R.color.amber), borderStrokeColor(activity))
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
