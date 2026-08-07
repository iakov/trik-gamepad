package com.trikset.gamepad

import android.os.Looper.getMainLooper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
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

@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
class SenderServiceTest {
  private val mExecutor = PausedExecutorService()
  private var client: SenderService? = null

  // A connected client keeps a real keepalive Timer thread alive that fires
  // into the STATIC SenderService.mExecutor (set to this test's executor by
  // setExecutor). Disconnecting every client in @After stops the Timer, so no
  // leaked keepalive task can land on a later test's executor.
  @After
  fun tearDown() {
    client?.disconnect("tearDown")
  }

  @Test
  fun senderServiceShouldConnectToServerSuccessfullyAfterSendingCommand() {
    DummyServer(1).use { server ->
      client = SenderService().also { it.setExecutor(mExecutor) }
      client!!.setTarget(DummyServer.IP, server.getPort())
      client!!.send("")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitConnection())
      assertTrue(server.isConnected())
    }
  }

  @Test
  fun senderServiceShouldSendSingleCommandCorrectly() {
    DummyServer(1).use { server ->
      client = SenderService().also { it.setExecutor(mExecutor) }
      client!!.setTarget(DummyServer.IP, server.getPort())
      client!!.setKeepaliveTimeout(10000000) // to disable keep-alive messages
      client!!.send("Test; check")
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitCommands())
      assertEquals("Test; check", server.getLastCommand())
    }
  }

  @Test
  fun senderServiceShouldSendMultipleCommandsCorrectly() {
    DummyServer(5).use { server ->
      client = SenderService().also { it.setExecutor(mExecutor) }
      client!!.setTarget(DummyServer.IP, server.getPort())
      client!!.setKeepaliveTimeout(10000000) // to disable keep-alive messages

      for (i in 0 until 5) {
        client!!.send(String.format(Locale.US, "%d checking", i))
      }
      mExecutor.runAll()
      shadowOf(getMainLooper()).idle()
      assertTrue(server.awaitCommands())
      assertEquals("4 checking", server.getLastCommand())
    }
  }

  @Test
  fun setTargetShouldSetServerSuccessfully() {
    client = SenderService().also { it.setExecutor(mExecutor) }
    client!!.setTarget("someaddr-test", 0)
    assertEquals("someaddr-test", client!!.getHostAddr())
  }

  @Test
  fun senderServiceShouldReturnCorrectKeepaliveTimeout() {
    client = SenderService().also { it.setExecutor(mExecutor) }
    client!!.setKeepaliveTimeout(3453)
    assertEquals(3453, client!!.getKeepaliveTimeout())
    client!!.setKeepaliveTimeout(1234)
    assertEquals(1234, client!!.getKeepaliveTimeout())
  }

  private class DummyServer(private val cmdNumber: Int) : AutoCloseable {
    companion object {
      const val IP = "localhost"
    }

    private val mConnectedLatch = CountDownLatch(1)
    private val mCommandsLatch = CountDownLatch(cmdNumber)
    @Volatile private var isConnected = false
    private var lastCommand: String? = null
    private val mServerSocket: ServerSocket
    private val mThread: Thread

    fun isConnected(): Boolean = isConnected

    fun getLastCommand(): String? = lastCommand

    fun getPort(): Int = mServerSocket.localPort

    fun awaitConnection(): Boolean = mConnectedLatch.await(5, TimeUnit.SECONDS)

    fun awaitCommands(): Boolean = mCommandsLatch.await(5, TimeUnit.SECONDS)

    init {
      mServerSocket =
          try {
            ServerSocket(0)
          } catch (e: IOException) {
            throw IllegalStateException("Cannot bind a server socket", e)
          }
      mThread = Thread {
        try {
          val client = mServerSocket.accept()
          isConnected = true
          mConnectedLatch.countDown()
          try {
            val clientInput = BufferedReader(InputStreamReader(client.getInputStream()))
            for (i in 0 until cmdNumber) {
              lastCommand = clientInput.readLine()
              mCommandsLatch.countDown()
            }
          } finally {
            client.close()
          }
        } catch (e: IOException) {
          e.printStackTrace()
        }
      }
      mThread.start()
    }

    override fun close() {
      try {
        mServerSocket.close()
      } catch (ignored: IOException) {}
      mThread.interrupt()
    }
  }
}
