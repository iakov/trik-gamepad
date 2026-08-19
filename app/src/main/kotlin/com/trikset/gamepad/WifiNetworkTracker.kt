package com.trikset.gamepad

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Tracks the currently available `TRANSPORT_WIFI` network by registering a
 * [ConnectivityManager.NetworkCallback] once at construction (the callback is posted to the calling
 * thread's looper, the main thread at every call site). A connect that happens before the first
 * [ConnectivityManager.NetworkCallback.onAvailable] falls back to the default network; the next
 * reconnect then binds to Wi-Fi. Requires `ACCESS_NETWORK_STATE`.
 *
 * Used by [WifiSocketBinder] (control + raw-socket video) and [WifiConnectionOpener] (https video)
 * so all sockets and connections prefer the robot Wi-Fi AP over the cellular default network (S13 /
 * A2).
 */
class WifiNetworkTracker(context: Context) {
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
