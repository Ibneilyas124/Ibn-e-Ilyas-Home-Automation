package com.ibneilyas.home.domain

sealed class VoiceResult {
    data class Device(val a: Appliance, val on: Boolean, val delaySec: Int = 0, val forDuration: Boolean = false) : VoiceResult()
    data class Group(val list: List<Appliance>, val on: Boolean, val label: String) : VoiceResult()
    data class RunScene(val s: Scene) : VoiceResult()
    data class SelectRoom(val room: Room) : VoiceResult()
    data class Message(val text: String) : VoiceResult()
}

/** Voice sentence -> Room + Appliance + Action. Pure logic, no Android. */
object VoiceParser {
    private val onWords = setOf("on", "start", "enable", "open", "activate", "chalao", "chala", "chalu", "jalao", "kholo")
    private val offWords = setOf("off", "stop", "disable", "close", "band", "bandh", "bujhao")
    private val ignore = setOf(
        "please", "turn", "switch", "the", "a", "an", "to", "set", "kindly", "can", "you",
        "my", "of", "in", "for", "and", "karo", "kar", "do", "is", "it"
    )
    private val generic = setOf("fan", "light", "bulb", "socket", "plug")
    private val core = setOf(
        "on", "off", "light", "bulb", "fan", "socket", "plug", "room", "all", "please", "select",
        "turn", "switch", "start", "stop", "enable", "disable", "open", "close", "good", "night", "home", "mode"
    )

