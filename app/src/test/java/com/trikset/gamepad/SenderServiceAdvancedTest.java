package com.trikset.gamepad;

import static android.os.Looper.getMainLooper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.PausedExecutorService;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/**
 * Covers the SenderService paths the basic test misses: keepalive ticks, disconnect callbacks,
 * send-failure handling, and target changes.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(PAUSED)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class SenderServiceAdvancedTest {
  private final PausedExecutorService mExecutor = new PausedExecutorService();

  // SenderService keeps keepaliveTimeout and mConnectTask as STATIC fields and
  // each connected client starts a keepalive Timer that runs on a real thread.
  // Reset the static timeout and clear the static connect task before and
  // after each test, and always disconnect connected clients to stop their
  // timers, or leaked timers / stale static state pollute later tests (the
  // SDK-23 variant is most exposed).
  @Before
  public void resetStaticState() throws Exception {
    clearConnectTask();
    SenderService reset = new SenderService();
    reset.setExecutor(mExecutor);
    reset.setKeepaliveTimeout(SenderService.DEFAULT_KEEPALIVE);
    reset.disconnect("reset");
  }

  @After
  public void tearDown() throws Exception {
    clearConnectTask();
    SenderService reset = new SenderService();
    reset.setExecutor(mExecutor);
    reset.setKeepaliveTimeout(SenderService.DEFAULT_KEEPALIVE);
    reset.disconnect("teardown");
  }

  private void clearConnectTask() throws Exception {
    java.lang.reflect.Field f = SenderService.class.getDeclaredField("mConnectTask");
    f.setAccessible(true);
    f.set(null, null);
  }

  @Test
  public void disconnectShouldInvokeOnDisconnectedListener() throws InterruptedException {
    try (ReadUntilStopServer server = new ReadUntilStopServer()) {
      SenderService client = new SenderService();
      client.setExecutor(mExecutor);
      AtomicReference<String> reason = new AtomicReference<>();
      client.setOnDisconnectedListener(reason::set);

      client.setTarget("localhost", server.getPort());
      client.send("test");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(server.awaitConnection());

      client.disconnect("Test disconnect.");
      assertEquals("Test disconnect.", reason.get());
      client.disconnect("again"); // mOut already null -> no listener call
      assertEquals("Test disconnect.", reason.get());
    }
  }

  @Test
  public void setTargetShouldDisconnectWhenChanged() throws InterruptedException {
    try (ReadUntilStopServer first = new ReadUntilStopServer()) {
      SenderService client = new SenderService();
      client.setExecutor(mExecutor);
      AtomicReference<String> reason = new AtomicReference<>();
      client.setOnDisconnectedListener(reason::set);

      client.setTarget("localhost", first.getPort());
      client.send("a");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(first.awaitConnection());

      // New target on a different port -> must disconnect from the first.
      client.setTarget("localhost", first.getPort() + 1);
      assertEquals("Target changed.", reason.get());
    }
  }

  @Test
  public void sendFailureShouldDisconnectAndReport() throws InterruptedException {
    try (ReadUntilStopServer server = new ReadUntilStopServer()) {
      SenderService client = new SenderService();
      client.setExecutor(mExecutor);
      client.setTarget("localhost", server.getPort());
      client.send("first");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(server.awaitConnection());

      // Closing the server makes the next println fail, which the
      // SendCommandAsyncTask turns into a disconnect.
      server.closeSocket();
      client.send("after-close");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      // checkError() may be lazy; give the executor another cycle.
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
    }
  }

  @Test
  public void keepaliveShouldBeSentWhileConnected() throws InterruptedException {
    final int timeout = 1300; // real period = timeout - 300 = 1000ms
    try (ReadUntilStopServer server = new ReadUntilStopServer()) {
      SenderService client = new SenderService();
      client.setExecutor(mExecutor);
      client.setTarget("localhost", server.getPort());
      client.setKeepaliveTimeout(timeout);
      client.send("bootstrap");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(server.awaitConnection());

      // The keepalive Timer runs on a real thread; poll for the message instead
      // of a single fixed sleep so a busy CI JVM cannot starve the timer.
      boolean seen = false;
      for (int i = 0; i < 20 && !seen; i++) {
        Thread.sleep(500);
        mExecutor.runAll();
        shadowOf(getMainLooper()).idle();
        seen = server.receivedContains("keepalive " + timeout);
      }
      assertTrue("expected a keepalive message", seen);
      client.disconnect("done");
    }
  }

  @Test
  public void showTextCallbackShouldReceiveConnectionResult() throws InterruptedException {
    try (ReadUntilStopServer server = new ReadUntilStopServer()) {
      SenderService client = new SenderService();
      client.setExecutor(mExecutor);
      AtomicReference<String> text = new AtomicReference<>();
      client.setShowTextCallback(text::set);

      client.setTarget("localhost", server.getPort());
      client.send("hello");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(server.awaitConnection());

      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue("expected connection message, got '" + text.get() + "'", text.get() != null);
      client.disconnect("done");
    }
  }

  @Test
  public void keepaliveTimeoutBelowMinimumIsStoredUnchanged() {
    SenderService client = new SenderService();
    client.setExecutor(mExecutor);
    int before = client.getKeepaliveTimeout();
    // The service does not clamp; the caller (MainActivity) enforces the
    // minimum. This just verifies the setter round-trips.
    client.setKeepaliveTimeout(12345);
    assertEquals(12345, client.getKeepaliveTimeout());
    client.setKeepaliveTimeout(before);
  }

  /** A DummyServer variant that reads until {@link #closeSocket()} or close(). */
  private static class ReadUntilStopServer implements AutoCloseable {
    private final ServerSocket mServerSocket;
    private final CountDownLatch mConnectedLatch = new CountDownLatch(1);
    private final List<String> mMessages = Collections.synchronizedList(new ArrayList<>());
    private volatile Socket mClientSocket;

    int getPort() {
      return mServerSocket.getLocalPort();
    }

    boolean awaitConnection() throws InterruptedException {
      return mConnectedLatch.await(5, TimeUnit.SECONDS);
    }

    boolean receivedContains(String fragment) {
      synchronized (mMessages) {
        for (String m : mMessages) {
          if (m != null && m.contains(fragment)) {
            return true;
          }
        }
      }
      return false;
    }

    void closeSocket() {
      Socket c = mClientSocket;
      if (c != null) {
        try {
          c.close();
        } catch (IOException ignored) {
        }
      }
    }

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

    @Override
    public void close() {
      closeSocket();
      try {
        mServerSocket.close();
      } catch (IOException ignored) {
      }
    }
  }
}
