package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills

/**
 * D5 (owner 10-11: "trimmed automatically once the player has 2+ level 99s"). OSRS Wiki "Cape of Accomplishment": "The moment
 * the player reaches 99 in another skill, all untrimmed capes currently in possession of the player, banked, ... will
 * automatically become permanently trimmed." Darkan Skills.trimCapes swaps only the worn cape; here the inventory, the
 * equipment and the bank, at every new 99, at login and right after a cape is bought.
 */
object SkillcapeTrim {
    /** Untrimmed -> trimmed, every 667 cape (DGN: Dungeoneering and its cape are removed). */
    val TRIMMED: Map<Int, Int> =
        Skillcapes.values().associate { it.untrimmedCape to it.trimmedCape }

    /** Trims every untrimmed skillcape [player] owns when he has two or more 99s; returns how many stacks changed. */
    fun trimAll(player: Player): Int {
        if (!Skills.hasTwo99s(player)) return 0
        var changed = 0
        listOf(player.inventory, player.equipment, player.bank).forEach { container ->
            for (slot in 0 until container.capacity) {
                val item = container[slot] ?: continue
                val trimmed = TRIMMED[item.id] ?: continue
                container[slot] = Item(trimmed, item.amount)
                changed++
            }
        }
        return changed
    }
}
