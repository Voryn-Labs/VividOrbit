package com.vorynlabs.vividorbit.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    fun getLocalIpAddress(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ||
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) == true
            if (!isConnected) {
                return null
            }
        }

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve local IP address: ${e.message}", e)
        }
        return null
    }
}
