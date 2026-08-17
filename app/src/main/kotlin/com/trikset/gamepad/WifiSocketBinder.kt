package com.trikset.gamepad

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 * null) instead of faking `ConnectivityManager` capabilities. The default provider tracks Wi-Fi
 * availability via a [ConnectivityManager.NetworkCallback] registered with
 * [ConnectivityManager.registerNetworkCallback] — the non-deprecated flow (`allNetworks()` is
 * deprecated since API 33 and is deliberately not used).
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

  /**
   * Tracks the currently available `TRANSPORT_WIFI` network by registering a
   * [ConnectivityManager.NetworkCallback] once at construction (the callback is posted to the
   * calling thread's looper, the main thread at every call site). A connect that happens before the
   * first [ConnectivityManager.NetworkCallback.onAvailable] falls back to the default network; the
   * next reconnect then binds to Wi-Fi.
   */
  private class WifiNetworkTracker(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile private var wifiNetwork: Network? = null

    init {
      val request =
          NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
      connectivityManager.registerNetworkCallback(
          request,
          object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
              wifiNetwork = network
            }

            override fun onLost(network: Network) {
              if (wifiNetwork == network) wifiNetwork = null
            }
          },
      )
    }

    fun current(): Network? = wifiNetwork
  }

  private companion object {
    const val TAG = "SocketBinder"
  }
}
