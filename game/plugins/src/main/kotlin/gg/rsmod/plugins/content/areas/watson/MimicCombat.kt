package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.dsl.NpcCombatDsl

/** Wiki The Mimic, pinned revision in docs/CLUE/sources/manifest.json. */
object MimicCombat {
    fun definition() = NpcCombatDsl.Builder().apply {
        configs {
            respawnDelay = 0
            attackSpeed = 3
            xpMultiplier = 1.25 // Wiki The Mimic: 25% combat experience bonus.
            poisonImmune = true
            venomImmune = true
            attackStyle = StyleType.CRUSH
        }
        aggro { neverAggro() }
        stats {
            // The DSL accepts 667 HP * 10; its built definition uses real HP.
            hitpoints = 2300
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
            attack = 15404 // OSRS MIMIC_MELEE 8308, existing mimic import
            death = 15406 // OSRS MIMIC_DEATH 8310
        }
    }.build()
}
