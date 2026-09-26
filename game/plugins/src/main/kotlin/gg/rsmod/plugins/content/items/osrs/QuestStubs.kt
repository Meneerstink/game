package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.foundation.SongOfTheElves

/**
 * OSRS-IMPORT: owner instruction (`cRYSTAL.rtf`, 2026-09-16) made Song of the Elves count as completed for everyone.
 * Owner 2026-09-26 (new-player foundation) replaced that: Song of the Elves is one of the three Gear choices and has a
 * short playable version (quests/foundation), so it is only complete when the account finished it - by playing it, by
 * picking it at the Quest Guide, or as a legacy account (every pre-existing account keeps it completed).
 */
object QuestStubs {
    /** OSRS Wiki: singing crystal armour and the Bow of faerdhinen needs Song of the Elves. */
    fun songOfTheElvesCompleted(player: Player): Boolean = SongOfTheElves.isFinished(player)
}