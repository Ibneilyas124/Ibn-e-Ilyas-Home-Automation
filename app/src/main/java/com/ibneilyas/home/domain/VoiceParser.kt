package com.ibneilyas.home.domain

sealed class VoiceResult {
    data class Device(val a: Appliance, val on: Boolean) : VoiceResult()
    data class Group(val list: List<Appliance>, val on: Boolean, val label: String) : VoiceResult()
    data class RunScene(val s: Scene) : VoiceResult()
    data class Message(val text: String) : VoiceResult()
}

/** Turns a spoken sentence into an action. Pure logic, no Android. */
object VoiceParser {
    private val onWords = setOf("on", "start", "enable", "open", "chalao", "chala", "chalu", "jalao", "kholo")
    private val offWords = setOf("off", "stop", "disable", "close", "band", "bandh", "bujhao")
    private val filler = setOf(
        "please", "turn", "switch", "the", "a", "to", "set", "kindly", "can", "you",
        "my", "of", "in", "for", "and", "karo", "kar", "do"
    )
    private val digits = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    private fun words(s: String): List<String> = Lexicon.words(s)

    private fun sceneFor(set: Set<String>, scenes: List<Scene>): Scene? =
        scenes.filter { s -> words(s.name).let { it.isNotEmpty() && set.containsAll(it) } }
            .maxByOrNull { words(it.name).size }

    private fun find(d: HomeData, set: Set<String>): List<Appliance> {
        val content = set - filler - onWords - offWords
        fun roomWords(a: Appliance) = words(d.rooms.firstOrNull { it.id == a.roomId }?.name.orEmpty())
        val strict = d.appliances.filter { a -> words(a.name).let { it.isNotEmpty() && set.containsAll(it) } }
        val pool = strict.ifEmpty {
            d.appliances.filter { a ->
                val c = content - roomWords(a).toSet()
                c.isNotEmpty() && words(a.name).containsAll(c)
            }
        }
        fun score(a: Appliance): Int {
            val rw = roomWords(a)
            return words(a.name).size * 2 + if (rw.isNotEmpty() && set.containsAll(rw)) rw.size else 0
        }
        val best = pool.maxOfOrNull { score(it) } ?: return emptyList()
        return pool.filter { score(it) == best }
    }

    fun parse(text: String, d: HomeData, scenes: List<Scene>): VoiceResult {
        val set = words(text).toSet()
        val on = set.any { it in onWords }
        val off = set.any { it in offWords }
        val type = when {
            "light" in set || "bulb" in set -> ApplianceType.LIGHT
            "fan" in set -> ApplianceType.FAN
            "socket" in set || "plug" in set -> ApplianceType.SOCKET
            else -> null
        }
        if ("all" in set && type != null && on != off) {
            val list = d.appliances.filter { it.type == type }
            val name = type.name.lowercase()
            if (list.isEmpty()) return VoiceResult.Message("No $name devices yet. Set device types in edit mode.")
            return VoiceResult.Group(list, on, "all ${name}s")
        }
        if (!on && !off) {
            return sceneFor(set, scenes)?.let { VoiceResult.RunScene(it) }
                ?: VoiceResult.Message("Say ON or OFF, for example: turn on zero bulb")
        }
        if (on && off) return VoiceResult.Message("Please say either ON or OFF, not both")
        val found = find(d, set)
        if (found.size == 1) return VoiceResult.Device(found[0], on)
        if (found.size > 1) {
            val names = found.take(3).joinToString(", ") { a ->
                a.name + " (" + (d.rooms.firstOrNull { it.id == a.roomId }?.name ?: "?") + ")"
            }
            return VoiceResult.Message("More than one match: $names. Say the room name too.")
        }
        return sceneFor(set, scenes)?.let { VoiceResult.RunScene(it) }
            ?: VoiceResult.Message("I could not find that device. Heard: $text")
    }
}
