package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.striked

/**
 * PROJECT48 F2: the hard requirements of a bracket mini-quest (OSRS Wiki "Requirements"): skill levels, quests, quest points.
 * Only for PLAYING the quest - the combat bracket unlocks a quest without any of this ([ShortQuest.needs] is read by runStart only).
 *
 * [skills] is skill id to level; the REAL level counts, except for the skills in [boostable] (the wiki says "boostable") where the
 * current level counts. [quests] are quest NAMES of the quest list, completed through [QuestStatus] (so "given by the bracket" counts).
 * [extra] are lines for the player only (a wiki requirement the server cannot check: "Ability to defeat a level 50 monster"); they
 * never block the start.
 */
class QuestRequirements(
    val skills: List<Pair<Int, Int>> = emptyList(),
    val quests: List<String> = emptyList(),
    val questPoints: Int = 0,
    val extra: List<String> = emptyList(),
    val boostable: Set<Int> = emptySet(),
) {
    val isEmpty: Boolean get() = skills.isEmpty() && quests.isEmpty() && questPoints <= 0 && extra.isEmpty()

    /** One requirement as the player reads it: the [text] and whether [met]. */
    class Line(val text: String, val met: Boolean, val blocking: Boolean = true)

    private fun skillLevel(player: Player, skill: Int): Int =
        if (skill in boostable) player.skills.getCurrentLevel(skill) else player.skills.getMaxLevel(skill)

    fun lines(player: Player): List<Line> {
        val out = mutableListOf<Line>()
        skills.forEach { (skill, level) ->
            out += Line("level $level ${Skills.getSkillName(world = player.world, skill = skill)}", skillLevel(player, skill) >= level)
        }
        quests.forEach { name ->
            val entry = QuestList.ENTRIES.firstOrNull { it.name == name }
            // An unknown name never lets the quest start (and the P48 tests list every name against the quest list).
            out += Line(name, entry != null && QuestStatus.state(player, entry.slot) == QuestStatus.State.COMPLETED)
        }
        if (questPoints > 0) out += Line("$questPoints Quest points", QuestStatus.points(player) >= questPoints)
        extra.forEach { out += Line(it, true, blocking = false) }
        return out
    }

    /** The messages of what is missing, in the words of the start npc: "You need level 49 Farming." */
    fun missing(player: Player): List<String> =
        lines(player).filter { it.blocking && !it.met }.map {
            when {
                it.text.startsWith("level ") -> "You need ${it.text}."
                it.text.endsWith("Quest points") -> "You need ${it.text}."
                else -> "You need to complete ${it.text}."
            }
        }

    fun met(player: Player): Boolean = lines(player).none { it.blocking && !it.met }

    /** The "Requirements" text of the quest overview and the Quest Helper: a met requirement is struck through, as in OSRS. */
    fun overview(player: Player): String =
        lines(player).joinToString(separator = "<br>") { if (it.met && it.blocking) striked(it.text) else it.text }

    /** One short line for the Quest Guide list ("Req: 49 Farming, Fairy Tale I - Growing Pains"). */
    fun summary(world: gg.rsmod.game.model.World): String {
        val parts = mutableListOf<String>()
        skills.forEach { (skill, level) -> parts += "$level ${Skills.getSkillName(world, skill)}" }
        parts += quests
        if (questPoints > 0) parts += "$questPoints QP"
        return if (parts.isEmpty()) "No requirements" else parts.joinToString(", ")
    }

    companion object {
        val NONE = QuestRequirements()
    }
}
