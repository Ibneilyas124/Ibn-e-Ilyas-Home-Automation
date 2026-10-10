package com.ibneilyas.home.domain

/** Reads and writes the schedule text stored on the ESP32: "hour,minute,days,channel,on;..." */
object SchedCodec {
    fun parse(nodeId: String, s: String): List<SchedEntry> = s.split(";").mapNotNull { p ->
        val x = p.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (x.size == 5) SchedEntry(nodeId, x[0], x[1], x[2], x[3], x[4] == 1) else null
    }

    fun encode(list: List<SchedEntry>): String =
        list.joinToString(";") { "${it.h},${it.m},${it.days},${it.ch},${if (it.on) 1 else 0}" }
}
