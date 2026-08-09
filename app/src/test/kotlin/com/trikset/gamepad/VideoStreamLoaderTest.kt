package com.trikset.gamepad

import android.os.Handler
import android.os.Looper
import com.trikset.gamepad.mjpeg.MjpegView
import com.trikset.gamepad.mjpeg.SyntheticMjpegServer
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.PausedExecutorService

@RunWith(RobolectricTestRunner::class)
class VideoStreamLoaderTest : RobolectricTestBase() {

  @Test
  fun openStreamReturnsNullForNullUrl() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val loader = VideoStreamLoader(view)
    assertNull(loader.openStream(null))
  }

  @Test
  fun openStreamWithUnreachableHostReturnsNull() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val loader = VideoStreamLoader(view)
    // A definitely-unreachable address; the HttpURLConnection fails and the
    // IOException is caught -> null.
    val url = URL("http://127.0.0.1:1/nope")
    assertNull(loader.openStream(url))
  }

  @Test
  fun openStreamHttpsFallsBackToHttpUrlConnectionAndReturnsNullOnFailure() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val loader = VideoStreamLoader(view)
    // Non-http schemes keep the HttpURLConnection path (the raw-socket client is
    // http-only); an unreachable https endpoint fails inside HttpURLConnection
    // and the IOException is caught -> null.
    val url = URL("https://127.0.0.1:1/nope")
    assertNull(loader.openStream(url))
  }

  @Test
  fun loadWithNullUrlInvokesOnResultFalse() {
    loadAndAssertResult(PausedExecutorService(), null, listOf(false))
  }

  @Test
  fun loadWithUnreachableHostInvokesOnResultFalse() {
    loadAndAssertResult(PausedExecutorService(), URL("http://127.0.0.1:1/nope"), listOf(false))
  }

  @Test
  fun loadWithLiveServerInvokesOnResultTrue() {
    val server = SyntheticMjpegServer(framesPerConnection = 2, frameIntervalMs = 5)
    val port = server.start()
    try {
      val view =
          loadAndAssertResult(
              PausedExecutorService(),
              URL("http://127.0.0.1:$port/?action=stream"),
              listOf(true),
          )
      view.stopPlayback()
    } finally {
      server.stop()
    }
  }

  /** Loads [url] through a fresh loader and asserts the collected onResult values. */
  private fun loadAndAssertResult(
      executor: PausedExecutorService,
      url: URL?,
      expected: List<Boolean>,
  ): MjpegView {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val loader = VideoStreamLoader(view, executor, Handler(Looper.getMainLooper()))
    val results = mutableListOf<Boolean>()
    loader.load(url) { results.add(it) }
    executor.runAll()
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(expected, results)
    return view
  }
}
