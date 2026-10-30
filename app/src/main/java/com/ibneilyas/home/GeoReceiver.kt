package com.ibneilyas.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.ibneilyas.home.data.HomeGeo

class GeoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        if (ev.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        HomeGeo.notifyArrived(ctx)
    }
}

// Phone restart par geofence dobara lagti hai
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        HomeGeo.register(ctx)
    }
}
