package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.skills.slayer.getSlayerAssignment

/**
 * Shared target-condition-gated equipment damage/accuracy multiplier (P9, 2026-09-02
 * autonomous run - see `RSPS_DECISIONS.md` for the master-plan section 32 "Target-specific
 * effects" this implements, and for the full audit of which items are cache-present vs.
 * blocked). Previously this exact logic (Salve amulet + black mask) was duplicated
 * byte-for-byte across `MeleeCombatFormula`/`RangedCombatFormula`/`MagicCombatFormula`, each
 * with the same `// TODO: this should only apply when target is slayer task?` marking a real,
 * live bug: both bonuses applied unconditionally to every target, not just undead (Salve) or
 * the player's actual current Slayer task (black mask/Slayer helmet). Centralising it here
 * fixes the bug once instead of three times and gives every future target-gated item
 * (dragonbane, demonbane, wilderness/revenant weapons - all currently blocked, see below) one
 * place to be added.
 */
object TargetModifiers {
    private val BLACK_MASKS =
        intArrayOf(
            Items.BLACK_MASK,
            Items.BLACK_MASK_1,
            Items.BLACK_MASK_2,
            Items.BLACK_MASK_3,
            Items.BLACK_MASK_4,
            Items.BLACK_MASK_5,
            Items.BLACK_MASK_6,
            Items.BLACK_MASK_7,
            Items.BLACK_MASK_8,
            Items.BLACK_MASK_9,
            Items.BLACK_MASK_10,
        )

    /**
     * Slayer helmet family - real, cache-present items (`Items.kt`) that were previously
     * wired into nothing at all (grep-confirmed zero prior references anywhere in the combat
     * formulas). Slayer helmets confer the exact same task-gated bonus as the black mask they
     *'re built from in real OSRS, so they're added to the same check here rather than given a
     * separate, parallel code path.
     */
    private val SLAYER_HELMETS =
        intArrayOf(
            Items.SLAYER_HELMET,
            Items.SLAYER_HELMET_E,
            Items.SLAYER_HELMET_CHARGED,
            Items.FULL_SLAYER_HELMET,
            Items.FULL_SLAYER_HELMET_E,
            Items.FULL_SLAYER_HELMET_CHARGED,
        )

    fun isUndead(target: Pawn): Boolean = target is Npc && target.isSpecies(NpcSpecies.UNDEAD)

    /**
     * True only while [target] is an [Npc] whose [gg.rsmod.game.model.combat.NpcCombatDef.slayerAssignment]
     * matches [player]'s own current Slayer task ([getSlayerAssignment]) - the real condition
     * the black mask/Slayer helmet bonus requires, and the exact thing the pre-existing
     * `// TODO` comment flagged as missing.
     */
    fun isCurrentSlayerTask(
        player: Player,
        target: Pawn,
    ): Boolean {
        val assignment = player.getSlayerAssignment() ?: return false
        return target is Npc && target.combatDef.slayerAssignment == assignment
    }

    /**
     * Salve amulet ((e)) vs. undead, black mask/Slayer helmet vs. the player's current Slayer
     * task. `Items.SALVE_AMULET_I`/`SALVE_AMULET_EI` (real, later OSRS content - Salve (i)/(ei)
     * apply to ranged/magic as well as melee, per the master plan's section 32) have no item
     * ids anywhere in this cache (grep-confirmed against `Items.kt`) and are therefore not
     * implemented - documented, not guessed, matching this project's standing rule.
     */
    fun equipmentMultiplier(
        player: Player,
        target: Pawn,
    ): Double =
        when {
            isUndead(target) && player.hasEquipped(EquipmentType.AMULET, Items.SALVE_AMULET) -> 7.0 / 6.0
            isUndead(target) && player.hasEquipped(EquipmentType.AMULET, Items.SALVE_AMULET_E) -> 1.2
            isCurrentSlayerTask(player, target) &&
                player.hasEquipped(EquipmentType.HEAD, *BLACK_MASKS, *SLAYER_HELMETS) -> 7.0 / 6.0
            else -> 1.0
        }

