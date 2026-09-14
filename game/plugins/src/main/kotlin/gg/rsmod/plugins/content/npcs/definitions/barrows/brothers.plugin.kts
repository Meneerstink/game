package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.combat.StyleType

/**
 * Combat defs for the six Barrows brothers = OSRS Wiki infoboxes (raw wikitext 2026-09-14, "Ahrim the Blighted" ... "Verac the Defiled"):
 * hitpoints 100 each, attack style and speed, att/str/def/mage/range, attbns/strbns/amagic/mbns/arange/rngbns and defences. Max hits
 * (20/29/24/20/23/23) live in npc-attacks.json. ADAPTED: 667 has one ranged defence (wiki dlight/dstandard/dheavy; dstandard used) and
 * levels below 1 are raised to 1 by NpcCombatBuilder. NOT YET BUILT (adjacent gap, recorded in HANDOFF): the OSRS NPC set effects -
 * Dharok +1% max hit per missing hitpoint, Verac 25% defence-ignoring hits (max 15 through Protect from Melee), Guthan 25% heal,
 * Torag 25% run-energy drain, Karil and Ahrim 25% effects. See Barrows.kt for the run/reward flow.
 */

data class BrotherConfig(
    val npc: Int,
    val style: StyleType,
    val speed: Int,
    val atk: Int,
    val str: Int,
    val def: Int,
    val mag: Int,
    val rng: Int,
    val attbns: Int,
    val strbns: Int,
    val amagic: Int,
    val mbns: Int,
    val arange: Int,
    val rngbns: Int,
    val dstab: Int,
    val dslash: Int,
    val dcrush: Int,
    val dmagic: Int,
    val dranged: Int,
    /** Attack / block / death sequence ids (Matrix 718 NPCCombatDefinitions, each verified present in the 667 cache AnimDefs). */
    val attackAnim: Int,
    val blockAnim: Int,
    val deathAnim: Int,
)

/** Real hitpoints from the wiki; the DSL takes real HP * 10. */
val BROTHER_HITPOINTS = 100

val brotherConfigs =
    listOf(
        BrotherConfig(Npcs.AHRIM_THE_BLIGHTED, StyleType.MAGIC, speed = 6, atk = 1, str = 1, def = 100, mag = 100, rng = 1, attbns = 0, strbns = 68, amagic = 73, mbns = 0, arange = -19, rngbns = 0, dstab = 103, dslash = 85, dcrush = 117, dmagic = 73, dranged = 0, attackAnim = 14223, blockAnim = 2079, deathAnim = 7197),
        BrotherConfig(Npcs.DHAROK_THE_WRETCHED, StyleType.SLASH, speed = 7, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 105, amagic = -58, mbns = 0, arange = -18, rngbns = 0, dstab = 252, dslash = 250, dcrush = 244, dmagic = -11, dranged = 249, attackAnim = 2067, blockAnim = 2063, deathAnim = 7197),
        BrotherConfig(Npcs.GUTHAN_THE_INFESTED, StyleType.CRUSH, speed = 5, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 75, amagic = -50, mbns = 0, arange = -19, rngbns = 0, dstab = 259, dslash = 257, dcrush = 241, dmagic = -11, dranged = 250, attackAnim = 2080, blockAnim = 2063, deathAnim = 7197),
        BrotherConfig(Npcs.KARIL_THE_TAINTED, StyleType.RANGED, speed = 4, atk = 1, str = 1, def = 100, mag = 1, rng = 100, attbns = 0, strbns = 0, amagic = -26, mbns = 0, arange = 134, rngbns = 55, dstab = 79, dslash = 71, dcrush = 90, dmagic = 106, dranged = 100, attackAnim = 2075, blockAnim = 424, deathAnim = 7197),
        BrotherConfig(Npcs.TORAG_THE_CORRUPTED, StyleType.CRUSH, speed = 5, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 72, amagic = -33, mbns = 0, arange = -11, rngbns = 0, dstab = 221, dslash = 235, dcrush = 222, dmagic = 0, dranged = 221, attackAnim = 2068, blockAnim = 2063, deathAnim = 7197),
        BrotherConfig(Npcs.VERAC_THE_DEFILED, StyleType.STAB, speed = 5, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 72, amagic = -42, mbns = 0, arange = -14, rngbns = 0, dstab = 227, dslash = 230, dcrush = 221, dmagic = 0, dranged = 225, attackAnim = 2067, blockAnim = 2063, deathAnim = 7197),
    )

brotherConfigs.forEach { cfg ->
    on_npc_death(cfg.npc) {
        val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
        Barrows.onBrotherDeath(npc, killer)
    }

    set_combat_def(npc = cfg.npc) {
        configs {
            attackSpeed = cfg.speed
            attackStyle = cfg.style
            respawnDelay = 200
        }
        stats {
            // Was cfg.hp (1000) * 10 = 1,000 real hitpoints per brother; the wiki gives 100.
            hitpoints = BROTHER_HITPOINTS * 10
            attack = cfg.atk
            strength = cfg.str
            defence = cfg.def
            magic = cfg.mag
            ranged = cfg.rng
        }
        bonuses {
            attackBonus = cfg.attbns
            strengthBonus = cfg.strbns
            attackMagic = cfg.amagic
            magicDamageBonus = cfg.mbns
            attackRanged = cfg.arange
            rangedStrengthBonus = cfg.rngbns
            defenceStab = cfg.dstab
            defenceSlash = cfg.dslash
            defenceCrush = cfg.dcrush
            defenceMagic = cfg.dmagic
            defenceRanged = cfg.dranged
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
