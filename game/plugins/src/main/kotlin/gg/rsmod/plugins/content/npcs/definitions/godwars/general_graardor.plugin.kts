package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType

/** General Graardor, leader of the Bandos faction. Drops: godwars_drops.plugin.kts ([GodWarsDrops], RCV-011). */
val GRAARDOR = Npcs.GENERAL_GRAARDOR

set_combat_def(npc = GRAARDOR) {
    configs {
        attackSpeed = 6
        attackStyle = StyleType.CRUSH
        // RCV-011 Q-043-a: Void bandos.npcs.toml respawn_delay 150 and Novite 667 combat definitions 6260 150 agree.
        respawnDelay = 150
    }
    stats {
                // OSRS Wiki / 2007-era values (unchanged through 2011): 255 hp, att 280, str 350, def 250, mag 80, rng 350
        hitpoints = 2550
        attack = 280
        strength = 350
        defence = 250
        magic = 80
        ranged = 350
    }
    bonuses {
                // OSRS Wiki / 2007-era values (unchanged through 2011): stab/slash/crush/ranged 90, magic 298; attack 120, ranged attack 100
        defenceStab = 90
        defenceSlash = 90
        defenceCrush = 90
        defenceMagic = 298
        defenceRanged = 90
        attackBonus = 120
        attackRanged = 100
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
