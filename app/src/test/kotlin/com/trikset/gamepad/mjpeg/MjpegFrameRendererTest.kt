package com.trikset.gamepad.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.apache.commons.io.input.BoundedInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class MjpegFrameRendererTest {

  private fun bitmap(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

  private fun stubDecoder(result: Bitmap?): (InputStream, BitmapFactory.Options) -> Bitmap? =
      { _, _ ->
        result
      }

  private fun emptyFrame(): BoundedInputStream =
      BoundedInputStream.builder().setInputStream(ByteArrayInputStream(ByteArray(0))).get()

  @Test
  fun destRectCentersLandscapeBitmap() {
    val rect = MjpegFrameRenderer().destRect(640, 480, 320, 240)
    assertEquals(0, rect.left)
    assertEquals(0, rect.top)
    assertEquals(320, rect.width())
    assertEquals(240, rect.height())
  }

  @Test
  fun destRectLetterboxesPortraitBitmap() {
    // Portrait bitmap in a landscape display: height-limited, horizontally centered.
    // aspect 480/640 = 0.75 -> height 240, width 180, left (320-180)/2 = 70.
    val rect = MjpegFrameRenderer().destRect(480, 640, 320, 240)
    assertEquals(240, rect.height())
    assertEquals(180, rect.width())
    assertEquals(0, rect.top)
    assertEquals(70, rect.left)
  }

  @Test
  fun extractFrameReturnsDestRectForDecodedBitmap() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 50)))
    val rect = renderer.extractFrame(emptyFrame(), 320, 240)
    assertNotNull("expected a dest rect", rect)
    assertEquals(320, rect!!.width())
    assertEquals(160, rect.height())
  }

  @Test
  fun extractFrameReturnsNullWhenDecodeFails() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(null))
    assertNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun extractFrameReturnsNullWhenDecodeThrows() {
    val renderer =
        MjpegFrameRenderer(decoder = { _, _ -> throw IllegalArgumentException("bad frame") })
    assertNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun extractFrameRecyclesPreviousBitmapWhenNotReused() {
    var calls = 0
    val renderer =
        MjpegFrameRenderer(
            decoder = { _, _ ->
              calls++
              if (calls == 1) bitmap(100, 100) else bitmap(50, 50)
            }
        )
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    // Second frame decodes into a different bitmap; the old one must be recycled.
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun extractFrameSkipsRecycleWhenBitmapIsReused() {
    // The decoder returns the same bitmap instance both times -> the renderer
    // reuses it and skips the recycle branch.
    val reused = bitmap(100, 100)
    val renderer = MjpegFrameRenderer(decoder = { _, _ -> reused })
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
    assertNotNull(renderer.extractFrame(emptyFrame(), 320, 240))
  }

  @Test
  fun drawFrameDrawsBitmapAndFpsOverlay() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 100)))
    // Force the FPS window to elapse so the fps string is computed.
    renderer.onRenderStarted(System.currentTimeMillis() - 6000)
    val dest = renderer.extractFrame(emptyFrame(), 320, 240)
    assertNotNull(dest)
    val canvas = Canvas(bitmap(320, 240))
    val fps = renderer.drawFrame(canvas, dest!!, 320, Paint())
    assertTrue("expected a computed fps string, got '$fps'", fps.isNotEmpty())
  }

  @Test
  fun drawFrameWithinFpsWindowKeepsEmptyOverlay() {
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 100)))
    renderer.onRenderStarted(System.currentTimeMillis())
    val dest = renderer.extractFrame(emptyFrame(), 320, 240)
    assertNotNull(dest)
    val fps = renderer.drawFrame(Canvas(bitmap(320, 240)), dest!!, 320, Paint())
    assertEquals("", fps)
  }

  @Test
  fun drawFrameWithoutBitmapSkipsDrawing() {
    // Never extract a frame -> bitmap is null; drawFrame must only draw the
    // FPS text and not touch a null bitmap.
    val renderer = MjpegFrameRenderer(decoder = stubDecoder(bitmap(100, 100)))
    renderer.onRenderStarted(System.currentTimeMillis())
    val rect = renderer.destRect(100, 100, 320, 240)
    val fps = renderer.drawFrame(Canvas(bitmap(320, 240)), rect, 320, Paint())
    assertEquals("", fps)
  }
}
