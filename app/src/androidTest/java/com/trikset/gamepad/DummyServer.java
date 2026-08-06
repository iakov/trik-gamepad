package com.trikset.gamepad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class DummyServer {
  static final String IP = "localhost";
  static final int DEFAULT_PORT = 12345;

  private boolean canStopListening = false;

  public void stopListening() {
    canStopListening = true;
  }

  private final ArrayList<String> receivedMessages = new ArrayList<>();

  public List<String> getReceivedMessages() {
    return receivedMessages;
  }

  /** Waits until the given message has been received, or the timeout elapses. */
  public boolean awaitMessage(String expected, long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    synchronized (lock) {
      while (!receivedMessages.contains(expected)) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          return false;
        }
        lock.wait(remaining);
      }
      return true;
    }
  }

  private final Object lock = new Object();

  DummyServer() {
    try {
      // Bind synchronously in the constructor so the port is guaranteed
      // listening before the client connects. A background bind raced the
      // client's connect on slow emulators (ECONNREFUSED), failing the
      // keepalive tests — the same lesson as the unit-test DummyServer
      // (MEMORY.md "Ephemeral ports (hard-won)").
      final ServerSocket server = new ServerSocket(DEFAULT_PORT);
      Thread serverThread =
          new Thread(
              () -> {
                try (ServerSocket s = server) {
                  Socket client = s.accept();

                  BufferedReader clientInput =
                      new BufferedReader(new InputStreamReader(client.getInputStream()));
                  String message;
                  while ((message = clientInput.readLine()) != null && !canStopListening) {
                    synchronized (lock) {
                      receivedMessages.add(message);
                      lock.notifyAll();
                    }
                  }
                } catch (IOException e) {
                  e.printStackTrace();
                }
              });
      serverThread.start();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to bind DummyServer", e);
    }
  }
}
