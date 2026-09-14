package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.restorePrayer
import gg.rsmod.plugins.content.inter.attack.AttackTab

/**
 * OSRS-IMPORT step 4 potions-brews (OSRS Wiki raw wikitext 2026-09-14; RuneLite `TimersAndBuffsPlugin` varbit timers):
 * - Ancient brew / Ancient mix: Magic floor(level / 20) + 2 (base); Prayer restore floor(Prayer / 10) + 2 "with the ability to restore up to
 *   5%, rounded down, over the player's base Prayer level"; Attack, Strength and Defence drained by floor(current level / 10) + 2. The mix
 *   "heals 6 Hitpoints". Forgotten brew: Magic floor(level x 8 / 100) + 3, the same Prayer restore and drain; recipe ancient brew(n) +
 *   20 x n ancient essence, 91 Herblore, 36 / 72 / 108 / 145 experience.
 * - Armadyl brew: Attack, Strength, Defence and Magic lowered by floor(current / 10) + 2; Hitpoints floor(base / 10) + 2 "does allow for
 *   overhealing above the player's maximum Hitpoints" by that amount; Ranged floor(base / 10) + 4.
 * - Menaphite remedy: "restores 6 + 16% of the player's combat stats (excluding prayer) every 15 seconds, over the course of 5 minutes"
 *   (RuneLite: counter x 25 ticks); "will not boost your combat stats back up if they were previously boosted"; drinking it "will remove all
 *   divine effects of existing boosts".
 * - Prayer regeneration potion: "restores one Prayer point every 12 ticks for 8 minutes, restoring 66 Prayer points in total" (RuneLite:
 *   counter x 12 ticks); "reset on death".
 * - Surge potion: "restore 25% special attack energy per dose, but can only be drunk once every five minutes" (RuneLite: counter x 10
 *   ticks); messages "You are still full of adrenaline. You'll be able to drink in [time remaining].", "You now feel capable of drinking
 *   another dose of surge potion." and "Drinking this would have no effect." (at 100 % energy).
 * - Goading potion: 9x9 area (radius 4), line of sight, 6-tick cycle "starting one tick after drinking", "Each dose ... will last for 6
 *   minutes" (600 ticks = 60 cycles; RuneLite: counter x 6 ticks). SOURCE_GAP/BLOCKED: the cancellation by cup of tea or teleport and its
 *   expiry messages are not wired (no sourced message text); the effect simply runs out. Recipe BLOCKED (aldarium).
 * Every timed effect is one counter per player (a single varbit in OSRS): a new dose restarts it. SOURCE_GAP: whether the remedy's 16 % is
 * taken from the base or the current level (base used, as for every OSRS restore potion); the first remedy and regeneration tick lands one
 * cycle after drinking; the surge time-remaining wording (ADAPTED: seconds).
 * BLOCKED recipes: ancient brew (nihil dust), Armadyl brew (umbral potion, rainbow crab paste), Menaphite remedy (lily of the sands), prayer
 * regeneration (huasca, aldarium), surge potion (demonic tallow).
 */
object BrewPotions {
    const val MIX_HEAL = 6
    const val MENAPHITE_INTERVAL = 25
    const val MENAPHITE_CYCLES = 20
    const val PRAYER_REGEN_INTERVAL = 12
    const val PRAYER_REGEN_CYCLES = 66
    const val SURGE_COOLDOWN_TICKS = 500
    const val SURGE_ENERGY = 25
    const val SURGE_WAIT_MESSAGE = "You are still full of adrenaline. You'll be able to drink in"
    const val SURGE_READY_MESSAGE = "You now feel capable of drinking another dose of surge potion."
    const val SURGE_NO_EFFECT_MESSAGE = "Drinking this would have no effect."

    val MENAPHITE_TIMER = TimerKey()
    val MENAPHITE_LEFT = AttributeKey<Int>()
    val PRAYER_REGEN_TIMER = TimerKey(resetOnDeath = true)
    val PRAYER_REGEN_LEFT = AttributeKey<Int>()
    val SURGE_COOLDOWN = TimerKey()

    const val GOADING_INTERVAL = 6
    const val GOADING_CYCLES = 60
    const val GOADING_RADIUS = 4
    val GOADING_TIMER = TimerKey()
    val GOADING_LEFT = AttributeKey<Int>()

