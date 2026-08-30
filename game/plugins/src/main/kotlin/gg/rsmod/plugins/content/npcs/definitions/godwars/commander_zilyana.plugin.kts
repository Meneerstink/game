package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare

/** Commander Zilyana, leader of the Saradomin faction. See kreearra.plugin.kts for notes. */
val ZILYANA = Npcs.COMMANDER_ZILYANA
val table = DropTableFactory

val drops =
    table.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }

        main {
            total(1000)
            obj(Items.COINS_995, quantityRange = 400..3000, slots = 300)
            obj(Items.RUNITE_ORE, quantityRange = 1..3, slots = 60)
            obj(Items.ADAMANT_BAR, quantity = 4, slots = 60)
            obj(Items.DEATH_RUNE, quantity = 45, slots = 60)
            obj(Items.LAW_RUNE, quantity = 75, slots = 60)
            obj(Items.YEW_LOGS, quantity = 70, slots = 60)
            obj(Items.MAGIC_LOGS, quantity = 15, slots = 40)

            table(Gems.gemTable, slots = 60)
            table(Rare.rareTable, slots = 40)

            // Saradomin equipment - rare
            obj(Items.SARADOMIN_SWORD, slots = 3)
            obj(Items.SARADOMIN_HILT, slots = 3)
            obj(Items.FROZEN_KEY_PIECE_SARADOMIN, slots = 3)

            nothing(27)
        }
    }

table.register(drops, ZILYANA)

on_npc_death(ZILYANA) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = ZILYANA) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 60
    }
    stats {
        hitpoints = 2150
        attack = 270
        strength = 270
        defence = 240
        magic = 260
        ranged = 260
    }
    bonuses {
        defenceStab = 40
        defenceSlash = 40
        defenceCrush = 40
        defenceMagic = 80
        defenceRanged = 80
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
