package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc

/**
 * Installs the four generals' boss mechanics and spawns their bodyguards.
 *
 * RCV-005 root cause: the generals used hand-written attack scripts; they now fight through the shared
 * data-driven attack model (NpcAttacks, Void bandos/saradomin/zamorak/armadyl `*.combat.toml`) in the generic
 * combat cycle, and only their real mechanics are hooks (see [GodWarsGenerals.installAttackHooks]).
 *
 * RCV-011 Q-043-a: bodyguards are no longer independent world spawns. Every spawn and respawn of a general adds the
 * bodyguards of his chamber that are not in the world ([GodWarsBodyguards], Void `npcSpawn` + Novite
 * `GodWarsBosses.respawn*Minions`); a bodyguard never respawns on its own (`respawnOverride = false`, Novite
 * `GodWarMinion.setRespawnTask`).
 */
GodWarsGenerals.installAttackHooks()

GodWarsBodyguards.BY_GENERAL.keys.forEach { general ->
    on_npc_spawn(general) {
        GodWarsBodyguards.missing(npc.id) { id -> world.npcs.any { it.id == id } }.forEach { guard ->
            val bodyguard = Npc(guard.id, Tile(guard.x, guard.z, guard.height), world)
            bodyguard.respawnOverride = false
            bodyguard.walkRadius = GodWarsBodyguards.WALK_RADIUS
            world.spawn(bodyguard)
        }
    }
}
