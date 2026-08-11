package com.trikset.gamepad

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

/**
 * Owns the connection-state feedback chrome: recolors the settings-button border by
 * [ConnectionState], shows Material [Snackbar]s for connection errors, and renders a centered
 * status pill ("Tap to connect…" / "Connecting…", hidden once connected) whose tap retries the
 * connection. The views are injected as providers so the object can be constructed before the view
 * hierarchy exists (like [SystemUiController]); recoloring the existing gear border adds no new
 * touch surface, and the status pill is the single tappable status affordance.
 */
class ConnectionFeedback(
    private val context: Context,
    private val settingsButtonProvider: () -> Button?,
    private val rootViewProvider: () -> View?,
    private val statusTextProvider: () -> TextView?,
    private val targetConfiguredProvider: () -> Boolean,
    private val connectAction: () -> Unit,
) {
  private val indicator = ConnectionIndicator()

  /** Wires the tap-to-connect action onto the status line; call once after the views exist. */
  fun attach() {
    statusTextProvider()?.setOnClickListener { connectAction() }
  }

  fun update(state: ConnectionState) {
    paintGearBorder(state)
    setStatusText(state)
  }

  private fun paintGearBorder(state: ConnectionState) {
    val btn = settingsButtonProvider() ?: return
    val layerDrawable = btn.background as? LayerDrawable ?: return
    val shape =
        layerDrawable.findDrawableByLayerId(R.id.settingsButtonBg) as? GradientDrawable ?: return
    val color = ContextCompat.getColor(context, indicator.borderColorResource(state))
    val stroke = context.resources.getDimensionPixelSize(R.dimen.settings_button_stroke)
    shape.setStroke(stroke, color)
    btn.invalidate()
  }

  private fun setStatusText(state: ConnectionState) {
    val status = statusTextProvider() ?: return
    when (state) {
      is ConnectionState.Connecting -> {
        status.text = context.getString(R.string.connection_status_connecting)
        status.visibility = View.VISIBLE
      }
      // Connected -> no pill: the video stream / gear border conveys the state, and the
      // centered text would sit over the video.
      is ConnectionState.Connected -> status.visibility = View.GONE
      is ConnectionState.Disconnected ->
          // No configured target (e.g. a video-only device): no "tap to connect" affordance
          // with nothing to connect to. The red gear + video placeholder already signal it.
          if (targetConfiguredProvider()) {
            status.text = context.getString(R.string.connection_status_disconnected)
            status.visibility = View.VISIBLE
          } else {
            status.visibility = View.GONE
          }
    }
  }

  fun error(message: String) {
    val root = rootViewProvider() ?: return
    Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
  }
}
