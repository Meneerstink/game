package gg.rsmod.game.sync.block

/**
 * @author Tom <rspsmods@gmail.com>
 */
enum class UpdateBlockType {
    APPEARANCE,

    ANIMATION,

    /** Spot-anim slot 0; [GFX_2]..[GFX_4] are slots 1..3 - see [GraphicBlock]. */
    GFX,

    GFX_2,

    GFX_3,

    GFX_4,

    FACE_TILE,

    FACE_PAWN,

    MOVEMENT_TYPE,

    MOVEMENT,

    CONTEXT_MENU,

    FORCE_MOVEMENT,

    HITMARK,

    FORCE_CHAT,

    /**
     * Player only. `PlayerExtendedInfoFlag.CLANMATE` (0x100000): whether this player is in the *observer's* clan - the client
     * draws a clanmate dot on the minimap (Minimap: mapdots[7]). The value differs per observer, so [PlayerUpdateBlockSegment]
     * computes it for the observer it writes for; raising the block on a player makes every observer re-read it.
     */
    CLANMATE,

    /**
     * NPC only. `NpcExtendedInfoFlag.COMBAT_LEVEL` (0x80000): overrides the combat level the
     * client would otherwise take from its own cached `NPCType`, for as long as that npc is
     * tracked. `NPCList` falls back to the cache value when 65535 is sent.
     */
    COMBAT_LEVEL,

    /**
     * NPC only. `NpcExtendedInfoFlag.NAME` (0x40000): the name the client shows for this npc instead of its cached
     * `NPCType` name; an empty string (or the cached name) puts the cached name back.
     */
    NAME,
}
