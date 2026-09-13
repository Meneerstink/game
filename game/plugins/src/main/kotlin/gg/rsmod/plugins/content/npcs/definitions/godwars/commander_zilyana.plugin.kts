package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType

/** Commander Zilyana, leader of the Saradomin faction. Drops: godwars_drops.plugin.kts ([GodWarsDrops], RCV-011). */
val ZILYANA = Npcs.COMMANDER_ZILYANA

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