    private val MELEE_TRIO = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE)
    private val COMBAT_STATS = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)

    fun ancientMagicBoost(base: Int): Int = base / 20 + 2

    fun forgottenMagicBoost(base: Int): Int = base * 8 / 100 + 3

    fun prayerRestore(basePrayer: Int): Int = basePrayer / 10 + 2

    fun prayerOvercap(basePrayer: Int): Int = basePrayer * 5 / 100

    fun drain(currentLevel: Int): Int = currentLevel / 10 + 2

    fun armadylHeal(baseHitpoints: Int): Int = baseHitpoints / 10 + 2

    fun armadylRangedBoost(base: Int): Int = base / 10 + 4

    fun menaphiteRestore(base: Int): Int = base * 16 / 100 + 6

    val FORGOTTEN_BREW_EXPERIENCE = mapOf(1 to 36.0, 2 to 72.0, 3 to 108.0, 4 to 145.0)

    private fun drainSkills(
        p: Player,
        skills: IntArray,
    ) {
        skills.forEach { skill -> p.skills.alterCurrentLevel(skill, -drain(p.skills.getCurrentLevel(skill)), -124) }
    }

    fun drinkAncient(
        p: Player,
        forgotten: Boolean,
    ) {
        val magicBase = p.skills.getMaxLevel(Skills.MAGIC)
        val boost = if (forgotten) forgottenMagicBoost(magicBase) else ancientMagicBoost(magicBase)
        p.skills.alterCurrentLevel(Skills.MAGIC, boost, boost)
        val prayerBase = p.getMaximumPrayerPoints()
        p.restorePrayer(prayerRestore(prayerBase), prayerOvercap(prayerBase))
        drainSkills(p, MELEE_TRIO)
    }

    fun drinkAncientMix(p: Player) {
        drinkAncient(p, forgotten = false)
        p.heal(MIX_HEAL)
    }

    fun drinkArmadyl(p: Player) {
        drainSkills(p, intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.MAGIC))
        val heal = armadylHeal(p.getMaximumLifepoints())
        p.heal(heal, heal)
        val ranged = armadylRangedBoost(p.skills.getMaxLevel(Skills.RANGED))
        p.skills.alterCurrentLevel(Skills.RANGED, ranged, ranged)
    }

    fun drinkMenaphite(p: Player) {
        DivinePotions.TIMERS.values.forEach { p.timers.remove(it) }
        p.attr[MENAPHITE_LEFT] = MENAPHITE_CYCLES
        p.timers[MENAPHITE_TIMER] = MENAPHITE_INTERVAL
    }

    /** One remedy cycle: combat stats below base move up by 6 + 16 % of base, never above base. Returns whether cycles remain. */
    fun menaphiteTick(p: Player): Boolean {
        COMBAT_STATS.forEach { skill ->
            val base = p.skills.getMaxLevel(skill)
            val current = p.skills.getCurrentLevel(skill)
            if (current < base) p.skills.setCurrentLevel(skill, minOf(base, current + menaphiteRestore(base)))
        }
        val left = (p.attr[MENAPHITE_LEFT] ?: 0) - 1
        if (left <= 0) {
            p.attr.remove(MENAPHITE_LEFT)
            return false
        }
        p.attr[MENAPHITE_LEFT] = left
        return true
    }

    fun drinkPrayerRegeneration(p: Player) {
        p.attr[PRAYER_REGEN_LEFT] = PRAYER_REGEN_CYCLES
        p.timers[PRAYER_REGEN_TIMER] = PRAYER_REGEN_INTERVAL
    }

    fun prayerRegenerationTick(p: Player): Boolean {
        p.restorePrayer(1)
        val left = (p.attr[PRAYER_REGEN_LEFT] ?: 0) - 1
        if (left <= 0) {
            p.attr.remove(PRAYER_REGEN_LEFT)
            return false
        }
        p.attr[PRAYER_REGEN_LEFT] = left
        return true
    }

    fun drinkGoading(p: Player) {
        p.attr[GOADING_LEFT] = GOADING_CYCLES
        p.timers[GOADING_TIMER] = 1
    }

    /** Consumes one goading cycle; false once the six minutes are over. */
    fun goadingTick(p: Player): Boolean {
        val left = (p.attr[GOADING_LEFT] ?: 0) - 1
        if (left < 0) {
            p.attr.remove(GOADING_LEFT)
            return false
        }
        p.attr[GOADING_LEFT] = left
        return true
    }

    fun canDrinkSurge(p: Player): Boolean {
        if (p.timers.has(SURGE_COOLDOWN)) {
            p.message("$SURGE_WAIT_MESSAGE ${(p.timers[SURGE_COOLDOWN] * 6 + 9) / 10} seconds.")
            return false
        }
        if (AttackTab.getEnergy(p) >= 100) {
            p.message(SURGE_NO_EFFECT_MESSAGE)
            return false
        }
        return true
    }

    fun drinkSurge(p: Player) {
        AttackTab.setEnergy(p, minOf(100, AttackTab.getEnergy(p) + SURGE_ENERGY))
        p.timers[SURGE_COOLDOWN] = SURGE_COOLDOWN_TICKS
    }
}
