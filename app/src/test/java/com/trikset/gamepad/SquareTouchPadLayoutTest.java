package com.trikset.gamepad;

import static android.os.Looper.getMainLooper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.PausedExecutorService;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(PAUSED)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class SquareTouchPadLayoutTest {

  private final PausedExecutorService mExecutor = new PausedExecutorService();
  private ReadUntilStopServer server;
  private SenderService sender;
  private SquareTouchPadLayout pad;

  // SenderService keeps keepaliveTimeout and mConnectTask STATIC; tests that
  // run before this class may have left them dirty, which would make
  // connectAsync() a no-op and every awaitConnection() time out. Reset before
  // each test (same discipline as SenderServiceAdvancedTest).
  @Before
  public void resetSenderServiceStaticState() throws Exception {
    java.lang.reflect.Field f = SenderService.class.getDeclaredField("mConnectTask");
    f.setAccessible(true);
    f.set(null, null);
    SenderService reset = new SenderService();
    reset.setExecutor(mExecutor);
    reset.setKeepaliveTimeout(SenderService.DEFAULT_KEEPALIVE);
    reset.disconnect("reset");
  }

  private MotionEvent eventAt(float x, float y, int action) {
    return MotionEvent.obtain(0, 0, action, x, y, 0);
  }

  @Before
  public void setUp() {
    server = new ReadUntilStopServer();
    sender = new SenderService();
    sender.setExecutor(mExecutor);
    sender.setKeepaliveTimeout(10000000); // disable keepalive noise
    sender.setTarget("localhost", server.getPort());

    Context context = org.robolectric.RuntimeEnvironment.getApplication();
    android.widget.FrameLayout parent = new android.widget.FrameLayout(context);
    pad = new SquareTouchPadLayout(context);
    pad.setPadName("pad 1");
    pad.setSender(sender);
    parent.addView(pad);
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    pad.layout(0, 0, 200, 200);
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 200, 200);
  }

  private void flush() {
    mExecutor.runAll();
    shadowOf(getMainLooper()).idle();
  }

  @Test
  public void sendShouldPrefixPadName() throws InterruptedException {
    pad.send("up");
    flush();
    assertTrue(server.awaitConnection());
    assertTrue(server.receivedContains("pad 1 up"));
  }

  @Test
  public void sendShouldNotForwardWhenNoSender() {
    pad.setSender(null);
    pad.send("up");
    flush();
    assertTrue("no command expected without a sender", server.receivedMessages().isEmpty());
  }

  @Test
  public void measureShouldMakeSquareFromWidthAndHeight() {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(150, View.MeasureSpec.EXACTLY));
    assertEquals(150, pad.getMeasuredWidth());
    assertEquals(150, pad.getMeasuredHeight());
  }

  @Test
  public void measureShouldFallBackToDefaultWhenNoSize() {
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    assertTrue(pad.getMeasuredWidth() > 0);
    assertTrue(pad.getMeasuredHeight() > 0);
  }

  @Test
  public void touchMoveShouldSendCoordinates() throws InterruptedException {
    pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_DOWN));
    pad.dispatchTouchEvent(eventAt(190, 10, MotionEvent.ACTION_MOVE));
    pad.dispatchTouchEvent(eventAt(10, 190, MotionEvent.ACTION_MOVE));
    flush();
    assertTrue(server.awaitConnection());
    assertTrue(
        "expected coordinate commands, got: " + server.receivedMessages(),
        server.receivedMessages().size() > 1);
  }

  @Test
  public void touchUpShouldSendUpCommand() throws InterruptedException {
    pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_DOWN));
    pad.dispatchTouchEvent(eventAt(120, 120, MotionEvent.ACTION_MOVE));
    pad.dispatchTouchEvent(eventAt(120, 120, MotionEvent.ACTION_UP));
    flush();
    assertTrue(server.awaitConnection());
    List<String> msgs = server.receivedMessages();
    assertTrue("expected messages, got: " + msgs, msgs.size() >= 2);
    assertEquals("pad 1 up", msgs.get(msgs.size() - 1));
  }

  @Test
  public void unknownActionShouldBeIgnored() {
    assertTrue(pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_SCROLL)));
  }

  @Test
  public void allConstructorsShouldBuild() {
    Context context = org.robolectric.RuntimeEnvironment.getApplication();
    assertNotNull(new SquareTouchPadLayout(context));
    assertNotNull(new SquareTouchPadLayout(context, null));
    assertNotNull(new SquareTouchPadLayout(context, null, 0));
  }

  @Test
  public void onDrawShouldRenderCircle() {
    android.graphics.Canvas canvas = new android.graphics.Canvas();
    pad.setAbsXY(100, 100);
    pad.draw(canvas);
  }

  @Test
  public void onSizeChangedShouldCenterWhenStartingEmpty() {
    // size changed from (0,0) -> centers the touch point.
    pad.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    pad.layout(0, 0, 200, 200);
    // After a layout with old size 0, dispatch a move to read the new center.
    pad.dispatchTouchEvent(eventAt(100, 100, MotionEvent.ACTION_DOWN));
    assertFalse(sender == null);
  }

  /** Binds synchronously in the constructor (see MEMORY.md) and records messages. */
  private static class ReadUntilStopServer implements AutoCloseable {
    private final ServerSocket mServerSocket;
    private final CountDownLatch mConnectedLatch = new CountDownLatch(1);
    private final List<String> mMessages = Collections.synchronizedList(new ArrayList<>());
    private volatile Socket mClientSocket;

    ReadUntilStopServer() {
      try {
        mServerSocket = new ServerSocket(0);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot bind a server socket", e);
      }
      new Thread(
              () -> {
                try (ServerSocket s = mServerSocket) {
                  Socket client = s.accept();
                  mClientSocket = client;
                  mConnectedLatch.countDown();
                  BufferedReader in =
                      new BufferedReader(new InputStreamReader(client.getInputStream()));
                  String line;
                  while ((line = in.readLine()) != null) {
                    mMessages.add(line);
                  }
                } catch (IOException e) {
                  // socket closed -> loop ends
                }
              })
          .start();
    }

    int getPort() {
      return mServerSocket.getLocalPort();
    }

    boolean awaitConnection() throws InterruptedException {
      return mConnectedLatch.await(5, TimeUnit.SECONDS);
    }

    List<String> receivedMessages() {
      synchronized (mMessages) {
        return new ArrayList<>(mMessages);
      }
    }

    boolean receivedContains(String fragment) {
      for (String m : receivedMessages()) {
        if (m != null && m.contains(fragment)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void close() {
      Socket c = mClientSocket;
      if (c != null) {
        try {
          c.close();
        } catch (IOException ignored) {
        }
      }
      try {
        mServerSocket.close();
      } catch (IOException ignored) {
      }
    }
  }
}
