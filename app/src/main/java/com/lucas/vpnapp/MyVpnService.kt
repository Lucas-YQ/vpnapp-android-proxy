package com.lucas.vpnapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException

// MyVpnService is a custom service that extends VpnService to manage the VPN interface.
class MyVpnService : VpnService() {

    // This variable holds the ParcelFileDescriptor for the VPN interface.
    var tunInterface: ParcelFileDescriptor? = null
        private set  // Making it private for direct modification, but it's accessible as a getter.

    // This method is invoked when the service is started, typically when the VPN needs to be established.
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            startVpn()  // Starts the VPN connection.
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e)  // This error should probably be more specific to VPN configuration.
        }
        return START_STICKY  // Indicates that the service should restart if it gets terminated.
    }

    // Method that establishes the VPN connection using the VpnService.Builder.
    @Throws(PackageManager.NameNotFoundException::class)
    private fun startVpn() {
        val builder = Builder()  // Builder for creating a VPN configuration.
        builder.setSession("VpnApp")  // Name the session, visible in the system UI.
            .setMtu(1500)  // Maximum Transmission Unit size, the standard for VPNs.
            .addAddress("10.0.0.2", 24)  // Assigns the VPN device an IP address in a specified subnet.
            .addRoute("0.0.0.0", 0)  // Add a default route for all traffic.
            .addDisallowedApplication("com.lucas.vpnapp")  // Disallow this app from using the VPN.

        try {
            tunInterface = builder.establish()  // Establishes the VPN connection.
            if (tunInterface != null) {
                Log.d(TAG, "VPN interface established with TUN file descriptor: " + tunInterface!!.fd)
            } else {
                Log.e(TAG, "Failed to establish VPN interface: tunInterface is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while establishing VPN interface", e)  // Log any errors while establishing the VPN.
        }
    }

    // This method is responsible for stopping the VPN service and closing the VPN interface.
    fun stopVpn() {
        Log.d(TAG, "Stopping VPN service...")
        if (tunInterface != null) {
            try {
                tunInterface!!.close()  // Close the VPN interface when stopping the service.
                Log.d(TAG, "VPN interface closed.")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing VPN interface", e)  // Handle errors when closing the interface.
            }
            tunInterface = null  // Clean up the reference to the VPN interface.
        }
        stopSelf()  // Stop the service once the VPN has been stopped.
    }

    // This method is triggered when the VPN is revoked or disconnected by the user.
    override fun onRevoke() {
        super.onRevoke()
        if (tunInterface != null) {
            try {
                tunInterface!!.close()  // Ensure the interface is closed when revoked.
            } catch (e: IOException) {
                Log.e(TAG, "Error closing VPN interface", e)  // Handle any issues while closing the interface.
            }
            tunInterface = null  // Clean up the reference to the VPN interface.
        }
    }

    // This method is triggered when the service is destroyed, ensuring proper cleanup.
    override fun onDestroy() {
        super.onDestroy()
        if (tunInterface != null) {
            try {
                tunInterface!!.close()  // Ensure proper closure of the VPN interface on service destruction.
            } catch (e: IOException) {
                Log.e(TAG, "Error closing VPN interface", e)  // Log any errors during closure.
            }
            tunInterface = null  // Clean up the reference to the VPN interface.
        }
    }

    // Inner class that provides a binder object for communication between the service and other components.
    inner class VpnBinder : Binder() {
        val service: MyVpnService
            get() = this@MyVpnService  // Returns the instance of MyVpnService.
    }

    // Method to return the binder for the service, allowing communication with other components.
    override fun onBind(intent: Intent): IBinder {
        return VpnBinder()  // Return the binder so clients can interact with the service.
    }

    companion object {
        private const val TAG = "MyVpnService"  // Define the tag used in logs for debugging.
    }
}
