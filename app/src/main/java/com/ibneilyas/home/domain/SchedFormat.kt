package com.ibneilyas.home.domain

import java.util.Locale

object SchedFormat {
    private val order = listOf(1, 2, 3, 4, 5, 6, 0)
    private val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun days(mask: Int): String {
        val m = mask and 127
        return when (m) {
            127 -> "Every day"
            62 -> "Mon-Fri"
            65 -> "Sat, Sun"
            0 -> "No days"
            else -> order.filter { (m and (1 shl it)) != 0 }.joinToString(", ") { names[it] }
        }
    }

    fun time(h: Int, m: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
    }
}
