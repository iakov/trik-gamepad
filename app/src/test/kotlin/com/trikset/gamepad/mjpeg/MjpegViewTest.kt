package com.trikset.gamepad.mjpeg

import java.io.ByteArrayInputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class MjpegViewTest {

  @Test
  fun setSourceAndStartStopPlaybackShouldNotCrash() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.setSource(MjpegInputStream(ByteArrayInputStream(ByteArray(0))))
    // With an empty stream the render loop hits EOF quickly; stop it.
    view.startPlayback()
    view.stopPlayback()
  }

  @Test
  fun stopPlaybackWhenNotRunningShouldBeNoOp() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.stopPlayback()
  }

  @Test
  fun setSourceNullThenStartPlaybackShouldBeNoOp() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.setSource(null)
    view.startPlayback()
  }

  @Test
  fun surfaceCallbacksShouldNotThrow() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val holder = view.holder
    view.surfaceCreated(holder)
    view.surfaceChanged(holder, 0, 640, 480)
    // surfaceDestroyed stops playback; safe.
    view.surfaceDestroyed(holder)
  }

  @Test
  fun streamErrorListenerShouldBeSettable() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.setOnStreamErrorListener {}
    view.setOnStreamErrorListener(null)
  }

  @Test
  fun constructorsShouldCreateView() {
    val viaContext = MjpegView(RuntimeEnvironment.getApplication())
    val viaAttrs = MjpegView(RuntimeEnvironment.getApplication(), null)
    assertNotNull(viaContext)
    assertNotNull(viaAttrs)
    assertNull(viaContext.tag)
  }
}
