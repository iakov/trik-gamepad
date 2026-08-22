package com.trikset.gamepad2.video

interface VideoPlayer {
  val isPlaying: Boolean
  var showFps: Boolean

  fun play(url: String?)

  fun stop()

  fun setOnStreamErrorListener(listener: (() -> Unit)?)

  fun setOnFirstFrameListener(listener: (() -> Unit)?)

  var onPlayResult: ((Boolean) -> Unit)?

  fun release()
}
