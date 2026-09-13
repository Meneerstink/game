package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.VoidDropTables
import java.io.File

/**
 * RCV-011 Q-043-d: registers the Void GWD drop tables ([GodWarsDrops]) for the four generals and twelve bodyguards
 * before the bulk tables (`on_world_init_late`) look for hand-written ones, and rolls them on death. This is the only
 * `on_npc_death` binding for these ids (the engine keeps one handler per npc).
 */
on_world_init {
    GodWarsDrops.register(VoidDropTables.load(File(GodWarsDrops.PATH)))
}

GodWarsDrops.NPC_IDS.forEach { id ->
    on_npc_death(id) {
        val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
        DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
    }
}
