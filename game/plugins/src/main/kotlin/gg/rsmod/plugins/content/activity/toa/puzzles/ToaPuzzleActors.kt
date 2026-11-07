package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.activity.toa.ToaRoomScope

/** World.spawn calls setNpcDefaults. Apply room-specific stats AFTER that call, with lifetime/points still owned by the scope. */
object ToaPuzzleActors {
    fun spawn(scope: ToaRoomScope, npc: Npc, definition: NpcCombatDef?): Boolean {
        npc.respawnOverride = false
        if (!scope.spawn(npc)) return false
        if (definition != null) {
            npc.combatDef = definition.copy(respawnDelay = -1, completesEncounter = false)
            definition.stats.forEachIndexed { i, value -> npc.stats.setMaxLevel(i, value); npc.stats.setCurrentLevel(i, value) }
            npc.setCurrentLifepoints(definition.lifepoints)
            npc.equipmentBonuses.fill(0)
            definition.bonuses.forEachIndexed { i, value -> if (i < npc.equipmentBonuses.size) npc.equipmentBonuses[i] = value }
        }
        return true
    }
}
