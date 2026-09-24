package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.plugins.content.combat.audio.NpcCombatAudio

/**
 * Deadman guard combat definitions + stationed spawns (OSRS Wiki "Guard (Deadman Mode)": level 1337,
 * 800 hitpoints, 2-tick attack speed, slash or ranged, attack 800, strength 400, defence 300,
 * magic/ranged 1, attack +60, strength +7, stab/slash/crush/magic/ranged defence 8/9/7/0/8,
 * aggressive). Damage is not taken from these stats but from
 * the sourced ramp in [CityGuards.rampedMaxHit]. Every imported per-city OSRS guard variant
 * ([CityGuards.VARIANTS]) uses the one shared definition of its attack style. The humanoid models animate with the shared 667
 * human attack/block/death sequences, the
 * same way the imported Ferox npcs reuse the 667 Man movement set.
 */
/*
 * The Tree Gnome Stronghold guards (OSRS 6574/11199 -> 14414/14415) are gnomes: human sequences cannot animate the OSRS gnome
 * skeleton. OSRS GNOME_ATTACKSWORD 12045 / GNOME_ATTACKBOW 12043 / GNOME_BLOCK 12046 / GNOME_DEATH 12048 (RuneLite gameval names),
 * imported with the deadman-guard batch (tx-20260924-181836).
 */
val GNOME_GUARDS = setOf(14414, 14415)
val GNOME_ATTACK_BOW = 15777
val GNOME_ATTACK_SWORD = 15778
val GNOME_BLOCK = 15779
val GNOME_DEATH = 15780

/*
 * The Sophanem (14428/14429) and Rellekka (14434/14435) guards stand with OSRS HUMAN_STAFFREADY 813 (same-body OSRS Sophanem Guard /
 * Honour guard), which animates the OSRS human skeleton, so their combat uses the OSRS sequences imported onto it:
 * HUMAN_SWORD_SLASH 390 -> 15705, HUMAN_SHIELD_DEFENCE 1156 -> 15781, HUMAN_DEATH 836 -> 15707, HUMAN_BOW 426 -> 15782,
 * HUMAN_UNARMEDBLOCK 424 -> 15706 (tx-20260924-182230).
 */
val OSRS_RIG_GUARDS = setOf(14428, 14429, 14434, 14435)

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
            val gnome = id in GNOME_GUARDS
            val osrsRig = id in OSRS_RIG_GUARDS
            attack = if (gnome) GNOME_ATTACK_SWORD else if (osrsRig) 15705 else Anims.ATTACK_SLASH
            death = if (gnome) GNOME_DEATH else if (osrsRig) 15707 else Anims.HUMAN_DEATH
            block = if (gnome) GNOME_BLOCK else if (osrsRig) 15781 else Anims.BLOCK_SHIELD
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
            val gnome = id in GNOME_GUARDS
            val osrsRig = id in OSRS_RIG_GUARDS
            attack = if (gnome) GNOME_ATTACK_BOW else if (osrsRig) 15782 else Anims.ATTACK_BOW
            death = if (gnome) GNOME_DEATH else if (osrsRig) 15707 else 836
            block = if (gnome) GNOME_BLOCK else if (osrsRig) 15706 else 424
        }
    }

CityGuards.MELEE_GUARD_IDS.forEach { meleeGuard(it) }
CityGuards.RANGED_GUARD_IDS.forEach { rangedGuard(it) }

/*
 * Owner live retest 2026-09-19 ("they have no sounds"): the imported guards had no combat-sound row. Defend 513 / death 512 are
 * the rev-667 Guard row (combat-sounds.json, guard_edgeville 296-299); the attack sound is the one of the weapon each guard holds
 * (items.yml attack_audio: longsword 2500, longbow 2700), played on the target like every Void attack sound.
 */
CityGuards.MELEE_GUARD_IDS.forEach {
    NpcCombatAudio.register(NpcCombatAudio.Row(id = it, name = "Guard", attack = listOf(NpcCombatAudio.Sound(id = 2500)), defend = 513, death = 512))
}
CityGuards.RANGED_GUARD_IDS.forEach {
    NpcCombatAudio.register(NpcCombatAudio.Row(id = it, name = "Guard", attack = listOf(NpcCombatAudio.Sound(id = 2700)), defend = 513, death = 512))
}

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
    }
    // Always re-arm (owner 2026-09-24, "geen enkele guard roamt"): an npc is only active while a player has it in view, so
    // re-arming inside the check let the timer lapse at boot - before anyone logged in - and no guard ever patrolled.
    npc.timers[CityGuards.GUARD_LEASH_TIMER] = 2
}

/** A skulled player logging out under the guards: the guards stand down and patrol. */
on_logout {
    CityGuards.release(player)
}
