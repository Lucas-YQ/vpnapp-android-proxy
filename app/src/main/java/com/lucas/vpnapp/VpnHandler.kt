package com.lucas.vpnapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Base64
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libXray.LibXray
import org.json.JSONObject
import java.io.File

object VpnHandler {

    // Function to ping the server with a provided configuration and check its latency
    fun pingServer(config: String?, datDir: String?, filesDir: File, pingResultText: TextView) {
        // Check if configuration is available, if not show a toast and return
        if (config.isNullOrEmpty()) {
            Toast.makeText(pingResultText.context, "No config available to ping", Toast.LENGTH_SHORT).show()
            return
        }

        // Define the ping parameters like timeout, URL, and proxy address
        val timeout = 10 // seconds
        val url = "https://www.google.com"
        val proxy = "socks5://127.0.0.1:1080"

        // Define the configuration file path
        val configFile = File(filesDir, "config.json")

        // Check if the configuration file exists, if not show a toast and return
        if (!configFile.exists()) {
            Toast.makeText(pingResultText.context, "Config file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Prepare the JSON string with configuration details to be used for pinging
        val inputJson = JSONObject().apply {
            put("datDir", datDir)
            put("configPath", configFile)
            put("timeout", timeout)
            put("url", url)
            put("proxy", proxy)
        }.toString()

        // Base64 encode the JSON string to be passed to LibXray
        val base64Input = Base64.encodeToString(inputJson.toByteArray(), Base64.NO_WRAP)

        // Perform the ping operation asynchronously in a coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Use LibXray to ping the server with the encoded configuration
                val base64Response = LibXray.ping(base64Input)

                // Decode the Base64 response from LibXray
                val decodedResponse = String(Base64.decode(base64Response, Base64.DEFAULT))

                // Parse the JSON response from the server
                val jsonResponse = JSONObject(decodedResponse)

                // Check if the ping was successful and update the UI accordingly
                val success = jsonResponse.optBoolean("success", false)
                val data = jsonResponse.optInt("data", -1)
                val error = jsonResponse.optString("error", "")

                withContext(Dispatchers.Main) {
                    // Update the TextView with the result of the ping operation
                    if (success) {
                        pingResultText.text = "Ping success: $data ms"
                        Log.d("Ping Result", "Ping delay: $data ms")
                    } else {
                        pingResultText.text = "Ping failed: $error"
                        Log.e("Ping Error", "Error: $error")
                    }
                }
            } catch (e: Exception) {
                // Handle any errors that occur during the ping operation
                withContext(Dispatchers.Main) {
                    pingResultText.text = "Ping failed: ${e.message}"
                    Log.e("Ping Error", "Error during ping", e)
                }
            }
        }
    }

    // Function to stop the Xray service and VPN service
    fun stopXray(context: Context, statusText: TextView) {
        try {
            // Attempt to stop the Xray service
            LibXray.stopXray()
            statusText.text = "Xray stopped successfully."
            Log.d("Xray Status", "Xray stopped.")
        } catch (e: Exception) {
            // Log and display error message if stopping Xray fails
            Log.e("Stop Xray", "Error stopping Xray", e)
            statusText.text = "Failed to stop Xray: ${e.message}"
        }

        // Stop the VPN service (MyVpnService)
        val intent = Intent(context, MyVpnService::class.java)
        context.stopService(intent)
    }

    // Function to request VPN permission from the user
    fun requestVpnPermission(activity: Activity, vpnPermissionResultLauncher: (Intent) -> Unit, onPermissionGranted: () -> Unit) {
        val vpnIntent = VpnService.prepare(activity)
        if (vpnIntent != null) {
            // If permission is not granted, launch the VPN permission request
            vpnPermissionResultLauncher(vpnIntent)
        } else {
            // If permission is already granted, call the onPermissionGranted callback
            onPermissionGranted()
        }
    }

    // Function to start the VPN service and bind it to the activity
    fun startVpnService(context: Context, serviceConnection: android.content.ServiceConnection) {
        val intent = Intent(context, MyVpnService::class.java)
        // Start the VPN service
        context.startService(intent)
        Toast.makeText(context, "VPN service started", Toast.LENGTH_SHORT).show()
        // Bind the service to the context
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // Function to start the Xray service with a provided configuration
    fun startXray(
        context: Context,
        datDir: String,
        config: String?,
        statusText: TextView,
        requestVpnPermission: () -> Unit
    ) {
        if (config.isNullOrEmpty()) {
            // Show a toast if the configuration is missing
            Toast.makeText(context, "No config available to start Xray", Toast.LENGTH_SHORT).show()
            return
        }

        val configFile = File(datDir, "config.json")
        if (!configFile.exists()) {
            // Show a toast if the configuration file does not exist
            Toast.makeText(context, "Config file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Start the Xray service asynchronously in a coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Prepare the input JSON with configuration details for Xray
                val inputJson = JSONObject().apply {
                    put("datDir", datDir)
                    put("configPath", configFile)
                    put("maxMemory", 128 * 1024 * 1024) // Set memory limit
                }.toString()

                // Base64 encode the JSON string
                val base64Config = Base64.encodeToString(inputJson.toByteArray(), Base64.NO_WRAP)
                // Run Xray with the encoded configuration
                val result = LibXray.runXray(base64Config)
                // Decode the Base64 response from Xray
                val decodedResponse = String(Base64.decode(result, Base64.DEFAULT), Charsets.UTF_8)
                // Parse the JSON response
                val responseJson = JSONObject(decodedResponse)

                withContext(Dispatchers.Main) {
                    // If Xray started successfully, update UI and request VPN permission
                    if (responseJson.optBoolean("success", false)) {
                        statusText.text = "Xray started successfully."
                        Log.d("Xray Status", "Xray started with config")
                        requestVpnPermission()
                    } else {
                        statusText.text = "Xray error: $decodedResponse"
                    }
                }
            } catch (e: Exception) {
                // Handle any errors that occur while starting Xray
                withContext(Dispatchers.Main) {
                    Log.e("Start Xray", "Error starting Xray", e)
                    statusText.text = "Failed to start Xray: ${e.message}"
                }
            }
        }
    }
}
