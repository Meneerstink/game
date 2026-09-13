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
        attackSpeed = 2 // OSRS Wiki: 2 ticks
        attackStyle = StyleType.STAB
        // RCV-011 Q-043-a: Void saradomin.npcs.toml respawn_delay 150 and Novite 667 combat definitions 6247 150 agree.
        respawnDelay = 150
    }
    stats {
                // OSRS Wiki / 2007-era values (unchanged through 2011): 255 hp, att 280, str 196, def 300, mag 300, rng 250
        hitpoints = 2550
        attack = 280
        strength = 196
        defence = 300
        magic = 300
        ranged = 250
    }
    bonuses {
                // OSRS Wiki / 2007-era values (unchanged through 2011): all defences 100; attack 195, magic attack 200
        defenceStab = 100
        defenceSlash = 100
        defenceCrush = 100
        defenceMagic = 100
        defenceRanged = 100
        attackBonus = 195
        attackMagic = 200
    }
    anims {
        // melee 6964 (magic 6967). Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 6964
        block = 6966
        death = 6965
    }
    aggro {
        radius = 15
    }
}
