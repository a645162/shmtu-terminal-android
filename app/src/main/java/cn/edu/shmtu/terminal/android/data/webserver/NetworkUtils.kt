package cn.edu.shmtu.terminal.android.data.webserver

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 网络工具 - 获取本机 IPv4 地址
 */
object NetworkUtils {
    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val wifiIp = wifiManager.connectionInfo?.ipAddress
                if (wifiIp != null && wifiIp != 0) {
                    return intToIp(wifiIp)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "0.0.0.0"
    }

    private fun intToIp(ip: Int): String {
        val b0 = ip and 0xFF
        val b1 = (ip shr 8) and 0xFF
        val b2 = (ip shr 16) and 0xFF
        val b3 = (ip shr 24) and 0xFF
        return "$b0.$b1.$b2.$b3"
    }
}
