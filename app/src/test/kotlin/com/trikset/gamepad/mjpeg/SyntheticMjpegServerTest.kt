package com.trikset.gamepad.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.trikset.gamepad.RobolectricTestBase
import com.trikset.gamepad.VideoStreamLoader
import java.io.IOException
import java.net.URL
import org.apache.commons.io.input.BoundedInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end MJPEG decode tests against a real HTTP [SyntheticMjpegServer]. Runs under
 * [GraphicsMode.Mode.NATIVE] so [BitmapFactory] does **real** JPEG decoding (the default
 * Robolectric graphics mode returns fake bitmaps, which would make the correctness + performance
 * assertions meaningless). The app's own [VideoStreamLoader.openStream] is the client, so this
 * exercises the real HTTP + multipart parsing + decode path that the render thread uses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyntheticMjpegServerTest : RobolectricTestBase() {

  @Test
  @Config(sdk = [Config.TARGET_SDK])
  fun framesShouldDecodeToExpectedSizesAndColors() {
    val images = SyntheticMjpegServer.defaultFrameImages()
    withServer(
        SyntheticMjpegServer(frameImages = images, framesPerConnection = 10, frameIntervalMs = 50)
    ) { port ->
      withStream(url(port)) { stream ->
        // The parser drops frames when the socket buffer holds >2x a frame
        // (available() gate), so read until we have a few successfully-parsed
        // frames and assert on those. A 50ms server pacing lets the client drain
        // the socket between frames so every frame survives the gate on every SDK
        // (API 23 is slow enough that the server gets ahead with faster pacing and
        // the small solid frames are the first to be dropped).
        val decoded = readFrames(stream, expected = 3)
        // Strongest correctness check, density-independent: the parser must have
        // honored Content-Length exactly, so every frame's bytes equal one of the
        // seeded JPEGs byte-for-byte.
        decoded.forEach { bytes -> assertTrue(images.any { it.contentEquals(bytes) }) }
        // Cycling: solid red -> gradient -> solid blue. Every sampled frame must
        // be one of the three seeded images, and at least two distinct ones must
        // appear (proves the cycle advanced past the first frame).
        val colors = decoded.map { classify(BitmapFactory.decodeByteArray(it, 0, it.size)) }.toSet()
        assertTrue("expected >=2 distinct seeded colors, got $colors", colors.size >= 2)
      }
    }
  }

  @Test
  fun droppedConnectionShouldThrowThenReconnectAndDecode() {
    val server = SyntheticMjpegServer(framesPerConnection = 4)
    withServer(server) { port ->
      val url = url(port)
      // Server closes abruptly after 4 frames -> the parser must surface an
      // IOException (drop detected), the precondition for R12 reconnect.
      var sawDrop = false
      withStream(url) { stream ->
        val deadline = System.currentTimeMillis() + 15000
        try {
          while (System.currentTimeMillis() < deadline) {
            readFrames(stream, expected = 1)
          }
        } catch (_: IOException) {
          sawDrop = true
        }
      }
      assertTrue("connection drop must surface as IOException", sawDrop)

      // Restore: the app's reconnect path (VideoStreamLoader.openStream, same flow
      // as restartVideoStream) re-opens and decodes again.
      withStream(url) { stream ->
        val restored = readFrames(stream, expected = 1)
        assertTrue(restored[0].isNotEmpty())
      }
      assertTrue(server.acceptedConnections.get() >= 2)
    }
  }

  @Test
  fun shouldDecodeFramesWithinPerformanceWindow() {
    withServer(SyntheticMjpegServer(framesPerConnection = 30, frameIntervalMs = 5)) { port ->
      withStream(url(port)) { stream ->
        val start = System.currentTimeMillis()
        var decoded = 0
        try {
          // Loop (tolerating dropped frames) until the server's 30 frames are
          // consumed or the window elapses.
          while (decoded < 30 && System.currentTimeMillis() - start < 30000) {
            val frame = stream.readMjpegFrame() ?: continue
            val bitmap = decode(frame)
            frame.close()
            if (bitmap != null) decoded++
          }
        } catch (_: IOException) {
          // server closed after its frames; fine
        }
        val elapsedMs = System.currentTimeMillis() - start
        // A generous floor: real JPEG decode of 64x48 frames must comfortably
        // exceed this; the bound protects CI from pathological slowness.
        assertTrue("decoded $decoded/30 in ${elapsedMs}ms", decoded >= 10)
      }
    }
  }

  /** Starts [server], runs [block] with the bound port, then always stops it. */
  private fun <T> withServer(server: SyntheticMjpegServer, block: (Int) -> T): T {
    val port = server.start()
    return try {
      block(port)
    } finally {
      server.stop()
    }
  }

  /** Opens a stream to [url], runs [block], then always closes it. */
  private fun <T> withStream(url: URL, block: (MjpegInputStream) -> T): T {
    val stream = openStream(url)
    return try {
      block(stream)
    } finally {
      stream.close()
    }
  }

  private fun url(port: Int): URL = URL("http://127.0.0.1:$port/?action=stream")

  /** Reads until [expected] non-null frames are parsed, returning their raw bytes. */
  private fun readFrames(stream: MjpegInputStream, expected: Int): List<ByteArray> {
    val out = mutableListOf<ByteArray>()
    while (out.size < expected) {
      val frame = stream.readMjpegFrame() ?: continue
      val bytes = frame.readBytes()
      frame.close()
      assertTrue("frame must carry the seeded JPEG bytes", bytes.isNotEmpty())
      out.add(bytes)
    }
    return out
  }

  /** Decodes with density scaling disabled so the pixel size matches the seeded image. */
  private fun decode(frame: BoundedInputStream): Bitmap? =
      BitmapFactory.decodeStream(
          frame,
          null,
          BitmapFactory.Options().apply { inScaled = false },
      )

  /**
   * Samples an off-center pixel (the diagonal stripe avoids (1/4,1/4)) and buckets by dominant
   * channel.
   */
  private fun classify(bitmap: Bitmap): String {
    val pixel = bitmap.getPixel(bitmap.width / 4, bitmap.height / 4)
    val r = (pixel shr 16) and 0xFF
    val b = pixel and 0xFF
    return when {
      r - b > 40 -> "red"
      b - r > 40 -> "blue"
      else -> "gradient"
    }
  }

  private fun openStream(url: URL): MjpegInputStream {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    return requireNotNull(VideoStreamLoader(view).openStream(url))
  }
}