    private fun words(s: String): List<String> = Lexicon.words(s)

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + (if (a[i - 1] == b[j - 1]) 0 else 1))
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun fuzzy(ws: List<String>, d: HomeData): List<String> {
        val known = (d.rooms.map { it.name } + d.appliances.map { it.name })
            .flatMap { words(it) }.filter { it.length >= 4 }.toSet() - core
        return ws.map { w ->
            when {
                w in known || w in core || w.length < 3 || !w.all { it in 'a'..'z' } -> w
                lev(w, "karo") <= 1 || lev(w, "kardo") <= 1 -> "please"
                w.length < 4 -> w
                else -> {
                    val best = known.minOfOrNull { lev(w, it) } ?: 99
                    val near = known.filter { lev(w, it) == best }
                    if (best <= 2 && best < w.length / 2 && near.size == 1) near[0] else (Sound.match(w, known) ?: w)
                }
            }
        }
    }

    private fun keys(a: Appliance): Set<String> {
        val k = words(a.name).toMutableSet()
        when (a.type) {
            ApplianceType.LIGHT -> { k.add("light"); k.add("bulb") }
            ApplianceType.FAN -> k.add("fan")
            ApplianceType.SOCKET -> { k.add("socket"); k.add("plug") }
            ApplianceType.OTHER -> {}
        }
        return k
    }

    private fun distinct(r: Room): Set<String> = words(r.name).toSet() - setOf("room", "s") - ignore

    private fun roomsIn(set: Set<String>, d: HomeData): List<Room> {
        val hit = d.rooms.filter { r -> distinct(r).let { it.isNotEmpty() && set.containsAll(it) } }
        val most = hit.maxOfOrNull { distinct(it).size } ?: return emptyList()
        return hit.filter { distinct(it).size == most }
    }

    private fun matches(list: List<Appliance>, q: Set<String>): List<Appliance> =
        list.filter { keys(it).containsAll(q) }

    private fun pick(m: List<Appliance>, q: Set<String>): List<Appliance> {
        if (m.size < 2) return m
        val exact = m.filter { words(it.name).toSet() == q }
        if (exact.size == 1) return exact
        val named = m.filter { words(it.name).containsAll(q) }
        return if (named.size == 1) named else m
    }

    private fun sceneFor(set: Set<String>, scenes: List<Scene>): Scene? =
        scenes.filter { s -> words(s.name).let { it.isNotEmpty() && set.containsAll(it) } }
            .maxByOrNull { words(it.name).size }

    private fun ask(list: List<Appliance>, d: HomeData, what: String, withRoom: Boolean): VoiceResult.Message {
        val names = list.take(4).joinToString(" or ") { a ->
            val rn = d.rooms.firstOrNull { it.id == a.roomId }?.name
            if (withRoom && rn != null) "${a.name} ($rn)" else a.name
        }
        val hint = if (withRoom) "Say the room name too, or set a Voice room." else "Say the full name."
        return VoiceResult.Message("Which $what? $names. $hint")
    }

    private fun notFound(q: Set<String>, d: HomeData, text: String, where: String): VoiceResult.Message {
        val vocab = d.appliances.flatMap { keys(it) }.toSet()
        val unknown = (q - vocab).joinToString(" ")
        val extra = if (unknown.isNotEmpty()) "Not sure about: $unknown. " else ""
        return VoiceResult.Message("I could not find that device$where. ${extra}Heard: $text")
    }

    private fun selectRoom(found: List<Room>, d: HomeData): VoiceResult = when {
        found.size == 1 -> VoiceResult.SelectRoom(found[0])
        found.size > 1 -> VoiceResult.Message("Which room? " + found.take(3).joinToString(" or ") { it.name })
        else -> VoiceResult.Message("Say which room, for example: " + (d.rooms.firstOrNull()?.name ?: "Sarfraz's Room") + " select karo")
    }


    /** A word the app can make sense of. Anything else is treated as chatter and ignored. */
    private fun known(w: String, d: HomeData): Boolean =
        w in generic || w in core || Lexicon.isKnown(w) ||
            d.appliances.any { w in keys(it) } || d.rooms.any { w in words(it.name) }
    fun parse(text: String, d: HomeData, scenes: List<Scene>, activeRoomId: String? = null): VoiceResult {
        val dur = VoiceTime.extract(text)
        val set = fuzzy(words(dur?.rest ?: text), d).toSet()
        val on = set.any { it in onWords }
        val off = set.any { it in offWords } || (!on && "of" in set && "please" in set)
        val found = roomsIn(set, d)
        if ("select" in set) return selectRoom(found, d)
        if (found.size > 1) {
            return VoiceResult.Message("Please name one room at a time: " + found.take(3).joinToString(", ") { it.name })
        }
        val explicit = found.firstOrNull()
        val active = d.rooms.firstOrNull { it.id == activeRoomId }
        val type = when {
            "light" in set || "bulb" in set -> ApplianceType.LIGHT
            "fan" in set -> ApplianceType.FAN
            "socket" in set || "plug" in set -> ApplianceType.SOCKET
            else -> null
        }
        if ("all" in set && type != null && on != off) {
            val list = d.appliances.filter { it.type == type && (explicit == null || it.roomId == explicit.id) }
            val name = type.name.lowercase()
            if (dur != null) return VoiceResult.Message("Timers work for one device at a time. Name the device.")
            val where = if (explicit != null) " in ${explicit.name}" else ""
            if (list.isEmpty()) return VoiceResult.Message("No $name devices$where. Set device types in edit mode.")
            return VoiceResult.Group(list, on, "all ${name}s$where")
        }
        if (!on && !off) {
            return sceneFor(set, scenes)?.let { VoiceResult.RunScene(it) }
                ?: VoiceResult.Message("Say ON or OFF, for example: fan off karo")
        }
        if (on && off) return VoiceResult.Message("Please say either ON or OFF, not both")
        val roomWords = found.flatMap { distinct(it) }.toSet()
        val rest = set - ignore - onWords - offWords - roomWords - setOf("all", "room", "select")
        val q = rest.filter { known(it, d) }.toSet()
        if (q.isEmpty()) return VoiceResult.Message("I could not find a device in that. Say for example: fan off karo. Heard: $text")
        val scope = explicit ?: active
        val what = q.firstOrNull { it in generic } ?: "one"
        if (scope != null) {
            val m = pick(matches(d.appliances.filter { it.roomId == scope.id }, q), q)
            if (m.size == 1) return VoiceResult.Device(m[0], on, dur?.seconds ?: 0, dur?.forDuration ?: false)
            if (m.size > 1) return ask(m, d, what, false)
            if (explicit != null) return notFound(q, d, text, " in ${explicit.name}")
        }
        val g = pick(matches(d.appliances, q), q)
        if (g.isEmpty()) {
            return sceneFor(set, scenes)?.let { VoiceResult.RunScene(it) } ?: notFound(q, d, text, "")
        }
        if (scope != null && (q - generic).isEmpty()) {
            val v = if (on) "on" else "off"
            return VoiceResult.Message("No $what in ${scope.name}. Say the room name, for example: Drawing Room $what $v karo")
        }
        if (g.size == 1) return VoiceResult.Device(g[0], on, dur?.seconds ?: 0, dur?.forDuration ?: false)
        return ask(g, d, what, true)
    }



    private fun score(t: String, d: HomeData): Int {
        val ws = fuzzy(words(t), d)
        val room = roomsIn(ws.toSet(), d).isNotEmpty()
        val unknown = ws.count { it !in ignore && !known(it, d) }
        return 1 + (if (room) 3 else 0) + (if (unknown == 0) 2 else if (unknown == 1) 1 else 0)
    }

    /** Looks at every recognizer guess and keeps the one the app understands best. A named room counts most. */
    fun parseBest(alts: List<String>, d: HomeData, scenes: List<Scene>, activeRoomId: String?): Pair<VoiceResult, String> {
        val list = alts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5)
        if (list.isEmpty()) return Pair(VoiceResult.Message("I did not catch that"), "")
        val topLen = list[0].split(" ").size
        val top = parse(list[0], d, scenes, activeRoomId)
        val topAsk = top is VoiceResult.Message && top.text.startsWith("Which ")
        var best: Pair<VoiceResult, String>? = null
        var bestScore = -1
        for ((i, t) in list.withIndex()) {
            if (i > 0 && t.split(" ").size != topLen) continue
            val r = if (i == 0) top else parse(t, d, scenes, activeRoomId)
            if (r is VoiceResult.Message) continue
            val s = score(t, d)
            if (topAsk && s < 4) continue
            if (s > bestScore) {
                best = Pair(r, t)
                bestScore = s
            }
        }
        return best ?: Pair(top, list[0])
    }
    /** True when nothing was understood, so the other language is worth a second try. */
    fun needsRetry(r: VoiceResult): Boolean =
        r is VoiceResult.Message &&
            (r.text.startsWith("Say ") || r.text.startsWith("I could not") || r.text.startsWith("I did not"))
}
