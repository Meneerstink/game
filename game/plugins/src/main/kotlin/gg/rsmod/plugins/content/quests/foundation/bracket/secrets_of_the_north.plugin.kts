package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.combat.StyleType

/*
 * PROJECT48 Q41 Secrets of the North: the three foes of the quest (imported OSRS npcs, [SotnNpcs]). They only exist as the owned foe
 * of a quest fight (QuestFights), never as a world spawn. Stats and bonuses: OSRS Wiki monster infoboxes (21-11) - Evelot (level 148),
 * Assassin (level 262), Strange Creature ranged form (level 368). Hitpoints are written times ten (NpcCombatDsl). Attacks: [SotnFoes].
 */
set_combat_def(npc = SotnNpcs.EVELOT) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 0
    }
    stats {
        hitpoints = 1600
        attack = 160
        strength = 150
        defence = 120
        magic = 80
        ranged = 1
    }
    bonuses {
        attackBonus = 30
        strengthBonus = 10
        defenceStab = 70
        defenceSlash = 60
        defenceCrush = 40
        defenceMagic = 0
        defenceRanged = 40
    }
    anims {
        attack = SotnFoes.EVELOT_ATTACK
        block = SotnFoes.EVELOT_BLOCK
        death = SotnFoes.HUMAN_DEATH
    }
}

set_combat_def(npc = SotnNpcs.ASSASSIN) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.SLASH
        respawnDelay = 0
    }
    stats {
        hitpoints = 4000
        attack = 140
        strength = 140
        defence = 140
        magic = 140
        ranged = 140
    }
    bonuses {
        attackBonus = 66
        strengthBonus = 12
        attackRanged = 66
        rangedStrengthBonus = 12
        defenceStab = 60
        defenceSlash = 30
        defenceCrush = 80
        defenceMagic = 200
        defenceRanged = 50
    }
    anims {
        attack = SotnFoes.ASSASSIN_ATTACK
        block = SotnFoes.EVELOT_BLOCK
        death = SotnFoes.HUMAN_DEATH
    }
}

set_combat_def(npc = SotnNpcs.STRANGE_CREATURE) {
    configs {
        attackSpeed = 6
        attackStyle = StyleType.RANGED
        respawnDelay = 0
        waitFullDeathAnimation = true
    }
    stats {
        hitpoints = 5500
        attack = 280
        strength = 280
        defence = 200
        magic = 150
        ranged = 280
    }
    bonuses {
        attackBonus = 60
        strengthBonus = -10
        attackMagic = 90
        magicDamageBonus = 66
        defenceStab = 110
        defenceSlash = 90
        defenceCrush = 80
        defenceMagic = 280
        defenceRanged = 0
    }
    anims {
        attack = SotnFoes.CREATURE_RANGED
        block = -1
        death = SotnFoes.CREATURE_DEATH
    }
}

on_npc_combat(*SotnFoes.ids) {
    npc.queue {
        SotnFoes.handleSpecialCombat(this)
    }
}
