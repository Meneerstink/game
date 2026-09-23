package gg.rsmod.plugins.content.skills.runecrafting

import gg.rsmod.plugins.api.cfg.Items

/**
 * @author Triston Plummer ("Dread")
 *
 * I was originally intending to use pairs to describe the different combinations at different altars, but
 * the Wiki seems to suggest that the same combination rune gives different XP depending on where it is created, so I figured
 * doing it this way would be easier to describe that.
 *
 * @param id        The crafted rune id
 * @param level     The level required to craft the combination rune
 * @param xp        The xp given per successful craft
 * @param talisman  The talisman required to craft the combination rune
 * @param rune      The opposite rune required to craft the combination
 * @param altar     The altar to craft the combination at
 */
enum class CombinationRune(
    val id: Int,
    val level: Int,
    val xp: Double,
    val talisman: Int,
    val rune: Int,
    val altar: Altar,
    /** Extra item consumed per essence (Aether catalyst), -1 when none. */
    val catalyst: Int = -1,
    /** Only craftable under Magic Imbue (no usable talisman). */
    val requiresImbue: Boolean = false,
) {
    MIST_AIR(
        id = Items.MIST_RUNE,
        level = 6,
        xp = 8.0,
        talisman = Items.WATER_TALISMAN,
        rune = Items.WATER_RUNE,
        altar = Altar.AIR,
    ),
    MIST_WATER(
        id = Items.MIST_RUNE,
        level = 6,
        xp = 8.5,
        talisman = Items.AIR_TALISMAN,
        rune = Items.AIR_RUNE,
        altar = Altar.WATER,
    ),

    DUST_AIR(
        id = Items.DUST_RUNE,
        level = 10,
        xp = 8.3,
        talisman = Items.EARTH_TALISMAN,
        rune = Items.EARTH_RUNE,
        altar = Altar.AIR,
    ),
    DUST_EARTH(
        id = Items.DUST_RUNE,
        level = 10,
        xp = 9.0,
        talisman = Items.AIR_TALISMAN,
        rune = Items.AIR_RUNE,
        altar = Altar.EARTH,
    ),

    MUD_WATER(
        id = Items.MUD_RUNE,
        level = 13,
        xp = 9.3,
        talisman = Items.EARTH_TALISMAN,
        rune = Items.EARTH_RUNE,
        altar = Altar.WATER,
    ),
    MUD_EARTH(
        id = Items.MUD_RUNE,
        level = 13,
        xp = 9.5,
        talisman = Items.WATER_TALISMAN,
        rune = Items.WATER_RUNE,
        altar = Altar.EARTH,
    ),

    SMOKE_AIR(
        id = Items.SMOKE_RUNE,
        level = 15,
        xp = 8.5,
        talisman = Items.FIRE_TALISMAN,
        rune = Items.FIRE_RUNE,
        altar = Altar.AIR,
    ),
    SMOKE_FIRE(
        id = Items.SMOKE_RUNE,
        level = 15,
        xp = 9.5,
        talisman = Items.AIR_TALISMAN,
        rune = Items.AIR_RUNE,
        altar = Altar.FIRE,
    ),

    STEAM_WATER(
        id = Items.STEAM_RUNE,
        level = 19,
        xp = 9.5,
        talisman = Items.FIRE_TALISMAN,
        rune = Items.FIRE_RUNE,
        altar = Altar.WATER,
    ),
    STEAM_FIRE(
        id = Items.STEAM_RUNE,
        level = 19,
        xp = 10.0,
        talisman = Items.WATER_TALISMAN,
        rune = Items.WATER_RUNE,
        altar = Altar.FIRE,
    ),

    LAVA_EARTH(
        id = Items.LAVA_RUNE,
        level = 23,
        xp = 10.0,
        talisman = Items.FIRE_TALISMAN,
        rune = Items.FIRE_RUNE,
        altar = Altar.EARTH,
    ),
    LAVA_FIRE(
        id = Items.LAVA_RUNE,
        level = 23,
        xp = 10.5,
        talisman = Items.EARTH_TALISMAN,
        rune = Items.EARTH_RUNE,
        altar = Altar.FIRE,
    ),

    /*
     * OSRS import run 2026-09-17, OSRS Wiki "Aether rune" / "Combination rune": pure essence + soul rune + aether catalyst on the
     * Cosmic Altar, 90 Runecraft, 20 experience; "The use of Magic Imbue is necessary as the true Soul Altar and soul talisman are not
     * accessible". Daeyalt/guardian essence and scarred extracts do not exist here (N/A).
     */
    AETHER_COSMIC(
        id = Items.AETHER_RUNE,
        level = 90,
        xp = 20.0,
        talisman = Items.SOUL_TALISMAN,
        rune = Items.SOUL_RUNE,
        altar = Altar.COSMIC,
        catalyst = Items.AETHER_CATALYST,
        requiresImbue = true,
    ),
    ;

    companion object {
        val values = enumValues<CombinationRune>()
    }
}
