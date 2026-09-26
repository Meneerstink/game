package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards

/**
 * The permanent quest choices at the Quest Guide (owner 2026-09-26): one option per group completes that quest on the
 * spot (its full reward, [FoundationRewards.complete]); the other options of the group can only be earned by playing
 * their short quests.
 */
enum class ChoiceGroup(val label: String, val quests: List<ShortQuest>) {
    MAGIC("Magic", listOf(DesertTreasure, LunarDiplomacy, DesertTreasureII)),
    PRAYER("Prayer", listOf(TempleAtSenntisten, KingsRansom)),
    GEAR("Gear", listOf(DragonSlayerII, SongOfTheElves, MonkeyMadnessII)),
    ;

    val key = AttributeKey<String>(persistenceKey = "quest_choice_${name.lowercase()}")
}

object QuestChoices {
    /**
     * Owner 2026-09-26, question a): accounts that existed before the choices keep every quest completed ("legacy");
     * only accounts created from now on get the choices ("choice"). Decided once, at the account's first login with
     * this build, and stored on the account so it can never change afterwards.
     */
    val COHORT = AttributeKey<String>(persistenceKey = "quest_foundation_cohort")
    const val LEGACY = "legacy"
    const val CHOICE = "choice"

    /** Short labels for the choice menu: what each option unlocks. */
    val UNLOCK_LABEL: Map<ShortQuest, String> =
        mapOf(
            DesertTreasure to "Desert Treasure (Ancient Magicks)",
            LunarDiplomacy to "Lunar Diplomacy (Lunar, Vengeance)",
            DesertTreasureII to "Desert Treasure II (sceptres, Magus ring)",
            TempleAtSenntisten to "Temple at Senntisten (Ancient Curses)",
            KingsRansom to "King's Ransom (Piety, Chivalry)",
            DragonSlayerII to "Dragon Slayer II (Ava's assembler)",
            SongOfTheElves to "Song of the Elves (Bow of faerdhinen, crystal)",
            MonkeyMadnessII to "Monkey Madness II (zenyte, heavy ballista)",
        )

    fun cohort(player: Player): String {
        player.attr[COHORT]?.let { return it }
        val cohort = if (player.attr[NEW_ACCOUNT_ATTR] == true) CHOICE else LEGACY
        player.attr[COHORT] = cohort
        return cohort
    }

    fun isLegacy(player: Player): Boolean = cohort(player) == LEGACY

    fun chosen(player: Player, group: ChoiceGroup): ShortQuest? =
        player.attr[group.key]?.let { name -> group.quests.firstOrNull { it.name == name } }

    /** The options still open in [group]: nothing once a choice is made, never a quest the player already finished. */
    fun available(player: Player, group: ChoiceGroup): List<ShortQuest> =
        if (isLegacy(player) || chosen(player, group) != null) emptyList() else group.quests.filterNot { it.isFinished(player) }

    enum class Result { CHOSEN, LEGACY_ACCOUNT, ALREADY_CHOSEN, NOT_IN_GROUP, ALREADY_COMPLETE }

    /**
     * Makes [quest] the permanent choice of [group]. The choice is stored before the reward is handed out and nothing
     * can clear it, so a second call - a double click, a reconnect in the middle of the dialogue - is refused.
     */
    fun choose(player: Player, group: ChoiceGroup, quest: ShortQuest): Result {
        if (isLegacy(player)) return Result.LEGACY_ACCOUNT
        if (quest !in group.quests) return Result.NOT_IN_GROUP
        if (player.attr[group.key] != null) return Result.ALREADY_CHOSEN
        if (quest.isFinished(player)) return Result.ALREADY_COMPLETE
        player.attr[group.key] = quest.name
        FoundationRewards.complete(player, quest)
        return Result.CHOSEN
    }

    /**
     * Legacy accounts keep "all quests completed" (owner, question a): the eight choice quests and the OSRS list entries
     * are set to complete with their unlocks, without paying experience again. Runs at every login and changes nothing
     * once applied.
     */
    fun applyLegacy(player: Player) {
        FoundationQuests.SHORT.forEach { quest ->
            if (!quest.isFinished(player)) {
                if (quest.usesVarbits) player.setVarbit(quest.questId, quest.completedValue) else player.setVarp(quest.questId, quest.completedValue)
            }
            if (!FoundationRewards.isRewarded(player, quest)) {
                player.attr[FoundationRewards.rewardedKey(quest)] = true
                quest.reward.unlock(player)
            }
        }
        if (player.getVarbit(KNIGHT_WAVES_VARBIT) < KNIGHT_WAVES_COMPLETE) player.setVarbit(KNIGHT_WAVES_VARBIT, KNIGHT_WAVES_COMPLETE)
    }

    /** True when every unlock of [quest] is only available because the quest is finished (guard used by tests). */
    fun unlockHeld(player: Player, quest: ShortQuest): Boolean =
        when (quest) {
            DesertTreasure -> player.attr[UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] == true
            LunarDiplomacy -> player.attr[UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED] == true
            TempleAtSenntisten -> player.attr[AncientCurses.UNLOCKED_ATTR] == true
            KingsRansom -> player.getVarbit(KNIGHT_WAVES_VARBIT) >= KNIGHT_WAVES_COMPLETE
            DesertTreasureII -> player.attr[UnlockNpcRewards.ANCIENT_RINGS_UNLOCKED] == true
            DragonSlayerII -> player.attr[UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED] == true
            else -> quest.isFinished(player)
        }
}
