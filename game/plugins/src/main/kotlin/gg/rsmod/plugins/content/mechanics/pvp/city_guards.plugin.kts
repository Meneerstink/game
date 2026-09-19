package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * Deadman guard combat definitions + stationed spawns (OSRS Wiki "Guard (Deadman Mode)": level 1337,
 * 800 hitpoints, 2-tick attack speed, slash or ranged, attack 800, strength 400, defence 300,
 * magic/ranged 1, attack +60, strength +7, stab/slash/crush/magic/ranged defence 8/9/7/0/8,
 * aggressive). Damage is not taken from these stats but from
 * the sourced ramp in [CityGuards.rampedMaxHit]. The three imported OSRS Deadman guard variants
 * use one shared definition per attack style. The humanoid models animate with the shared 667
 * human attack/block/death sequences, the
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
            attack = CityGuards.ATTACK_LEVEL
            strength = CityGuards.STRENGTH_LEVEL
            defence = CityGuards.DEFENCE_LEVEL
            magic = CityGuards.MAGIC_LEVEL
            ranged = CityGuards.RANGED_LEVEL
        }
        bonuses {
            attackStab = CityGuards.ATTACK_BONUS
            attackSlash = CityGuards.ATTACK_BONUS
            attackCrush = CityGuards.ATTACK_BONUS
            strengthBonus = CityGuards.STRENGTH_BONUS
            defenceStab = CityGuards.DEFENCE_STAB_BONUS
            defenceSlash = CityGuards.DEFENCE_SLASH_BONUS
            defenceCrush = CityGuards.DEFENCE_CRUSH_BONUS
            defenceMagic = CityGuards.DEFENCE_MAGIC_BONUS
            defenceRanged = CityGuards.DEFENCE_RANGED_BONUS
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
            // Without a projectile on the def RangedCombatStrategy fires nothing for an npc: the
            // guard only played its animation. Plain arrow (the tier is not published: ADAPTED).
            attackProjectile = Gfx.BRONZE_ARROW_IN_FLIGHT
            respawnDelay = 50
        }
        aggro {
            radius = 8
            searchDelay = 1
            alwaysAggro()
        }
        stats {
            hitpoints = CityGuards.HITPOINTS_TIMES_TEN
            attack = CityGuards.ATTACK_LEVEL
            strength = CityGuards.STRENGTH_LEVEL
            defence = CityGuards.DEFENCE_LEVEL
            magic = CityGuards.MAGIC_LEVEL
            ranged = CityGuards.RANGED_LEVEL
        }
        bonuses {
            attackRanged = CityGuards.ATTACK_BONUS
            rangedStrengthBonus = CityGuards.STRENGTH_BONUS
            defenceStab = CityGuards.DEFENCE_STAB_BONUS
            defenceSlash = CityGuards.DEFENCE_SLASH_BONUS
            defenceCrush = CityGuards.DEFENCE_CRUSH_BONUS
            defenceMagic = CityGuards.DEFENCE_MAGIC_BONUS
            defenceRanged = CityGuards.DEFENCE_RANGED_BONUS
        }
        anims {
            // The ranged guards hold a bow, not a crossbow: OSRS 11203 is the Falador longbow guard
            // body (OSRS 3272-3274: models 233/250/9458/9450/176/28285/185) with bow model 512 in
            // the weapon slot, and the Third Age Ranger wields the third-age bow. The crossbow
            // sequence 4230 made them "shoot a crossbow" they do not hold (owner 2026-09-18);
            // OSRS Wiki "Guard": only the crossbow guards use a crossbow animation.
            attack = Anims.ATTACK_BOW
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
