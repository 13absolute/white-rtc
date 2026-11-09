package com.whitertc.proxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.whitertc.core.logging.WhiteLogger
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class VpnNetworkManager(context: Context) {

    companion object {
        private const val TAG = "VpnNetworkManager"
        private const val VPN_NETWORK_ACQUIRE_TIMEOUT_MS = 3_500L
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _vpnNetwork = MutableStateFlow<Network?>(null)
    val vpnNetwork: StateFlow<Network?> = _vpnNetwork.asStateFlow()

    @Volatile
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isVpnTransport(network)) {
                WhiteLogger.i(TAG) { "VPN Network available: $network" }
                _vpnNetwork.value = network
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                if (_vpnNetwork.value != network) {
                    WhiteLogger.i(TAG) { "VPN Network updated via capabilities: $network" }
                    _vpnNetwork.value = network
                }
            }
        }

        override fun onLost(network: Network) {
            if (_vpnNetwork.value == network) {
                WhiteLogger.w(TAG) { "VPN Network lost: $network" }
                _vpnNetwork.value = findActiveVpnNetworkSynchronously()
            }
        }
    }

    @Synchronized
    fun startMonitoring() {
        if (isRegistered) return
        _vpnNetwork.value = findActiveVpnNetworkSynchronously()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        } catch (e: Exception) {
            WhiteLogger.e(TAG, e) { "Failed to register VPN NetworkCallback" }
        }
    }

    fun notifyVpnEstablished() {
        val current = _vpnNetwork.value
        if (current != null && isVpnTransport(current)) return
        findActiveVpnNetworkSynchronously()?.let { found ->
            WhiteLogger.i(TAG) { "Synchronously resolved VPN Network after establish(): $found" }
            _vpnNetwork.value = found
        }
    }

    @Synchronized
    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
        } finally {
            isRegistered = false
            _vpnNetwork.value = null
        }
    }

    suspend fun requireVpnNetwork(): Network {
        _vpnNetwork.value?.let { return it }
        notifyVpnEstablished()
        _vpnNetwork.value?.let { return it }

        return withTimeoutOrNull(VPN_NETWORK_ACQUIRE_TIMEOUT_MS) {
            _vpnNetwork.filterNotNull().first()
        } ?: throw IOException(
            "Active VPN Network (tun0) is unavailable after ${VPN_NETWORK_ACQUIRE_TIMEOUT_MS}ms"
        )
    }

    @Throws(IOException::class)
    fun bindSocketToVpn(socket: Socket, network: Network) {
        network.bindSocket(socket)
    }

    @Throws(IOException::class)
    fun bindDatagramSocketToVpn(socket: DatagramSocket, network: Network) {
        network.bindSocket(socket)
    }

    @Throws(IOException::class)
    suspend fun resolveHostThroughVpn(host: String, network: Network): List<InetAddress> =
        withContext(Dispatchers.IO) {
            val addresses = network.getAllByName(host)
            if (addresses.isEmpty()) {
                throw IOException("DNS resolution returned empty result for host: $host")
            }
            addresses.sortedWith(compareBy { address ->
                when (address) {
                    is Inet4Address -> 0
                    is Inet6Address -> 1
                    else -> 2
                }
            })
        }

    private fun findActiveVpnNetworkSynchronously(): Network? {
        return try {
            connectivityManager.allNetworks.firstOrNull { network ->
                isVpnTransport(network)
            }
        } catch (e: Exception) {
            WhiteLogger.w(TAG) { "Failed to query allNetworks: ${e.message}" }
            null
        }
    }

    private fun isVpnTransport(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
