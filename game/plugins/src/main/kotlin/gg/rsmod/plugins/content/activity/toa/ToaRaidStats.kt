package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.toa.ui.ToaSummaryUi

/**
 * N462 what the Raid Summary (OSRS toa_raid_summary 482, read on the burial chamber's Scoreboard: "a summary of the raid, including details
 * such as total damage dealt and received, number of deaths" - Tombs of Amascut?oldid=15391794 Rewards, line 599) shows: the time of every
 * room (paths and bosses tabs), the challenge and overall totals, and per player damage dealt/taken on the raid's npcs.
 * ONZEKER: whether OSRS counts a room from its first or its last attempt is not in the sources - first start to clear here (a retried room
 * includes its failed attempt).
 */
class ToaRaidStats {
    private val roomStart = hashMapOf<ToaRoomKey, Int>()
    /** Game cycles from the room's first start to its clear. */
    val roomCycles = linkedMapOf<ToaRoomKey, Int>()
    val damageDealt = hashMapOf<PlayerUID, Int>()
    val damageTaken = hashMapOf<PlayerUID, Int>()

    fun started(key: ToaRoomKey, cycle: Int) { roomStart.putIfAbsent(key, cycle) }

    fun cleared(key: ToaRoomKey, cycle: Int) {
        roomStart[key]?.let { roomCycles[key] = (cycle - it).coerceAtLeast(0) }
    }

    /** At a room clear, before the scope removes them: what every member did to and took from the room's npcs (their damage maps). */
    fun credit(players: List<Player>, npcs: List<Npc>) {
        players.forEach { p ->
            val dealt = npcs.sumOf { it.damageMap.getDamageFrom(p) }
            val taken = npcs.sumOf { p.damageMap.getDamageFrom(it) }
            if (dealt > 0) damageDealt.merge(p.uid, dealt, Int::plus)
            if (taken > 0) damageTaken.merge(p.uid, taken, Int::plus)
        }
    }

    fun room(path: ToaPath?, boss: Boolean): Int? = roomCycles[ToaRoomKey(path, boss)]

    fun times(overallCycles: Int?): ToaSummaryUi.Times {
        val challenges = ToaPath.values().mapNotNull { room(it, false) }
        return ToaSummaryUi.Times(
            kephri = room(ToaPath.SCABARAS, true), akkha = room(ToaPath.HET, true), baba = room(ToaPath.APMEKEN, true),
            zebak = room(ToaPath.CRONDIS, true), wardens = room(null, true),
            scabaras = room(ToaPath.SCABARAS, false), het = room(ToaPath.HET, false), apmeken = room(ToaPath.APMEKEN, false),
            crondis = room(ToaPath.CRONDIS, false),
            challengeTotal = challenges.takeIf { it.isNotEmpty() }?.sum(),
            overall = overallCycles,
        )
    }
}
