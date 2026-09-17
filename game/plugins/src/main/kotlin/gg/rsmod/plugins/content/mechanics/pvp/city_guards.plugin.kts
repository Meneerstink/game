package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * Deadman guard combat definitions + stationed spawns (OSRS Wiki "Guard (Deadman Mode)": level 1337,
 * 800 hitpoints, 2-tick attack speed, slash or ranged, attack +60, strength +7, stab/slash/crush
 * defence +8/+9/+7, ranged defence +8, aggressive). Damage is not taken from these stats but from
 * the sourced ramp in [CityGuards.rampedMaxHit]. Owner 2026-09-17: every guard - the imported OSRS
 * Deadman guards and the Third Age Ranger / Third Age Mage / Lucien posts - "moeten allemaal de
 * zelfde stats hebben", so one shared definition per attack style is applied to every id of that
 * style. The humanoid models animate with the shared 667 human attack/block/death sequences, the
 * same way the imported Ferox npcs reuse the 667 Man movement set.
 */
fun KotlinPlugin.meleeGuard(id: Int) =
    set_combat_def(id) {
        configs {
            attackSpeed = CityGuards.ATTACK_SPEED_CYCLES
            respawnDelay = 50
        }
        aggro {
            radius = 8
            searchDelay = 1
            alwaysAggro()
        }
        stats {
            hitpoints = CityGuards.HITPOINTS_TIMES_TEN
            attack = 200
            strength = 200
            defence = 150
            magic = 200
            ranged = 200
        }
        bonuses {
            attackStab = 60
            attackSlash = 60
            attackCrush = 60
            strengthBonus = 7
            defenceStab = 8
            defenceSlash = 9
            defenceCrush = 7
            defenceMagic = 8
            defenceRanged = 8
        }
        anims {
            attack = Anims.ATTACK_SLASH
            death = Anims.HUMAN_DEATH
            block = Anims.BLOCK_SHIELD
        }
    }

fun KotlinPlugin.rangedGuard(id: Int) =
    set_combat_def(id) {
        configs {
            attackSpeed = CityGuards.ATTACK_SPEED_CYCLES
            attackStyle = StyleType.RANGED
            respawnDelay = 50
        }
        aggro {
            radius = 8
            searchDelay = 1
            alwaysAggro()
        }
        stats {
            hitpoints = CityGuards.HITPOINTS_TIMES_TEN
            attack = 200
            strength = 200
            defence = 150
            magic = 200
            ranged = 200
        }
        bonuses {
            attackRanged = 60
            rangedStrengthBonus = 7
            defenceStab = 8
            defenceSlash = 9
            defenceCrush = 7
            defenceMagic = 8
            defenceRanged = 8
        }
        anims {
            // Real cache-sourced crossbow set already used by the Falador crossbow guards.
            attack = Anims.ATTACK_CROSSBOW
            death = 836
            block = 424
        }
    }

/** The owner's Third Age Mage post fights through the engine's magic strategy with the same stats;
 * the cast is the 667 wizard-npc pattern (spell 71 = Fire Strike visuals, see wizard_lvl_9), the
 * damage the shared guard ramp (MagicCombatFormula). */
fun KotlinPlugin.mageGuard(id: Int) =
    set_combat_def(id) {
        configs {
            attackSpeed = CityGuards.ATTACK_SPEED_CYCLES
            attackStyle = StyleType.MAGIC
            spell = CombatSpell.FIRE_STRIKE.uniqueId
            respawnDelay = 50
        }
        aggro {
            radius = 8
            searchDelay = 1
            alwaysAggro()
        }
        stats {
            hitpoints = CityGuards.HITPOINTS_TIMES_TEN
            attack = 200
            strength = 200
            defence = 150
            magic = 200
            ranged = 200
        }
        bonuses {
            attackMagic = 60
            defenceStab = 8
            defenceSlash = 9
            defenceCrush = 7
            defenceMagic = 8
            defenceRanged = 8
        }
        anims {
            death = Anims.HUMAN_DEATH
            block = Anims.BLOCK_SHIELD
        }
    }

CityGuards.MELEE_GUARD_IDS.forEach { meleeGuard(it) }
CityGuards.RANGED_GUARD_IDS.forEach { rangedGuard(it) }
CityGuards.MAGE_GUARD_IDS.forEach { mageGuard(it) }

/** The Wizguard never fights through the combat engine (it casts once and vanishes, see
 * [CityGuards.wizguardStrike]); this def only keeps combatDef/aggro lookups from seeing a missing
 * definition. */
set_combat_def(CityGuards.WIZGUARD_ID) {
    configs {
        attackSpeed = CityGuards.ATTACK_SPEED_CYCLES
        respawnDelay = 0
    }
    stats {
        hitpoints = CityGuards.HITPOINTS_TIMES_TEN
        defence = 150
        magic = 200
    }
    bonuses {
        defenceMagic = 8
    }
    anims {
        death = Anims.HUMAN_DEATH
    }
}

on_world_init {
    println(CityGuards.spawnStationedGuards(world))
}

/** Keeps the 1337 level, skulled-only aggro, smart pathing, 8-tile patrol and the zone leash on
 * every (re)spawn of a Deadman guard. */
on_global_npc_spawn {
    if (CityGuards.isGuard(npc)) {
        CityGuards.configure(npc)
    }
}

/** Owner 2026-09-17: guards never leave a safe zone and stop the moment their target is no longer a
 * skulled intruder inside one. */
on_timer(CityGuards.GUARD_LEASH_TIMER) {
    if (npc.isActive()) {
        CityGuards.leash(npc)
        npc.timers[CityGuards.GUARD_LEASH_TIMER] = 2
    }
}

/** A skulled player logging out under the guards: the guards stand down and patrol. */
on_logout {
    CityGuards.release(player)
}
