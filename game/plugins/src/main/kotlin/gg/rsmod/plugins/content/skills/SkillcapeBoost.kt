package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills

/**
 * N410: https://oldschool.runescape.wiki/w/Cape_of_Accomplishment#Skill_boost
 * Equip/Boost raises 99 to 100, subject to normal decay and a minute cooldown for Constitution/Prayer.
 * https://oldschool.runescape.wiki/w/Magic_cape also specifies no drain restoration and removal on unequip.
 * Track our own activation so removing a cape does not erase a potion boost. Max capes and hoods have no Boost.
 */
object SkillcapeBoost {
    const val CAP = 100
    const val COOLDOWN_TICKS = 100

    private val LAST_USE = AttributeKey<Int>()
    private val ACTIVE_SKILL = AttributeKey<Int>()

    /** Only an undrained 99 can become 100. */
    fun boosted(
        base: Int,
        current: Int,
    ): Int? = if (base == 99 && current == 99) CAP else null

    fun hasCooldown(skill: Int): Boolean = skill == Skills.CONSTITUTION || skill == Skills.PRAYER

    fun capeFor(item: Int): Skillcapes? = Skillcapes.values().firstOrNull { item == it.untrimmedCape || item == it.trimmedCape }

    fun equip(player: Player, item: Int): Boolean = capeFor(item)?.let { apply(player, it) } ?: false

    private fun current(player: Player, skill: Int): Int =
        when (skill) {
            Skills.CONSTITUTION -> player.getCurrentLifepoints()
            Skills.PRAYER -> player.getCurrentPrayerPoints()
            else -> player.skills.getCurrentLevel(skill)
        }

    private fun setCurrent(player: Player, skill: Int, level: Int) {
        when (skill) {
            Skills.CONSTITUTION -> player.setCurrentLifepoints(level)
            Skills.PRAYER -> {
                player.setCurrentPrayerPoints(level)
                player.skills.setCurrentLevel(skill, level)
            }
            else -> player.skills.setCurrentLevel(skill, level)
        }
    }

    /** Runs on removal and replacement. A drain or a stronger boost must survive unchanged. */
    fun remove(player: Player, item: Int) {
        val skill = capeFor(item)?.let { SkillcapeSeller.skill(it) } ?: return
        if (player.attr[ACTIVE_SKILL] != skill) return
        player.attr.remove(ACTIVE_SKILL)
        if (current(player, skill) == CAP) {
            setCurrent(player, skill, minOf(99, player.skills.getMaxLevel(skill)))
        }
    }

    /** Applies the boost of [cape] to [player]; false when nothing changed. */
    fun apply(
        player: Player,
        cape: Skillcapes,
    ): Boolean {
        val skill = SkillcapeSeller.skill(cape)
        val level = boosted(player.skills.getMaxLevel(skill), current(player, skill)) ?: return false
        if (hasCooldown(skill)) {
            val last = player.attr[LAST_USE]
            if (last != null && player.world.currentCycle - last < COOLDOWN_TICKS) return false
            player.attr[LAST_USE] = player.world.currentCycle
        }
        setCurrent(player, skill, level)
        player.attr[ACTIVE_SKILL] = skill
        return true
    }
}
