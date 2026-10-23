package gg.rsmod.plugins.content.mechanics.removed

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet

/**
 * Skills taken out of the game (owner 2026-10-11: "de skill volledig weg"; Dungeoneering). The rule itself is
 * [SkillSet.REMOVED_SKILLS] in the engine (no XP in [Player.addXp], not in the total level); this object is the plugin-side
 * entry every feature asks: max cape, trimmed skillcapes, lamps, the XP choosers, skill guides and targets.
 */
object RemovedSkills {
    /** The old Daemonheim floor loop's saved progress (deleted with the loop); cleared at login. */
    private val OLD_FLOOR_ATTR = AttributeKey<Int>(persistenceKey = "dungeoneering_floor")

    fun isRemoved(skill: Int): Boolean = SkillSet.isRemovedSkill(skill)

    /** Every skill of [player] except the removed ones (24 skills, as OSRS). */
    fun activeSkills(player: Player): List<Int> = SkillSet.activeSkills(player.skills.maxSkills)

    /**
     * Login: XP an account earned in a removed skill before the removal is reset to level 1 (0 XP), so it shows nowhere
     * (stats, total, hiscores) and counts for nothing; the old floor progress goes too. Returns true when anything changed.
     */
    fun migrate(player: Player): Boolean {
        var changed = false
        for (skill in SkillSet.REMOVED_SKILLS) {
            if (skill >= player.skills.maxSkills) continue
            if (player.skills.getCurrentXp(skill) != 0.0 || player.skills.getCurrentLevel(skill) != 1) {
                player.skills.setBaseXp(skill, 0.0)
                changed = true
            }
        }
        if (player.attr.has(OLD_FLOOR_ATTR)) {
            player.attr.remove(OLD_FLOOR_ATTR)
            changed = true
        }
        return changed
    }
}
