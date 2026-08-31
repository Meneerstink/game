package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Tile

/**
 * BATCH 1 (owner night-run instruction, R02.1-R02.4/HOME_DESIGN_2.png): the SINGLE source of
 * truth for every home facility's position, now that [BountyHunterHome.SAFE_RADIUS] has grown
 * from 5 to 24 (~49x49 usable interior) to actually fit the confirmed design's quadrant layout
 * instead of stacking more objects around the old cramped footprint.
 *
 * Every home_*.plugin.kts file reads its tile(s) from here instead of inlining its own
 * `home.transform(dx, dz)` literal, so there is exactly one place that can put two facilities on
 * the same tile - and `home_verify.plugin.kts` checks every entry below for overlap and real
 * flood-fill reachability at boot, not just "is inside the polygon".
 *
 * All offsets are relative to [gg.rsmod.game.GameContext.home] (world tile, from game.yml).
 * Quadrant assignment matches HOME_DESIGN_2.png exactly: NE=Shops, E=Pool&Altar, SE=Transport,
 * SW=PvM&Minigames, NW=Summoning, S=Arrival, centre=Bank&GE, N=fast Wilderness exit (kept empty
 * of facilities on purpose - the design shows nothing built on the north approach).
 *
 * [Facility.footprint] is only used by this codebase's own boot-time overlap/reachability
 * self-check (`home_verify.plugin.kts`) to make sure this file's OWN plan doesn't double-book a
 * tile - it is not a substitute for each object's real cache footprint/collision, which stays the
 * responsibility of the plugin file that actually spawns the object.
 */
object HomeLayout {
    data class Facility(
        val name: String,
        val dx: Int,
        val dz: Int,
        val width: Int = 1,
        val length: Int = 1,
        /** false = purely decorative/non-solid, excluded from the strict no-overlap check. */
        val solid: Boolean = true,
    ) {
        fun tile(home: Tile): Tile = home.transform(dx, dz)

        /** Every tile this facility's declared footprint occupies (dx grows east, dz grows north). */
        fun footprint(home: Tile): List<Tile> {
            val base = tile(home)
            return (0 until width).flatMap { w -> (0 until length).map { l -> base.transform(w, l) } }
        }
    }

    // ---- Centre: Bank & GE plaza ----
    val bank = Facility("bank", 0, 0)
    val bankRoof = Facility("bank-roof", -2, -2, width = 4, length = 4, solid = false) // Tent, non-solid, over the bank

    // ---- South: Aankomst/arrival, between the plaza and the south gate ----
    val arrival = Facility("arrival", 0, -10)
    val board = Facility("board", -4, -9)

    // ---- NE: Shops ----
    val shopGeneral = Facility("shop-general", 10, 9)
    val shopRunes = Facility("shop-runes", 13, 9)
    val shopArmour = Facility("shop-armour", 10, 12)
    val shopArchery = Facility("shop-archery", 13, 12)
    val shopStaffs = Facility("shop-staffs", 16, 10)

    // ---- E: Pool & Altar ----
    val pool = Facility("pool", 16, 2, width = 2, length = 2)
    val altar = Facility("altar", 16, -2, width = 2, length = 1)

    // ---- SE: Transport hub ----
    // BATCH 1 real-evidence relocation: the original (13,-13) tile sat inside a fully-enclosed
    // pre-existing cache collision island (confirmed via a boot-time isClipped grid scan - see
    // git history for the deleted aa_scratch_transport_scan.plugin.kts). dz=-7 (6 tiles north of
    // the old spot, same dx) was the nearest row that scan showed completely clear (all 15
    // sampled dx columns unclipped), so the facility moved there instead of being guessed.
    val transport = Facility("transport", 13, -7)

    // ---- SW: PvM & Minigames ----
    val pvmArenaEntrance = Facility("pvm-arena-entrance", -12, -10)
    val pvmArcheryTarget = Facility("pvm-archery-target", -10, -13)

    // ---- NW: Summoning ----
    val summoning = Facility("summoning", -12, 12)

    /** Every facility that must be distinct, reachable and non-overlapping - the hard-checked set. */
    val functional: List<Facility> =
        listOf(
            bank, bankRoof, arrival, board,
            shopGeneral, shopRunes, shopArmour, shopArchery, shopStaffs,
            pool, altar, transport, pvmArenaEntrance, pvmArcheryTarget, summoning,
        )

    // ---- Decoration (non-solid, one source of truth so `home_decor.plugin.kts` can't drift from
    // the functional layout above) ----
    val decorShopTorch1 = Facility("decor-shop-torch-1", 9, 8, solid = false)
    val decorShopTorch2 = Facility("decor-shop-torch-2", 14, 11, solid = false)
    val decorSummoningObelisk = Facility("decor-summoning-obelisk", -14, 13, solid = false)
    val decorSummoningTree = Facility("decor-summoning-tree", -11, 14, solid = false)
    val decorPvmBanner = Facility("decor-pvm-banner", -14, -9, solid = false)
    val decorPvmFence = Facility("decor-pvm-fence", -9, -14, solid = false)

    /** Intentionally the SAME tile as [arrival] - a non-solid ground emblem, not a separate spot. */
    val decorArrivalWheel = Facility("decor-arrival-wheel", arrival.dx, arrival.dz, solid = false)
    val decorArrivalTorch1 = Facility("decor-arrival-torch-1", 1, -10, solid = false)
    val decorArrivalTorch2 = Facility("decor-arrival-torch-2", -1, -11, solid = false)
    val decorRuinRubble = Facility("decor-ruin-rubble", 15, -14, solid = false)
    val decorRuinPillar = Facility("decor-ruin-pillar", -14, -11, solid = false)
    // Shifted with transport (see above) by the same +6 dz to keep the original relative layout.
    val decorTransportCart = Facility("decor-transport-cart", 15, -7, solid = false)
    val decorTransportBalloon = Facility("decor-transport-balloon", 14, -8, solid = false)
    val decorTransportRug = Facility("decor-transport-rug", 12, -8, solid = false)

    val decor: List<Facility> =
        listOf(
            decorShopTorch1, decorShopTorch2, decorSummoningObelisk, decorSummoningTree,
            decorPvmBanner, decorPvmFence, decorArrivalWheel, decorArrivalTorch1, decorArrivalTorch2,
            decorRuinRubble, decorRuinPillar, decorTransportCart, decorTransportBalloon, decorTransportRug,
        )
}
