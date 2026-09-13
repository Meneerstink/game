package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType

/**
 * Kree'arra, leader of the Armadyl faction at the God Wars Dungeon. Attacks: shared NpcAttacks model + GodWarsGenerals
 * hooks (RCV-005). Drops: godwars_drops.plugin.kts ([GodWarsDrops], RCV-011 Void 2011-filtered tables).
 */
val KREEARRA = Npcs.KREEARRA

set_combat_def(npc = KREEARRA) {
    configs {
        attackSpeed = 3 // OSRS Wiki: 3 ticks
        attackStyle = StyleType.RANGED
        // Owner decision 2026-09-13: Void armadyl.npcs.toml respawn_delay 150 (Novite 60 not used).
        respawnDelay = 150
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
