package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.dsl.NpcCombatDsl
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * Regular MA2 actors, never the Breach actors. Facts: OSRS Wiki Porazdir, Derwen and Justiciar Zachariah;
 * animation identities: RuneLite gameval AnimationID (cache2686). See docs/n415/RESEARCH.md.
 *
 * This supplies the sourced combat definitions and damage rules. Attack selection, special delays and tracking are
 * deliberately not activated here: their outstanding primary-source evidence is a release gate, not a set of defaults.
 */
object MageArenaIIGuardians {
    data class Guardian(
        val god: MageArenaII.God,
        val upstream: Int,
        val spell: CombatSpell,
        val attack: Int,
        val strength: Int,
        val magic: Int,
        val meleeBonus: Int,
        val magicBonus: Int,
        val xpBonusPercent: Int,
        val meleeStyle: StyleType,
        val meleeAnimation: Int,
        val magicAnimation: Int,
        val blockAnimation: Int,
        val deathAnimation: Int,
        val meleeMax: Int,
        val magicMax: Int,
    )

    val ALL = listOf(
        Guardian(MageArenaII.God.SARADOMIN, 7858, CombatSpell.SARADOMIN_STRIKE,
            500, 250, 180, 200, 80, 55, StyleType.SLASH, 7853, 7962, 7965, 7854, 43, 26),
        Guardian(MageArenaII.God.GUTHIX, 7859, CombatSpell.CLAWS_OF_GUTHIX,
            250, 150, 80, 0, 180, 20, StyleType.CRUSH, 7848, 7849, 7846, 7850, 16, 43),
        Guardian(MageArenaII.God.ZAMORAK, 7860, CombatSpell.FLAMES_OF_ZAMORAK,
            250, 150, 180, 0, 80, 20, StyleType.SLASH, 7840, 7841, 7838, 7843, 16, 43),
    )
    const val HITPOINTS = 320
    const val ATTACK_TICKS = 6
    const val ZACHARIAH_SWORD_TICKS = 3
    const val HEALER_HITPOINTS = 20
    const val HEALER_HEAL = 5

    /** Resolves only finalized regular-actor allocations. Missing mappings never fall back to OSRS or Breach ids. */
    fun forNpc(id: Int, npcId: (Int) -> Int? = MageArenaAssets::npc): Guardian? =
        ALL.firstOrNull { npcId(it.upstream) == id }

    /** No numeric fallback for animation ids either: a definition must not use an unrelated667 rig. */
    fun definition(guardian: Guardian, sequence: (Int) -> Int? = MageArenaAssets::sequence): NpcCombatDef? {
        val mappedMelee = sequence(guardian.meleeAnimation) ?: return null
        val mappedBlock = sequence(guardian.blockAnimation) ?: return null
        val mappedDeath = sequence(guardian.deathAnimation) ?: return null
        return NpcCombatDsl.Builder().apply {
            configs {
                attackSpeed = ATTACK_TICKS
                attackStyle = guardian.meleeStyle
                respawnDelay = 0
                waitFullDeathAnimation = true
                poisonImmune = true
                venomImmune = true
                // Combat.getNpcXpMultiplier already derives the Wiki's20/55% from these stats/bonuses.
                // Multiplying by1.20/1.55 here would apply the bonus twice.
                xpMultiplier = 1.0
            }
            stats {
                hitpoints = HITPOINTS * 10
                attack = guardian.attack
                strength = guardian.strength
                defence = 100
                magic = guardian.magic
                ranged = 1
            }
            bonuses {
                attackBonus = guardian.meleeBonus
                attackMagic = guardian.magicBonus
                magicDamageBonus = 80
                defenceStab = 200
                defenceSlash = 200
                defenceCrush = 200
                defenceMagic = -60
                defenceRanged = 200
            }
            anims { attack = mappedMelee; block = mappedBlock; death = mappedDeath }
            // Derwen explicitly does not count for Ent assignments. Only Porazdir is assigned.
            if (guardian.god == MageArenaII.God.ZAMORAK) {
                species { +NpcSpecies.DEMON }
                slayer { assignment = SlayerAssignment.BLACK_DEMON; experience = 376.0 }
            }
        }.build()
    }

    fun allows(guardian: Guardian, style: CombatClass?, spellId: Int?): Boolean =
        style == CombatClass.MAGIC && spellId == guardian.spell.uniqueId

    /** Shared launch gate: also rejects powered magic, wrong god spells and stale spell state on melee/ranged hits. */
    fun modifyIncomingDamage(npc: Npc, type: HitType, damage: Int, spellId: Int?): Int {
        val guardian = forNpc(npc.id) ?: return damage
        // The MA2 walkthrough explicitly recommends rings of recoil (two rings give80 damage).
        if (type == HitType.REFLECTED) return damage
        val magic = type == HitType.MAGIC || type == HitType.CRIT_MAGIC
        return if (magic && allows(guardian, CombatClass.MAGIC, spellId)) damage else 0
    }

    /** Uses the spell captured on this projectile, independent of subsequent spells or weapon switches. */
    fun modifyHit(guardian: Guardian, hit: Hit) {
        val matching = allows(guardian, hit.sourceCombatClass, hit.sourceSpellId)
        hit.hitmarks.filter { it.type != HitType.REFLECTED.id &&
            (!matching || it.type !in listOf(HitType.MAGIC.id, HitType.CRIT_MAGIC.id))
        }.forEach { it.damage = 0 }
    }
}
