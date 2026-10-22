package gg.rsmod.plugins.content.drops

enum class DropTableType {
    KILL,
    PICKPOCKET,
    STALL,
    CHEST,
    BOX,

    /** N113 (2026-10-22): bird nests (woodcutting roll and the nests' contents) - not npc kills, so no drop viewer row, no kill boost. */
    NEST,
}
