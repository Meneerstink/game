package gg.rsmod.game.fs

enum class ArchiveType(
    val id: Int,
    val modernArchive: Boolean,
    val subId: Int = -1,
    val archiveOperand: Int = 8,
    val fileOperand: Int = 0xFF,
) {
    STRUCT(2, modernArchive = false, subId = 26),
    VARP(2, modernArchive = false, subId = 16),
    OBJECT(16, modernArchive = true),
    ENUM(17, modernArchive = true),
    NPC(18, modernArchive = true, archiveOperand = 134238215, fileOperand = 0x7f),
    ITEM(19, modernArchive = true),
    ANIM(20, modernArchive = true, archiveOperand = 7, fileOperand = 0x7f),
    SPOTANIM(21, modernArchive = true, archiveOperand = 8),
    VARBIT(22, modernArchive = true, archiveOperand = 10, fileOperand = 0x3FF),

    /**
     * Body animation sets - the npc/player "render animation" table. Not a modern paged index:
     * it is config group 32 inside index 2 (`Js5ConfigGroup.BASTYPE = 32` in the revision 667
     * client), which is where every npc's real ready/walk/run/crawl sequences live.
     */
    BAS(2, modernArchive = false, subId = 32),
}
