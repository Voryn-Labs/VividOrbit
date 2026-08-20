package com.vorynlabs.vividorbit.server

import android.content.Context
import android.util.Log
import com.vorynlabs.vividorbit.data.Channel
import com.vorynlabs.vividorbit.data.ChannelRepository
import com.vorynlabs.vividorbit.data.StartupMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal fun isConstantTimeTokenValid(token: String, sessionToken: String): Boolean {
    if (token.isEmpty() || sessionToken.isEmpty()) return false
    return MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), sessionToken.toByteArray(Charsets.UTF_8))
}

class LocalConfigServer(
    private val context: Context,
    private val repository: ChannelRepository,
    private val port: Int = 10230,
    private val sessionToken: String,
    private val onDataChanged: () -> Unit,
    private val onTuneRequested: (Long) -> Unit
) {
    companion object {
        private const val TAG = "LocalConfigServer"
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_BODY_BYTES = 65536
        private const val SOCKET_TIMEOUT_MS = 10000
        private const val MAX_CONCURRENT_THREADS = 8
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var threadPool: ExecutorService? = null
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    fun start(bindAddress: String? = null): Boolean {
        if (isRunning.get()) return true
        return try {
            val addr = if (bindAddress != null) InetAddress.getByName(bindAddress) else null
            serverSocket = ServerSocket(port, 50, addr)
            isRunning.set(true)

            threadPool = Executors.newFixedThreadPool(MAX_CONCURRENT_THREADS)

            threadPool?.execute {
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

        // Close in-flight client sockets so blocked handlers exit promptly
        // instead of waiting up to SOCKET_TIMEOUT_MS on a read.
        val sockets = activeSockets.toList()
        activeSockets.clear()
        sockets.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }

        // Non-blocking: queued/in-flight handlers finish on their own.
        try {
            threadPool?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        threadPool = null
    }

    private fun acceptLoop() {
        while (isRunning.get()) {
            try {
                val client = serverSocket?.accept() ?: break
                activeSockets.add(client)
                threadPool?.execute {
                    try {
                        handleClient(client)
                    } catch (e: Exception) {
                        Log.d(TAG, "Client socket handled with error: ${e.message}")
                    } finally {
                        activeSockets.remove(client)
                    }
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun readLine(input: BufferedInputStream, maxBytes: Int): String? {
        val bos = ByteArrayOutputStream()
        var bytesRead = 0
        while (bytesRead < maxBytes) {
            val b = input.read()
            if (b == -1) {
                return if (bos.size() == 0) null else bos.toString(Charsets.UTF_8.name())
            }
            bytesRead++
            if (b == '\n'.code) {
                val arr = bos.toByteArray()
                val len = if (arr.isNotEmpty() && arr.last() == '\r'.code.toByte()) arr.size - 1 else arr.size
                return String(arr, 0, len, Charsets.UTF_8)
            }
            bos.write(b)
        }
        return bos.toString(Charsets.UTF_8.name())
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = SOCKET_TIMEOUT_MS
            val input = BufferedInputStream(s.getInputStream())
            val output = s.getOutputStream()

            val requestLine = readLine(input, MAX_HEADER_BYTES) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullPath = parts[1]

            val headers = mutableMapOf<String, String>()
            var totalHeaderBytes = requestLine.length
            var line: String? = readLine(input, MAX_HEADER_BYTES)

            while (!line.isNullOrBlank()) {
                totalHeaderBytes += line.length
                if (totalHeaderBytes > MAX_HEADER_BYTES) {
                    sendJsonResponse(output, 400, JSONObject().put("error", "Header too large"))
                    return
                }

                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val k = line.substring(0, colonIdx).trim().lowercase()
                    val v = line.substring(colonIdx + 1).trim()
                    headers[k] = v
                }
                line = readLine(input, MAX_HEADER_BYTES)
            }

            val contentLength = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_BODY_BYTES)
            val body = if (contentLength > 0) {
                val buffer = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = input.read(buffer, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                String(buffer, 0, totalRead, Charsets.UTF_8)
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

            if (!isConstantTimeTokenValid(providedToken, sessionToken)) {
                sendJsonResponse(output, 401, JSONObject().put("error", "Unauthorized"))
                return
            }

            when {
                method == "GET" && path == "/api/state" ->
                    runBlocking(Dispatchers.IO) { handleGetState(output) }
                method == "POST" && path == "/api/number" ->
                    runBlocking(Dispatchers.IO) { handlePostNumber(output, body) }
                method == "POST" && path == "/api/reorder" ->
                    runBlocking(Dispatchers.IO) { handlePostReorder(output, body) }
                method == "POST" && path == "/api/favorite" -> handlePostFavorite(output, body)
                method == "POST" && path == "/api/hidden" -> handlePostHidden(output, body)
                method == "POST" && path == "/api/config" -> handlePostConfig(output, body)
                method == "POST" && path == "/api/tune" -> handlePostTune(output, body)
                method == "GET" && path == "/api/export" ->
                    runBlocking(Dispatchers.IO) { handleGetExport(output) }
                method == "POST" && path == "/api/import" ->
                    runBlocking(Dispatchers.IO) { handlePostImport(output, body) }
                else -> sendJsonResponse(output, 404, JSONObject().put("error", "Not Found"))
            }
        }
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

    private suspend fun handleGetState(output: OutputStream) {
        val channels = repository.getChannels()
        val favorites = repository.getFavoriteIds()
        val json = JSONObject()
        val array = JSONArray()
        for (ch in channels) {
            val item = JSONObject()
            item.put("id", ch.id)
            item.put("displayNumber", ch.displayNumber)
            item.put("originalNumber", ch.originalDisplayNumber)
            item.put("name", ch.displayName)
            item.put("isFavorite", favorites.contains(ch.id))
            item.put("isHidden", repository.isHidden(ch.id))
            array.put(item)
        }
        json.put("channels", array)
        json.put("customNumbersEnabled", repository.isCustomNumbersEnabled())
        json.put("startupMode", repository.getStartupMode().key)
        json.put("defaultChannelId", repository.getDefaultChannelId())

        val favArray = JSONArray()
        favorites.forEach { favArray.put(it) }
        json.put("favoriteIds", favArray)

        sendJsonResponse(output, 200, json)
    }

    private fun handlePostFavorite(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            val channelId = req.getLong("channelId")
            val isNowFav = repository.toggleFavorite(channelId)
            onDataChanged()

            val resp = JSONObject()
            resp.put("success", true)
            resp.put("channelId", channelId)
            resp.put("isFavorite", isNowFav)
            sendJsonResponse(output, 200, resp)
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private fun handlePostHidden(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            val channelId = req.getLong("channelId")
            val hidden = req.optBoolean("hidden", true)
            repository.setHidden(channelId, hidden)
            onDataChanged()

            val resp = JSONObject()
            resp.put("success", true)
            resp.put("channelId", channelId)
            resp.put("isHidden", hidden)
            sendJsonResponse(output, 200, resp)
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private suspend fun handlePostNumber(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            val channelId = req.getLong("channelId")
            val number = req.getString("number").trim()

            if (!number.matches(Regex("^[0-9]{1,4}$"))) {
                sendJsonResponse(output, 400, JSONObject().put("error", "Invalid number format"))
                return
            }

            val swappedId = repository.assignChannelNumber(channelId, number)
            repository.setCustomNumbersEnabled(true)
            onDataChanged()

            val resp = JSONObject()
            resp.put("success", true)
            if (swappedId != null) resp.put("swappedChannelId", swappedId)
            sendJsonResponse(output, 200, resp)
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private suspend fun handlePostReorder(output: OutputStream, body: String) {
        try {
            val req = JSONObject(body)
            val orderedIds = req.getJSONArray("orderedChannelIds")
            val idList = mutableListOf<Long>()
            for (i in 0 until orderedIds.length()) {
                idList.add(orderedIds.getLong(i))
            }

            val newMap = mutableMapOf<Long, String>()
            idList.forEachIndexed { index, id ->
                newMap[id] = (index + 1).toString()
            }
            repository.saveCustomNumbersMap(newMap)
            repository.setCustomNumbersEnabled(true)
            onDataChanged()

            sendJsonResponse(output, 200, JSONObject().put("success", true))
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

    private suspend fun handleGetExport(output: OutputStream) {
        val channels = repository.getChannels()
        val array = JSONArray()
        for (ch in channels) {
            val item = JSONObject()
            item.put("name", ch.displayName)
            item.put("customNumber", ch.displayNumber)
            item.put("originalNumber", ch.originalDisplayNumber)
            array.put(item)
        }
        sendJsonResponse(output, 200, array)
    }

    private suspend fun handlePostImport(output: OutputStream, body: String) {
        try {
            val array = JSONArray(body)
            val currentChannels = repository.getChannels()
            val newMap = mutableMapOf<Long, String>()
            var matched = 0

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("name")
                val customNum = item.optString("customNumber").trim()
                if (name.isNotBlank() && customNum.matches(Regex("^[0-9]{1,4}$"))) {
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

            val resp = JSONObject()
            resp.put("success", true)
            resp.put("matchedCount", matched)
            sendJsonResponse(output, 200, resp)
        } catch (e: Exception) {
            sendJsonResponse(output, 400, JSONObject().put("error", e.message ?: "Invalid JSON"))
        }
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: Any) {
        try {
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
                    "Connection: close\r\n\r\n"
            output.write(response.toByteArray(Charsets.UTF_8))
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
            Log.d(TAG, "Socket closed during response write: ${e.message}")
        }
    }
}
