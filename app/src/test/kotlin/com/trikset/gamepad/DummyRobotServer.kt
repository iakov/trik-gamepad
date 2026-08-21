package com.trikset.gamepad

import com.trikset.gamepad.mjpeg.SyntheticMjpegServer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Host-side mock of a TRIK robot for on-device smoke tests and profiling: a log-only TCP command
 * port (the app's `SenderService` writes plain-text commands; the real robot never replies — an
 * optional `--tcp-keepalive <ms>` makes it emit `keepalive <ms>` to exercise the TCP read path), a
 * log-only UDP command port (the UDP transport sends one command per datagram; the robot may reply
 * with optional `keepalive <ms>` — for the smoke it just logs), and a steady MJPEG stream. Pure
 * Kotlin, lives in the test source set so it reuses the committed CC0 cat fixtures and
 * `SyntheticMjpegServer`, and never ships in a release APK. Start it with `./gradlew
 * runDummyRobotServer`; the phone only needs the host's LAN IP (ports are the app defaults 4444 /
 * 8080).
 */
object DummyRobotServer {

  const val TCP_PORT = 4444
  const val MJPEG_PORT = 8080

  private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

  @JvmStatic
  fun main(args: Array<String>) {
    // Optional `--tcp-keepalive <ms>`: the robot emits `keepalive <ms>` on each TCP connection so
    // the app's TCP keepalive read path can be exercised (additive protocol rule — by default the
    // robot never replies, exactly like the real one).
    val tcpKeepaliveMs =
        args.indexOf("--tcp-keepalive").let { i ->
          if (i >= 0 && i + 1 < args.size) args[i + 1].toIntOrNull() else null
        }
    val mjpeg =
        SyntheticMjpegServer(
            framesPerConnection = Int.MAX_VALUE,
            frameIntervalMs = 20,
            port = MJPEG_PORT,
        )
    val tcp = DummyRobotTcpServer(TCP_PORT, tcpKeepaliveMs)
    val udp = DummyRobotUdpServer(TCP_PORT)
    val tcpNote = if (tcpKeepaliveMs != null) "; robot emits keepalive $tcpKeepaliveMs ms" else ""
    println("DummyRobotServer")
    println("  MJPEG:  http://<host-ip>:$MJPEG_PORT/?action=stream")
    println("  TCP:    <host-ip>:$TCP_PORT (log-only, app writes plain-text commands$tcpNote)")
    println("  UDP:    <host-ip>:$TCP_PORT (log-only, one command per datagram)")
    println("  LAN addresses:")
    for (address in lanIpv4Addresses()) {
      println("    $address  (MJPEG http://$address:$MJPEG_PORT/?action=stream)")
    }
    mjpeg.start()
    tcp.start()
    udp.start()
    println("Serving. Ctrl+C to stop.")
    // Periodic stats line: stdout is block-buffered when redirected to a file,
    // so the explicit flush keeps the profiling session's counters live.
    val stats = Thread {
      while (true) {
        Thread.sleep(STATS_INTERVAL_MS)
        System.out.println(
            "${LocalTime.now().format(TIME_FORMAT)} STATS accepted=${mjpeg.acceptedConnections.get()} " +
                "frames=${mjpeg.servedFrames.get()} tcpClients=${tcp.clients}"
        )
        System.out.flush()
      }
    }
    stats.isDaemon = true
    stats.start()
    Thread.currentThread().join()
  }

  private const val STATS_INTERVAL_MS = 5000L

  /** Closes the given resource, tolerating an already-closed socket (idempotent). */
  private fun closeQuietly(close: () -> Unit) {
    try {
      close()
    } catch (_: IOException) {
      // already closed
    }
  }

  /**
   * All non-loopback IPv4 addresses of the host (the phone connects over Wi-Fi to one of these).
   */
  fun lanIpv4Addresses(): List<String> =
      NetworkInterface.getNetworkInterfaces()
          .asSequence()
          .filter { it.isUp && !it.isLoopback }
          .flatMap { it.inetAddresses.asSequence() }
          .filterIsInstance<Inet4Address>()
          .mapNotNull { it.hostAddress }
          .toList()

  /** Log-only TCP server: accepts any number of connections and prints every received line. */
  class DummyRobotTcpServer(private val port: Int, private val tcpKeepaliveMs: Int? = null) {
    private val serverSocket = ServerSocket(port)
    private var running = true
    val clients = java.util.concurrent.atomic.AtomicInteger(0)

    fun start() {
      Thread {
            while (running) {
              try {
                val client = serverSocket.accept()
                clients.incrementAndGet()
                Thread { readLoop(client) }.start()
              } catch (_: IOException) {
                // server socket closed on stop()
              }
            }
          }
          .apply { isDaemon = true }
          .start()
    }

    fun stop() {
      running = false
      DummyRobotServer.closeQuietly { serverSocket.close() }
    }

    private fun readLoop(client: Socket) {
      try {
        client.use {
          val reader = BufferedReader(InputStreamReader(client.getInputStream()))
          val keepaliveThread = tcpKeepaliveMs?.let { startKeepaliveEmitter(client, it) }
          while (true) {
            val line = reader.readLine() ?: break
            System.out.println(
                "${LocalTime.now().format(TIME_FORMAT)} TCP< ${client.inetAddress.hostAddress}: $line"
            )
            System.out.flush()
          }
          keepaliveThread?.interrupt()
        }
      } catch (_: IOException) {
        // client disconnected
      }
    }

    /** Emits `keepalive <ms>` on the client socket every `<ms>` (the robot-keepalive read path). */
    private fun startKeepaliveEmitter(client: Socket, ms: Int): Thread {
      val writer =
          PrintWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)
      return Thread {
            while (running && !client.isClosed && !Thread.currentThread().isInterrupted) {
              val line = "keepalive $ms"
              writer.println(line)
              writer.flush()
              System.out.println(
                  "${LocalTime.now().format(TIME_FORMAT)} TCP> ${client.inetAddress.hostAddress}: $line"
              )
              System.out.flush()
              try {
                Thread.sleep(ms.toLong())
              } catch (_: InterruptedException) {
                return@Thread
              }
            }
          }
          .apply { isDaemon = true }
          .also { it.start() }
    }
  }

  /** Log-only UDP server: prints every received datagram (one command per datagram over UDP). */
  class DummyRobotUdpServer(private val port: Int) {
    private val serverSocket = DatagramSocket(port)
    @Volatile private var running = true

    fun start() {
      Thread {
            val buffer = ByteArray(4096)
            while (running) {
              try {
                val packet = DatagramPacket(buffer, buffer.size)
                serverSocket.receive(packet)
                val line =
                    String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
                System.out.println(
                    "${LocalTime.now().format(TIME_FORMAT)} UDP< " +
                        "${packet.address.hostAddress}: $line"
                )
                System.out.flush()
              } catch (_: IOException) {
                // server socket closed on stop()
              }
            }
          }
          .apply { isDaemon = true }
          .start()
    }

    fun stop() {
      running = false
      DummyRobotServer.closeQuietly { serverSocket.close() }
    }
  }
}
