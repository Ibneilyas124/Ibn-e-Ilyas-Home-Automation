package com.ibneilyas.home.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UsageRow(val node: String, val ch: Int, val on: Boolean, val src: Int, val t: Long)
data class UsageItem(val node: String, val ch: Int, val onSec: Long, val times: Int)

class UsageStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val keepSec = 30L * 86400L
    private val maxRows = 1500

    fun load(): List<UsageRow> {
        val arr = try { JSONArray(p.getString("rows", "[]")) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<UsageRow>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(UsageRow(o.optString("n"), o.optInt("c"), o.optInt("o") == 1, o.optInt("s"), o.optLong("t")))
        }
        return out
    }

    fun merge(fresh: List<UsageRow>) {
        val now = System.currentTimeMillis() / 1000
        val seen = HashSet<String>()
        val all = ArrayList<UsageRow>()
        for (r in load() + fresh) {
            if (r.t <= 0L || r.t < now - keepSec) continue
            if (seen.add(r.node + "|" + r.ch + "|" + r.on + "|" + r.t)) all.add(r)
        }
        all.sortByDescending { it.t }
        val keep = if (all.size > maxRows) all.subList(0, maxRows) else all
        val arr = JSONArray()
        keep.forEach { arr.put(JSONObject().put("n", it.node).put("c", it.ch).put("o", if (it.on) 1 else 0).put("s", it.src).put("t", it.t)) }
        p.edit().putString("rows", arr.toString()).apply()
    }

    private fun overlap(a: Long, b: Long, from: Long, to: Long): Long {
        val s = if (a > from) a else from
        val e = if (b < to) b else to
        return if (e > s) e - s else 0L
    }

    // Pichle 7 din: har device kitni der on raha aur kitni baar on hua (andaza)
    fun usage7(now: Long): List<UsageItem> {
        val from = now - 7L * 86400L
        val out = ArrayList<UsageItem>()
        for ((_, g) in load().groupBy { it.node + "|" + it.ch }) {
            val ev = g.sortedBy { it.t }
            var onAt = -1L
            var sec = 0L
            var times = 0
            for (r in ev) {
                if (r.on) {
                    if (onAt < 0L) {
                        onAt = r.t
                        if (r.t >= from) times++
                    }
                } else if (onAt >= 0L) {
                    sec += overlap(onAt, r.t, from, now)
                    onAt = -1L
                }
            }
            if (onAt >= 0L) sec += overlap(onAt, now, from, now)
            if (sec > 0L || times > 0) out.add(UsageItem(ev[0].node, ev[0].ch, sec, times))
        }
        return out.sortedByDescending { it.onSec }
    }
}
