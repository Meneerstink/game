package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items

/**
 * O26 PROJECT48 23-11: the Fountain of Rune (east of the Demonic Ruins, level 51 Wilderness).
 *
 * Map: OSRS cache 2499 square 13372 has the fountain multiloc 26782 (varbit 5714 -> 31941 on desktop) at 3373,3892,0 type 10 rot 0
 * (3x3) and the gushing energy 26783 (anim 7141) on plane 1 of the same tile; neither is on the rev-667 map, the server places both
 * (OsrsLocImportTool batch fountain-of-rune, seq batch fountain-of-rune-seq). The rev-667 square has four ruin pieces where OSRS has
 * the fountain (type 10 locs 14462 / 14464 / 14465 / 14466, absent from the OSRS square), removed at world init.
 *
 * Recharging (OSRS Wiki 'Fountain of Rune', 'Amulet of glory', 'Skills necklace', 'Combat bracelet', 'Ring of wealth'):
 * - using one piece charges ALL applicable jewellery in the inventory AND the equipment at once;
 * - amulets of glory (and the trimmed variant) need Heroes' Quest and go to 6 charges; skills necklaces and combat bracelets need
 *   Legends' Quest and go to 6; the ring of wealth needs nothing and goes to 5 (it can ONLY be charged here);
 * - combat bracelets and skills necklaces may be noted, rings of wealth and amulets of glory may not;
 * - each glory recharged has a 1/25,000 chance to become an amulet of eternal glory, whose message replaces the standard one
 *   (Transcript:Fountain of Rune).
 * The charges this fountain gives do not exist in the 667 cache: OsrsItemImportTool batch fountain-of-rune-jewellery.
 */
object FountainOfRune {
    /** Local loc ids of the import (OSRS 31941 'Fountain of Rune' and 26783 its energy plume). */
    const val FOUNTAIN = 67078
    const val ENERGY = 67079
    val TILE = Tile(3373, 3892, 0)
    val ENERGY_TILE = Tile(3373, 3892, 1)

    /** The rev-667 ruin pieces on the spot of the fountain (none of them is in the OSRS square). */
    val REMOVED_667 = listOf(14462 to Tile(3376, 3893, 0), 14464 to Tile(3375, 3893, 0), 14465 to Tile(3377, 3892, 0), 14466 to Tile(3376, 3895, 0))

    // OSRS imports (local id <- OSRS gameval)
    const val GLORY_T6 = 25632 // AMULET_OF_GLORY_TRIMMED6 11964
    const val GLORY_T5 = 25634 // AMULET_OF_GLORY_TRIMMED5 11966
    const val SKILLS_6 = 25636 // SKILLS_NECKLACE6 11968
    const val SKILLS_5 = 25638 // SKILLS_NECKLACE5 11970
    const val COMBAT_6 = 25640 // COMBAT_BRACELET6 11972
    const val COMBAT_5 = 25642 // COMBAT_BRACELET5 11974
    const val WEALTH_5 = 25644 // RING_OF_WEALTH5 11980

    const val ETERNAL_CHANCE = 25_000

    enum class Kind { GLORY, SKILLS_OR_COMBAT, WEALTH }

    /** Every piece the fountain charges -> (charged result, kind). The full piece itself is not in the table (nothing to charge). */
    val CHARGES: Map<Int, Pair<Int, Kind>> =
        buildMap {
            listOf(Items.AMULET_OF_GLORY, Items.AMULET_OF_GLORY_1, Items.AMULET_OF_GLORY_2, Items.AMULET_OF_GLORY_3, Items.AMULET_OF_GLORY_4, Items.AMULET_OF_GLORY_5)
                .forEach { put(it, Items.AMULET_OF_GLORY_6 to Kind.GLORY) }
            listOf(Items.AMULET_OF_GLORY_T, Items.AMULET_OF_GLORY_T1, Items.AMULET_OF_GLORY_T2, Items.AMULET_OF_GLORY_T3, Items.AMULET_OF_GLORY_T4, GLORY_T5)
                .forEach { put(it, GLORY_T6 to Kind.GLORY) }
            listOf(Items.SKILLS_NECKLACE, Items.SKILLS_NECKLACE_1, Items.SKILLS_NECKLACE_2, Items.SKILLS_NECKLACE_3, Items.SKILLS_NECKLACE_4, SKILLS_5)
                .forEach { put(it, SKILLS_6 to Kind.SKILLS_OR_COMBAT) }
            listOf(Items.COMBAT_BRACELET, Items.COMBAT_BRACELET_1, Items.COMBAT_BRACELET_2, Items.COMBAT_BRACELET_3, Items.COMBAT_BRACELET_4, COMBAT_5)
                .forEach { put(it, COMBAT_6 to Kind.SKILLS_OR_COMBAT) }
            listOf(Items.RING_OF_WEALTH, Items.RING_OF_WEALTH_1, Items.RING_OF_WEALTH_2, Items.RING_OF_WEALTH_3, Items.RING_OF_WEALTH_4)
                .forEach { put(it, WEALTH_5 to Kind.WEALTH) }
        }
}