    /**
     * S3, 2026-09-03: Twisted bow (`Items.TWISTED_BOW`, local id 22326 - see
     * `RSPS_IMPORT_MANIFEST.yml`) target-Magic-scaled accuracy/damage passive.
     *
     * SOURCE: OSRS Wiki "Twisted bow" page, Passive effect section, quoting a real Jagex
     * developer statement (Mod Kieren) as its primary reference:
     * `Acc modifier: 140 + (3*Magic - 10)/100 - ((3*Magic/10 - 100)^2)/100, capped at 140`
     * `Dmg modifier: 250 + (3*Magic - 14)/100 - ((3*Magic/10 - 140)^2)/100, capped at 250`
     * (both additionally floored at 0, per the wiki's own stated domain `Accuracy in [0, 140]`,
     * `Damage in [0, 250]`). Cross-checked independently against two of the wiki's own worked
     * table entries, both matching this formula to the hundredths place: Magic 99 -> accuracy
     * 93.45% (140 + 2.87 - 49.42 = 93.45); Magic 350 -> damage 248.11% inside Chambers of Xeric
     * (250 + 10.36 - 12.25 = 248.11). `Magic` is "the target's Magic level or Magic accuracy,
     * whichever is higher"; the level/accuracy input itself is capped at 250 outside Chambers of
     * Xeric and 350 inside it. This server has no Chambers of Xeric content (grep-confirmed no
     * "Chambers of Xeric"/"CoX"/"Olm" location anywhere in the plugins module), so only the
     * flat 250 cap applies here - the 350-inside-raids branch is source-real but genuinely out
     * of scope for this codebase rather than omitted by guesswork.
     *
     * UNSOURCED, DOCUMENTED ENGINEERING DECISION: no source (wiki page, developer tweet, or
     * talk-page discussion) states whether the percentage is floored to a whole number before
     * being applied. The wiki's own illustrative table reports fractional percentages (93.45%,
     * 248.11%) rather than integers, which is why this implementation applies the *continuous*
     * fraction (`percent / 100.0`) as a `Double` multiplier - exactly like every other
     * `TargetModifiers` multiplier (Salve's `7.0 / 6.0`, the black mask's `7.0 / 6.0`) - and
     * relies on the same downstream integer truncation `RangedCombatFormula` already applies to
     * every other multiplier (`floor(hit)` after the damage-stage multiply, `.toInt()` on the
     * attack roll), rather than introducing a second, bow-specific rounding rule.
     */
    private const val TWISTED_BOW_ACCURACY_CAP_PERCENT = 140.0
    private const val TWISTED_BOW_DAMAGE_CAP_PERCENT = 250.0

    // No Chambers of Xeric content exists in this codebase (see source note above), so the
    // target Magic input is always capped at the outside-raids value.
    private const val TWISTED_BOW_MAGIC_INPUT_CAP = 250

    fun isWieldingTwistedBow(player: Player): Boolean = player.hasEquipped(EquipmentType.WEAPON, Items.TWISTED_BOW)

    /**
     * The target Magic value the Twisted bow's passive scales against: the higher of the
     * target's current (boosted) Magic level and their Magic attack bonus, per the sourced
     * "whichever is higher" rule above, capped at [TWISTED_BOW_MAGIC_INPUT_CAP].
     */
    private fun twistedBowMagicInput(target: Pawn): Int {
        val magicLevel =
            when (target) {
                is Player -> target.skills.getCurrentLevel(Skills.MAGIC)
                is Npc -> target.stats.getCurrentLevel(NpcSkills.MAGIC)
                else -> 0
            }
        val magicAccuracyBonus = target.getBonus(BonusSlot.ATTACK_MAGIC)
        return maxOf(magicLevel, magicAccuracyBonus).coerceAtMost(TWISTED_BOW_MAGIC_INPUT_CAP)
    }

    /** The sourced accuracy-modifier formula, as a raw percentage capped to `[0, 140]`. */
    private fun twistedBowAccuracyPercent(magic: Int): Double {
        val scaledMagic = 3.0 * magic / 10.0
        val squaredTerm = (scaledMagic - 100.0) * (scaledMagic - 100.0)
        val raw = TWISTED_BOW_ACCURACY_CAP_PERCENT + (3.0 * magic - 10.0) / 100.0 - squaredTerm / 100.0
        return raw.coerceIn(0.0, TWISTED_BOW_ACCURACY_CAP_PERCENT)
    }

    /** The sourced damage-modifier formula, as a raw percentage capped to `[0, 250]`. */
    private fun twistedBowDamagePercent(magic: Int): Double {
        val scaledMagic = 3.0 * magic / 10.0
        val squaredTerm = (scaledMagic - 140.0) * (scaledMagic - 140.0)
        val raw = TWISTED_BOW_DAMAGE_CAP_PERCENT + (3.0 * magic - 14.0) / 100.0 - squaredTerm / 100.0
        return raw.coerceIn(0.0, TWISTED_BOW_DAMAGE_CAP_PERCENT)
    }

