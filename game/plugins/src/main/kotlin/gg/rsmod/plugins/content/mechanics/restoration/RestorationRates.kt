package gg.rsmod.plugins.content.mechanics.restoration

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * Natural restoration: one level of each lowered stat and one life point per minute (100 ticks), and one level of each
 * boosted non-combat stat back down per minute.
 *
 * The Rapid prayers (owner audit 2026-09-22: "Rapid Restore/Heal/Renewal kunnen worden aangezet ... maar ik vond geen
 * daadwerkelijke herstelwerking") speed that up:
 * - Rapid Restore: lowered stats return twice as fast. Life points, Prayer and boosted stats are untouched
 *   (OSRS Wiki "Rapid Restore").
 * - Rapid Heal: life points return twice as fast (OSRS Wiki "Rapid Heal").
 * - Rapid Renewal: life points return five times as fast (RuneScape Wiki "Rapid Renewal", 2010-2012: "Restores life
 *   points at 5x the normal rate"); it replaces Rapid Heal rather than stacking with it.
 *
 * The timer runs every [STEP] ticks and each channel accumulates progress, so a rate change takes effect within one step
 * and a prayer switched on mid-minute is credited for exactly the time it was on.
 */
object RestorationRates {
    const val STEP = 10
    const val CYCLE = 100

    private val LOWERED_PROGRESS = AttributeKey<Int>()
    private val BOOSTED_PROGRESS = AttributeKey<Int>()
    private val LIFE_PROGRESS = AttributeKey<Int>()

    fun loweredStatRate(player: Player): Int = if (Prayers.isActive(player, Prayer.RAPID_RESTORE)) 2 else 1

    fun lifePointRate(player: Player): Int =
        when {
            Prayers.isActive(player, Prayer.RAPID_RENEWAL) -> 5
            Prayers.isActive(player, Prayer.RAPID_HEAL) -> 2
            else -> 1
        }

    /** Adds one step at [rate] to [key]; returns how many whole restorations are due now. */
    private fun advance(
        player: Player,
        key: AttributeKey<Int>,
        rate: Int,
    ): Int {
        val total = (player.attr[key] ?: 0) + STEP * rate
        player.attr[key] = total % CYCLE
        return total / CYCLE
    }

    fun loweredDue(player: Player): Int = advance(player, LOWERED_PROGRESS, loweredStatRate(player))

    fun boostedDue(player: Player): Int = advance(player, BOOSTED_PROGRESS, 1)

    fun lifeDue(player: Player): Int = advance(player, LIFE_PROGRESS, lifePointRate(player))
}
