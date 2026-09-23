package gg.rsmod.plugins.content.npcs.definitions.humanoids

import gg.rsmod.plugins.content.drops.DropTableFactory

val table = DropTableFactory
val citizen =
    table.build {
        guaranteed {
            obj(Items.BONES)
        }
    }

table.register(citizen, Npcs.JONNY_THE_BEARD)


on_npc_death(Npcs.JONNY_THE_BEARD) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}

set_combat_def(Npcs.JONNY_THE_BEARD) {
    configs {
        attackSpeed = 4
        respawnDelay = 37
    }
    stats {
        hitpoints = 80
    }
    anims {
        attack = Anims.ATTACK_PUNCH
        death = Anims.HUMAN_DEATH
        block = Anims.BLOCK_ONE_HAND
    }
}
