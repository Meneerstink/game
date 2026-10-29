package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills

/**
 * K03 N410 part 4: the skillcape Boost. OSRS Wiki "Cape of Accomplishment": "temporarily boosts the player's effective level in the
 * cape's skill by 1, up to 100, lasting up to 60 seconds" (the normal boost decay); "Selecting Boost in the equipment menu or
 * equipping the cape activates it"; it "cannot be reactivated until its skill has dropped back to 99"; the Hitpoints and Prayer
 * capes "have a 1 minute cool-down period between uses of this effect". The max cape has no Boost.
 */
object SkillcapeBoost {
    const val CAP = 100
    const val COOLDOWN_TICKS = 100

    private val LAST_USE = AttributeKey<Int>()

    /** The boosted level, or null when there is nothing to boost (no real 99, or the skill is still above 99). */
    fun boosted(
        base: Int,
        current: Int,
    ): Int? = if (base < 99 || current > 99) null else minOf(current + 1, CAP)

    fun hasCooldown(skill: Int): Boolean = skill == Skills.CONSTITUTION || skill == Skills.PRAYER

    fun capeFor(item: Int): Skillcapes? = Skillcapes.values().firstOrNull { item == it.untrimmedCape || item == it.trimmedCape }

    /** Applies the boost of [cape] to [player]; false when nothing changed. */
    fun apply(
        player: Player,
        cape: Skillcapes,
    ): Boolean {
        val skill = SkillcapeSeller.skill(cape)
        // Hitpoints and Prayer live in their own varbits (lifepoints / prayer points); every other skill is its current level.
        val current =
            when (skill) {
                Skills.CONSTITUTION -> player.getCurrentLifepoints()
                Skills.PRAYER -> player.getCurrentPrayerPoints()
                else -> player.skills.getCurrentLevel(skill)
            }
        val level = boosted(player.skills.getMaxLevel(skill), current) ?: return false
        if (hasCooldown(skill)) {
            val last = player.attr[LAST_USE]
            if (last != null && player.world.currentCycle - last < COOLDOWN_TICKS) return false
            player.attr[LAST_USE] = player.world.currentCycle
        }
        when (skill) {
            Skills.CONSTITUTION -> player.setCurrentLifepoints(level)
            Skills.PRAYER -> {
                player.setCurrentPrayerPoints(level)
                player.skills.setCurrentLevel(skill, level)
            }
            else -> player.skills.setCurrentLevel(skill, level)
        }
        return true
    }
}