    /**
     * Composes the generic [equipmentMultiplier] (Salve/black mask) with the Twisted bow's
     * accuracy passive, applied only while [player] has the Twisted bow equipped as their
     * weapon - `1.0` (no effect) otherwise, and never applied at all for any other bow. This is
     * the ONLY function `RangedCombatFormula`'s accuracy/attack-roll stage should call; it must
     * not also call [equipmentMultiplier] directly, or Salve/black mask would apply twice.
     */
    /**
     * OSRS-exact audit 2026-09-13: the plain Salve amulet, Salve amulet (e), black mask and Slayer helmet are
     * melee-only ("Maximum ranged hit": ranged needs the imbued (i)/(ei) versions, 1.15 / 7/6 / 1.2). No imbued
     * variant exists in this cache, so ranged starts from 1.0 and only the Twisted bow passive applies.
     */
    fun rangedAccuracyMultiplier(
        player: Player,
        target: Pawn,
    ): Double {
        var multiplier = 1.0
        if (isWieldingTwistedBow(player)) {
            multiplier *= twistedBowAccuracyPercent(twistedBowMagicInput(target)) / 100.0
        }
        if (isDragonbaneRanged(player, target)) {
            multiplier *= DHCB_ACCURACY
        }
        return multiplier
    }

    /**
     * OSRS-IMPORT dragonbane passives (OSRS Wiki item pages + "Draconic (attribute)", 2026-09-14):
     * - Dragon hunter crossbow: "30% increase in ranged accuracy and 25% increase in damage" against draconic targets;
     *   it "stacks additively with the Slayer helm (i)" (no imbued helm exists in this cache) and multiplicatively with
     *   Void and salve amulets - applied in the ranged target-specific gear bonus stage ("Damage per second/Ranged").
     * - Dragon hunter lance: "20% increased accuracy and damage", "stacks multiplicatively" with Void and the Slayer
     *   helm - applied as its own floored step after the melee target-specific gear bonus ("Damage per second/Melee").
     * Floor order: the wiki's own DPS calculator (weirdgloop/osrs-dps-calc `src/lib/PlayerVsNPCCalc.ts`, read 2026-09-14)
     * applies the lance as a separate truncated factor [6, 5] on the attack roll and max hit, and the crossbow as
     * `Math.trunc(attackRoll * 13 / 10)` and `Math.trunc(maxHit * 5 / 4)`. The crossbow shares this ranged stage only with
     * the Twisted bow, which can never be wielded at the same time, so one floor here is identical.
     */
    const val DHCB_ACCURACY = 1.30
    const val DHCB_DAMAGE = 1.25
    const val LANCE_ACCURACY = 1.20
    const val LANCE_DAMAGE = 1.20

    fun isDragonbaneRanged(player: Player, target: Pawn): Boolean =
        player.hasEquipped(EquipmentType.WEAPON, Items.DRAGON_HUNTER_CROSSBOW) && Draconic.isDraconic(target)

    fun isDragonbaneMelee(player: Player, target: Pawn): Boolean =
        player.hasEquipped(EquipmentType.WEAPON, Items.DRAGON_HUNTER_LANCE) && Draconic.isDraconic(target)

    /** Melee dragonbane accuracy step (Dragon hunter lance), 1.0 otherwise. */
    fun meleeDragonbaneAccuracy(player: Player, target: Pawn): Double = if (isDragonbaneMelee(player, target)) LANCE_ACCURACY else 1.0

    /** Melee dragonbane damage step (Dragon hunter lance), 1.0 otherwise. */
    fun meleeDragonbaneDamage(player: Player, target: Pawn): Double = if (isDragonbaneMelee(player, target)) LANCE_DAMAGE else 1.0

    /**
     * Composes the generic [equipmentMultiplier] (Salve/black mask) with the Twisted bow's
     * damage passive, applied only while [player] has the Twisted bow equipped as their weapon.
     * See [rangedAccuracyMultiplier] - the damage-stage counterpart, same double-application
     * caveat applies to `RangedCombatFormula`'s max-hit stage.
     */
    fun rangedDamageMultiplier(
        player: Player,
        target: Pawn,
    ): Double {
        var multiplier = 1.0
        if (isWieldingTwistedBow(player)) {
            multiplier *= twistedBowDamagePercent(twistedBowMagicInput(target)) / 100.0
        }
        if (isDragonbaneRanged(player, target)) {
            multiplier *= DHCB_DAMAGE
        }
        return multiplier
    }
}
