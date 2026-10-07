package com.ibneilyas.home.domain

/** Rough "how does it sound" matching, so a mis-heard room or device name can still be recognised. */
object Sound {
    private val swaps = listOf(
        "tch" to "x", "ch" to "x", "sh" to "x", "ph" to "f",
        "ck" to "k", "kn" to "n", "wr" to "r", "gh" to ""
    )

    fun key(w: String): String {
        var s = w.lowercase().filter { it in 'a'..'z' }
        for ((a, b) in swaps) s = s.replace(a, b)
        val sb = StringBuilder()
        for (c in s) {
            val m = when (c) {
                'a', 'e', 'i', 'o', 'u', 'y', 'h', 'w' -> continue
                'c', 'q' -> 'k'
                'z', 'j' -> 's'
                else -> c
            }
            if (sb.isEmpty() || sb[sb.length - 1] != m) sb.append(m)
        }
        return sb.toString()
    }

    /** The one known word that sounds the same, or null when there is none or more than one. */
    fun match(w: String, known: Set<String>): String? {
        if (w.length < 4) return null
        val k = key(w)
        if (k.length < 3) return null
        val hit = known.filter { it.length >= 4 && key(it) == k }
        return if (hit.size == 1) hit[0] else null
    }
}
