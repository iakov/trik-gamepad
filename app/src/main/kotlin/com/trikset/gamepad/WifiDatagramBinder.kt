package com.trikset.gamepad

import android.content.Context
import android.net.Network
import com.trikset.gamepad.diagnostics.AppLog
import java.io.IOException
import java.net.DatagramSocket

/**
 * [DatagramBinder] that prefers the Wi-Fi network (the UDP twin of [WifiSocketBinder]): when a
 * `TRANSPORT_WIFI` network is available, [bind] calls [Network.bindSocket] so the datagram socket's
 * traffic goes out over the robot AP even when cellular is the system default network (S13 / A2).
 * Falls back to the default network when no Wi-Fi transport is available. Requires
 * `ACCESS_NETWORK_STATE`.
 */
class WifiDatagramBinder(
    context: Context,
    private val wifiNetworkProvider: () -> Network? = WifiNetworkTracker(context)::current,
) : DatagramBinder {

  override fun bind(socket: DatagramSocket): DatagramSocket {
    val wifi = wifiNetworkProvider()
    if (wifi != null) {
      try {
        wifi.bindSocket(socket)
      } catch (e: IOException) {
        // A bind failure must not kill the send path: fall back to the default network.
        AppLog.e(TAG, "Could not bind datagram socket to the Wi-Fi network; using the default.", e)
      }
    }
    return socket
  }

  private companion object {
    const val TAG = "UdpBinder"
  }
}
