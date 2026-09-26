package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.quests.foundation.DesertTreasureII
import gg.rsmod.plugins.content.quests.foundation.MonkeyMadnessII
import gg.rsmod.plugins.content.quests.foundation.ShortQuest

/**
 * The OSRS quest requirements of ported OSRS items that belong to the four OSRS quests of the permanent quest choices
 * (owner 2026-09-26: "pas die eis toe zoals in OSRS (maken/dragen)"). One table, one check; quests that are simply
 * completed for everyone change nothing. OSRS Wiki, 2026-09-26:
 *
 *  - Magus ring: "requires players to have killed Duke Sucellus at least once to wear, which is done during Desert
 *    Treasure II"; the Venator ring likewise needs its Desert Treasure II boss - wearing needs Desert Treasure II.
 *  - Heavy ballista: "You must have completed Monkey Madness II ... to be able to wield this weapon."
 *  - Blood/ice/smoke/shadow ancient sceptre: the quartz comes from the Desert Treasure II bosses; adding it to the
 *    ancient sceptre needs Desert Treasure II (osrs_sceptres.plugin.kts).
 *  - Bow of faerdhinen and crystal armour: singing them needs Song of the Elves (crystal_singing_bowl.plugin.kts,
 *    [QuestStubs]); Ava's assembler needs Dragon Slayer II (ava.plugin.kts, AVAS_ASSEMBLER_UNLOCKED).
 */
object OsrsQuestRequirements {
    val WEAR: Map<Int, ShortQuest> =
        mapOf(
            Items.MAGUS_RING to DesertTreasureII,
            Items.VENATOR_RING to DesertTreasureII,
            Items.HEAVY_BALLISTA to MonkeyMadnessII,
            Items.HEAVY_BALLISTA_OR to MonkeyMadnessII,
        )

    /** Null when [player] may wear [item], else the refusal message. */
    fun wearRefusal(player: Player, item: Int): String? {
        val quest = WEAR[item] ?: return null
        return if (quest.isFinished(player)) null else "You need to complete ${quest.name} to wear that."
    }

    fun canUpgradeSceptre(player: Player): Boolean = DesertTreasureII.isFinished(player)
}
