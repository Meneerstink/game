package gg.rsmod.plugins.content.magic

import gg.rsmod.plugins.api.cfg.Items

/**
 * Staves that provide an unlimited supply of an elemental rune while wielded.
 *
 * OSRS import run 2026-09-17 (OSRS Wiki "Combination rune" equivalent-staff table and the staff item pages): every elemental staff,
 * battlestaff and mystic staff, and every combination battlestaff / mystic staff counts for both of its elements (lava = earth + fire,
 * mud = water + earth, steam = water + fire, smoke = air + fire, mist = air + water, dust = air + earth). The previous table only knew the
 * plain staves, the elemental battlestaves, the Mystic smoke staff and the Kodai wand, so mystic elemental staves and the 667 lava, mud and
 * steam staves supplied no runes at all.
 *
 * @author Alycia <https://github.com/alycii>
 */
enum class MagicStaves(
    val runeId: Int,
    val staves: Array<Int>,
) {
    FIRE_RUNE(
        Items.FIRE_RUNE,
        arrayOf(
            Items.STAFF_OF_FIRE, Items.FIRE_BATTLESTAFF, Items.MYSTIC_FIRE_STAFF, Items.LAVA_BATTLESTAFF, Items.LAVA_BATTLESTAFF_OR, Items.MYSTIC_LAVA_STAFF,
            Items.STEAM_BATTLESTAFF, Items.STEAM_BATTLESTAFF_OR, Items.MYSTIC_STEAM_STAFF, Items.MYSTIC_STEAM_STAFF_OR, Items.SMOKE_BATTLESTAFF, Items.MYSTIC_SMOKE_STAFF,
        ),
    ),

    // OSRS-IMPORT Kodai wand: "provides unlimited water runes when equipped" (OSRS Wiki).
    WATER_RUNE(
        Items.WATER_RUNE,
        arrayOf(
            Items.STAFF_OF_WATER, Items.WATER_BATTLESTAFF, Items.MYSTIC_WATER_STAFF, Items.MUD_BATTLESTAFF, Items.MYSTIC_MUD_STAFF,
            Items.STEAM_BATTLESTAFF, Items.STEAM_BATTLESTAFF_OR, Items.MYSTIC_STEAM_STAFF, Items.MYSTIC_STEAM_STAFF_OR, Items.MIST_BATTLESTAFF, Items.MYSTIC_MIST_STAFF, Items.KODAI_WAND,
        ),
    ),

    AIR_RUNE(
        Items.AIR_RUNE,
        arrayOf(
            Items.STAFF_OF_AIR, Items.AIR_BATTLESTAFF, Items.MYSTIC_AIR_STAFF, Items.SMOKE_BATTLESTAFF, Items.MYSTIC_SMOKE_STAFF,
            Items.MIST_BATTLESTAFF, Items.MYSTIC_MIST_STAFF, Items.DUST_BATTLESTAFF, Items.MYSTIC_DUST_STAFF,
        ),
    ),

    EARTH_RUNE(
        Items.EARTH_RUNE,
        arrayOf(
            Items.STAFF_OF_EARTH, Items.EARTH_BATTLESTAFF, Items.MYSTIC_EARTH_STAFF, Items.LAVA_BATTLESTAFF, Items.LAVA_BATTLESTAFF_OR, Items.MYSTIC_LAVA_STAFF,
            Items.MUD_BATTLESTAFF, Items.MYSTIC_MUD_STAFF, Items.DUST_BATTLESTAFF, Items.MYSTIC_DUST_STAFF,
        ),
    ),
}
