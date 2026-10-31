package com.ibneilyas.home.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import com.ibneilyas.home.GeoRunService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.ibneilyas.home.GeoReceiver
import com.ibneilyas.home.MainActivity

data class GeoCfg(
    val enabled: Boolean,
    val lat: Double,
    val lon: Double,
    val radius: Float,
    val sceneId: String,
    val sceneName: String
)

// Ghar aate hi: ghar ke ilaqe mein dakhil hone par notification, tap par Scene chale (Scene chalana app ka kaam hai)
object HomeGeo {
    private const val CH = "ibn_arrive"
    private const val COOLDOWN_MS = 30L * 60L * 1000L

    private fun sp(ctx: Context) = ctx.getSharedPreferences("geo", Context.MODE_PRIVATE)

    fun load(ctx: Context): GeoCfg? {
        val p = sp(ctx)
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
        return GeoCfg(
            p.getBoolean("on", false), lat, lon,
            p.getFloat("radius", 150f),
            p.getString("sid", "") ?: "",
            p.getString("sname", "") ?: ""
        )
    }

    fun save(ctx: Context, c: GeoCfg) {
        val r = if (c.radius < 100f) 100f else if (c.radius > 1000f) 1000f else c.radius
        sp(ctx).edit()
            .putBoolean("on", c.enabled)
            .putString("lat", c.lat.toString())
            .putString("lon", c.lon.toString())
            .putFloat("radius", r)
            .putString("sid", c.sceneId)
            .putString("sname", c.sceneName)
            .apply()
    }

    // Natija: "regat" (kab kamyab), "regerr" (kya galti) prefs mein, taake screen dikha sake
    fun status(ctx: Context): String {
        val p = sp(ctx)
        val err = p.getString("regerr", null)
        if (err != null) return "Error: $err"
        val at = p.getLong("regat", 0L)
        return if (at > 0L) "Registered" else "Not registered"
    }

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, GeoReceiver::class.java)
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) f = f or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(ctx, 0, i, f)
    }

    // true = request bheji gayi (asli natija status() mein)
    @SuppressLint("MissingPermission")
    fun register(ctx: Context): Boolean {
        val c = load(ctx) ?: return false
        if (!c.enabled) return false
        return try {
            val g = Geofence.Builder()
                .setRequestId("home")
                .setCircularRegion(c.lat, c.lon, c.radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
            val req = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofence(g)
                .build()
            LocationServices.getGeofencingClient(ctx).addGeofences(req, pending(ctx))
                .addOnSuccessListener {
                    sp(ctx).edit().putLong("regat", System.currentTimeMillis()).remove("regerr").apply()
                }
                .addOnFailureListener { e ->
                    sp(ctx).edit().putString("regerr", e.message ?: "unknown").apply()
                }
            true
        } catch (e: Exception) {
            sp(ctx).edit().putString("regerr", e.message ?: "unknown").apply()
            false
        }
    }

    fun unregister(ctx: Context) {
        try {
            LocationServices.getGeofencingClient(ctx).removeGeofences(pending(ctx))
            sp(ctx).edit().remove("regat").apply()
        } catch (e: Exception) { }
    }

    fun isAuto(ctx: Context): Boolean = sp(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean("auto", v).apply() }
    fun clearLast(ctx: Context) { sp(ctx).edit().remove("last").apply() }

    fun notifyArrived(ctx: Context, allowAuto: Boolean = true) {
        try {
            val c = load(ctx) ?: return
            if (!c.enabled || c.sceneId.isEmpty()) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val p = sp(ctx)
            val now = System.currentTimeMillis()
            if (now - p.getLong("last", 0L) < COOLDOWN_MS) return
            p.edit().putLong("last", now).apply()
            if (allowAuto && isAuto(ctx) && GeoRunService.start(ctx)) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CH, "Home arrival", NotificationManager.IMPORTANCE_HIGH))
            val open = Intent(ctx, MainActivity::class.java)
            open.putExtra("geoScene", c.sceneId)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            val pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Welcome home")
                .setContentText("Tap to run: " + c.sceneName)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(7001, n)
        } catch (e: Exception) { }
    }
}
