package com.ibneilyas.home.data

import android.content.Context

/** Saved connection settings. Token is plain prefs for now; encrypt in polish phase. */
class ConnectionStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("conn", Context.MODE_PRIVATE)

    var ip: String
        get() = p.getString("ip", "").orEmpty()
        set(v) { p.edit().putString("ip", v).apply() }

    var token: String
        get() = p.getString("token", "").orEmpty()
        set(v) { p.edit().putString("token", v).apply() }

    var real: Boolean
        get() = p.getBoolean("real", false)
        set(v) { p.edit().putBoolean("real", v).apply() }
}
