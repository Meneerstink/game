package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.heal

/**
 * Redemption's real effect (auto-heal + deactivate when the caster's health drops below 10% of
 * max) had no HP-change hook anywhere - `Prayer.REDEMPTION` only ever drove the overhead
 * icon/toggle in [Prayers.kt]. Sole source is Void's `content/skill/prayer/active/Redemption.kt`
 * (`levelChanged(Skill.Constitution)`); Novite has no Redemption implementation (confirmed by
 * grep, same as Retribution/Wrath).
 *
 * Void's hook fires on *any* Constitution change (damage, poison, disease, ...). This engine has
 * no equivalent generic HP-change listener; wired instead through the same once-per-landed-hit
 * combat dispatcher in `PawnExt.kt` that Smite/Retribution/Ancient Curses already use, so
 * Redemption only triggers on combat damage (not poison/disease ticks) - a narrower trigger surface
 * than the donor, deferred rather than building a new generic HP hook for this single effect.
 *
 * Ported faithfully to Void's own formula:
 * - Triggers only when `0 < currentLifepoints < maxLifepoints / 10` (donor's own `to <= 0 || to >=
 *   max / 10` skip condition, inverted).
 * - Skipped entirely while a Phoenix necklace is worn (donor's own `equipped(Amulet).id ==
 *   "phoenix_necklace"` check).
 * - Heal amount is `floor(maxPrayerLevel * 0.25)` (donor's `levels.getMax(Skill.Prayer) * 2.5` on x10 life points,
 *   i.e. the base/max Prayer level, not the current boosted one).
 * - Prayer points are fully drained and all prayers/curses deactivated immediately (donor's
 *   `levels.set(Skill.Prayer, 0)`), matching the same `Prayers.deactivateAll` +
 *   `AncientCurses.deactivateAllCurses` pair `Prayers.drainPrayer` already uses when prayer points
 *   hit zero naturally.
 *
 * No graphic ported this batch (Void's `gfx("redemption")` references a donor-specific graphic ID
 * not yet cross-checked against this cache) - visual polish deferred, the heal/deactivate effect is
 * the actual mechanic.
 */
object Redemption {
    // OSRS Wiki "Redemption": heals 25 % of the Prayer level. The donor's 2.5 was on its x10 life points; this server is 1:1.
    private const val HEAL_MULTIPLIER = 0.25
    private const val THRESHOLD_DIVISOR = 10

    /** Audit C-16: OSRS "below 10 %" without integer division (99 HP: triggers at 9 HP, not only at 8). */
    fun belowThreshold(
        current: Int,
        max: Int,
    ): Boolean = current * THRESHOLD_DIVISOR < max

    fun onDamageDealt(
        target: Pawn,
        damage: Int,
    ) {
        if (target !is Player || damage <= 0) return
        if (!Prayers.isActive(target, Prayer.REDEMPTION)) return
        val current = target.getCurrentLifepoints()
        val max = target.getMaximumLifepoints()
        if (current <= 0 || !belowThreshold(current, max)) return
        if (target.hasEquipped(EquipmentType.AMULET, Items.PHOENIX_NECKLACE)) return

        target.setCurrentPrayerPoints(0)
        Prayers.deactivateAll(target)
        AncientCurses.deactivateAllCurses(target)

        val heal = (target.skills.getMaxLevel(Skills.PRAYER) * HEAL_MULTIPLIER).toInt()
        target.heal(heal)
    }
}
