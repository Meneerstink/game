package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Tile

/**
 * Single source of truth for every home facility's position inside the imported Ferox Enclave.
 *
 * Since the Ferox world import the physical hub (walls, floors, bank chest, pools, altar,
 * barriers, stairs) is real cache content at fixed world coordinates, so facilities are declared
 * as absolute tiles rather than offsets from `game.yml`'s home tile. `game.yml` only decides the
 * arrival tile (`home-x`/`home-z` = 3137,3629, the open plaza east of the bank chest);
 * [Facility.tile] still takes `home` so the existing `home_*.plugin.kts` call sites are unchanged
 * (only the height is taken from it).
 *
 * Two kinds of facility:
 *  * IMPORTED - a real Ferox object already placed by the cache (bank chest, pools, altar). The
 *    plugin only binds its option; `home_verify.plugin.kts` asserts the object really stands there.
 *  * SPAWNED - an NPC or object this server places on an open plaza tile (shops, board, PvM
 *    practice), verified at boot to be inside the safe zone, collision-free and reachable.
 *
 * Plaza tiles were chosen from the server's real boot-time collision map of the imported regions
 * (home_verify prints it when something is unreachable); the boot-time checks are what prove them.
 */
object HomeLayout {
    class Facility(
        val name: String,
        val x: Int,
        val z: Int,
        val width: Int = 1,
        val length: Int = 1,
        /** true when players cannot stand on the footprint (objects); false for NPCs/markers. */
        val solid: Boolean = true,
        /** true when the object is imported cache content rather than spawned by a plugin. */
        val imported: Boolean = false,
        /** Object id expected on the tile at boot for imported facilities. */
        val objectId: Int = -1,
    ) {
        fun tile(home: Tile): Tile = Tile(x, z, home.height)

        fun footprint(home: Tile): List<Tile> =
            (0 until width).flatMap { dx -> (0 until length).map { dz -> Tile(x + dx, z + dz, home.height) } }
    }

    // ---- imported Ferox content -------------------------------------------------------------
    val bank = Facility("bank-chest", 3130, 3632, imported = true, objectId = FeroxObjects.BANK_CHEST)
    val pool = Facility("pool-of-refreshment", 3128, 3633, width = 2, length = 2, imported = true, objectId = FeroxObjects.POOL_OF_REFRESHMENT)
    val poolNorth = Facility("pool-of-refreshment-north", 3128, 3638, width = 2, length = 2, imported = true, objectId = FeroxObjects.POOL_OF_REFRESHMENT)
    val altar = Facility("altar", 3177, 3625, width = 3, imported = true, objectId = FeroxObjects.ALTAR)

    // ---- arrival ----------------------------------------------------------------------------
    /** One tile south of the configured home tile (kept in sync with the (0,-1) offset in the game module). */
    val arrival = Facility("arrival", 3137, 3628, solid = false)

    // ---- spawned services on the plaza -----------------------------------------------------
    val shopGeneral = Facility("shop-general", 3139, 3627, solid = false)
    val shopRunes = Facility("shop-runes", 3141, 3627, solid = false)
    val shopArmour = Facility("shop-armour", 3143, 3627, solid = false)
    val shopArchery = Facility("shop-archery", 3145, 3627, solid = false)
    val shopStaffs = Facility("shop-staffs", 3147, 3627, solid = false)
    val summoning = Facility("summoning", 3149, 3627, solid = false)
    val transport = Facility("transport", 3151, 3628, solid = false)
    val board = Facility("board", 3140, 3631)

    // 2026-09-06 owner human retest: "The unwanted Arena entrance in Ferox must be REMOVED."
    // `pvmArenaEntrance`/`home_pvm_minigames.plugin.kts` spawned a custom `Objs.ARENA_ENTRANCE`
    // object here as a physical door into Practice PvP - not authentic Ferox content, and the
    // owner does not want it. Removed outright (deleted the spawning plugin file); Practice PvP
    // itself is unaffected, it remains reachable via the pre-existing `::practice` command
    // (`practicepvp.plugin.kts`), which this entrance was layered on top of, not a replacement for.
    // The vacated tile is reused below for Farid Morrisane - already boot-verified
    // collision-free/reachable when it held the (solid) arena entrance object, so it is a safe
    // spot for another NPC.

    // 2026-09-06 owner human retest: "Farid Morrisane must be MOVED from his current
    // Varrock/Grand Exchange placement into Ferox Enclave." See `home_shops.plugin.kts`.
    val faridMorrisane = Facility("farid-morrisane", 3142, 3631, solid = false)

    val functional: List<Facility> =
        listOf(
            bank, pool, poolNorth, altar, arrival,
            shopGeneral, shopRunes, shopArmour, shopArchery, shopStaffs,
            summoning, transport, board, faridMorrisane,
        )

    /** Ferox carries its own decoration; nothing is spawned on top of it. */
    val decor: List<Facility> = emptyList()
}
