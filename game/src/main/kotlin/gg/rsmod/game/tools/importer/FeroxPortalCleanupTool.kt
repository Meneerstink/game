package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Ferox portal-removal follow-up (owner run 2026-09-06). Two rounds so far:
 *
 * Round 1 (applied, no longer in [REMOVALS]): [FeroxFollowupTool] removed the central Bounty
 * Hunter portal placement (loc 62576 at 3140,3621,0) but left its two flanking pieces - loc 62580
 * at 3142,3623,0 (type 11) and loc 62581 at 3142,3620,0 (type 10), both still literally named
 * "Bounty Hunter portal" in the cache. This tool removed both; confirmed gone from the live cache
 * (`Rev667RegionProbeTool locs` no longer lists either id).
 *
 * Round 2 (current): the owner's latest human retest still reports a graphical artefact at the
 * former portal site, and a fresh `Rev667RegionProbeTool locs 12600` dump explains why - round 1
 * only removed the portal's *frame* pieces, not its **ground effect**. Three floor-decoration
 * (`type=22`) loc ids form a exact, symmetric 4x4 diamond ring centred on the removed portal's own
 * tile (3140,3621) and appear **nowhere else** in either Ferox region (16 hits total, all inside
 * this one cluster) - loc 62577 (inner 2x2, 4 tiles), 62578 (middle ring, 8 tiles) and 62579
 * (outer corners, 4 tiles). That is the "portal glow" ground graphic, and it is what the owner is
 * still seeing.
 *
 * Round 2 also carries the two portals the owner separately asked removed this session (`Castle
 * Wars portal in Ferox must be REMOVED`, `Challenge portal in Ferox must be REMOVED` - distinct
 * from Bounty Hunter, which stays removed): loc 62554 "Castle Wars portal" at 3146,3638,0 and loc
 * 62419 "Challenge portal" at 3126,3620,0, both confirmed present and unbound (no server-side
 * option handler exists for either - `BountyHunterHome.kt`'s `FeroxObjects` comment already
 * called both "modern content that has no server-side counterpart here", they were left as inert
 * scenery, not something to keep visible).
 *
 * Same removal mechanism throughout: byte-exact `Rev667LocCodec` round-trip, one A7
 * [CacheTransaction] on both production caches. The LocTypes stay in the cache; only placements go.
 *
 * Usage: `./gradlew :game:runFeroxPortalCleanupTool --args="plan|apply"`.
 */
object FeroxPortalCleanupTool {
    class Removal(val locId: Int, val x: Int, val z: Int, val plane: Int, val label: String)

    private val REMOVALS =
        listOf(
            // Castle Wars portal + Challenge portal - owner instruction, 2026-09-06.
            Removal(62554, 3146, 3638, 0, "Castle Wars portal"),
            Removal(62419, 3126, 3620, 0, "Challenge portal"),
            // Bounty Hunter portal ground-glow ring - the residual artefact from the owner's
            // latest human retest (see KDoc "Round 2").
            Removal(62577, 3140, 3621, 0, "Bounty Hunter portal ground glow (inner)"),
            Removal(62577, 3140, 3622, 0, "Bounty Hunter portal ground glow (inner)"),
            Removal(62577, 3141, 3621, 0, "Bounty Hunter portal ground glow (inner)"),
            Removal(62577, 3141, 3622, 0, "Bounty Hunter portal ground glow (inner)"),
            Removal(62578, 3139, 3621, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62578, 3139, 3622, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62578, 3140, 3620, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62578, 3140, 3623, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62578, 3141, 3620, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62578, 3141, 3623, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62578, 3142, 3621, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62578, 3142, 3622, 0, "Bounty Hunter portal ground glow (middle)"),
            Removal(62579, 3139, 3620, 0, "Bounty Hunter portal ground glow (outer)"),
            Removal(62579, 3139, 3623, 0, "Bounty Hunter portal ground glow (outer)"),
            Removal(62579, 3142, 3620, 0, "Bounty Hunter portal ground glow (outer)"),
            Removal(62579, 3142, 3623, 0, "Bounty Hunter portal ground glow (outer)"),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            REMOVALS.groupBy { ((it.x shr 6) shl 8) or (it.z shr 6) }.forEach { (regionId, removals) ->
                val rx = regionId shr 8
                val rz = regionId and 0xFF
                val locName = "l${rx}_$rz"
                val archive =
                    library.index(FeroxImportTool.MAP_INDEX).archive(locName, keys[regionId]) ?: error("$locName missing")
                val bytes = archive.file(0)!!.data!!
                val locs = Rev667LocCodec.decode(bytes)
                check(Rev667LocCodec.encode(locs).contentEquals(bytes)) { "$locName round-trip failed" }
                val toRemove =
                    removals.map { r ->
                        val hit =
                            locs.filter {
                                it.id == r.locId && it.localX == r.x - rx * 64 && it.localZ == r.z - rz * 64 && it.plane == r.plane
                            }
                        check(hit.size == 1) {
                            "${r.label}: expected exactly one placement of ${r.locId} at ${r.x},${r.z},${r.plane}, found ${hit.size}"
                        }
                        println("REMOVE ${r.label} loc=${r.locId} at ${r.x},${r.z},${r.plane} type=${hit[0].type} rot=${hit[0].rotation} region=$regionId")
                        hit[0]
                    }
                val kept = locs - toRemove.toSet()
                val newBytes = Rev667LocCodec.encode(kept)
                check(Rev667LocCodec.decode(newBytes).size == kept.size)
                println("REGION $regionId $locName locs ${locs.size} -> ${kept.size} bytes ${bytes.size} -> ${newBytes.size}")
                mutations +=
                    CacheMutation(
                        FeroxImportTool.MAP_INDEX,
                        archive.id,
                        0,
                        newBytes,
                        "ferox portal cleanup remove ${removals.joinToString { it.label }} from $locName",
                        CacheItemProbeTool.sha1(bytes),
                        xtea = keys[regionId],
                    )
            }
        } finally {
            library.close()
        }

        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
