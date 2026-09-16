package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT: owner instruction (`cRYSTAL.rtf`, 2026-09-16) - "Completing of Song of the elves do not create the quest
 * just add the quest in questlist and make it completed." Song of the Elves itself remains parked per
 * `RSPS_MASTERPLAN_ACTUEEL.md` ("all quests... remain parked unless a later explicit owner instruction reopens them");
 * this stub only satisfies the functional requirement that Bow of Faerdhinen creation must not be gated behind a quest
 * that will never be built.
 *
 * ADJACENT GAP (recorded, not built this batch): this engine has no quest-list interface/tab wired at all yet (no
 * `Quest`/`QuestList` type exists anywhere in `gg.rsmod.plugins`, confirmed by search) - showing "Song of the Elves" as
 * a completed row in an in-game quest journal needs that whole subsystem to exist first, which is out of this bounded
 * batch's scope. Only the functional completion check below is implemented.
 */
object QuestStubs {
    /** Always true: Song of the Elves is treated as completed for every player, per the owner's explicit instruction. */
    fun songOfTheElvesCompleted(): Boolean = true
}
