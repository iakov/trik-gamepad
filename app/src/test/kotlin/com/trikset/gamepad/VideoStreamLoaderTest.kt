package com.trikset.gamepad

import com.trikset.gamepad.mjpeg.MjpegView
import java.net.URL
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class VideoStreamLoaderTest {

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
}
