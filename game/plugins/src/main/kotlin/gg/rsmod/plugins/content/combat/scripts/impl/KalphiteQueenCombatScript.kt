package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.CombatFormula
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula

/**
 * Kalphite Queen combat script.
 *
 * Source (OSRS Wiki combat infobox, cross-checked against this project's own [MeleeCombatFormula]
 * - her Attack/Strength/Defence of 300 with zero explicit offensive bonus reproduces the infobox's
 * stated max hit of 31 exactly via this project's existing melee formula: `0.5 + (300+8)*64/640 =
 * 31.3`, which is strong internal corroboration that 31 is her real, style-independent cap rather
 * than a per-style computed number): combat level 333, hitpoints 255 (x10 = 2550, this project's
 * convention), Attack/Strength/Defence 300, Magic 150, Ranged 1 (not used - her ranged "barbed
 * spine" attack is not driven by a Ranged skill level, the same way [KingBlackDragonCombatScript]'s
 * fire breath is CombatClass.MAGIC rather than a Ranged-skill-driven attack; both are custom, fixed
 * per-hit numbers scripted here rather than derived from a skill level that the infobox itself
 * lists as 1/irrelevant), attack speed 4 ticks (all styles), defence bonuses stab/slash +50,
 * **crush +10** (her real, well-documented weak point), magic +100, ranged +100.
 *
 * Two forms, identified from this cache's own [gg.rsmod.game.fs.def.NpcDef.headIcon] field (decoded
 * via [PrayerIcon], see its KDoc for how that sheet was reverse-engineered) rather than guessed:
 * npc 1158 carries `headIcon=6` = [PrayerIcon.PROTECT_FROM_MISSLES_AND_MAGIC], matching the wiki's
 * "the first form will simultaneously have Protect from Magic and Protect from Missiles active" -
 * so 1158 is the first (crawling) form and attacks primarily with Magic/Ranged. npc 1160 carries
 * `headIcon=0` = [PrayerIcon.PROTECT_FROM_MELEE], matching "the second form will only have Protect
 * from Melee active" - so 1160 is the second (airborne) form and attacks primarily with Melee. Both
 * forms also have "a decently accurate melee attack" per the wiki, so each form's non-dominant
 * styles are kept as a minority weight rather than removed entirely. npc 1159 (no options, no
 * headicon) is the ~20-tick transformation frame between the two - see `kalphite_queen.plugin.kts`.
 *
 * KNOWN LIMITATION, not invented here: like every other multi-style boss already in this codebase
 * (Kree'arra, Zilyana, K'ril, Nex, Graardor - none of which rotate styles either), the attack
 * *animation* played is her one placeholder `set_combat_def` anim (`Anims.ATTACK_PUNCH`), which is
 * the pre-existing, already-audited, SOURCE_BLOCKED project-wide npc-attack-animation gap recorded
 * in `RSPS_LIVE_BUG_BACKLOG.md` ("NPC / BOSS ATTACK ANIMATIONS") - Kalphite Queen is added to that
 * same known gap, not a new regression.
 */
object KalphiteQueenCombatScript : CombatScript() {
    val FIRST_FORM = Npcs.KALPHITE_QUEEN // 1158
    val SECOND_FORM = Npcs.KALPHITE_QUEEN_1160 // 1160

    override val ids = intArrayOf(FIRST_FORM, SECOND_FORM)

    /** Delegates accuracy to the real style formula (so protection prayers behave normally) but
     * caps the roll at Kalphite Queen's real, style-independent infobox max hit of 31, since her
     * Magic/Ranged "attacks" are not driven by a real Ranged/Magic skill level (see class KDoc). */
    private class FixedMaxHitFormula(
        private val accuracyFormula: CombatFormula,
        private val maxHit: Int,
    ) : CombatFormula {
        override fun getAccuracy(
            pawn: Pawn,
            target: Pawn,
            specialAttackMultiplier: Double,
        ): Double = accuracyFormula.getAccuracy(pawn, target, specialAttackMultiplier)

        override fun getMaxHit(
            pawn: Pawn,
            target: Pawn,
            specialAttackMultiplier: Double,
            specialPassiveMultiplier: Double,
        ): Double = maxHit.toDouble()
    }

    private const val MAX_HIT = 31
    private val MAGIC_FORMULA = FixedMaxHitFormula(MagicCombatFormula, MAX_HIT)
    private val RANGED_FORMULA = FixedMaxHitFormula(RangedCombatFormula, MAX_HIT)

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return

        while (npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = 1, projectile = false) && npc.isAttackDelayReady()) {
                if (npc.id == FIRST_FORM) firstFormAttack(npc, target) else secondFormAttack(npc, target)
                npc.postAttackLogic(target)
            }
            it.wait(4)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /** First (crawling) form: mostly Magic/Ranged, occasionally Melee. */
    private fun firstFormAttack(
        npc: Npc,
        target: Pawn,
    ) {
        when (npc.world.random(9)) {
            in 0..3 -> magicAttack(npc, target)
            in 4..6 -> rangedAttack(npc, target)
            else -> meleeAttack(npc, target)
        }
    }

    /** Second (airborne) form: mostly Melee, occasionally Magic/Ranged. */
    private fun secondFormAttack(
        npc: Npc,
        target: Pawn,
    ) {
        when (npc.world.random(9)) {
            in 0..6 -> meleeAttack(npc, target)
            in 7..8 -> magicAttack(npc, target)
            else -> rangedAttack(npc, target)
        }
    }

    private fun meleeAttack(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.STAB, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
    }

    /** "Her Magic attack bounces from player to player" (OSRS Wiki) - not modelled here beyond the
     * single-target hit; the multi-target bounce needs a sourced GFX/projectile id this cache does
     * not carry (same SOURCE_BLOCKED gap as her transformation VFX, see `kalphite_queen.plugin.kts`). */
    private fun magicAttack(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(npc.combatDef.attackAnimation)
        npc.dealHit(target = target, formula = MAGIC_FORMULA, delay = 2, type = HitType.MAGIC)
    }

    /** "Shooting barbed spines from her abdomen" (OSRS Wiki). */
    private fun rangedAttack(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(npc.combatDef.attackAnimation)
        npc.dealHit(target = target, formula = RANGED_FORMULA, delay = 2, type = HitType.RANGE)
    }
}
