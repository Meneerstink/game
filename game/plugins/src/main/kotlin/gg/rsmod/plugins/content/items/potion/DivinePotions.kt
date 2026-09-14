package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message

/**
 * OSRS-IMPORT step 4 potions-combat (OSRS Wiki raw wikitext 2026-09-14, "Super combat potion", "Bastion potion", "Battlemage potion", the
 * divine potion pages):
 * - Super combat: Attack, Strength and Defence floor(level x 15 / 100) + 5; made with a torstol or torstol potion (unf) on a 4-dose super
 *   attack, strength or defence while carrying a 4-dose of each, 90 Herblore, 150 experience.
 * - Bastion: Ranged floor(level / 10) + 4 and Defence floor(level x 15 / 100) + 5. Battlemage: Magic +4 and Defence floor(15 %) + 5.
 * - Divine potions: the same boosts "for 5 minutes. During these 5 minutes, the player's ... levels will not drain below the maximum boost";
 *   "Once the 5 minutes are over, any stats normally boosted by the potion will be reduced back to their base level"; "Drinking this potion
 *   will also damage the player by 10 Hitpoints"; with 10 Hitpoints or less: "You need more than 10 hitpoints to survive the power of a
 *   divine potion."
 * BLOCKED: Bastion, Battlemage and every divine recipe (vial of blood and crystal dust are absent in 667). ADJACENT GAP (recorded, not
 * changed): the 667 Ranging potion (floor(10 %) + 3) and Magic potion (the same) differ from OSRS (Ranging floor(10 %) + 4, Magic +4).
 */
object DivinePotions {
    const val DURATION_TICKS = 500
    const val HITPOINT_COST = 10
    const val REFUSAL_MESSAGE = "You need more than 10 hitpoints to survive the power of a divine potion."

    /** One timer per boosted skill, so divine potions drunk at different times each keep their own five minutes. */
    val TIMERS: Map<Int, TimerKey> =
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC).associateWith { TimerKey() }

    fun superBoost(level: Int): Int = level * 15 / 100 + 5

    fun rangedBoost(level: Int): Int = level / 10 + 4

    const val MAGIC_BOOST = 4

    fun boostFor(
        skill: Int,
        level: Int,
    ): Int =
        when (skill) {
            Skills.RANGED -> rangedBoost(level)
            Skills.MAGIC -> MAGIC_BOOST
            else -> superBoost(level)
        }

    /** Boosts [skills] from their base level, never above base + boost (a higher current boost is kept). */
    fun boost(
        p: Player,
        skills: IntArray,
    ) {
        skills.forEach { skill ->
            val boost = boostFor(skill, p.skills.getMaxLevel(skill))
            p.skills.alterCurrentLevel(skill, boost, boost)
        }
    }

    fun canDrinkDivine(p: Player): Boolean {
        if (p.getCurrentLifepoints() > HITPOINT_COST) return true
        p.message(REFUSAL_MESSAGE)
        return false
    }

    fun drinkDivine(
        p: Player,
        skills: IntArray,
    ) {
        boost(p, skills)
        skills.forEach { skill -> TIMERS[skill]?.let { p.timers[it] = DURATION_TICKS } }
        p.hit(HITPOINT_COST)
    }

    /** True while a divine potion stops [skill] from draining back toward its base level. */
    fun protects(
        p: Player,
        skill: Int,
    ): Boolean = TIMERS[skill]?.let { p.timers.has(it) } == true

    /** The five minutes are over: a boosted [skill] returns to its base level. */
    fun expire(
        p: Player,
        skill: Int,
    ) {
        val base = p.skills.getMaxLevel(skill)
        if (p.skills.getCurrentLevel(skill) > base) p.skills.setCurrentLevel(skill, base)
    }
}
