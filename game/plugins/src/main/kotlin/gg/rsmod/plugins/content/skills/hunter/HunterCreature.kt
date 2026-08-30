package gg.rsmod.plugins.content.skills.hunter

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Objs

/**
 * A single Hunter trap-based creature. Provisional, 2011-baseline stats scaled by roughly
 * 7x for XP (within the confirmed 5-10x progression envelope) - see IMPLEMENTATION_STATUS.md.
 *
 * ponytail: creatures are trap-and-wait only (no wandering/tracking simulation, no per-area
 * gating). Real Hunter also has deadfall/net-trap/impling mechanics; add when a dedicated
 * pass revisits Hunter polish.
 */
data class HunterCreature(
    val name: String,
    val trapItem: Int,
    val emptyTrapObj: Int,
    val fullTrapObj: Int,
    val level: Int,
    val xp: Double,
    val catchItem: Int,
    val catchAmount: Int = 1,
    val lowChance: Int = 40,
    val highChance: Int = 180,
    val ticksBetweenRolls: Int = 5,
)

object HunterCreatures {
    val ALL =
        listOf(
            HunterCreature(
                name = "Crimson swift",
                trapItem = Items.BIRD_SNARE,
                emptyTrapObj = Objs.BIRD_SNARE_19174,
                fullTrapObj = Objs.BIRD_SNARE_19175,
                level = 9,
                xp = 119.0,
                catchItem = Items.CRIMSON_SWIFT,
            ),
            HunterCreature(
                name = "Copper longtail",
                trapItem = Items.BIRD_SNARE,
                emptyTrapObj = Objs.BIRD_SNARE_19176,
                fullTrapObj = Objs.BIRD_SNARE_19177,
                level = 14,
                xp = 172.0,
                catchItem = Items.COPPER_LONGTAIL,
            ),
            HunterCreature(
                name = "Grey chinchompa",
                trapItem = Items.BOX_TRAP,
                emptyTrapObj = Objs.BOX_TRAP_19187,
                fullTrapObj = Objs.BOX_TRAP_19188,
                level = 21,
                xp = 434.0,
                catchItem = Items.CHINCHOMPA,
            ),
            HunterCreature(
                name = "Black warlock",
                trapItem = Items.BIRD_SNARE,
                emptyTrapObj = Objs.BIRD_SNARE_19178,
                fullTrapObj = Objs.BIRD_SNARE_19179,
                level = 43,
                xp = 1015.0,
                catchItem = Items.BLACK_WARLOCK,
            ),
            HunterCreature(
                name = "Red chinchompa",
                trapItem = Items.BOX_TRAP,
                emptyTrapObj = Objs.BOX_TRAP_19192,
                fullTrapObj = Objs.BOX_TRAP_19193,
                level = 63,
                xp = 1386.0,
                catchItem = Items.RED_CHINCHOMPA,
            ),
        )

    fun bestFor(
        trapItem: Int,
        hunterLevel: Int,
    ): HunterCreature? =
        ALL
            .filter { it.trapItem == trapItem && hunterLevel >= it.level }
            .maxByOrNull { it.level }
}
