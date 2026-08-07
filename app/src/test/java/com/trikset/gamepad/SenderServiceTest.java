package com.trikset.gamepad;

import static android.os.Looper.getMainLooper;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.PausedExecutorService;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(PAUSED)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class SenderServiceTest {
  private final PausedExecutorService mExecutor = new PausedExecutorService();
  private SenderService client;

  // A connected client keeps a real keepalive Timer thread alive that fires
  // into the STATIC SenderService.mExecutor (set to this test's executor by
  // setExecutor). Disconnecting every client in @After stops the Timer, so no
  // leaked keepalive task can land on a later test's executor (this is what
  // made SquareTouchPadLayoutTest.sendShouldNotForwardWhenNoSender flake on CI).
  @After
  public void tearDown() {
    if (client != null) {
      client.disconnect("tearDown");
    }
  }

  @Test
  public void senderServiceShouldConnectToServerSuccessfullyAfterSendingCommand()
      throws InterruptedException {
    try (DummyServer server = new DummyServer(1)) {
      client = new SenderService();
      client.setExecutor(mExecutor);
      client.setTarget(DummyServer.IP, server.getPort());
      client.send("");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(server.awaitConnection());
      assertTrue(server.isConnected());
    }
  }

  @Test
  public void senderServiceShouldSendSingleCommandCorrectly() throws InterruptedException {
    try (DummyServer server = new DummyServer(1)) {
      client = new SenderService();
      client.setExecutor(mExecutor);
      client.setTarget(DummyServer.IP, server.getPort());
      client.setKeepaliveTimeout(10000000); // to disable keep-alive messages
      client.send("Test; check");
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(server.awaitCommands());
      assertEquals("Test; check", server.getLastCommand());
    }
  }

  @Test
  public void senderServiceShouldSendMultipleCommandsCorrectly() throws InterruptedException {
    try (DummyServer server = new DummyServer(5)) {
      client = new SenderService();
      client.setExecutor(mExecutor);
      client.setTarget(DummyServer.IP, server.getPort());
      client.setKeepaliveTimeout(10000000); // to disable keep-alive messages

      for (int i = 0; i < 5; ++i) {
        client.send(String.format("%d checking", i));
      }
      mExecutor.runAll();
      shadowOf(getMainLooper()).idle();
      assertTrue(server.awaitCommands());
      assertEquals("4 checking", server.getLastCommand());
    }
  }

  @Test
  public void setTargetShouldSetServerSuccessfully() {
    SenderService client = new SenderService();
    client.setExecutor(mExecutor);
    client.setTarget("someaddr-test", 0);
    assertEquals("someaddr-test", client.getHostAddr());
  }

  @Test
  public void senderServiceShouldReturnCorrectKeepaliveTimeout() {
    SenderService client = new SenderService();
    client.setExecutor(mExecutor);
    client.setKeepaliveTimeout(3453);
    assertEquals(3453, client.getKeepaliveTimeout());
    client.setKeepaliveTimeout(1234);
    assertEquals(1234, client.getKeepaliveTimeout());
  }

  private static class DummyServer implements AutoCloseable {
    static final String IP = "localhost";

    private final ServerSocket mServerSocket;
    private final Thread mThread;
    private final CountDownLatch mConnectedLatch = new CountDownLatch(1);
    private final CountDownLatch mCommandsLatch;

    private boolean isConnected = false;

    public boolean isConnected() {
      return isConnected;
    }

    private String lastCommand;

    public String getLastCommand() {
      return lastCommand;
    }

    int getPort() {
      return mServerSocket.getLocalPort();
    }

    public boolean awaitConnection() throws InterruptedException {
      return mConnectedLatch.await(5, TimeUnit.SECONDS);
    }

    public boolean awaitCommands() throws InterruptedException {
      return mCommandsLatch.await(5, TimeUnit.SECONDS);
    }

    DummyServer(final int cmdNumber) {
      mCommandsLatch = new CountDownLatch(cmdNumber);
      try {
        mServerSocket = new ServerSocket(0);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot bind a server socket", e);
      }
      mThread =
          new Thread(
              () -> {
                try {
                  Socket client = mServerSocket.accept();
                  isConnected = true;
                  mConnectedLatch.countDown();

                  try {
                    BufferedReader clientInput =
                        new BufferedReader(new InputStreamReader(client.getInputStream()));
                    for (int i = 0; i < cmdNumber; ++i) {
                      lastCommand = clientInput.readLine();
                      mCommandsLatch.countDown();
                    }
                  } finally {
                    client.close();
                  }
                } catch (IOException e) {
                  e.printStackTrace();
                }
              });
      mThread.start();
    }

    @Override
    public void close() {
      try {
        mServerSocket.close();
      } catch (IOException ignored) {
      }
      mThread.interrupt();
    }
  }
}
