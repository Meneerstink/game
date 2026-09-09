package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare

/** General Graardor, leader of the Bandos faction. See kreearra.plugin.kts for notes. */
val GRAARDOR = Npcs.GENERAL_GRAARDOR
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

            // Bandos equipment - rare
            obj(Items.BANDOS_CHESTPLATE, slots = 4)
            obj(Items.BANDOS_TASSETS, slots = 3)
            obj(Items.BANDOS_BOOTS, slots = 3)
            obj(Items.BANDOS_HILT, slots = 2)
            obj(Items.FROZEN_KEY_PIECE_BANDOS, slots = 3)

            nothing(21)
        }
    }

table.register(drops, GRAARDOR)

on_npc_death(GRAARDOR) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = GRAARDOR) {
    configs {
        attackSpeed = 6
        attackStyle = StyleType.CRUSH
        respawnDelay = 60
    }
    stats {
        hitpoints = 2550
        attack = 300
        strength = 340
        defence = 240
        magic = 1
        ranged = 1
    }
    bonuses {
        defenceStab = 60
        defenceSlash = 60
        defenceCrush = 60
        defenceMagic = 0
        defenceRanged = 60
    }
    anims {
        // melee 7060 (ranged 7063). Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 7060
        block = 7061
        death = 7062
    }
    aggro {
        radius = 15
    }
}
