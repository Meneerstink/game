package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType

/** K'ril Tsutsaroth, leader of the Zamorak faction. Drops: godwars_drops.plugin.kts ([GodWarsDrops], RCV-011). */
val KRIL = Npcs.KRIL_TSUTSAROTH

set_combat_def(npc = KRIL) {
    configs {
        attackSpeed = 6 // OSRS Wiki: 6 ticks
        attackStyle = StyleType.SLASH
        respawnDelay = 60
    }
    stats {
                // OSRS Wiki / 2007-era values (unchanged through 2011): 255 hp, att 340, str 300, def 270, mag 200, rng 1
        hitpoints = 2550
        attack = 340
        strength = 300
        defence = 270
        magic = 200
        ranged = 1
    }
    bonuses {
                // OSRS Wiki / 2007-era values (unchanged through 2011): stab/slash/crush 80, magic 130, ranged 80; attack 160
        defenceStab = 80
        defenceSlash = 80
        defenceCrush = 80
        defenceMagic = 130
        defenceRanged = 80
        attackBonus = 160
    }
    anims {
        // melee 14962, special 14963, block 14965. Death: the donor carries none and 14964 is absent from the 667 cache - SOURCE_BLOCKED, so no animation rather than a human one. Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 14962
        block = 14965
        death = -1
    }
    aggro {
        radius = 15
    }
}
