package com.localfm.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Tiện ích mạng: lấy địa chỉ IPv4 local (thường là 192.168.x.x)
 * khi thiết bị đang kết nối Wi-Fi.
 */
object NetworkUtils {

    /**
     * Ưu tiên lấy IPv4 của interface đang dùng Wi-Fi / LAN.
     * Bỏ qua địa chỉ loopback và interface ảo.
     *
     * @return chuỗi IP dạng "192.168.1.23" hoặc null nếu không xác định được.
     */
    fun getLocalWifiIpv4(context: Context): String? {
        // Cách 1: duyệt NetworkInterface — chính xác hơn trên Android mới
        val fromInterfaces = ipv4FromNetworkInterfaces()
        if (!fromInterfaces.isNullOrBlank() && fromInterfaces != "0.0.0.0") {
            return fromInterfaces
        }

        // Cách 2: WifiManager (API cũ, vẫn hữu ích trên nhiều máy)
        return ipv4FromWifiManager(context)
    }

    /**
     * Duyệt mọi card mạng, chọn IPv4 không phải loopback.
     * Ưu tiên dải LAN phổ biến: 192.168.x.x, 10.x.x.x, 172.16-31.x.x.
     */
    private fun ipv4FromNetworkInterfaces(): String? {
        val candidates = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            val name = intf.name.lowercase(Locale.US)
            // Bỏ interface ảo / di động nếu có lựa chọn khác
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("192.168.") ||
                        host.startsWith("10.") ||
                        isPrivate172(host)
                    ) {
                        // wlan thường là Wi-Fi
                        if (name.contains("wlan") || name.contains("ap") || name.contains("eth")) {
                            return host
                        }
                        candidates.add(host)
                    }
                }
            }
        }
        return candidates.firstOrNull()
    }

    private fun isPrivate172(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        if (parts[0] != "172") return false
        val second = parts[1].toIntOrNull() ?: return false
        return second in 16..31
    }

    @Suppress("DEPRECATION")
    private fun ipv4FromWifiManager(context: Context): String? {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo?.ipAddress ?: return null
            if (ip == 0) return null
            String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Kiểm tra thiết bị đang có kết nối (Wi-Fi hoặc Ethernet). */
    fun hasLocalNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
