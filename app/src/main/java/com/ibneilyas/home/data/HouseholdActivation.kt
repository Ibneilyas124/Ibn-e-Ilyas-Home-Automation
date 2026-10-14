package com.ibneilyas.home.data

import android.content.Context
import com.ibneilyas.home.core.License
import com.ibneilyas.home.core.LicenseKey

/** Activates a phone with a household key. The controller counts how many phones the key allows. */
object HouseholdActivation {
    /** Returns null when the phone is activated, or a message to show the user. */
    suspend fun run(ctx: Context, key: String, ip: String, token: String): String? {
        val h = License.parseHousehold(LicenseKey.PUBLIC_B64, key) ?: return "This household key is not valid."
        val host = ip.trim().removePrefix("http://").trimEnd('/')
        val client = HttpNodeClient("http://" + host, token.trim())
        val info = client.info() ?: return "Cannot reach the controller at " + host + ". Join the home Wi-Fi and check the IP address."
        val id = Regex("\"id\":\"([^\"]*)\"").find(info)?.groupValues?.get(1).orEmpty()
        if (id != h.espId) return "This key belongs to another controller (" + id + ")."
        val r = client.seat(License.deviceId(ctx), h.max) ?: return "The controller did not answer. Try again."
        return when (r.first) {
            200 -> {
                License.saveHousehold(ctx, key)
                null
            }
            401 -> "Wrong controller token."
            403 -> "All " + h.max + " phones for this key are already in use."
            else -> "The controller refused (code " + r.first + "). Update its firmware to 0.6.0 or newer."
        }
    }
}
