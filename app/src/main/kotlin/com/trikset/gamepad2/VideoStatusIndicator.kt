package com.trikset.gamepad2

import androidx.annotation.ColorRes

/**
 * Maps the [VideoStatus] to the Type 1 HUD glyph color — the single source of truth for the
 * video-state tone of the robot-chip eye glyph (see DESIGN.md "Robot-target chip"). Pure so the
 * mapping is directly unit-testable, mirroring [ConnectionIndicator] for the control connection.
 *
 * Color semantics: Playing → green, Loading/Reconnecting → amber, Unavailable → red, Disabled (no
 * video URL) → muted gray. "Color is never the only signal": the chip's contentDescription
 * announces the video state too.
 */
class VideoStatusIndicator {

  @ColorRes
  fun colorResource(status: VideoStatus): Int =
      when (status) {
        VideoStatus.PLAYING -> R.color.greenlight
        VideoStatus.LOADING,
        VideoStatus.RECONNECTING -> R.color.amber
        VideoStatus.UNAVAILABLE -> R.color.red
        VideoStatus.DISABLED -> R.color.hud_disabled
      }
}
