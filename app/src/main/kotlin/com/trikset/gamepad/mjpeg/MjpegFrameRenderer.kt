package com.trikset.gamepad.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.trikset.gamepad.diagnostics.AppLog
import java.io.InputStream
import java.util.Locale
import org.apache.commons.io.input.BoundedInputStream

private const val FRAME_TEMP_STORAGE_BYTES = 100000
private const val FPS_WINDOW_MS = 5000

private fun defaultMjpegDecoder(stream: InputStream, opts: BitmapFactory.Options): Bitmap? =
    BitmapFactory.decodeStream(stream, null, opts)

/**
 * Device-independent render logic for the MJPEG player: decodes a JPEG frame, computes the
 * center-crop destination rectangle (the frame covers the display, cropping any aspect mismatch
 * from the center) and draws it with the FPS overlay. Kept separate from [MjpegView]'s render
 * thread so it is testable under Robolectric (inject a decoder and a plain [Canvas]); the renderer
 * owns the reusable bitmap so decoding can reuse it across frames.
 */
class MjpegFrameRenderer(
    private val decoder: (InputStream, BitmapFactory.Options) -> Bitmap? = ::defaultMjpegDecoder,
    private val tempStorage: ByteArray = ByteArray(FRAME_TEMP_STORAGE_BYTES),
) {

  private var bitmap: Bitmap? = null
  private var frameCounter = 0
  private var startTimeMs = 0L
  private var fpsString = ""

  private companion object {
    const val TAG = "MjpegFrameRenderer"
    const val MILLIS_PER_SECOND = 1000.0f
  }

  /**
   * Center-crops a [bitmapWidth]x[bitmapHeight] frame to cover the display area (a camera feed
   * fills the whole screen edge-to-edge; the crop removes the aspect mismatch from the center). The
   * returned [Rect] may sit partially outside the display — the canvas clips the overflow.
   */
  fun destRect(bitmapWidth: Int, bitmapHeight: Int, dispWidth: Int, dispHeight: Int): Rect {
    val scale = maxOf(dispWidth.toFloat() / bitmapWidth, dispHeight.toFloat() / bitmapHeight)
    val bmw = (bitmapWidth * scale).toInt()
    val bmh = (bitmapHeight * scale).toInt()
    val tempX = dispWidth / 2 - bmw / 2
    val tempY = dispHeight / 2 - bmh / 2
    return Rect(tempX, tempY, bmw + tempX, bmh + tempY)
  }

  fun onRenderStarted(startTimeMs: Long) {
    this.startTimeMs = startTimeMs
  }

  /**
   * Decodes [frame] into the renderer's reusable bitmap and returns the destination rectangle for
   * the current display size, or null when the frame cannot be decoded.
   */
  @Suppress("SwallowedException") // a decode failure just means "skip this frame"
  fun extractFrame(frame: BoundedInputStream, dispWidth: Int, dispHeight: Int): Rect? {
    val opts =
        BitmapFactory.Options().apply {
          inBitmap = bitmap // reuse if possible
          inMutable = true
          inTempStorage = tempStorage
        }
    val decoded: Bitmap? =
        try {
          decoder(frame, opts)
        } catch (e: IllegalArgumentException) {
          null
        }
    if (decoded == null) return null

    val previous = bitmap
    if (previous != null && decoded !== previous) {
      // Was not reused
      previous.recycle()
      AppLog.v(TAG, "Bitmap was not reused, recycled.")
    }
    bitmap = decoded
    return destRect(decoded.width, decoded.height, dispWidth, dispHeight)
  }

  /** Draws the current bitmap (letterboxed) plus the FPS overlay; returns the fps string. */
  fun drawFrame(
      canvas: Canvas,
      destRect: Rect,
      dispWidth: Int,
      fpsTextPaint: Paint,
      showFps: Boolean = true,
  ): String {
    frameCounter++
    val now = System.currentTimeMillis()
    val elapsedMs = now - startTimeMs
    if (elapsedMs >= FPS_WINDOW_MS) {
      startTimeMs = now
      val fps = MILLIS_PER_SECOND * frameCounter / FPS_WINDOW_MS
      frameCounter = 0
      fpsString = String.format(Locale.getDefault(), "%.1f", fps)
    }

    canvas.drawColor(Color.BLACK)
    val current = bitmap
    if (current != null) {
      canvas.drawBitmap(current, null, destRect, null)
    }
    if (showFps) {
      canvas.drawText(fpsString, (dispWidth - 1).toFloat(), -fpsTextPaint.ascent(), fpsTextPaint)
    }
    return fpsString
  }
}
