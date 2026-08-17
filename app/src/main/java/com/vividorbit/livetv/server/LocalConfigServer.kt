package com.vividorbit.livetv.server

import android.content.Context
import android.util.Log
import com.vividorbit.livetv.data.Channel
import com.vividorbit.livetv.data.ChannelRepository
import com.vividorbit.livetv.data.StartupMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalConfigServer(
    private val context: Context,
    private val repository: ChannelRepository,
    private val port: Int = 8080,
    private val sessionToken: String,
    private val onDataChanged: () -> Unit,
    private val onTuneRequested: (Long) -> Unit
) {
    companion object {
        private const val TAG = "LocalConfigServer"
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val threadPool = Executors.newCachedThreadPool()
    private val serverScope = CoroutineScope(Dispatchers.IO)

    fun start(bindAddress: String? = null): Boolean {
        if (isRunning.get()) return true
        return try {
            val addr = if (bindAddress != null) InetAddress.getByName(bindAddress) else null
            serverSocket = ServerSocket(port, 50, addr)
            isRunning.set(true)
            threadPool.execute {
                acceptLoop()
            }
            Log.i(TAG, "Server started on port $port, bound to $bindAddress")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server: ${e.message}", e)
            false
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
    }

    private fun acceptLoop() {
        while (isRunning.get()) {
            try {
                val client = serverSocket?.accept() ?: break
                threadPool.execute {
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 10000
            val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val output = s.getOutputStream()

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullPath = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String? = input.readLine()
            var contentLength = 0
            while (!line.isNullOrBlank()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val k = line.substring(0, colonIdx).trim().lowercase()
                    val v = line.substring(colonIdx + 1).trim()
                    headers[k] = v
                    if (k == "content-length") {
                        contentLength = v.toIntOrNull() ?: 0
                    }
                }
                line = input.readLine()
            }

            val body = if (contentLength in 1..65536) {
                val charArray = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = input.read(charArray, read, contentLength - read)
                    if (count == -1) break
                    read += count
                }
                String(charArray, 0, read)
            } else ""

            val path = if (fullPath.contains('?')) fullPath.substringBefore('?') else fullPath
            val query = if (fullPath.contains('?')) fullPath.substringAfter('?') else ""
            val queryParams = parseQueryParams(query)

            val tokenFromHeader = headers["x-token"]
            val tokenFromQuery = queryParams["t"]
            val providedToken = tokenFromHeader ?: tokenFromQuery ?: ""

            if (path == "/" || path == "/index.html") {
                serveHtml(output, providedToken)
                return
            }

            if (!isTokenValid(providedToken)) {
                sendJsonResponse(output, 401, JSONObject().put("error", "Unauthorized"))
                return
            }

            when {
                method == "GET" && path == "/api/state" -> handleGetState(output)
                method == "POST" && path == "/api/number" -> handlePostNumber(output, body)
                method == "POST" && path == "/api/reorder" -> handlePostReorder(output, body)
                method == "POST" && path == "/api/config" -> handlePostConfig(output, body)
                method == "POST" && path == "/api/tune" -> handlePostTune(output, body)
                method == "GET" && path == "/api/export" -> handleGetExport(output)
                method == "POST" && path == "/api/import" -> handlePostImport(output, body)
                else -> sendJsonResponse(output, 404, JSONObject().put("error", "Not Found"))
            }
        }
    }

    private fun isTokenValid(token: String): Boolean {
        if (token.isEmpty() || sessionToken.isEmpty()) return false
        return MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), sessionToken.toByteArray(Charsets.UTF_8))
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isBlank()) return result
        val pairs = query.split('&')
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx != -1) {
                result[pair.substring(0, idx)] = pair.substring(idx + 1)
            } else {
                result[pair] = ""
            }
        }
        return result
    }

    private fun serveHtml(output: OutputStream, token: String) {
        try {
            var html = context.assets.open("web/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (token.isNotEmpty()) {
                html = html.replace("__SESSION_TOKEN__", token)
            }
            val bytes = html.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
            sendJsonResponse(output, 500, JSONObject().put("error", "Failed to load index.html"))
        }
    }

    private fun handleGetState(output: OutputStream) {
        serverScope.launch {
            val channels = repository.getChannels()
            val json = JSONObject()
            val array = JSONArray()
            for (ch in channels) {
                val item = JSONObject()
                item.put("id", ch.id)
                item.put("displayNumber", ch.displayNumber)
                item.put("originalNumber", ch.originalDisplayNumber)
                item.put("name", ch.displayName)
                array.put(item)
            }
            json.put("channels", array)
            json.put("customNumbersEnabled", repository.isCustomNumbersEnabled())
            json.put("startupMode", repository.getStartupMode().key)
            json.put("defaultChannelId", repository.getDefaultChannelId())

            withContext(Dispatchers.IO) {
                sendJsonResponse(output, 200, json)
            }
        }
    }

    private fun handlePostNumber(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            val channelId = req.getLong("channelId")
            val number = req.getString("number").trim()

            if (!number.matches(Regex("^[0-9]{1,4}$"))) {
                sendJsonResponse(output, 400, JSONObject().put("error", "Invalid number format"))
                return
            }

            serverScope.launch {
                val swappedId = repository.assignChannelNumber(channelId, number)
                repository.setCustomNumbersEnabled(true)
                onDataChanged()

                withContext(Dispatchers.IO) {
                    val resp = JSONObject()
                    resp.put("success", true)
                    if (swappedId != null) resp.put("swappedChannelId", swappedId)
                    sendJsonResponse(output, 200, resp)
                }
            }
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private fun handlePostReorder(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            val orderedIds = req.getJSONArray("orderedChannelIds")
            val idList = mutableListOf<Long>()
            for (i in 0 until orderedIds.length()) {
                idList.add(orderedIds.getLong(i))
            }

            serverScope.launch {
                val newMap = mutableMapOf<Long, String>()
                idList.forEachIndexed { index, id ->
                    newMap[id] = (index + 1).toString()
                }
                repository.saveCustomNumbersMap(newMap)
                repository.setCustomNumbersEnabled(true)
                onDataChanged()

                withContext(Dispatchers.IO) {
                    sendJsonResponse(output, 200, JSONObject().put("success", true))
                }
            }
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private fun handlePostConfig(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            if (req.has("customNumbersEnabled")) {
                repository.setCustomNumbersEnabled(req.getBoolean("customNumbersEnabled"))
            }
            if (req.has("startupMode")) {
                val modeKey = req.getString("startupMode")
                repository.setStartupMode(StartupMode.fromKey(modeKey))
            }
            if (req.has("defaultChannelId")) {
                repository.setDefaultChannelId(req.getLong("defaultChannelId"))
            }

            onDataChanged()
            sendJsonResponse(output, 200, JSONObject().put("success", true))
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private fun handlePostTune(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            val channelId = req.getLong("channelId")
            onTuneRequested(channelId)
            sendJsonResponse(output, 200, JSONObject().put("success", true))
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private fun handleGetExport(output: OutputStream) {
        serverScope.launch {
            val channels = repository.getChannels()
            val array = JSONArray()
            for (ch in channels) {
                val item = JSONObject()
                item.put("name", ch.displayName)
                item.put("customNumber", ch.displayNumber)
                item.put("originalNumber", ch.originalDisplayNumber)
                array.put(item)
            }
            withContext(Dispatchers.IO) {
                sendJsonResponse(output, 200, array)
            }
        }
    }

    private fun handlePostImport(output: OutputStream, body: String) {
        try {
            val array = JSONArray(body)
            serverScope.launch {
                val currentChannels = repository.getChannels()
                val newMap = mutableMapOf<Long, String>()
                var matched = 0

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val name = item.optString("name")
                    val customNum = item.optString("customNumber")
                    if (name.isNotBlank() && customNum.isNotBlank()) {
                        val match = currentChannels.find { it.displayName.equals(name, ignoreCase = true) }
                        if (match != null) {
                            newMap[match.id] = customNum
                            matched++
                        }
                    }
                }

                if (newMap.isNotEmpty()) {
                    repository.saveCustomNumbersMap(newMap)
                    repository.setCustomNumbersEnabled(true)
                    onDataChanged()
                }

                withContext(Dispatchers.IO) {
                    val resp = JSONObject()
                    resp.put("success", true)
                    resp.put("matchedCount", matched)
                    sendJsonResponse(output, 200, resp)
                }
            }
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: Any) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}
