package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * Kree'arra, leader of the Armadyl faction at the God Wars Dungeon.
 *
 * ponytail: uses the generic combat_def auto-attack (fixed Ranged style here) rather than a
 * bespoke CombatScript alternating ranged/magic + the real knock-back special - functional
 * boss, simplified mechanics. Provisional 2011-baseline stats/drop rates - see
 * IMPLEMENTATION_STATUS.md.
 */
val KREEARRA = Npcs.KREEARRA
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

            // Armadyl equipment - rare
            obj(Items.ARMADYL_HELMET, slots = 4)
            obj(Items.ARMADYL_CHESTPLATE, slots = 3)
            obj(Items.ARMADYL_CHAINSKIRT, slots = 3)
            obj(Items.ARMADYL_HILT, slots = 2)
            obj(Items.FROZEN_KEY_PIECE_ARMADYL, slots = 3)

            nothing(21)
        }
    }

table.register(drops, KREEARRA)

on_npc_death(KREEARRA) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = KREEARRA) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.RANGED
        respawnDelay = 60
    }
    stats {
        hitpoints = 2250 // 225 real HP - was 2255, not a multiple of 10 like every other GWD general here
        attack = 300
        strength = 300
        defence = 240
        magic = 300
        ranged = 300
    }
    bonuses {
        defenceStab = 0
        defenceSlash = 0
        defenceCrush = 0
        defenceMagic = 150
        defenceRanged = 100
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
