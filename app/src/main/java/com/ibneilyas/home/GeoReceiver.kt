package com.ibneilyas.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.ibneilyas.home.data.HomeGeo

class GeoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent)
        if (ev == null) { HomeGeo.glog(ctx, "receiver: no event in intent"); return }
        if (ev.hasError()) { HomeGeo.glog(ctx, "receiver: error code " + ev.errorCode); return }
        HomeGeo.glog(ctx, "receiver: transition " + ev.geofenceTransition + " (1=enter)")
        if (ev.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) { HomeGeo.glog(ctx, "receiver: exited home area"); return }
        if (ev.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        if (HomeGeo.justRegistered(ctx)) { HomeGeo.glog(ctx, "receiver: enter ignored, geofence was just registered"); return }
        HomeGeo.notifyArrived(ctx)
    }
}

// Phone restart ya app update par geofence dobara lagti hai
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        HomeGeo.glog(ctx, "receiver: re-register after " + (if (a == Intent.ACTION_MY_PACKAGE_REPLACED) "app update" else "boot"))
        HomeGeo.register(ctx)
    }
}
