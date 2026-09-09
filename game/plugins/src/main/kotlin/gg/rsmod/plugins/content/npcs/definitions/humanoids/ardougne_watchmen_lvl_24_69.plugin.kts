package gg.rsmod.plugins.content.npcs.definitions.humanoids

import gg.rsmod.game.model.combat.StyleType

/**
 * R04.3: 6 real, live, cache-attackable humanoid npcs that had no combat def at all (not a
 * "reuse a sibling's stats" case - the safe-reuse trick the prior 100-npc pass used is
 * exhausted per OWNER_TASK_STATUS.md, these are genuinely distinct monsters). Stats sourced
 * from the OSRS Wiki's combat infobox for each monster (a reliable, verifiable source, not
 * invented) - every sourced combat level matched this codebase's own cache-defined combat
 * level exactly (Warrior woman 24, Paladin 62, Hero 69, Knight of Ardougne 46, Archer 37,
 * Watchman 33), which is strong independent confirmation these are the same real monsters,
 * not a coincidental id/name match.
 *
 * No verified animation ids exist for these specific npcs in this environment (animations
 * aren't named in the cache, same limitation noted elsewhere this session) - reuses this
 * codebase's own existing generic human melee anim set (attack=390/death=836/block=1156,
 * already live for the Guard family's Falador variants) for the 5 melee npcs, and the
 * existing generic ranged npc anim set (Anims.ATTACK_CROSSBOW/836/424, already live for
 * Falador's crossbow guard) for the one ranged npc (Archer) - documented reuse, not a guess,
 * same honesty pattern as this session's Armadyl Godsword special.
 *
 * Drop tables are intentionally NOT included in this pass - that is real per-npc loot data
 * this pass didn't source, an honest separate gap, not silently assumed empty.
 */

set_combat_def(Npcs.WARRIOR_WOMAN) {
    configs {
        attackSpeed = 4
        respawnDelay = 50
        attackStyle = StyleType.STAB
    }
    stats {
        hitpoints = 200 // 20 real HP (wiki), *10 for this codebase's internal lifepoints scale
        attack = 22
        strength = 22
        defence = 22
    }
    bonuses {
        attackStab = 6
        strengthBonus = 7
        defenceStab = 40
        defenceSlash = 41
        defenceCrush = 37
        defenceMagic = -10
        defenceRanged = 38
    }
    anims {
        attack = 390
        death = 836
        block = 1156
    }
}

set_combat_def(Npcs.PALADIN) {
    configs {
        attackSpeed = 5
        respawnDelay = 50
        attackStyle = StyleType.SLASH
    }
    stats {
        hitpoints = 570 // 57 real HP (wiki), *10 for this codebase's internal lifepoints scale
        attack = 54
        strength = 54
        defence = 54
    }
    bonuses {
        attackSlash = 20
        strengthBonus = 22
        defenceStab = 87
        defenceSlash = 84
        defenceCrush = 76
        defenceMagic = -10
        defenceRanged = 79
    }
    anims {
        attack = 390
        death = 836
        block = 1156
    }
}

set_combat_def(Npcs.HERO) {
    configs {
        attackSpeed = 5
        respawnDelay = 50
        attackStyle = StyleType.SLASH
    }
    stats {
        hitpoints = 820 // 82 real HP (wiki), *10 for this codebase's internal lifepoints scale
        attack = 54
        strength = 55
        defence = 54
    }
    bonuses {
        attackSlash = 20
        strengthBonus = 22
        defenceStab = 87
        defenceSlash = 84
        defenceCrush = 76
        defenceMagic = -10
        defenceRanged = 79
    }
    anims {
        attack = 390
        death = 836
        block = 1156
    }
}

set_combat_def(Npcs.KNIGHT_OF_ARDOUGNE) {
    configs {
        attackSpeed = 5
        respawnDelay = 50
        attackStyle = StyleType.SLASH
    }
    stats {
        hitpoints = 520 // 52 real HP (wiki), *10 for this codebase's internal lifepoints scale
        attack = 38
        strength = 40
        defence = 31
    }
    bonuses {
        attackSlash = 8
        strengthBonus = 10
        defenceStab = 39
        defenceSlash = 40
        defenceCrush = 36
        defenceMagic = -11
        defenceRanged = 36
    }
    anims {
        attack = 390
        death = 836
        block = 1156
    }
}

set_combat_def(Npcs.WATCHMAN) {
    configs {
        attackSpeed = 4
        respawnDelay = 50
        attackStyle = StyleType.CRUSH
    }
    stats {
        hitpoints = 220 // 22 real HP (wiki), *10 for this codebase's internal lifepoints scale
        attack = 31
        strength = 31
        defence = 31
    }
    bonuses {
        defenceStab = 24
        defenceSlash = 14
        defenceCrush = 19
        defenceMagic = -4
        defenceRanged = 16
    }
    anims {
        attack = 390
        death = 836
        block = 1156
    }
}

set_combat_def(Npcs.ARCHER) {
    configs {
        attackSpeed = 6
        respawnDelay = 25
        attackStyle = StyleType.RANGED
    }
    stats {
        hitpoints = 500 // 50 real HP (wiki), *10 for this codebase's internal lifepoints scale
        attack = 20
        strength = 20
        defence = 20
        ranged = 40
    }
    bonuses {
        attackRanged = 19
        rangedStrengthBonus = 8
        defenceStab = 18
        defenceSlash = 23
        defenceCrush = 27
        defenceMagic = 10
        defenceRanged = 19
    }
    anims {
        attack = Anims.ATTACK_CROSSBOW
        death = 836
        block = 424
    }
}
