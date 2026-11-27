package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.diaries.Diaries.Trigger

/**
 * DIARIES-OSRS D1: the diary tick (inventory / xp / place / teleport / talk against the last tick), the kill hook and the test
 * commands. Content plugins call [Diaries.event] for what is not visible in that tick (laps, pest control, pickpockets, hunting).
 */
val DIARY_TICK = gg.rsmod.game.model.timer.TimerKey()

on_login {
    player.timers[DIARY_TICK] = 1
}

on_player_death {
    gg.rsmod.plugins.content.diaries.Diaries.respawned(player)
}

on_timer(DIARY_TICK) {
    player.timers[DIARY_TICK] = 1
    runCatching { Diaries.tick(player) }.onFailure { gg.rsmod.game.model.Trace.crash("diary tick", it) }
}

on_npc_completed { killer, npc ->
    runCatching { Diaries.onKill(killer, npc) }.onFailure { gg.rsmod.game.model.Trace.crash("diary kill", it) }
}

/** Events that content plugins send today (grep 'Diaries.event'); an Ev task with a key outside this list cannot be done. */
val EMITTED = listOf("lap:", "pest_control:", "hunt:", "pickpocket:", "poh_build:", "estate:", "spell:", "god_spell:", "chest:", "plant:", "organ:", "shortcut:", "farming_protect:", "dog_bone", "emote:", "spirit_tree", "tele:", "slayer_task:", "sawmill:", "canoe:", "tindel:", "totem:", "picklock:", "crest:", "light:", "fightcave:", "farm_health:", "eat:", "cart:", "charter:", "altar:", "poh_talk:")

// Test command (agent bot tests): `diary <name> [set|clear <task|diary>] [list]`.
gg.rsmod.game.CommandServerCommands.handlers["diary"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    when {
        p == null -> "usage: diary <name> [set|clear <task id|diary>] | diary <name> list"
        a.getOrNull(1) == "set" -> "set ${Diaries.adminSet(p, a.getOrNull(2) ?: "", true)}"
        a.getOrNull(1) == "clear" -> "cleared ${Diaries.adminSet(p, a.getOrNull(2) ?: "", false)}"
        a.getOrNull(1) == "claimed" -> {
            val d = Diaries.Diary.valueOf(a[2].uppercase()); Diaries.setClaimed(p, d, a[3].toInt()); "claimed $d=${Diaries.claimed(p, d)}"
        }
        else -> Diaries.Diary.values().joinToString(" | ") { d ->
            "${d.name} " + Diaries.Tier.values().joinToString("/") { "${Diaries.doneCount(p, d, it)}:${Diaries.totalCount(d, it)}" } + " claimed=${Diaries.claimed(p, d)}"
        } + " | progress=${p.attr[Diaries.PROGRESS] ?: "-"} | done=${p.attr[Diaries.DONE] ?: "-"}"
    }
}

// `diarycheck`: every task trigger against our world - npc and item names that do not exist, events nobody sends.
gg.rsmod.game.CommandServerCommands.handlers["diarycheck"] = { w, _ ->
    val defs = w.definitions
    val npcNames = (0 until defs.getCount(NpcDef::class.java)).mapNotNull { runCatching { defs.get(NpcDef::class.java, it).name.lowercase() }.getOrNull() }.toSet()
    val spawned = w.npcs.entries.filterNotNull().map { it.def.name.lowercase() }.toSet()
    val itemNames = (0 until defs.getCount(ItemDef::class.java)).mapNotNull { runCatching { defs.get(ItemDef::class.java, it).name.lowercase() }.getOrNull() }.toSet()
    val out = StringBuilder()
    fun check(id: String, t: Trigger) {
        when (t) {
            is Trigger.Kill -> t.names.filter { it !in spawned }.takeIf { it.size == t.names.size }?.let { out.append("$id kill-not-spawned=${it.joinToString("/")}${if (it.none { n -> n in npcNames }) "(no def)" else ""};") }
            is Trigger.Gain -> if (t.ids.isEmpty() && t.names.none { it in itemNames }) out.append("$id item-missing=${t.names.joinToString("/")};")
            is Trigger.Burn -> if (t.names.none { it in itemNames }) out.append("$id burn-missing;")
            is Trigger.Talk -> if (t.names.none { it in spawned }) out.append("$id talk-not-spawned=${t.names.joinToString("/")};")
            is Trigger.Ev -> if (EMITTED.none { t.key.startsWith(it) }) out.append("$id event-nobody=${t.key};")
            is Trigger.Steps -> t.steps.forEach { check(id, it) }
            is Trigger.All -> t.parts.forEach { check(id, it) }
            else -> {}
        }
    }
    Diaries.TASKS.forEach { check(it.id, it.trigger) }
    "tasks=${Diaries.TASKS.size} " + out.toString()
}
