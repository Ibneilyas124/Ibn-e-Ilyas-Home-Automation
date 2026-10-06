package com.ibneilyas.home.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Live state push from each ESP32 (WebSocket, port 81). Polling stays as the fallback. */
class SocketHub(
    private val configs: List<NodeConfig>,
    private val onState: (String, List<Boolean>) -> Unit
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val connected = ConcurrentHashMap<String, Boolean>()

    fun isOpen(id: String): Boolean = connected[id] == true

    fun ensure(id: String) {
        synchronized(this) {
            if (sockets.containsKey(id)) return
            val c = configs.firstOrNull { it.id == id } ?: return
            val req = Request.Builder().url("ws://${c.ip}:81").build()
            sockets[id] = http.newWebSocket(req, Listener(id, c.token))
        }
    }

    private fun drop(id: String) {
        synchronized(this) {
            sockets.remove(id)
            connected.remove(id)
        }
    }

    fun closeAll() {
        synchronized(this) {
            sockets.values.forEach { it.cancel() }
            sockets.clear()
            connected.clear()
        }
    }

    private inner class Listener(val id: String, val token: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("auth $token")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            connected[id] = true
            try {
                val arr = JSONObject(text).getJSONArray("channels")
                onState(id, List(arr.length()) { arr.getBoolean(it) })
            } catch (e: Exception) {
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = drop(id)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = drop(id)
    }
}
