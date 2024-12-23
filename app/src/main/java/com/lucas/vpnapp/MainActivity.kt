package com.lucas.vpnapp

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var inputField: EditText
    private lateinit var convertButton: Button
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView
    private lateinit var startXrayButton: Button
    private lateinit var stopXrayButton: Button
    private lateinit var pingButton: Button
    private lateinit var pingResultText: TextView

    // Configuration and directory for storing files
    private var config: String? = null
    private lateinit var datDir: String

    // VPN permission and service binding
    private lateinit var vpnPermissionResultLauncher: ActivityResultLauncher<Intent>
    private var myVpnService: MyVpnService? = null
    private var isBound = false

    // JNI methods for VPN handling (C/C++ methods)
    companion object {
        init {
            System.loadLibrary("vpnapp")  // Loading the native library
        }

        // External functions to interact with VPN-related C/C++ code
        @JvmStatic
        external fun startTun2proxy(
            proxyUrl: String,
            tunFd: Int,
            closeFdOnDrop: Boolean,
            tunMtu: Char,
            verbosity: Int,
            dnsStrategy: Int
        ): Int

        @JvmStatic
        external fun stopTun2proxy(): Int
    }

    // onCreate method: Initializes the UI and sets up listeners
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // Sets the layout for the activity

        initView()  // Initializes UI components
        datDir = filesDir.absolutePath  // Sets the directory path for storing data
        setupButtonListeners()  // Sets up listeners for button actions
        copyRequiredAssets()  // Copies necessary assets (geolite data files)
        loadConfig()  // Loads configuration file if it exists

        // Register for VPN permission result using ActivityResultLauncher
        vpnPermissionResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    VpnHandler.startVpnService(this, serviceConnection)  // Start VPN service on permission granted
                } else {
                    Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()  // Show error if permission denied
                }
            }
    }

    // initView method: Initializes UI elements
    private fun initView() {
        inputField = findViewById(R.id.inputField)
        convertButton = findViewById(R.id.convertButton)
        saveButton = findViewById(R.id.saveButton)
        statusText = findViewById(R.id.statusText)
        startXrayButton = findViewById(R.id.startXrayButton)
        stopXrayButton = findViewById(R.id.stopXrayButton)
        pingButton = findViewById(R.id.pingButton)
        pingResultText = findViewById(R.id.pingResultText)
    }

    // setupButtonListeners: Sets listeners for buttons to trigger specific actions
    private fun setupButtonListeners() {
        convertButton.setOnClickListener {
            Conversion.handleConversion(inputField.text.toString(), statusText) { newConfig ->
                config = newConfig  // Update config with the new converted value
            }
        }

        saveButton.setOnClickListener {
            Conversion.saveConfig(config, filesDir, statusText)  // Saves the current config to storage
        }

        startXrayButton.setOnClickListener {
            VpnHandler.startXray(this, datDir, config, statusText) {
                // Request VPN permission and start VPN service if granted
                VpnHandler.requestVpnPermission(this, vpnPermissionResultLauncher::launch) {
                    VpnHandler.startVpnService(this, serviceConnection)
                }
            }
        }

        stopXrayButton.setOnClickListener {
            VpnHandler.stopXray(this, statusText)  // Stops the Xray service
            Tun2proxy.stopTun2proxyInBackground()  // Stops the tun2proxy process
            myVpnService?.stopVpn()  // Stops the Vpn service
        }

        pingButton.setOnClickListener {
            VpnHandler.pingServer(config, datDir, filesDir, pingResultText)  // Sends a ping to the server
        }
    }

    // copyRequiredAssets: Copies essential assets (geosite and geoip files) if they do not exist
    private fun copyRequiredAssets() {
        listOf("geosite.dat", "geoip.dat").forEach { copyAssetIfNotExists(it) }
    }

    // copyAssetIfNotExists: Helper function to copy an asset from the app's assets folder to the internal storage
    private fun copyAssetIfNotExists(assetName: String) {
        val destinationFile = File(datDir, assetName)
        if (!destinationFile.exists()) {
            try {
                assets.open(assetName).use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)  // Copies the file content
                    }
                }
            } catch (e: IOException) {
                Log.e("Asset Copy Error", "Failed to copy $assetName", e)  // Logs an error if copy fails
            }
        }
    }

    // loadConfig: Loads the configuration from a file if it exists
    private fun loadConfig() {
        val configFile = File(filesDir, "config.json")
        if (configFile.exists()) {
            config = configFile.readText()  // Loads the config file
            statusText.text = "Config loaded successfully."
        } else {
            statusText.text = "Config file not found."  // Displays message if config file doesn't exist
        }
    }

    // onDestroy: Unbinds the service when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)  // Unbind the VPN service
            isBound = false
        }
    }

    // ServiceConnection to handle the VPN service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MyVpnService.VpnBinder
            myVpnService = binder.service  // Get the service instance
            isBound = true
            Tun2proxy.startTun2proxyInBackground(myVpnService)  // Starts tun2proxy process with the VPN service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false  // Sets the service bound flag to false when disconnected
        }
    }

}
