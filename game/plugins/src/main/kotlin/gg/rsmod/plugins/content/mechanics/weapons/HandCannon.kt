package gg.rsmod.plugins.content.mechanics.weapons

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import kotlin.math.max
import kotlin.math.min

/**
 * Hand cannon (P8, 2026-09-02 autonomous run - see RSPS_DECISIONS.md for the full sourcing
 * note). "Hand cannon" is real cache-verified content in this revision (`items.yml` id 15241,
 * examine "A miniature dwarven cannon.") - it does NOT exist on the modern Old School RuneScape
 * wiki (confirmed: that wiki's "Hand cannon" entry is its generic "Nonexistence" placeholder),
 * because this item predates the 2007 backup OSRS is built from. It IS documented on the
 * current (post-Evolution of Combat) RuneScape 3 wiki, released 9 September 2009 via the
 * "Forgiveness of a Chaos Dwarf" quest update - two years before this codebase's revision 667
 * (~2011) and three years before EOC (November 2012), so this revision's original mechanic
 * predates every adrenaline/ability-bar number the current wiki article shows. Requirements
 * (75 Ranged, 61 Firemaking) and the "Bolts" ranged attack-style classification are confirmed
 * by both the RS3 wiki and this cache's own equipment bonuses; the exploding-on-use mechanic
 * (Firemaking reduces the chance) is confirmed structurally by the RS3 wiki, but every EXACT
 * number quoted there (explosion-chance %, self-damage amount) is a post-2012 EOC-era value,
 * not a pre-EOC/2011 one - no pre-EOC formula or table was found by this or a prior session's
 * search. Two numbers below are therefore explicitly flagged as inferred placeholders, not
 * sourced facts - see each one's own comment.
 */
object HandCannon {
    /**
     * Firemaking level -> raw per-shot explosion chance. Linearly interpolated between the
     * only two concrete data points the (EOC-era) RS3 wiki gives - "at Firemaking 61, raw
     * explosion chance ~2.75%" and "at Firemaking 99, raw chance ~0.39%" - clamped outside that
     * range. INFERRED, NOT VERIFIED FOR THIS REVISION: these are 2012+-era numbers adapted for
     * lack of any earlier source; the direction (higher Firemaking = safer) is sourced and
     * real, the exact curve is not. Replace this with a real pre-EOC/rev-667 formula the moment
     * one is found, per RSPS_DECISIONS.md.
     */
    private const val ANCHOR_LOW_LEVEL = 61
    private const val ANCHOR_LOW_CHANCE = 0.0275
    private const val ANCHOR_HIGH_LEVEL = 99
    private const val ANCHOR_HIGH_CHANCE = 0.0039

    /**
     * Special attack ("Aimed Shot"-equivalent) carries extra explosion risk per the sourced
     * "Aimed Shot carries a greater explosion risk than normal attacks" line. No exact
     * multiplier is given anywhere found - 2x the normal-attack chance is an inferred,
     * explicitly-flagged placeholder, not a sourced ratio.
     */
    private const val SPECIAL_ATTACK_RISK_MULTIPLIER = 2.0

    /**
     * Self-damage dealt to the wielder when the cannon explodes. No sourced magnitude exists
     * anywhere (the RS3 wiki only says the weapon itself "is completely deleted", nothing about
     * player damage). Modelled as a fraction of current lifepoints (scale-safe across levels,
     * a common shape for "weapon backfire" mechanics) rather than a flat number, explicitly
     * flagged as an inferred placeholder pending a real sourced value.
     */
    private const val SELF_DAMAGE_FRACTION = 0.10

    fun explosionChance(
        firemakingLevel: Int,
        isSpecialAttack: Boolean,
    ): Double {
        val t =
            ((firemakingLevel - ANCHOR_LOW_LEVEL).toDouble() / (ANCHOR_HIGH_LEVEL - ANCHOR_LOW_LEVEL))
                .coerceIn(0.0, 1.0)
        val chance = ANCHOR_LOW_CHANCE + t * (ANCHOR_HIGH_CHANCE - ANCHOR_LOW_CHANCE)
        return if (isSpecialAttack) min(1.0, chance * SPECIAL_ATTACK_RISK_MULTIPLIER) else chance
    }

    /**
     * Rolls whether this shot causes the equipped hand cannon to explode. Real per-shot roll -
     * call once per fired shot (both normal attacks and the special attack), matching the
     * sourced "for autoattacks, explosion is always checked" rule.
     */
    fun rollExplodes(
        world: World,
        firemakingLevel: Int,
        isSpecialAttack: Boolean,
    ): Boolean = world.randomDouble() < explosionChance(firemakingLevel, isSpecialAttack)

    /**
     * Fires when [rollExplodes] comes back true: destroys the hand cannon (no broken/degraded
     * item id exists anywhere in this cache - matches the sourced "completely deleted, no
     * reclaim" real behaviour, not a guessed intermediate state) and self-damages the wielder.
     */
    fun explode(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        if (weapon.id != Items.HAND_CANNON) return

        player.equipment.remove(Items.HAND_CANNON, amount = 1)
        val selfDamage = max(1, (player.getCurrentLifepoints() * SELF_DAMAGE_FRACTION).toInt())
        player.hit(damage = selfDamage, type = HitType.REGULAR_HIT)
        player.message("Your hand cannon explodes in your hands!")
    }

    fun firemakingLevel(player: Player): Int = player.skills.getMaxLevel(Skills.FIREMAKING)
}
