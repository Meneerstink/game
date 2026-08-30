package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * Nex, behind the Ancient Prison's Frozen Door. Reached via the four-faction frozen key
 * chain (see godwars_frozen_key.plugin.kts): combine one frozen key piece from each of the
 * four GWD generals into a full [Items.FROZEN_KEY_20120], then use it on the real
 * [Objs.FROZEN_DOOR] (baked into the map, so this works at its authentic location without
 * this pass needing to know its coordinates).
 *
 * ponytail: Nex herself is summoned next to the player on entry rather than placed in a
 * real separate arena room (no verified Ancient Prison interior coordinates), and fights as
 * a single very-hard phase rather than the real ice/blood/shadow/smoke rotation - see
 * IMPLEMENTATION_STATUS.md. This is the single biggest simplification in this pass; a real
 * arena + phase script is the natural next step once map data can be verified.
 */
val NEX = Npcs.NEX
val table = DropTableFactory

val drops =
    table.build {
        guaranteed {
            obj(Items.COINS_995, quantityRange = 5000..25000)
        }

        main {
            total(2000)
            obj(Items.RUNITE_ORE, quantity = 10, slots = 300)
            obj(Items.DEATH_RUNE, quantity = 200, slots = 300)
            obj(Items.MAGIC_LOGS, quantity = 50, slots = 200)
            table(Rare.rareTable, slots = 300)

            // Torva/Pernix/Virtus (Ancient warriors' successor gear) - very rare
            obj(Items.ANCIENT_STATUETTE, slots = 10)
            obj(Items.ZURIELS_STAFF, slots = 6)
            obj(Items.STATIUSS_WARHAMMER, slots = 6)
            obj(Items.VESTAS_LONGSWORD, slots = 6)

            nothing(872)
        }
    }

table.register(drops, NEX)

on_npc_death(NEX) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = NEX) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.MAGIC
        respawnDelay = 200
    }
    stats {
        hitpoints = 3500
        attack = 400
        strength = 400
        defence = 320
        magic = 400
        ranged = 400
    }
    bonuses {
        defenceStab = 120
        defenceSlash = 120
        defenceCrush = 120
        defenceMagic = 120
        defenceRanged = 120
    }
    anims {
        attack = Anims.ATTACK_PUNCH
        block = Anims.BLOCK_UNARMED
        death = Anims.HUMAN_DEATH
    }
    aggro {
        radius = 15
    }
}

on_item_on_obj(obj = Objs.FROZEN_DOOR, item = Items.FROZEN_KEY_20120) {
    player.inventory.remove(Items.FROZEN_KEY_20120, 1)
    player.filterableMessage("The Frozen Door grinds open. Nex awakens...")
    val tile = Tile(player.tile)
    val nex = Npc(NEX, tile, player.world)
    nex.respawnOverride = false
    nex.walkRadius = 0
    player.world.spawn(nex)
}
