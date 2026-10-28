package com.ibneilyas.home.data

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Remote path: cloud broker over TLS (port 8883). Used only when the local ESP32 is unreachable. */
class MqttRemote(
    host: String,
    private val user: String,
    private val pass: String,
    private val esp: String = ""
) {
    @Volatile private var onState: ((String, List<Boolean>) -> Unit)? = null
    @Volatile private var onOnline: ((String, Boolean) -> Unit)? = null
    private val who: String = esp.trim().ifBlank { "+" }
    private val schedMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val schedSeq = java.util.concurrent.atomic.AtomicInteger(0)

    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .identifier("ibn-app-" + UUID.randomUUID())
        .serverHost(host)
        .serverPort(8883)
        .sslWithDefaultConfig()
        .automaticReconnectWithDefaultConfig()
        .addConnectedListener { subscribeNow() }
        .useMqttVersion3()
        .buildAsync()

    private fun subscribeNow() {
        val cb = onState ?: return
        client.subscribeWith()
            .topicFilter("ibn/$who/state")
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { p -> parse(p.payloadAsBytes, cb) }
            .send()
        client.subscribeWith()
            .topicFilter("ibn/$who/sch/state")
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { p ->
                val parts = p.topic.toString().split("/")
                val txt = String(p.payloadAsBytes, Charsets.UTF_8)
                if (parts.size == 4 && txt.startsWith("S:")) {
                    schedMap[parts[1]] = txt.substring(2)
                    schedSeq.incrementAndGet()
                }
            }
            .send()
        val ol = onOnline
        if (ol != null) {
            client.subscribeWith()
                .topicFilter("ibn/$who/online")
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback { p ->
                    val parts = p.topic.toString().split("/")
                    if (parts.size == 3) ol(parts[1], String(p.payloadAsBytes, Charsets.UTF_8).trim() == "1")
                }
                .send()
        }
    }

    private fun parse(b: ByteArray, cb: (String, List<Boolean>) -> Unit) {
        try {
            val o = JSONObject(String(b, Charsets.UTF_8))
            val a = o.getJSONArray("channels")
            cb(o.getString("id"), List(a.length()) { a.getBoolean(it) })
        } catch (e: Exception) {
        }
    }

    fun start(cb: (String, List<Boolean>) -> Unit, onl: (String, Boolean) -> Unit) {
        onState = cb
        onOnline = onl
        client.connectWith()
            .simpleAuth()
            .username(user)
            .password(pass.toByteArray(Charsets.UTF_8))
            .applySimpleAuth()
            .send()
    }

    /** Blocking: call from a background thread only. */
    fun command(id: String, ch: Int, on: Boolean, token: String): Boolean = try {
        client.publishWith()
            .topic("ibn/$id/cmd/$ch")
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload("$token:${if (on) 1 else 0}".toByteArray(Charsets.UTF_8))
            .send()
            .get(6, TimeUnit.SECONDS)
        true
    } catch (e: Exception) {
        false
    }

    /** Blocking: call from a background thread only. Timer over the cloud: payload token:seconds:on. */
    fun timer(id: String, ch: Int, sec: Int, on: Boolean, token: String): Boolean = try {
        client.publishWith()
            .topic("ibn/$id/tmr/$ch")
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload("$token:$sec:${if (on) 1 else 0}".toByteArray(Charsets.UTF_8))
            .send()
            .get(6, TimeUnit.SECONDS)
        true
    } catch (e: Exception) {
        false
    }

    fun schedText(id: String): String? = schedMap[id]

    /** Blocking. Sends the schedule and returns the list the ESP32 publishes back, or null. */
    fun schedule(id: String, text: String, token: String): String? {
        return try {
            val before = schedSeq.get()
            client.publishWith()
                .topic("ibn/$id/sch/set")
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload("$token:$text".toByteArray(Charsets.UTF_8))
                .send()
                .get(6, TimeUnit.SECONDS)
            val end = System.currentTimeMillis() + 8000
            var out: String? = null
            while (System.currentTimeMillis() < end) {
                if (schedSeq.get() > before) { out = schedMap[id]; break }
                Thread.sleep(200)
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        try { client.disconnect() } catch (e: Exception) { }
    }
}
