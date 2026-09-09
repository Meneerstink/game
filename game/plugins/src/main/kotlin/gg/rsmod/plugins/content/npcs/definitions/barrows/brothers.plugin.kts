package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.combat.StyleType

/**
 * Combat defs for the six Barrows brothers. Distinguished by style/stats only (no bespoke
 * set-effect mechanics like Guthan's lifesteal or Dharok's low-hp damage boost - those are
 * real player equipment set effects in vanilla RS anyway, not monster behaviour, so nothing
 * to replicate here for the NPCs themselves). See Barrows.kt for the run/reward flow.
 */

data class BrotherConfig(
    val npc: Int,
    val style: StyleType,
    val hp: Int,
    val atk: Int,
    val str: Int,
    val def: Int,
    val mag: Int,
    val rng: Int,
    /** Attack / block / death sequence ids (Matrix 718 NPCCombatDefinitions, each verified present in the 667 cache AnimDefs). */
    val attackAnim: Int,
    val blockAnim: Int,
    val deathAnim: Int,
)

val brotherConfigs =
    listOf(
        BrotherConfig(Npcs.AHRIM_THE_BLIGHTED, StyleType.MAGIC, hp = 255, atk = 75, str = 75, def = 70, mag = 150, rng = 1, attackAnim = 14223, blockAnim = 2079, deathAnim = 7197),
        BrotherConfig(Npcs.DHAROK_THE_WRETCHED, StyleType.CRUSH, hp = 255, atk = 150, str = 150, def = 70, mag = 1, rng = 1, attackAnim = 2067, blockAnim = 2063, deathAnim = 7197),
        BrotherConfig(Npcs.GUTHAN_THE_INFESTED, StyleType.STAB, hp = 255, atk = 105, str = 105, def = 90, mag = 1, rng = 1, attackAnim = 2080, blockAnim = 2063, deathAnim = 7197),
        BrotherConfig(Npcs.KARIL_THE_TAINTED, StyleType.RANGED, hp = 255, atk = 75, str = 75, def = 70, mag = 1, rng = 150, attackAnim = 2075, blockAnim = 424, deathAnim = 7197),
        BrotherConfig(Npcs.TORAG_THE_CORRUPTED, StyleType.CRUSH, hp = 255, atk = 105, str = 130, def = 90, mag = 1, rng = 1, attackAnim = 2068, blockAnim = 2063, deathAnim = 7197),
        BrotherConfig(Npcs.VERAC_THE_DEFILED, StyleType.SLASH, hp = 255, atk = 130, str = 105, def = 100, mag = 1, rng = 1, attackAnim = 2067, blockAnim = 2063, deathAnim = 7197),
    )

brotherConfigs.forEach { cfg ->
    on_npc_death(cfg.npc) {
        val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
        Barrows.onBrotherDeath(npc, killer)
    }

    set_combat_def(npc = cfg.npc) {
        configs {
            attackSpeed = 5
            attackStyle = cfg.style
            respawnDelay = 200
        }
        stats {
            hitpoints = cfg.hp * 10 // cfg.hp is the real 255 HP; *10 for this codebase's internal lifepoints scale
            attack = cfg.atk
            strength = cfg.str
            defence = cfg.def
            magic = cfg.mag
            ranged = cfg.rng
        }
        bonuses {
            defenceStab = 30
            defenceSlash = 30
            defenceCrush = 30
            defenceMagic = 30
            defenceRanged = 30
        }
        anims {
            attack = cfg.attackAnim
            block = cfg.blockAnim
            death = cfg.deathAnim
        }
        aggro {
            radius = 15
        }
    }
}
