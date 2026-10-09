package gg.rsmod.game.model.decor

import gg.rsmod.game.model.Tile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Royal Hall's dressing baked into region 12598's loc file (owner 2026-10-09: "in de kaart zetten" - the hall built up
 * piece by piece after every login / teleport / stair climb, because ~900 dynamic objects reached the client as 2 packets
 * each after the map load, each placed once its models loaded). Locs of the map file are counted in the client's loading
 * screen instead, like every Jagex building (Void packs whole maps the same way, tools/map/MapPacker.kt).
 *
 * `RoyalHallDecorBakeTool` writes the baked placements to [MANIFEST] after both caches verified; `home_decor_live.plugin.kts`
 * compares home_decor.txt with it: lines in both are already in the map, lines only in the file are spawned dynamically,
 * lines only in the manifest are taken out of the world, so the file stays the one design the owner edits.
 */
object HomeDecorBake {
    val MANIFEST: Path = Paths.get("data", "cfg", "home_decor.baked.txt")

    /** Region 12598 (mapsquare 49,54): the only region baked. */
    const val REGION_ID = 12598
    val REGION_X = 3136..3199
    val REGION_Z = 3456..3519

    /**
     * The hall's carpet (Varrock carpet 15548 corner / 15549 edge / 15550 middle) as forcedecor copies (opcode 73,
     * RoyalHallLocTool). A non-interactive, walk-through ground decoration in a map file is dropped by the client while
     * "Ground decoration" is off - the low preset turns it off (MapRegion / Static213) - where a dynamic one is always drawn.
     */
    val FORCEDECOR_CARPETS = linkedMapOf(15548 to 62911, 15549 to 62912, 15550 to 62913)

    /** [placement] as it stands in the map file: a carpet piece as its forcedecor copy. */
    fun baked(placement: HomeDecorFile.Placement): HomeDecorFile.Placement =
        FORCEDECOR_CARPETS[placement.id]?.takeIf { placement.type == 22 }?.let { placement.copy(id = it) } ?: placement

    fun inRegion(tile: Tile) = tile.x in REGION_X && tile.z in REGION_Z

    /** The baked placements ([MANIFEST], `obj` lines with the map file's ids); empty before the first bake. */
    fun loadManifest(path: Path = MANIFEST): List<HomeDecorFile.Placement> =
        if (Files.exists(path)) HomeDecorFile.parse(Files.readAllLines(path)).placements else emptyList()

    fun manifestText(placements: List<HomeDecorFile.Placement>): String =
        buildString {
            append("# Written by RoyalHallDecorBakeTool after both caches verified - do not edit; edit home_decor.txt and bake again.\n")
            append("# Every line is a loc in region $REGION_ID's map file (l49_54); carpet ids are the forcedecor copies.\n")
            placements.forEach { p -> append("obj ${p.id} ${p.tile.x} ${p.tile.z} ${p.tile.height} ${p.type} ${p.rot}\n") }
        }

    /**
     * home_decor.txt against the map: [inMap] = file lines already baked, [extra] = file lines to spawn dynamically,
     * [missing] = baked locs the file no longer has (taken out of the world while that is so).
     */
    class Diff(val inMap: List<HomeDecorFile.Placement>, val extra: List<HomeDecorFile.Placement>, val missing: List<HomeDecorFile.Placement>)

    fun diff(
        design: HomeDecorFile.Design,
        manifest: List<HomeDecorFile.Placement>,
    ): Diff {
        val left = manifest.groupingBy { it }.eachCount().toMutableMap()
        val inMap = ArrayList<HomeDecorFile.Placement>()
        val extra = ArrayList<HomeDecorFile.Placement>()
        design.placements.forEach { p ->
            val b = baked(p)
            val n = left[b] ?: 0
            if (n > 0) {
                left[b] = n - 1
                inMap += p
            } else {
                extra += p
            }
        }
        val missing = left.flatMap { (p, n) -> List(n) { p } }
        return Diff(inMap, extra, missing)
    }
}
