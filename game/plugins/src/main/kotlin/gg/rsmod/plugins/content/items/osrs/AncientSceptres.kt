package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import kotlin.math.floor

/**
 * OSRS-IMPORT ancient sceptres (OSRS Wiki raw wikitext 2026-09-14, "Ancient sceptre", "Ice/Smoke/Shadow/Blood ancient sceptre"):
 * - "increases the effects of all Ancient Magicks by 10%": smoke poison severity 10 -> 11 (Rush/Burst) and 20 -> 22 (Blitz/Barrage);
 *   shadow Attack drain 10 % -> 11 % and 15 % -> 16.5 %; blood heal "27.5% of the damage dealt instead of 25%"; freeze Ice Burst 16 ->
 *   17, Ice Blitz 24 -> 26, Ice Barrage 32 -> 35 ticks, Ice Rush unchanged ("8.8 ticks ... round down").
 * - Blood quartz: blood spells "overheal up to 10% over the player's base Hitpoints".
 * - Ice quartz: "an additional 10% accuracy bonus given to ice spells on targets that can be frozen and are not currently frozen".
 * - Smoke quartz: "reducing a poisoned target's healing by 20% for 6 seconds after taking damage from smoke spells" (10 ticks).
 * - Shadow quartz: also lowers Strength and Defence - the amounts are not stated (SOURCE_GAP, not implemented).
 * Locked (l) sceptres behave like their base sceptre.
 */
object AncientSceptres {
    enum class Quartz { NONE, BLOOD, ICE, SMOKE, SHADOW }

    private val byWeapon =
        mapOf(
            Items.ANCIENT_SCEPTRE to Quartz.NONE, Items.ANCIENT_SCEPTRE_L to Quartz.NONE,
            Items.BLOOD_ANCIENT_SCEPTRE to Quartz.BLOOD, Items.BLOOD_ANCIENT_SCEPTRE_L to Quartz.BLOOD,
            Items.ICE_ANCIENT_SCEPTRE to Quartz.ICE, Items.ICE_ANCIENT_SCEPTRE_L to Quartz.ICE,
            Items.SMOKE_ANCIENT_SCEPTRE to Quartz.SMOKE, Items.SMOKE_ANCIENT_SCEPTRE_L to Quartz.SMOKE,
            Items.SHADOW_ANCIENT_SCEPTRE to Quartz.SHADOW, Items.SHADOW_ANCIENT_SCEPTRE_L to Quartz.SHADOW,
        )

    val ALL: Set<Int> = byWeapon.keys

    /**
     * Quartz item -> unlocked quartz sceptre. "Blood ancient sceptre" Recipe: Ancient sceptre + Blood quartz, 0 ticks, no skill; "this
     * process can be reversed" and "the regular, non-locked variant of the sceptre can now be dismantled to return the component items".
     */
    val QUARTZ_UPGRADES =
        mapOf(
            Items.BLOOD_QUARTZ to Items.BLOOD_ANCIENT_SCEPTRE,
            Items.ICE_QUARTZ to Items.ICE_ANCIENT_SCEPTRE,
            Items.SMOKE_QUARTZ to Items.SMOKE_ANCIENT_SCEPTRE,
            Items.SHADOW_QUARTZ to Items.SHADOW_ANCIENT_SCEPTRE,
        )

    const val SMOKE_HEAL_REDUCTION_TICKS = 10
    const val SMOKE_HEAL_MULTIPLIER = 0.8

    val ICE_SPELLS = setOf(CombatSpell.ICE_RUSH, CombatSpell.ICE_BURST, CombatSpell.ICE_BLITZ, CombatSpell.ICE_BARRAGE)
    val SMOKE_SPELLS = setOf(CombatSpell.SMOKE_RUSH, CombatSpell.SMOKE_BURST, CombatSpell.SMOKE_BLITZ, CombatSpell.SMOKE_BARRAGE)

    /** The quartz of the sceptre [pawn] wields, or null without a sceptre. */
    fun quartz(pawn: Pawn): Quartz? = (pawn as? Player)?.getEquipment(EquipmentType.WEAPON)?.id?.let { byWeapon[it] }

    fun boosted(pawn: Pawn): Boolean = quartz(pawn) != null

    fun freezeTicks(
        ticks: Int,
        boosted: Boolean,
    ): Int = if (boosted) floor(ticks * 1.1).toInt() else ticks

    /** Smoke spells: the 667 effect value is the first poison hit (2 or 4) = OSRS severity / 5 (10 or 20). */
    fun smokeSeverity(
        firstHit: Int,
        boosted: Boolean,
    ): Int = if (boosted) floor(firstHit * 5 * 1.1).toInt() else firstHit * 5

    fun bloodHeal(
        damage: Int,
        boosted: Boolean,
    ): Int = floor(damage * (if (boosted) 0.275 else 0.25)).toInt()

    /** Shadow Rush/Burst drain 10 %, Blitz/Barrage 15 % of Attack (OSRS Wiki Ancient sceptre table, before the 10 % boost). */
    fun shadowDrainPercent(spell: CombatSpell): Int = if (spell == CombatSpell.SHADOW_BLITZ || spell == CombatSpell.SHADOW_BARRAGE) 15 else 10

    fun overhealCap(
        pawn: Pawn,
        maximumLifepoints: Int,
    ): Int = if (quartz(pawn) == Quartz.BLOOD) floor(maximumLifepoints * 0.1).toInt() else 0

    fun iceAccuracyMultiplier(
        pawn: Pawn,
        target: Pawn,
        spell: CombatSpell?,
    ): Double {
        if (quartz(pawn) != Quartz.ICE || spell !in ICE_SPELLS) return 1.0
        return if (target.timers.has(FROZEN_TIMER) || target.timers.has(FREEZE_IMMUNITY_TIMER)) 1.0 else 1.1
    }
}
