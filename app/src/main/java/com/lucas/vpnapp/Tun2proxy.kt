package com.lucas.vpnapp

import android.util.Log
import com.lucas.vpnapp.MainActivity.Companion.startTun2proxy
import com.lucas.vpnapp.MainActivity.Companion.stopTun2proxy

// Object that handles starting and stopping the tun2proxy process.
object Tun2proxy {

    // A reference to the thread running the tun2proxy process.
    private var tun2proxyThread: Thread? = null

    // Starts the tun2proxy in a background thread if it's not already running.
    fun startTun2proxyInBackground(myVpnService: MyVpnService?) {
        // Check if tun2proxy is not running or the thread is not alive.
        if (tun2proxyThread == null || !tun2proxyThread!!.isAlive) {
            // Create a new thread to run tun2proxy.
            tun2proxyThread = Thread {
                // Get the file descriptor for the TUN interface from the VPN service.
                // If the interface is null, it defaults to -1.
                val tunFd = myVpnService?.tunInterface?.fd ?: -1

                // Define the proxy URL to be used (socks5 in this case).
                val proxyUrl = "socks5://127.0.0.1:1080"

                // Verbosity level (5 is a standard info level).
                val verbosity = 5

                // DNS strategy (2 means direct DNS query).
                val dnsStrategy = 2

                // Start tun2proxy with the proxy URL, TUN file descriptor, and other configurations.
                startTun2proxy(proxyUrl, tunFd, false, 1500.toChar(), verbosity, dnsStrategy)

                // Log the proxy URL and TUN file descriptor for debugging purposes.
                Log.d("Tun2proxy", "Starting tun2proxy with proxy URL: $proxyUrl and tunFd: $tunFd")
            }
            // Start the thread to run tun2proxy.
            tun2proxyThread?.start()
        }
    }

    // Stops the tun2proxy if it's running in the background.
    fun stopTun2proxyInBackground() {
        // Check if the tun2proxy thread is alive and running.
        if (tun2proxyThread != null && tun2proxyThread!!.isAlive) {
            // Call the stop function to stop tun2proxy.
            stopTun2proxy()

            // Set the tun2proxyThread reference to null, marking it as stopped.
            tun2proxyThread = null
        }
    }
}
