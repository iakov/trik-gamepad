package com.trikset.gamepad

import android.content.Context
import android.net.Network
import com.trikset.gamepad.diagnostics.AppLog
import java.io.IOException
import java.net.Socket

/**
 * [SocketBinder] that prefers the Wi-Fi network: when a `TRANSPORT_WIFI` network is available,
 * [bind] calls [Network.bindSocket] so the socket's traffic goes out over the robot AP even when
 * cellular is the system default network (the robot AP has no internet, so the system keeps
 * cellular default). Falls back to the default network when no Wi-Fi transport is available (A2).
 * Requires `ACCESS_NETWORK_STATE`.
 *
 * The Wi-Fi lookup is injected as [wifiNetworkProvider] so tests can supply a fixed [Network] (or
 * null) instead of faking `ConnectivityManager` capabilities; the production provider is
 * [WifiNetworkTracker] (the non-deprecated callback flow — `allNetworks()` is deprecated since API
 * 33 and is deliberately not used).
 */
class WifiSocketBinder(
    context: Context,
    private val wifiNetworkProvider: () -> Network? = WifiNetworkTracker(context)::current,
) : SocketBinder {

  override fun bind(socket: Socket): Socket {
    val wifi = wifiNetworkProvider()
    if (wifi != null) {
      try {
        wifi.bindSocket(socket)
      } catch (e: IOException) {
        // A bind failure must not kill the connect attempt: fall back to the default network.
        AppLog.e(TAG, "Could not bind socket to the Wi-Fi network; using the default network.", e)
      }
    }
    return socket
  }

  private companion object {
    const val TAG = "SocketBinder"
  }
}
