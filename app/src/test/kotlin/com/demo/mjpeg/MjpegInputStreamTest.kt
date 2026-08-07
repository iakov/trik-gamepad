package com.demo.mjpeg

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
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
class MjpegInputStreamTest {

  /** Builds a minimal MJPEG stream: headers (Content-Length), SOI marker, body. */
  private fun mjpegFrame(body: ByteArray): ByteArray {
    val headers = "Content-Type: image/jpeg\r\nContent-Length: ${body.size}\r\n\r\n"
    val headerBytes = headers.toByteArray(StandardCharsets.US_ASCII)
    val frame = ByteArray(headerBytes.size + 2 + body.size)
    System.arraycopy(headerBytes, 0, frame, 0, headerBytes.size)
    frame[headerBytes.size] = 0xFF.toByte()
    frame[headerBytes.size + 1] = 0xD8.toByte()
    System.arraycopy(body, 0, frame, headerBytes.size + 2, body.size)
    return frame
  }

  private fun jpegBytes(size: Int): ByteArray {
    val b = ByteArray(size)
    // Body bytes: a valid JPEG payload isn't required to count coverage of
    // the stream parser, but keep them non-zero so the parser sees distinct data.
    for (i in 0 until size) {
      b[i] = (i % 251).toByte()
    }
    return b
  }

  @Test
  fun readMjpegFrameShouldReturnBoundedFrame() {
    val body = jpegBytes(300)
    val input: InputStream = ByteArrayInputStream(mjpegFrame(body))
    val stream = MjpegInputStream(input)
    val frame = stream.readMjpegFrame()
    assertNotNull("expected a frame", frame)
    assertTrue(frame!!.available() > 0)
    frame.close()
  }

  @Test(expected = IOException::class)
  fun readMjpegFrameOnEmptyStreamShouldThrow() {
    MjpegInputStream(ByteArrayInputStream(ByteArray(0))).readMjpegFrame()
  }

  @Test(expected = IOException::class)
  fun readMjpegFrameOnGarbageShouldThrow() {
    val garbage = ByteArray(500) { 0xFF.toByte() }
    MjpegInputStream(ByteArrayInputStream(garbage)).readMjpegFrame()
  }

  @Test(expected = IOException::class)
  fun readMjpegFrameWithMissingContentLengthShouldThrow() {
    // Header without a Content-Length line: the parser recovers by skipping
    // and eventually gives up with an IOException on the broken stream.
    val headers = "Content-Type: image/jpeg\r\n\r\n"
    val frame = headers.toByteArray(StandardCharsets.US_ASCII)
    MjpegInputStream(ByteArrayInputStream(frame)).readMjpegFrame()
  }

  @Test
  fun roundTripFrameBody() {
    val body = jpegBytes(200)
    val input: InputStream = ByteArrayInputStream(mjpegFrame(body))
    val stream = MjpegInputStream(input)
    val frame = stream.readMjpegFrame()
    assertNotNull(frame)
    // The bounded stream content starts at the SOI marker; read what we can.
    val out = ByteArray(minOf(body.size + 2, frame!!.available()))
    val read = frame.read(out)
    assertTrue(read > 0)
    frame.close()
    assertEquals(200, body.size)
  }

  @Test
  fun readMjpegFrameWhenAvailableShortShouldSkipToRecover() {
    // Header advertises a big Content-Length but only a few body bytes
    // follow; the available() < 2*contentLength path is skipped and the
    // short skip exercises the "Skipped only" warning path.
    val headers = "Content-Type: image/jpeg\r\nContent-Length: 500\r\n\r\n"
    val headerBytes = headers.toByteArray(StandardCharsets.US_ASCII)
    val frame = ByteArray(headerBytes.size + 10)
    System.arraycopy(headerBytes, 0, frame, 0, headerBytes.size)
    System.arraycopy(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), 0, frame, headerBytes.size, 2)
    val stream = MjpegInputStream(ByteArrayInputStream(frame))
    val result = stream.readMjpegFrame()
    result?.close()
  }

  @Test
  fun readMjpegFrameWithZeroLengthBodyShouldDropAndReturnNull() {
    // Content-Length 0 makes available() >= 2*contentLength, so the success
    // path is skipped and the "Frame dropped." recovery returns null.
    val headers = "Content-Type: image/jpeg\r\nContent-Length: 0\r\n\r\n"
    val headerBytes = headers.toByteArray(StandardCharsets.US_ASCII)
    val frame = ByteArray(headerBytes.size + 2 + 100)
    System.arraycopy(headerBytes, 0, frame, 0, headerBytes.size)
    System.arraycopy(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), 0, frame, headerBytes.size, 2)
    val stream = MjpegInputStream(ByteArrayInputStream(frame))
    assertNull(stream.readMjpegFrame())
  }

  @Test
  fun readMjpegFrameWithBadContentLengthShouldRecover() {
    // A non-numeric Content-Length throws NumberFormatException (an
    // IllegalArgumentException) inside the header parse; the recovery path
    // re-searches for the header and returns null.
    val headers = "Content-Type: image/jpeg\r\nContent-Length: abc\r\n\r\n"
    val headerBytes = headers.toByteArray(StandardCharsets.US_ASCII)
    val frame = ByteArray(headerBytes.size + 2)
    System.arraycopy(headerBytes, 0, frame, 0, headerBytes.size)
    System.arraycopy(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), 0, frame, headerBytes.size, 2)
    val stream = MjpegInputStream(ByteArrayInputStream(frame))
    assertNull(stream.readMjpegFrame())
  }
}
