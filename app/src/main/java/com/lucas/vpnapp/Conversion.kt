package com.lucas.vpnapp

import android.util.Base64
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object Conversion {

    // Function to handle the conversion of input text into a configuration JSON string
    fun handleConversion(inputText: String, statusText: TextView, onConfigConverted: (String) -> Unit) {
        // Check if input text is empty and prompt the user to enter valid input
        if (inputText.isEmpty()) {
            Toast.makeText(statusText.context, "Please enter a share link or text", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Sanitize input by removing carriage returns and split by line breaks
            val sanitizedInput = inputText.replace("\r", "")
            val links = sanitizedInput.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

            // Parse each link and convert them into configuration JSON objects
            val configs = links.mapNotNull { parseLink(it) }
            val configJson = JSONObject().apply {
                put("outbounds", JSONArray(configs))  // Add the parsed configurations into the JSON object
            }

            // Trigger the callback function with the generated JSON configuration
            onConfigConverted(configJson.toString())
            statusText.text = "Conversion successful. Ready to save."  // Update status text
        } catch (e: Exception) {
            Log.e("Conversion Error", "Error during conversion", e)  // Log any errors during conversion
            statusText.text = "Failed to convert: ${e.message}"  // Display error message
        }
    }

    // Function to save the configuration to a JSON file
    fun saveConfig(config: String?, filesDir: File, statusText: TextView) {
        // Check if config is null or empty
        if (config.isNullOrEmpty()) {
            Toast.makeText(statusText.context, "No config to save", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Open or create the config.json file in the application's files directory
            val configFile = File(filesDir, "config.json")
            val existingJson = if (configFile.exists()) {
                // If config.json exists, read it and convert it into a JSONObject
                JSONObject(configFile.readText())
            } else {
                // If config.json doesn't exist, create an empty JSONObject
                JSONObject()
            }

            val newConfigJson = JSONObject(config)  // Convert the provided config string to JSONObject

            // Append default or required sections (log, inbounds, outbounds, routing)
            val updatedLog = createLog()
            val updatedInbounds = createInbounds()
            val updatedOutbounds = newConfigJson.optJSONArray("outbounds")
            val updatedRouting = createRouting()

            // Update the existing JSON with new information
            existingJson.put("log", updatedLog)
            existingJson.put("inbounds", updatedInbounds)
            existingJson.put("outbounds", updatedOutbounds)
            existingJson.put("routing", updatedRouting)

            // Write the updated JSON back to the file
            configFile.writeText(existingJson.toString(4))

            // Update the status text
            statusText.text = "Config saved successfully."
        } catch (e: Exception) {
            Log.e("Save Config", "Error saving config", e)  // Log any errors during saving
            statusText.text = "Failed to save config: ${e.message}"  // Display error message
        }
    }

    // Helper function to create a default log configuration
    private fun createLog(): JSONObject {
        return JSONObject().apply {
            put("loglevel", "debug")  // Set log level to debug for more verbose logs
            put("access", "")         // Placeholder for access log path
            put("error", "")          // Placeholder for error log path
        }
    }

    private fun createInbounds(): JSONArray {
        return JSONArray().apply {
            // SOCKS inbound configuration
            put(JSONObject().apply {
                put("tag", "socks")           // Tag for this inbound
                put("port", 1080)            // Local port for the proxy
                put("listen", "127.0.0.1")      // Listen on all interfaces
                put("protocol", "socks")      // SOCKS protocol
                put("sniffing", JSONObject().apply {
                    put("enabled", true)      // Enable sniffing
                    put("destOverride", JSONArray().apply {
                        put("http")          // Override HTTP traffic
                        put("tls")           // Override TLS traffic
                    })
                })
                put("settings", JSONObject().apply {
                    put("auth", "noauth")     // No authentication
                    put("udp", true)          // Enable UDP relay
                    put("allowTransparent", false) // Disallow transparent proxy
                })
            })
        }
    }

    private fun createRouting(): JSONObject {
        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")    // Use IP strategy if no domain match
            put("domainMatcher", "hybrid")            // Hybrid domain matcher strategy
            put("rules", JSONArray().apply {
                // Block ads
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply { put("geosite:category-ads-all") })
                    put("outboundTag", "block")
                })
                // Direct route for China domains
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply { put("geosite:cn") })
                    put("outboundTag", "direct")
                })
                // Direct route for private and China IPs
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().apply {
                        put("geoip:private")
                        put("geoip:cn")
                    })
                    put("outboundTag", "direct")
                })
                // Proxy all ports
                put(JSONObject().apply {
                    put("type", "field")
                    put("port", "0-65535")
                    put("outboundTag", "proxy")
                })
            })
        }
    }

    private fun decodeBase64Text(input: String): String {
        val decodedBytes = Base64.decode(input, Base64.DEFAULT)
        return String(decodedBytes, StandardCharsets.UTF_8)
    }

    private fun parseLink(link: String): JSONObject? {
        return when {
            link.startsWith("vmess://") -> parseVmessLink(link)
            link.startsWith("vless://") -> parseVlessLink(link)
            link.startsWith("trojan://") -> parseTrojanLink(link)
            else -> null
        }
    }

    private fun parseVmessLink(link: String): JSONObject? {
        return try {
            // Decode the base64 text from the link
            val decoded = decodeBase64Text(link.substring(8))
            val jsonObject = JSONObject(decoded)

            // Create the JSON object for the Vmess link
            JSONObject().apply {
                put("tag", jsonObject.optString("ps", ""))  // Default to empty if not present
                put("protocol", "vmess")
                put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            // Extract address and port
                            put("address", jsonObject.getString("add"))
                            put("port", jsonObject.getInt("port"))

                            // Add users array
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", jsonObject.getString("id"))
                                    put("alterId", jsonObject.getInt("aid"))
                                    put("security", jsonObject.optString("scy", ""))
                                })
                            })
                        })
                    })
                })
                put("streamSettings", JSONObject().apply {
                    put("network", jsonObject.optString("net", ""))
                    put("security", jsonObject.optString("tls", ""))
                    put("tcpSettings", JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("type", jsonObject.optString("type", ""))
                        })
                    })
                })
            }
        } catch (e: Exception) {
            Log.e("Parse Vmess", "Error parsing vmess link", e)
            null
        }
    }

    private fun parseVlessLink(link: String): JSONObject? {
        return try {
            // Remove the "vless://" prefix and split by '@'
            val uriParts = link.substring(8).split("@", limit = 2)
            val userId = uriParts[0]

            // Split by ':' to get address and port
            val serverInfo = uriParts[1].split(":", limit = 2)

            // Extract port by splitting at any potential query string (if present)
            val portAndQuery = serverInfo[1].split("?", limit = 2)[0]  // Get only the port part
            val port = portAndQuery.toIntOrNull() ?: throw NumberFormatException("Invalid port")

            // Parse the query parameters (everything after the "?")
            val queryParams = serverInfo[1].split("?").getOrNull(1)
            val queryMap = mutableMapOf<String, String>()
            queryParams?.split("&")?.forEach {
                val param = it.split("=")
                if (param.size == 2) {
                    queryMap[param[0]] = param[1]
                }
            }

            // Extract the tag from the fragment part (everything after "#")
            val tag = uriParts[1].split("#").getOrNull(1)?.let {
                URLDecoder.decode(it, "UTF-8")  // Decode URL-encoded tag
            }

            // Extract the headerType and separate the URL-encoded portion
            val headerType = queryMap["headerType"]?.split("#")?.get(0) ?: "none"  // Only take the first part ("http")

            // Create the JSON object for the VLESS link
            val json = JSONObject().apply {
                put("tag", tag)
                put("protocol", "vless")
                put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", serverInfo[0])
                            put("port", port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", userId)
                                    put("encryption", "none")
                                })
                            })
                        })
                    })
                })
                put("streamSettings", JSONObject().apply {
                    put("network", queryMap["type"] ?: "none")
                    put("security", queryMap["security"] ?: "none")
                    put("tcpSettings", JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("type", headerType)
                        })
                    })
                })
            }

            // Beautify the JSON by calling toString(4)
            JSONObject(json.toString(4))

        } catch (e: Exception) {
            Log.e("Parse VLESS", "Error parsing VLESS link", e)
            null
        }
    }


    private fun parseTrojanLink(link: String): JSONObject? {
        return try {
            // Split the link into the password and server info part
            val uriParts = link.substring(9).split("@", limit = 2)
            val password = uriParts[0]

            // Split the server info into address and port, considering possible query parameters
            val serverInfo = uriParts[1].split(":")
            val address = serverInfo[0]
            val portAndQuery = serverInfo[1].split("?")[0]
            val port = portAndQuery.toInt()  // Ignore query parameters after the port

            // Parse the query parameters (everything after the "?")
            val queryParams = uriParts[1].split("?").getOrNull(1)
            val queryMap = mutableMapOf<String, String>()
            queryParams?.split("&")?.forEach {
                val param = it.split("=")
                if (param.size == 2) {
                    queryMap[param[0]] = param[1]
                }
            }

            // Extract the tag from the fragment part (everything after "#")
            val tag = uriParts[1].split("#").getOrNull(1)?.let {
                URLDecoder.decode(it, "UTF-8")  // Decode URL-encoded tag
            }

            // Extract the headerType and separate the URL-encoded portion
            val headerType = queryMap["headerType"]?.split("#")?.get(0) ?: "none"  // Only take the first part ("http")

            // Create the JSONObject for the Trojan link
            val json = JSONObject().apply {
                put("tag", tag)
                put("protocol", "trojan")
                put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", address)
                            put("port", port)
                            put("password", password)
                        })
                    })
                })
                put("streamSettings", JSONObject().apply {
                    put("network", queryMap["type"] ?: "none")
                    put("security", queryMap["security"] ?: "none")
                    put("tcpSettings", JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("type", headerType)
                        })
                    })
                })
            }

            // Beautify the JSON by calling toString(4) directly
            val beautifiedJson = json.toString(4)
            Log.d("Beautified JSON", beautifiedJson)

            // Optionally, if you need to return the beautified JSON as a JSONObject:
            JSONObject(beautifiedJson)

        } catch (e: Exception) {
            Log.e("Parse Trojan", "Error parsing Trojan link", e)
            null
        }
    }

}