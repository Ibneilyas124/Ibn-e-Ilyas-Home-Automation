package com.ibneilyas.home.domain

data class Dur(val seconds: Int, val rest: String, val forDuration: Boolean)

/** "30 minute baad", "in 2 hours", "for 10 min" -> seconds, plus the sentence without the time part. */
object VoiceTime {
    private val re = Regex(
        "(\\d{1,3})\\s*(minutes?|mins?|hours?|hrs?|ghantay|ghante|ghanta|seconds?|secs?|منٹ|گھنٹے|گھنٹہ|سیکنڈ)",
        RegexOption.IGNORE_CASE
    )

    fun extract(text: String): Dur? {
        val m = re.find(text) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val u = m.groupValues[2].lowercase()
        val mult = when {
            u.startsWith("h") || u.startsWith("g") || u == "گھنٹے" || u == "گھنٹہ" -> 3600
            u.startsWith("s") || u == "سیکنڈ" -> 1
            else -> 60
        }
        val sec = n * mult
        if (sec < 5 || sec > 86400) return null
        val rest = text.replaceRange(m.range, " ").toString()
        val low = " " + rest.lowercase() + " "
        val forDur = low.contains(" for ") || low.contains("liye") || low.contains("لیے") || low.contains("کیلئے")
        return Dur(sec, rest, forDur)
    }
}
