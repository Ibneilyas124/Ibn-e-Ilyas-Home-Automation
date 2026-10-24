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
    private val pass: String
) {
    @Volatile private var onState: ((String, List<Boolean>) -> Unit)? = null

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
            .topicFilter("ibn/+/state")
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { p -> parse(p.payloadAsBytes, cb) }
            .send()
    }

    private fun parse(b: ByteArray, cb: (String, List<Boolean>) -> Unit) {
        try {
            val o = JSONObject(String(b, Charsets.UTF_8))
            val a = o.getJSONArray("channels")
            cb(o.getString("id"), List(a.length()) { a.getBoolean(it) })
        } catch (e: Exception) {
        }
    }

    fun start(cb: (String, List<Boolean>) -> Unit) {
        onState = cb
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

    fun close() {
        try { client.disconnect() } catch (e: Exception) { }
    }
}
