package com.trikset.gamepad

import androidx.annotation.ColorRes

/**
 * Maps the TCP [ConnectionState] to the gear-button border color (Campaign 9 A). Pure so the
 * mapping is directly unit-testable; [MainActivity] applies the color to the
 * `@+id/settingsButtonBg` stroke at runtime without adding a new touch surface.
 */
class ConnectionIndicator {

  @ColorRes
  fun borderColorResource(state: ConnectionState): Int =
      when (state) {
        is ConnectionState.Connecting -> R.color.amber
        is ConnectionState.Connected -> R.color.greendark
        is ConnectionState.Disconnected -> R.color.red
      }
}
