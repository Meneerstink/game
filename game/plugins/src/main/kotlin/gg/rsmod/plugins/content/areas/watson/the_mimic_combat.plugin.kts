package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.combat.StyleType

/**
 * Owner answer Q10: The Mimic's combat definition, OSRS Wiki "The Mimic" infobox (id 8633, raw wikitext 2026-09-14): combat 186, size 5,
 * hitpoints 230, att 185, str 120, def 120, mage 60, range 1, attbns +135, strbns +48, amagic +180, arange 0, dstab 160, dslash 165,
 * dcrush 150, dmagic 30, dlight / dstandard / dheavy 145, poison and venom resistance 100, aggressive No, attack speed 3, attack style Crush /
 * Magic. Sequences: MIMIC_MELEE 8308 -> 15404, MIMIC_DEATH 8310 -> 15406 (OsrsNpcImportTool batch "mimic").
 * ADAPTED: 667 has one ranged defence (the three OSRS ranged defences are all 145); the DSL has no venom immunity flag (poison immune set).
 * SOURCE_GAP: no block sequence in the sources (default kept); no per-style melee attack bonuses on the infobox (attbns only).
 * Not here (fight, blocked by owner question 17): candy attack, stomp, Third Age minion spawns, attempts, instance and rewards.
 */
set_combat_def(Npcs.THE_MIMIC_14402) {
    configs {
        // Required by the DSL. The Mimic is an instance boss summoned per fight (no world spawn), so it never respawns; 0 as for the dark core.
        respawnDelay = 0
        attackSpeed = 3
        poisonImmune = true
        attackStyle = StyleType.CRUSH
    }
    aggro {
        neverAggro()
    }
    stats {
        hitpoints = 230
        attack = 185
        strength = 120
        defence = 120
        magic = 60
        ranged = 1
    }
    bonuses {
        attackBonus = 135
        strengthBonus = 48
        attackMagic = 180
        attackRanged = 0
        defenceStab = 160
        defenceSlash = 165
        defenceCrush = 150
        defenceMagic = 30
        defenceRanged = 145
    }
    anims {
        attack = 15404
        death = 15406
    }
}
