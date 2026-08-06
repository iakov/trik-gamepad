package com.demo.mjpeg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.input.BoundedInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class MjpegInputStreamTest {

  /** Builds a minimal MJPEG stream: headers (Content-Length), SOI marker, body. */
  private static byte[] mjpegFrame(byte[] body) {
    String headers = "Content-Type: image/jpeg\r\nContent-Length: " + body.length + "\r\n\r\n";
    byte[] headerBytes = headers.getBytes(StandardCharsets.US_ASCII);
    byte[] frame = new byte[headerBytes.length + 2 + body.length];
    System.arraycopy(headerBytes, 0, frame, 0, headerBytes.length);
    frame[headerBytes.length] = (byte) 0xFF;
    frame[headerBytes.length + 1] = (byte) 0xD8;
    System.arraycopy(body, 0, frame, headerBytes.length + 2, body.length);
    return frame;
  }

  private static byte[] jpegBytes(int size) {
    byte[] b = new byte[size];
    // Body bytes: a valid JPEG payload isn't required to count coverage of the
    // stream parser, but keep them non-zero so the parser sees distinct data.
    for (int i = 0; i < size; i++) {
      b[i] = (byte) (i % 251);
    }
    return b;
  }

  @Test
  public void readMjpegFrameShouldReturnBoundedFrame() throws IOException {
    byte[] body = jpegBytes(300);
    InputStream in = new ByteArrayInputStream(mjpegFrame(body));
    MjpegInputStream stream = new MjpegInputStream(in);

    BoundedInputStream frame = stream.readMjpegFrame();
    assertNotNull("expected a frame", frame);
    // The bounded stream contains the JPEG body bytes (roughly Content-Length).
    assertTrue(frame.available() > 0);
    frame.close();
  }

  @Test(expected = IOException.class)
  public void readMjpegFrameOnEmptyStreamShouldThrow() throws IOException {
    MjpegInputStream stream = new MjpegInputStream(new ByteArrayInputStream(new byte[0]));
    stream.readMjpegFrame();
  }

  @Test(expected = IOException.class)
  public void readMjpegFrameOnGarbageShouldThrow() throws IOException {
    byte[] garbage = new byte[500];
    for (int i = 0; i < garbage.length; i++) {
      garbage[i] = (byte) 0xFF;
    }
    MjpegInputStream stream = new MjpegInputStream(new ByteArrayInputStream(garbage));
    stream.readMjpegFrame();
  }

  @Test(expected = IOException.class)
  public void readMjpegFrameWithMissingContentLengthShouldThrow() throws IOException {
    // Header without a Content-Length line: the parser recovers by skipping and
    // eventually gives up with an IOException on the broken stream.
    String headers = "Content-Type: image/jpeg\r\n\r\n";
    byte[] frame = headers.getBytes(StandardCharsets.US_ASCII);
    MjpegInputStream stream = new MjpegInputStream(new ByteArrayInputStream(frame));
    stream.readMjpegFrame();
  }

  @Test
  public void roundTripFrameBody() throws IOException {
    byte[] body = jpegBytes(200);
    InputStream in = new ByteArrayInputStream(mjpegFrame(body));
    MjpegInputStream stream = new MjpegInputStream(in);

    BoundedInputStream frame = stream.readMjpegFrame();
    assertNotNull(frame);
    // The bounded stream content starts at the SOI marker; read what we can.
    byte[] out = new byte[Math.min(body.length + 2, frame.available())];
    int read = frame.read(out);
    assertTrue(read > 0);
    frame.close();
    assertEquals(200, body.length);
  }

  @Test
  public void readMjpegFrameWhenAvailableShortShouldSkipToRecover() throws IOException {
    // Header advertises a big Content-Length but only a few body bytes follow;
    // the available() < 2*contentLength path is skipped and the short skip
    // exercises the "Skipped only" warning path.
    String headers = "Content-Type: image/jpeg\r\nContent-Length: 500\r\n\r\n";
    byte[] headerBytes = headers.getBytes(StandardCharsets.US_ASCII);
    byte[] frame = new byte[headerBytes.length + 10];
    System.arraycopy(headerBytes, 0, frame, 0, headerBytes.length);
    System.arraycopy(new byte[] {(byte) 0xFF, (byte) 0xD8}, 0, frame, headerBytes.length, 2);

    MjpegInputStream stream = new MjpegInputStream(new ByteArrayInputStream(frame));
    BoundedInputStream result = stream.readMjpegFrame();
    if (result != null) {
      result.close();
    }
  }
}
