package gg.rsmod.plugins.content.mechanics.aggro

import com.google.gson.Gson
import java.io.File
import java.io.FileReader

/**
 * RCV-012.B14 - per-npc hunt modes (`data/cfg/npcs/hunt-modes.json`, generated from the Void donor's `hunt_modes.toml` and every
 * `*.npcs.toml` hunt_mode / hunt_range).
 *
 * Root cause: `npc_aggro.plugin.kts` gave every aggressive npc one rule - "player combat level <= 2 x npc level" - which is Void's
 * `cowardly` mode. Void's `aggressive` mode (thrower trolls and 54 more live npcs) sets `check_not_too_strong = false`, so a level-138
 * player was never attacked by a level-68 thrower troll. Void `Hunting.canHunt` checks, in order: range, line of sight / walk,
 * `targetTooStrong` (player combat > npc combat * 2) when `check_not_too_strong`, a target under attack outside multi-combat when
 * `check_not_combat`, and a busy target (delay or open menu) when `check_not_busy`.
 *
 * Applied only where both sources agree the npc hunts players (combat-defs.json aggressive and a Void player hunt mode); every other npc
 * keeps the previous rule (C:\RSPS\RCV012_B14_AGGRESSION_SOURCES.txt lists the disagreements for the owner). ADAPTED: Void's "delay" busy
 * state has no mapped equivalent here (only the open-menu part is checked); "hunted" (targeted by another npc within 2 ticks) is not
 * modelled. The existing 10-minute tolerance (DEFAULT_AGGRO_TIMER 1000, Void Tolerance.toleranceTime 10 minutes) is unchanged.
 */
object NpcHuntModes {
    const val DEFAULT_PATH = "./data/cfg/npcs/hunt-modes.json"

    /** Void `Hunting.run`: `npc.def["hunt_range", 5]`. */
    const val DEFAULT_RANGE = 5

    data class Mode(
        val checkVisual: String = "none",
        val checkNotTooStrong: Boolean = true,
        val checkNotCombat: Boolean = true,
        val checkNotCombatSelf: Boolean = true,
        val checkNotBusy: Boolean = true,
        val checkAfk: Boolean = false,
    )

    data class NpcEntry(val id: Int = -1, val mode: String = "", val range: Int = DEFAULT_RANGE)

    class Table(val modes: Map<String, Mode> = emptyMap(), val npcs: List<NpcEntry> = emptyList()) {
        private val byId by lazy { npcs.associateBy { it.id } }

        fun modeName(npcId: Int): String? = byId[npcId]?.mode

        fun mode(npcId: Int): Mode? = modeName(npcId)?.let { modes[it] }

        fun range(npcId: Int): Int? = byId[npcId]?.range
    }

    private class Document(val modes: Map<String, Mode> = emptyMap(), val npcs: List<NpcEntry> = emptyList())

    fun load(file: File = File(DEFAULT_PATH)): Table =
        FileReader(file).use { Gson().fromJson(it, Document::class.java) }.let { Table(it.modes, it.npcs) }

    val table: Table by lazy { load() }

    /** Void `Hunting.canHunt` for a player target, with the visual check already resolved by the caller. */
    fun allows(
        mode: Mode,
        playerCombatLevel: Int,
        npcCombatLevel: Int,
        playerUnderAttack: Boolean,
        playerInMulti: Boolean,
        playerMenuOpen: Boolean,
        visible: Boolean,
    ): Boolean {
        if (mode.checkVisual != "none" && !visible) return false
        if (mode.checkNotTooStrong && playerCombatLevel > npcCombatLevel * 2) return false
        if (mode.checkNotCombat && playerUnderAttack && !playerInMulti) return false
        if (mode.checkNotBusy && playerMenuOpen) return false
        return true
    }
}
