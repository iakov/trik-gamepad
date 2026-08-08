package com.trikset.gamepad.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Synthetic MJPEG-over-HTTP server for the unit suite (Campaign 4). Binds an **ephemeral** port
 * (each of the 3 parallel unit-test JVMs gets its own), serves `multipart/x-mixed-replace` frames
 * generated from a small cycling set of JPEG images (solid color → gradient → second color), and
 * can **drop the connection after N frames** before resuming the accept loop — emulating a real
 * robot stream dying and coming back (R12 reconnect-on-error).
 *
 * The test MUST run under `@GraphicsMode(NATIVE)` so [Bitmap.compress] here and
 * [BitmapFactory.decodeStream] on the client side do real JPEG work instead of fake bitmaps.
 */
class SyntheticMjpegServer(
    /** JPEG-encoded frames to cycle; default: solid red, vertical gradient, solid blue. */
    private val frameImages: List<ByteArray> = defaultFrameImages(),
    /** Frames served per connection before an abrupt close. */
    private val framesPerConnection: Int = 4,
    /** Pause between frames so the client's available()-based parser keeps up. */
    private val frameIntervalMs: Long = 20,
) : Closeable {
  val servedFrames = AtomicInteger(0)
  val acceptedConnections = AtomicInteger(0)

  private var serverSocket: ServerSocket? = null
  private var acceptThread: Thread? = null
  @Volatile private var running = false

  /** Binds and starts accepting; returns the ephemeral port. */
  fun start(): Int {
    serverSocket = ServerSocket(0)
    running = true
    val socket = requireNotNull(serverSocket)
    acceptThread =
        Thread {
              while (running) {
                try {
                  val client = socket.accept()
                  acceptedConnections.incrementAndGet()
                  Thread { serveConnection(client) }.start()
                } catch (_: IOException) {
                  // socket closed on stop(); loop exits below.
                }
              }
            }
            .apply { isDaemon = true }
    acceptThread?.start()
    return socket.localPort
  }

  fun stop() {
    running = false
    try {
      serverSocket?.close()
    } catch (_: IOException) {
      // already closed
    }
    acceptThread?.interrupt()
  }

  override fun close() {
    stop()
  }

  // Client-disconnect is the expected way this loop ends; the exception carries nothing actionable.
  @Suppress("SwallowedException")
  private fun serveConnection(client: Socket) {
    try {
      client.use {
        // Consume the HTTP request headers so the client's write doesn't block.
        readRequestHeaders(it.getInputStream())
        val out = BufferedOutputStream(it.getOutputStream())
        writeResponseHeaders(out)
        out.flush()
        for (i in 0 until framesPerConnection) {
          if (!running) return
          val jpeg = frameImages[i % frameImages.size]
          writeFrame(out, jpeg)
          out.flush()
          servedFrames.incrementAndGet()
          if (frameIntervalMs > 0) {
            Thread.sleep(frameIntervalMs)
          }
        }
        // Abrupt drop: close the socket (no trailer) so the client sees EOF/IOException.
      }
    } catch (e: IOException) {
      // Client went away mid-frame; drop the connection as usual.
    } catch (e: InterruptedException) {
      // server stopping
    }
  }

  private fun readRequestHeaders(input: java.io.InputStream) {
    // Consume until the CRLF CRLF that ends the request head.
    val crlfCrlf =
        byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
    var matched = 0
    while (matched < crlfCrlf.size) {
      val b = input.read()
      if (b < 0) return
      if (b.toByte() == crlfCrlf[matched]) {
        matched++
      } else {
        matched = if (b == '\r'.code) 1 else 0
      }
    }
  }

  private fun writeResponseHeaders(out: OutputStream) {
    val headers =
        ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=--trikgamepad\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n" +
                "\r\n")
            .toByteArray(Charsets.US_ASCII)
    out.write(headers)
  }

  private fun writeFrame(out: OutputStream, jpeg: ByteArray) {
    val boundary = "--trikgamepad\r\n".toByteArray(Charsets.US_ASCII)
    val contentLength =
        "Content-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
    out.write(boundary)
    out.write(contentLength)
    out.write(jpeg)
  }

  companion object {
    /** Encodes [width]x[height] solid/gradient test images once at startup. */
    fun encodeJpeg(
        painter: (Canvas, Paint) -> Unit,
        width: Int,
        height: Int,
        quality: Int = 85,
    ): ByteArray {
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      val paint = Paint()
      painter(canvas, paint)
      val out = java.io.ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
      bitmap.recycle()
      return out.toByteArray()
    }

    /**
     * Solid red / vertical gradient / solid blue — comparable JPEG sizes so the parser's 2x gate
     * does not systematically drop the small solid frames on slower SDKs.
     */
    fun defaultFrameImages(
        width: Int = 64,
        height: Int = 48,
        quality: Int = 95,
    ): List<ByteArray> {
      fun diagonalStripe(color: Int) =
          encodeJpeg(
              { canvas, paint ->
                paint.color = color
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                // A thin lighter diagonal adds comparable JPEG entropy to the gradient
                // frame, keeping the three seeded images close in encoded size.
                paint.color = Color.WHITE
                paint.strokeWidth = 4f
                canvas.drawLine(0f, height.toFloat(), width.toFloat(), 0f, paint)
              },
              width,
              height,
              quality,
          )
      return listOf(
          diagonalStripe(Color.RED),
          encodeJpeg(
              { canvas, paint ->
                paint.shader =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        height.toFloat(),
                        Color.WHITE,
                        Color.BLACK,
                        Shader.TileMode.CLAMP,
                    )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
              },
              width,
              height,
              quality,
          ),
          diagonalStripe(Color.BLUE),
      )
    }
  }
}
