package gg.rsmod.plugins.content.combat

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import java.io.File
import java.io.FileReader
import kotlin.math.abs

/**
 * RCV-005 root cause (owner retest 2026-09-13: npcs forget you when you walk or run away): npcs had no
 * leash model. The combat cycle guessed one - drop the fight when a route failed in single combat and
 * the target was more than 6 tiles away - so a running player was forgotten, while elsewhere an npc
 * could be dragged anywhere.
 *
 * This is Void's model, `CombatMovement.withinAggro`: an npc keeps fighting while its target stays
 * within `max_range + attack range` tiles of the npc's spawn tile on both axes (a melee npc also loses
 * the exact diagonal corner). `max_range` is per npc (`npc_ranges`, Void `NPC.maxRange()`, default 7),
 * generated into `data/cfg/npcs/npc-ranges.json` by `C:\RSPS\tools\npc-ranges\generate.js`.
 */
object NpcLeash {
    const val DEFAULT_PATH = "./data/cfg/npcs/npc-ranges.json"

    /** Void `NPC.maxRange()` fallback when an npc has no `npc_ranges` row. */
    const val DEFAULT_MAX_RANGE = 7

    class Row(
        val id: Int = -1,
        val name: String = "",
        @SerializedName("void_key") val voidKey: String = "",
        @SerializedName("max_range") val maxRange: Int = DEFAULT_MAX_RANGE,
    )

    @Volatile
    private var ranges: Map<Int, Int> = emptyMap()

    fun load(file: File = File(DEFAULT_PATH)): Int {
        val loaded: Array<Row> = FileReader(file).use { Gson().fromJson(it, Array<Row>::class.java) }
        ranges = loaded.associate { it.id to it.maxRange }
        return ranges.size
    }

    fun maxRange(npcId: Int): Int = ranges[npcId] ?: DEFAULT_MAX_RANGE

    /** Void `CombatMovement.withinAggro`, axis-aligned against the spawn tile. */
    fun withinAggro(
        spawn: Tile,
        target: Tile,
        maxRange: Int,
        attackRange: Int,
    ): Boolean {
        val aggroRange = maxRange + attackRange
        val absX = abs(target.x - spawn.x)
        val absZ = abs(target.z - spawn.z)
        if (attackRange == 1 && absX == absZ && absX == aggroRange) {
            return false
        }
        return absX <= aggroRange && absZ <= aggroRange
    }

    /**
     * Whether [npc] may keep fighting [target]. Familiars (an owner is set) are never leashed, and
     * neither is an npc fighting a familiar: Void `Combat.retaliate` skips the leash when the source is
     * a familiar, so an npc hit by one always defends itself.
     */
    fun mayPursue(
        npc: Npc,
        target: Pawn,
        attackRange: Int,
    ): Boolean {
        if (npc.owner != null) return true
        if (target is Npc && target.owner != null) return true
        // Deadman guards (owner 2026-09-17: "they need to be always aggro in a safezone when a player
        // enters with a skull, so even if a barrier or object is in the way the guard needs to walk
        // behind it and still attack the player"): a guard is leashed to its safe zone, not to a
        // radius around its post - it pursues anywhere inside the zone and stops the moment the
        // intruder steps out or loses the skull (CityGuards.mayPursue).
        if (gg.rsmod.plugins.content.mechanics.pvp.CityGuards.isGuard(npc)) {
            return gg.rsmod.plugins.content.mechanics.pvp.CityGuards.mayPursue(npc, target)
        }
        if (gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(npc)) {
            // A breach monster never follows anyone into a Safe (guarded) zone: breaches are Dangerous-area events only.
            if (gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(target.tile)) return false
            return withinAggro(npc.spawnTile, target.tile, gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.CHASE_RANGE, attackRange)
        }
        return withinAggro(npc.spawnTile, target.tile, maxRange(npc.id), attackRange)
    }
}
