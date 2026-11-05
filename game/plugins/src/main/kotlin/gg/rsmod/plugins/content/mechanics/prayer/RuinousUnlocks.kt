package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.ownership.Possessions
import gg.rsmod.plugins.content.quests.foundation.DesertTreasureII

/** N490, owner final plan + correction 2026-10-31: DT2 AND a permanently consumed drop, for everyone. */
object RuinousUnlocks {
    val UNLOCKED = AttributeKey<Boolean>(persistenceKey = "ruinous_unlocked")

    fun refusal(player: Player): String? = when {
        !DesertTreasureII.isFinished(player) -> "Complete Desert Treasure II and read the Ruinous powers book to use the Ruinous Powers."
        player.attr[UNLOCKED] != true -> "Find and read the Ruinous powers book to unlock the Ruinous Powers."
        else -> null
    }

    enum class ReadResult { QUEST_REQUIRED, ALREADY_UNLOCKED, MISSING_BOOK, UNLOCKED }

    /** N545: reading now would unlock (quest done, not unlocked yet, the book in the backpack) - only then the warning is shown. */
    fun wouldUnlock(player: Player): Boolean =
        DesertTreasureII.isFinished(player) && player.attr[UNLOCKED] != true && player.inventory.contains(Items.RUINOUS_POWERS)

    /** No suspension between checking, consuming exactly one book and persisting the unlock. */
    fun read(player: Player): ReadResult {
        if (!DesertTreasureII.isFinished(player)) return ReadResult.QUEST_REQUIRED
        if (player.attr[UNLOCKED] == true) return ReadResult.ALREADY_UNLOCKED
        if (player.inventory.remove(Items.RUINOUS_POWERS, 1).hasFailed()) return ReadResult.MISSING_BOOK
        player.attr[UNLOCKED] = true
        return ReadResult.UNLOCKED
    }

    fun eligibleForDrop(player: Player): Boolean =
        player.attr[UNLOCKED] != true && !Possessions.owns(player, setOf(Items.RUINOUS_POWERS))

    /** After legacy quest completion at login; deliberately bypasses the in-combat switching restriction. */
    fun login(player: Player) {
        if (AncientCurses.getBook(player) != AncientCurses.PrayerBook.RUINOUS || refusal(player) == null) return
        AncientCurses.resetLockedRuinousBook(player)
        player.message("Your prayer book has been reset to normal. Complete Desert Treasure II and read the Ruinous powers book to unlock the Ruinous Powers.")
    }
}
