package com.ibneilyas.home.data

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object Alert {
    private const val CH = "ibn_update"

    fun ask(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT < 33) return
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
            var c: Context = ctx
            while (c is ContextWrapper) {
                if (c is Activity) {
                    ActivityCompat.requestPermissions(c, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
                    return
                }
                c = c.baseContext
            }
        } catch (e: Exception) { }
    }

    fun notify(ctx: Context, title: String, text: String) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CH, "Update alerts", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
            val n = Notification.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (e: Exception) { }
    }
}
