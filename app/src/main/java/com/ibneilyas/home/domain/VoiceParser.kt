package com.ibneilyas.home.domain

sealed class VoiceResult {
    data class Device(val a: Appliance, val on: Boolean) : VoiceResult()
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
    private val generic = setOf("fan", "light", "socket")
    private val core = setOf(
        "on", "off", "light", "bulb", "fan", "socket", "plug", "room", "all", "please", "select",
        "turn", "switch", "start", "stop", "enable", "disable", "open", "close", "good", "night", "home", "mode"
    )

    private fun canon(w: String): String = when (w) {
        "bulb" -> "light"
        "plug" -> "socket"
        else -> w
    }

    private fun words(s: String): List<String> = Lexicon.words(s).map { canon(it) }

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
            if (w in known || w in core || w.length < 4 || !w.all { it in 'a'..'z' }) {
                w
            } else {
                val best = known.minOfOrNull { lev(w, it) } ?: 99
                val near = known.filter { lev(w, it) == best }
                if (best <= 2 && best < w.length / 2 && near.size == 1) near[0] else w
            }
        }
    }

    private fun keys(a: Appliance): Set<String> {
        val k = words(a.name).toMutableSet()
        when (a.type) {
            ApplianceType.LIGHT -> k.add("light")
            ApplianceType.FAN -> k.add("fan")
            ApplianceType.SOCKET -> k.add("socket")
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
        return if (exact.size == 1) exact else m
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
        else -> VoiceResult.Message("Which room? Say for example: " + (d.rooms.firstOrNull()?.name ?: "Sarfraz's Room") + " select karo")
    }

    fun parse(text: String, d: HomeData, scenes: List<Scene>, activeRoomId: String? = null): VoiceResult {
        val set = fuzzy(words(text), d).toSet()
        val on = set.any { it in onWords }
        val off = set.any { it in offWords }
        val found = roomsIn(set, d)
        if ("select" in set) return selectRoom(found, d)
        if (found.size > 1) {
            return VoiceResult.Message("Please name one room at a time: " + found.take(3).joinToString(", ") { it.name })
        }
        val explicit = found.firstOrNull()
        val active = d.rooms.firstOrNull { it.id == activeRoomId }
        val type = when {
            "light" in set -> ApplianceType.LIGHT
            "fan" in set -> ApplianceType.FAN
            "socket" in set -> ApplianceType.SOCKET
            else -> null
        }
        if ("all" in set && type != null && on != off) {
            val list = d.appliances.filter { it.type == type && (explicit == null || it.roomId == explicit.id) }
            val name = type.name.lowercase()
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
        val q = set - ignore - onWords - offWords - roomWords - setOf("all", "room", "select")
        if (q.isEmpty()) return VoiceResult.Message("Which device? Say for example: fan off karo")
        val scope = explicit ?: active
        val what = q.firstOrNull { it in generic } ?: "one"
        if (scope != null) {
            val m = pick(matches(d.appliances.filter { it.roomId == scope.id }, q), q)
            if (m.size == 1) return VoiceResult.Device(m[0], on)
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
        if (g.size == 1) return VoiceResult.Device(g[0], on)
        return ask(g, d, what, true)
    }
}
