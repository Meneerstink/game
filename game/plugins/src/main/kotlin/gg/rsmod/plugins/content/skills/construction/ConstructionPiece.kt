package gg.rsmod.plugins.content.skills.construction

import gg.rsmod.plugins.api.cfg.Items

/**
 * A single buildable furniture piece.
 *
 * ponytail: Construction here is a level/material/XP progression tracked on the player
 * (persisted, see [Construction.BUILT_ATTR]) rather than a literal per-player 3D house you
 * walk around in - this engine has no player-instancing system to give each player their
 * own private copy of a room. See IMPLEMENTATION_STATUS.md for the tradeoff and the upgrade
 * path (a real instance/zone manager) once one exists.
 */
data class ConstructionPiece(
    val id: String,
    val name: String,
    val room: String,
    val level: Int,
    val xp: Double,
    val materials: List<Pair<Int, Int>>,
)

object ConstructionData {
    /** Provisional 2011-style furniture chain; XP scaled ~7x per the confirmed 5-10x envelope. */
    val PIECES =
        listOf(
            ConstructionPiece(
                "wooden_chair",
                "Wooden chair",
                "Parlour",
                level = 1,
                xp = 56.0,
                materials = listOf(Items.PLANK to 1),
            ),
            ConstructionPiece(
                "wooden_bookcase",
                "Wooden bookcase",
                "Study",
                level = 4,
                xp = 96.0,
                materials = listOf(Items.PLANK to 2),
            ),
            ConstructionPiece(
                "oak_table",
                "Oak table",
                "Dining room",
                level = 22,
                xp = 420.0,
                materials = listOf(Items.OAK_PLANK to 2, Items.IRON_NAILS to 3),
            ),
            ConstructionPiece(
                "oak_larder",
                "Oak larder",
                "Kitchen",
                level = 33,
                xp = 660.0,
                materials = listOf(Items.OAK_PLANK to 4),
            ),
            ConstructionPiece(
                "teak_armchair",
                "Teak armchair",
                "Parlour",
                level = 44,
                xp = 900.0,
                materials = listOf(Items.TEAK_PLANK to 3, Items.IRON_NAILS to 2),
            ),
            ConstructionPiece(
                "teak_dining_table",
                "Teak dining table",
                "Dining room",
                level = 52,
                xp = 1200.0,
                materials = listOf(Items.TEAK_PLANK to 6, Items.IRON_NAILS to 4),
            ),
            ConstructionPiece(
                "mahogany_table",
                "Mahogany table",
                "Dining room",
                level = 64,
                xp = 1960.0,
                materials = listOf(Items.MAHOGANY_PLANK to 6, Items.IRON_NAILS to 4),
            ),
            ConstructionPiece(
                "gilded_bench",
                "Gilded bench",
                "Throne room",
                level = 79,
                xp = 3080.0,
                materials = listOf(Items.MAHOGANY_PLANK to 10, Items.STEEL_BAR to 1),
            ),
            ConstructionPiece(
                "ornate_mahogany_table",
                "Ornate mahogany table",
                "Throne room",
                level = 90,
                xp = 4200.0,
                materials = listOf(Items.MAHOGANY_PLANK to 15, Items.STEEL_BAR to 2),
            ),
        )
}
