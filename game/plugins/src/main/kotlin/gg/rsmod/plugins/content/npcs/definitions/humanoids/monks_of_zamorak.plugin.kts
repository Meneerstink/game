package gg.rsmod.plugins.content.npcs.definitions.humanoids

import gg.rsmod.plugins.content.drops.DropTableFactory

val ids =
    intArrayOf(
        Npcs.MONK_OF_ZAMORAK_1044, Npcs.MONK_OF_ZAMORAK_1045, Npcs.MONK_OF_ZAMORAK_1046,
        Npcs.MONK_OF_ZAMORAK, Npcs.MONK_OF_ZAMORAK_189,
    )

val table = DropTableFactory
val monkOfZamorak =
    table.build {
        guaranteed {
            obj(Items.BONES)
        }
        main {
            total(total = 20)
            obj(Items.ZAMORAK_ROBE_1035, slots = 1)
            obj(Items.ZAMORAK_ROBE, slots = 1)
            if (!player.hasItem(Items.GOLDEN_KEY)) {
                obj(Items.GOLDEN_KEY, slots = 10)
                nothing(slots = 8)
            } else {
                nothing(slots = 18)
            }
        }
        table("Charms") {
            total(1000)
            obj(Items.GOLD_CHARM, quantity = 1, slots = 10)
            obj(Items.GREEN_CHARM, quantity = 1, slots = 100)
            obj(Items.CRIMSON_CHARM, quantity = 1, slots = 20)
            obj(Items.BLUE_CHARM, quantity = 1, slots = 10)
            nothing(slots = 860)
        }
    }

table.register(monkOfZamorak, *ids)

on_npc_pre_death(Npcs.MONK_OF_ZAMORAK_1044, Npcs.MONK_OF_ZAMORAK_1045, Npcs.MONK_OF_ZAMORAK_1046) {
    val p = npc.killer() ?: return@on_npc_pre_death
    p.playSound(Sfx.HUMAN_DEATH)
}

on_npc_death(*ids) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}

ids.forEach {
    set_combat_def(it) {
        configs {
            attackSpeed = 4
            respawnDelay = 250
        }
        stats {
            hitpoints = 400
            attack = 38
            strength = 38
            defence = 42
            magic = 40
            ranged = 1
        }
        anims {
            attack = Anims.ATTACK_PUNCH
            death = Anims.HUMAN_DEATH
            block = Anims.BLOCK_ONE_HAND
        }
    }
}
