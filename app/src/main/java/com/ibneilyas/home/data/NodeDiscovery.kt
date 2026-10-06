package com.ibneilyas.home.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

data class Found(val name: String, val ip: String)

/** Finds ESP32 nodes on the local Wi-Fi (mDNS service _ibnhome._tcp). */
class NodeDiscovery(ctx: Context) {
    private val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var disc: NsdManager.DiscoveryListener? = null
    private var onFound: (Found) -> Unit = {}
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var busy = false

    fun start(found: (Found) -> Unit, state: (Boolean) -> Unit) {
        stop()
        onFound = found
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { main.post { state(true) } }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) { enqueue(serviceInfo) }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {
                disc = null
                main.post { state(false) }
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                disc = null
                main.post { state(false) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        disc = l
        try {
            nsd.discoverServices("_ibnhome._tcp", NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            disc = null
            state(false)
        }
    }

    private fun enqueue(info: NsdServiceInfo) {
        synchronized(this) { queue.addLast(info) }
        next()
    }

    private fun next() {
        val info = synchronized(this) {
            if (busy || queue.isEmpty()) null else { busy = true; queue.removeFirst() }
        } ?: return
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { done() }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val ip = serviceInfo.host?.hostAddress
                    if (ip != null && !ip.contains(':')) main.post { onFound(Found(serviceInfo.serviceName, ip)) }
                    done()
                }
            })
        } catch (e: Exception) {
            done()
        }
    }

    private fun done() {
        synchronized(this) { busy = false }
        next()
    }

    fun stop() {
        disc?.let { try { nsd.stopServiceDiscovery(it) } catch (e: Exception) { } }
        synchronized(this) { queue.clear(); busy = false }
    }
}
