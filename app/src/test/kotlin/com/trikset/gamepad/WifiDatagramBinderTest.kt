package com.trikset.gamepad

import android.content.Context
import android.net.ConnectivityManager
import java.net.DatagramSocket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

/**
 * A2/S13: UDP datagram sockets must prefer the robot's Wi-Fi network when one is present (the
 * default network can be cellular), and fall back to the default network otherwise. The UDP twin of
 * [SocketBinderTest]; [WifiDatagramBinder] binds via `Network.bindSocket(DatagramSocket)`.
 */
@RunWith(RobolectricTestRunner::class)
class WifiDatagramBinderTest : RobolectricTestBase() {

  private val context: Context = RuntimeEnvironment.getApplication()

  private fun connectivityManager(): ConnectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  @Test
  fun noWifiProviderFallsBackToTheGivenSocket() {
    val binder = WifiDatagramBinder(context) { null }
    val socket = DatagramSocket()
    // No Wi-Fi network -> the socket is returned untouched (default-network fallback).
    assertSame(socket, binder.bind(socket))
    socket.close()
  }

  @Test
  fun wifiProviderBindsTheDatagramSocketToTheWifiNetwork() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val binder = WifiDatagramBinder(context) { wifi }
    val socket = binder.bind(DatagramSocket())
    assertTrue(shadowOf(wifi).isSocketBound(socket))
    socket.close()
  }

  @Test
  fun trackerRegistersACallbackAndTracksWifiAvailability() {
    val binder = WifiDatagramBinder(context)
    val callback =
        shadowOf(connectivityManager()).getNetworkCallbacks().first {
          it is ConnectivityManager.NetworkCallback
        }
    val wifi = ShadowNetwork.newInstance(nextNetworkId())

    callback.onAvailable(wifi)
    val bound = binder.bind(DatagramSocket())
    assertTrue(shadowOf(wifi).isSocketBound(bound))
    bound.close()

    // onLost for an untracked network must not clear the tracked one.
    callback.onLost(ShadowNetwork.newInstance(nextNetworkId()))
    val stillBound = binder.bind(DatagramSocket())
    assertTrue(shadowOf(wifi).isSocketBound(stillBound))
    stillBound.close()

    callback.onLost(wifi)
    val fallback = binder.bind(DatagramSocket())
    assertFalse(shadowOf(wifi).isSocketBound(fallback))
    fallback.close()
  }

  private companion object {
    var nextId = 1

    fun nextNetworkId(): Int = nextId++
  }
}
