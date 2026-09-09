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
        attackSpeed = 3 // OSRS Wiki: 3 ticks
        attackStyle = StyleType.RANGED
        respawnDelay = 60
    }
    stats {
                // OSRS Wiki / 2007-era values (unchanged through 2011): 255 hp, att 300, str 200, def 260, mag 200, rng 380
        hitpoints = 2550
        attack = 300
        strength = 200
        defence = 260
        magic = 200
        ranged = 380
    }
    bonuses {
                // OSRS Wiki / 2007-era values (unchanged through 2011): stab/slash/crush 180, magic 200, ranged 200; attack 136, ranged attack 120
        defenceStab = 180
        defenceSlash = 180
        defenceCrush = 180
        defenceMagic = 200
        defenceRanged = 200
        attackBonus = 136
        attackRanged = 120
    }
    anims {
        // melee flap 6997 (ranged/magic use 6976 in the script). Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 6997
        block = 6974
        death = 6975
    }
    aggro {
        radius = 15
    }
}
