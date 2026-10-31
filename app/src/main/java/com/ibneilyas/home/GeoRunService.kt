package com.ibneilyas.home

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.ibneilyas.home.data.HomeGeo
import com.ibneilyas.home.data.MqttRemote
import com.ibneilyas.home.data.MultiNodeRepository
import com.ibneilyas.home.data.NodeStore
import com.ibneilyas.home.data.SceneStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// Ghar ke dayre mein dakhil hote hi Scene khud chalata hai (app band ho tab bhi).
// Chal na sake to purani notification (tap par Scene) fallback mein aati hai.
class GeoRunService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startFg()
        } catch (e: Exception) {
            HomeGeo.clearLast(this)
            HomeGeo.notifyArrived(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            var ok = false
            try { ok = withTimeout(45000L) { runScene() } } catch (e: Exception) { ok = false }
            if (!ok) {
                HomeGeo.clearLast(this@GeoRunService)
                HomeGeo.notifyArrived(this@GeoRunService, false)
            }
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("geo_run", "Home arrival run", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "geo_run")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Welcome home")
            .setContentText("Running your scene")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        else startForeground(7002, n)
    }

    private suspend fun runScene(): Boolean {
        val cfg = HomeGeo.load(this) ?: return false
        if (cfg.sceneId.isEmpty()) return false
        val scene = SceneStore(this).load().firstOrNull { it.id == cfg.sceneId } ?: return false
        val store = NodeStore(this)
        val list = store.load()
        if (!store.real || list.isEmpty()) return false
        val remote = if (store.remoteHost.isNotBlank() && store.remoteUser.isNotBlank() && store.remotePass.isNotEmpty())
            MqttRemote(store.remoteHost, store.remoteUser, store.remotePass, store.remoteEsp) else null
        val repo = MultiNodeRepository(list, scope, remote, onEspId = { id, eid -> store.setEspId(id, eid) })
        try {
            var tries = 0
            while (tries < 40) {
                val d = repo.data.value
                val apps = scene.actions.mapNotNull { a -> d.appliances.firstOrNull { it.id == a.applianceId } }
                if (apps.isNotEmpty() && apps.all { d.isOnline(it) }) break
                delay(500)
                tries++
            }
            var done = 0
            for (a in scene.actions) {
                val d = repo.data.value
                val app = d.appliances.firstOrNull { it.id == a.applianceId } ?: continue
                if (!d.isOnline(app)) continue
                if (d.stateOf(app).isOn != a.on) repo.toggle(app.id)
                done++
            }
            delay(1500)
            return done > 0
        } finally {
            repo.close()
        }
    }

    companion object {
        fun start(ctx: Context): Boolean = try {
            ctx.startForegroundService(Intent(ctx, GeoRunService::class.java))
            true
        } catch (e: Exception) {
            false
        }
    }
}
