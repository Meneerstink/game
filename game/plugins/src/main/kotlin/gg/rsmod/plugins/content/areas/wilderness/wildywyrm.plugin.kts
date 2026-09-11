package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.combat.StyleType

/**
 * WildyWyrm (3334).
 *
 * Spawn: Novite npcspawns.json places it at 3093,10123 (Forinthry Dungeon, Wilderness level 26 underground).
 * Combat definition: lifepoints 10,000, strength 715, anims 12791 / 12792 / 12793, attack speed 4, death
 * delay 5, respawn 60 from the Matrix-sourced bulk row; attack bonuses 500 and defence bonuses 300 from
 * Novite npcbonuses.json.
 *
 * PROVISIONAL (not source data): neither donor carries attack, ranged or magic levels for this npc - Matrix
 * rolls npc accuracy from bonuses alone - so the three offensive levels reuse the row's strength level so
 * that the target's level-based accuracy formulas give the boss a meaningful roll. Replace when a
 * revision-667 level source is found.
 *
 * Attack logic: [gg.rsmod.plugins.content.combat.scripts.impl.WildyWyrmCombatScript].
 */
spawn_npc(npc = Npcs.WILDYWYRM, x = 3093, z = 10123, height = 0, walkRadius = 5, direction = Direction.NORTH, static = false)

set_combat_def(npc = Npcs.WILDYWYRM) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.RANGED
        respawnDelay = 60
        deathDelay = 5
    }
    stats {
        hitpoints = 10000
        attack = 715
        strength = 715
        defence = 1
        magic = 715
        ranged = 715
    }
    bonuses {
        attackStab = 500
        attackSlash = 500
        attackCrush = 500
        attackMagic = 500
        attackRanged = 500
        defenceStab = 300
        defenceSlash = 300
        defenceCrush = 300
        defenceMagic = 300
        defenceRanged = 300
    }
    anims {
        attack = 12791
        block = 12792
        death = 12793
    }
    aggro {
        radius = 8
    }
}
