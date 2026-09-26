package gg.rsmod.plugins.content.areas.deathsoffice

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile

/**
 * Death's Office, imported from OSRS map square 12633 into square 12639 by `DeathsOfficeMapImportTool` (z + 384, x kept),
 * and the OSRS Death's Domain entrances.
 *
 * Cache ids: the OSRS npcs 9855 / 9856 / 9857 as imported by `OsrsNpcImportTool` batch `deaths-office`, the OSRS locs as
 * imported by `DeathsOfficeMapImportTool` (both recorded in `OSRS_IMPORT_MASTER.yml`).
 */
object DeathsOfficeArea {
    const val REGION = 12639

    /** OSRS square 12633 -> 667 square 12639. */
    const val DZ = 384

    /** OSRS 9855 "Death" (Talk-to, Collect), size 2, seated (stand 8748). */
    const val DEATH = DeathsOfficeIds.DEATH

    /** OSRS 9856 / 9857 "Grave" (Check, Loot): the basic gravestone and the Angel of Death. */
    const val GRAVE = DeathsOfficeIds.GRAVE
    const val GRAVE_ANGEL = DeathsOfficeIds.GRAVE_ANGEL

    /** OSRS 39549 "Portal" (Use) and 39550 "Death's Coffer" (Sacrifice). */
    const val PORTAL = DeathsOfficeIds.PORTAL
    const val COFFER = DeathsOfficeIds.COFFER

    /**
     * OSRS Wiki "Death (NPC)" map pin 3180,5727 is the centre of the size-2 npc; its south-west tile is 3179,5726, the 2x2 of
     * tiles the OSRS map itself marks as blocked behind the desk (imported square, tile flags).
     */
    val DEATH_TILE = Tile(3179, 5726 + DZ, 0)
    val DEATH_FACING = Direction.WEST

    /** Where the player appears in the office: the first free tile east of the portal (OSRS portal 3169,5726, 2x2). */
    val ARRIVAL = Tile(DeathsOfficeIds.ARRIVAL_X, DeathsOfficeIds.ARRIVAL_Z, 0)

    fun inOffice(tile: Tile): Boolean = tile.regionId == REGION

    /**
     * A Death's Domain entrance. [loc] is the imported OSRS loc; [tile]/[rotation] its OSRS placement (OSRS Wiki
     * "Death's Domain (scenery)", OSRS map squares 12849 / 10806 / 12342 / 12344); [replaces] a rev-667 loc that stood on
     * the same spot and is removed first.
     */
    data class Entrance(
        val name: String,
        val loc: Int,
        val tile: Tile,
        val rotation: Int,
        val replaces: List<Tile> = emptyList(),
    )

    /**
     * Owner 2026-09-26: an entrance at every OSRS location (no Falador: its White Knights' Castle crypt does not exist in the
     * 667 map) plus one at the Grand Exchange home in the Ferox style, placed through `data/cfg/home_decor.txt`.
     *  - Lumbridge: OSRS 38426 at 3238,3192 - the 667 graveyard has a gravestone there (3239,3192), so the open grave takes
     *    that gravestone's place.
     *  - Seers' Village: OSRS 39546 at 2713,3466 rotation 3 (2 tiles wide); the 667 plant on its second tile (2714,3466) goes.
     *  - Edgeville: OSRS 39547 at 3096,3476 - the very coffin that stands there in 667 (loc 26939) becomes the entrance.
     *  - Ferox Enclave: the imported 62430, already in the map.
     */
    val ENTRANCES =
        listOf(
            Entrance("Lumbridge", DeathsOfficeIds.ENTRANCE_LUMBRIDGE, Tile(3239, 3192, 0), 2, replaces = listOf(Tile(3239, 3192, 0))),
            Entrance("Seers' Village", DeathsOfficeIds.ENTRANCE_SEERS, Tile(2713, 3466, 0), 3, replaces = listOf(Tile(2714, 3466, 0))),
            Entrance("Edgeville", DeathsOfficeIds.ENTRANCE_EDGEVILLE, Tile(3096, 3476, 0), 0, replaces = listOf(Tile(3096, 3476, 0))),
        )

    /** Every loc that takes a player into the office ("Enter"). */
    val ENTRANCE_LOCS: Set<Int> = ENTRANCES.map { it.loc }.toSet() + DeathsOfficeIds.ENTRANCE_FEROX
}
