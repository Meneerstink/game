package gg.rsmod.game.sync.block

/**
 * @author Tom <rspsmods@gmail.com>
 */
enum class UpdateBlockType {
    APPEARANCE,

    ANIMATION,

    GFX,

    FACE_TILE,

    FACE_PAWN,

    MOVEMENT_TYPE,

    MOVEMENT,

    CONTEXT_MENU,

    FORCE_MOVEMENT,

    HITMARK,

    FORCE_CHAT,

    /**
     * NPC only. `NpcExtendedInfoFlag.COMBAT_LEVEL` (0x80000): overrides the combat level the
     * client would otherwise take from its own cached `NPCType`, for as long as that npc is
     * tracked. `NPCList` falls back to the cache value when 65535 is sent.
     */
    COMBAT_LEVEL,
}
