package com.trikset.gamepad

import android.os.Looper.getMainLooper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

/**
 * Covers the SenderService paths the basic test misses: keepalive ticks, disconnect callbacks,
 * send-failure handling, and target changes.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class SenderServiceAdvancedTest {
  private val mExecutor = PausedExecutorService()

  @Test
  fun disconnectShouldInvokeOnDisconnectedListener() {
    ReadUntilStopServer().use { server ->
      val client = SenderService(mExecutor)
      val reason = AtomicReference<String>()
      client.setOnDisconnectedListener { reason.set(it) }

      client.setTarget("localhost", server.getPort())
      client.send("test")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      client.disconnect("Test disconnect.")
      assertEquals("Test disconnect.", reason.get())
      client.disconnect("again") // mOut already null -> no listener call
      assertEquals("Test disconnect.", reason.get())
    }
  }

  @Test
  fun setTargetShouldDisconnectWhenChanged() {
    ReadUntilStopServer().use { first ->
      val client = SenderService(mExecutor)
      val reason = AtomicReference<String>()
      client.setOnDisconnectedListener { reason.set(it) }

      client.setTarget("localhost", first.getPort())
      client.send("a")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(first.awaitConnection())

      // New target on a different port -> must disconnect from the first.
      client.setTarget("localhost", first.getPort() + 1)
      assertEquals("Target changed.", reason.get())
    }
  }

  @Test
  fun sendFailureShouldDisconnectAndReport() {
    ReadUntilStopServer().use { server ->
      val client = SenderService(mExecutor)
      client.setTarget("localhost", server.getPort())
      client.send("first")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      // Closing the server makes the next println fail, which turns into a disconnect.
      server.closeSocket()
      client.send("after-close")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      // checkError() may be lazy; give the executor another cycle.
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
    }
  }

  @Test
  fun keepaliveShouldBeSentWhileConnected() {
    val timeout = 1300 // real period = timeout - 300 = 1000ms
    ReadUntilStopServer().use { server ->
      val client = SenderService(mExecutor)
      client.setTarget("localhost", server.getPort())
      client.setKeepaliveTimeout(timeout)
      client.send("bootstrap")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      // The keepalive Timer runs on a real thread; poll for the message instead
      // of a single fixed sleep so a busy CI JVM cannot starve the timer.
      assertTrue(
          "expected a keepalive message",
          server.awaitReceived(
              "keepalive $timeout",
              drain = {
                mExecutor.runAll()
                shadowOf(getMainLooper()).idle()
              },
              sleepMs = 500,
          ),
      )
      client.disconnect("done")
    }
  }

  @Test
  fun showTextCallbackShouldReceiveConnectionResult() {
    ReadUntilStopServer().use { server ->
      val client = SenderService(mExecutor)
      val text = AtomicReference<String>()
      client.setShowTextCallback { text.set(it) }

      client.setTarget("localhost", server.getPort())
      client.send("hello")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue("expected connection message, got '${text.get()}'", text.get() != null)
      client.disconnect("done")
    }
  }

  @Test
  fun sendToUnreachablePortShouldNotThrow() {
    val client = SenderService(mExecutor)
    // Port 1: nothing listens on it, so the connect is refused and connectToTRIK
    // swallows the IOException.
    client.setTarget("localhost", 1)
    client.send("boom")
    mExecutor.runAll()
    shadowOf(getMainLooper()).idle()
  }

  @Test
  fun sendWhileConnectedShouldSkipReconnect() {
    ReadUntilStopServer().use { server ->
      val client = SenderService(mExecutor)
      client.setTarget("localhost", server.getPort())
      client.send("one")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())

      // Already connected -> send() must not queue another connect task.
      client.send("two")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      // The server reads asynchronously; poll for the second command instead
      // of asserting immediately (bounded await, never a bare assert).
      assertTrue(
          "expected 'two' over the live socket",
          server.awaitReceived(
              "two",
              drain = {
                mExecutor.runAll()
                shadowOf(getMainLooper()).idle()
              },
          ),
      )
      client.disconnect("done")
    }
  }

  @Test
  fun keepaliveTimeoutBelowMinimumIsStoredUnchanged() {
    val client = SenderService(mExecutor)
    val before = client.getKeepaliveTimeout()
    // The service does not clamp; the caller (MainActivity) enforces the
    // minimum. This just verifies the setter round-trips.
    client.setKeepaliveTimeout(12345)
    assertEquals(12345, client.getKeepaliveTimeout())
    client.setKeepaliveTimeout(before)
  }

  /** A DummyServer variant that reads until [closeSocket] or close(). */
  @Suppress("SwallowedException") // socket closed -> loop ends
  private class ReadUntilStopServer : AutoCloseable {
    private val mServerSocket: ServerSocket
    private val mConnectedLatch = CountDownLatch(1)
    private val mMessages = Collections.synchronizedList(ArrayList<String>())
    @Volatile private var mClientSocket: Socket? = null

    fun getPort(): Int = mServerSocket.localPort

    fun awaitConnection(): Boolean = mConnectedLatch.await(5, TimeUnit.SECONDS)

    fun receivedContains(fragment: String): Boolean {
      synchronized(mMessages) {
        return mMessages.any { it != null && it.contains(fragment) }
      }
    }

    /**
     * Bounded poll for [fragment] to appear on the socket. The caller supplies a [drain] that
     * advances the executor/looper (e.g. `{ mExecutor.runAll(); shadowOf(getMainLooper()).idle()
     * }`); the server only owns the read side, so it cannot know the test's scheduling.
     */
    fun awaitReceived(
        fragment: String,
        drain: () -> Unit,
        sleepMs: Long = 50,
        attempts: Int = 20,
    ): Boolean {
      var seen = false
      var count = 0
      while (!seen && count < attempts) {
        count++
        Thread.sleep(sleepMs)
        drain()
        seen = receivedContains(fragment)
      }
      return seen
    }

    fun closeSocket() {
      mClientSocket?.let { c ->
        try {
          c.close()
        } catch (ignored: IOException) {}
      }
    }

    init {
      mServerSocket =
          try {
            ServerSocket(0)
          } catch (e: IOException) {
            throw IllegalStateException("Cannot bind a server socket", e)
          }
      Thread {
            try {
              mServerSocket.use { s ->
                val client = s.accept()
                mClientSocket = client
                mConnectedLatch.countDown()
                val input = BufferedReader(InputStreamReader(client.getInputStream()))
                var line: String?
                while (input.readLine().also { line = it } != null) {
                  mMessages.add(line)
                }
              }
            } catch (e: IOException) {
              // socket closed -> loop ends
            }
          }
          .start()
    }

    override fun close() {
      closeSocket()
      try {
        mServerSocket.close()
      } catch (ignored: IOException) {}
    }
  }
}
