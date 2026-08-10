package com.trikset.gamepad

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.Button
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

/**
 * Owns the connection-state feedback chrome: recolors the settings-button border by
 * [ConnectionState] and shows Material [Snackbar]s for connection errors. The views are injected as
 * providers so the object can be constructed before the view hierarchy exists (like
 * [SystemUiController]). Recoloring the existing gear border adds no new touch surface.
 */
class ConnectionFeedback(
    private val context: Context,
    private val settingsButtonProvider: () -> Button?,
    private val rootViewProvider: () -> View?,
) {
  private val indicator = ConnectionIndicator()

  fun update(state: ConnectionState) {
    val btn = settingsButtonProvider() ?: return
    val layerDrawable = btn.background as? LayerDrawable ?: return
    val shape =
        layerDrawable.findDrawableByLayerId(R.id.settingsButtonBg) as? GradientDrawable ?: return
    val color = ContextCompat.getColor(context, indicator.borderColorResource(state))
    val stroke = context.resources.getDimensionPixelSize(R.dimen.settings_button_stroke)
    shape.setStroke(stroke, color)
    btn.invalidate()
  }

  fun error(message: String) {
    val root = rootViewProvider() ?: return
    Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
  }
}
