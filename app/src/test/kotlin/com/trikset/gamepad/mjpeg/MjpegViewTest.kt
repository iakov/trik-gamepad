package com.trikset.gamepad.mjpeg

import com.trikset.gamepad.RobolectricTestBase
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MjpegViewTest : RobolectricTestBase() {

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

  /**
   * Runs stopPlayback against a render thread blocked in a socket read and asserts the stream was
   * closed so the read unblocks.
   */
  private fun blockedReadCycle(view: MjpegView, client: Socket) {
    view.surfaceCreated(view.holder) // surfaceDone -> the render loop actually reads
    view.setSource(MjpegInputStream(client.getInputStream()))
    view.startPlayback()
    // Best-effort: give the render thread a moment to reach the blocking read.
    Thread.sleep(100)
    view.stopPlayback()
  }

  @Test
  fun stopPlaybackShouldCloseStreamToUnblockBlockedRead() {
    val server = ServerSocket(0)
    try {
      val client = Socket("127.0.0.1", server.localPort)
      try {
        val view = MjpegView(RuntimeEnvironment.getApplication())
        blockedReadCycle(view, client)
        assertTrue("stopPlayback must close the stream so a blocked read unblocks", client.isClosed)
      } finally {
        client.close()
      }
    } finally {
      server.close()
    }
  }

  @Test
  fun stopPlaybackShouldNotFireErrorListenerOnDeliberateStop() {
    val server = ServerSocket(0)
    try {
      val client = Socket("127.0.0.1", server.localPort)
      try {
        val view = MjpegView(RuntimeEnvironment.getApplication())
        var errors = 0
        view.setOnStreamErrorListener { errors++ }
        blockedReadCycle(view, client)
        assertEquals("deliberate stop must not trigger the reconnect listener", 0, errors)
      } finally {
        client.close()
      }
    } finally {
      server.close()
    }
  }
}
