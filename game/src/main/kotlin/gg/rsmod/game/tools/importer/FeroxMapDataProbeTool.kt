package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.io.PrintStream

/**
 * RCV-011 home (owner: Ferox Enclave must appear on the minimap). Read-only evidence for the minimap patch.
 *
 * The Ferox import (tx-20260905-052852) dropped the modern map-scene (OSRS op68) and map-area/icon (op82) of every
 * imported loc, and the 667 client draws minimap sprites from LocType op102 (msi) and icons from op107 (mapelement).
 * OSRS and 667 number those tables differently, so values must come from 667 locs that show the same thing.
 * This prints, for every Ferox loc in `RSPS_IMPORT_ASSET_MAP.yml`, its OSRS mapScene/mapArea and current local
 * msi/mapelement; for reference names, the (msi, mapelement) pairs 667 locs of that name use; and every mapelement
 * value in the 667 cache with the loc names that carry it. Writes nothing to any cache.
 *
 * Usage: `./gradlew :game:runFeroxMapDataProbeTool --args="[outFile]"`
 */
object FeroxMapDataProbeTool {
    const val ASSET_MAP = "C:/RSPS/RSPS_IMPORT_ASSET_MAP.yml"
    const val IMPORT_LOG = "C:/RSPS/import-source/ferox/ferox_apply_tx-20260905-052852.txt"
    val REFERENCE_NAMES =
        listOf("bank booth", "bank chest", "altar", "staircase", "stairs", "tree", "bush", "flowers", "pool", "fountain", "ladder", "portal", "barrier", "roots")

    data class Entry(val upstream: Int, val local: Int, val mode: String)

    fun feroxEntries(file: File = File(ASSET_MAP)): List<Entry> {
        val section = file.readText().substringAfter("name: Ferox Enclave world import").substringBefore("\n  - name:")
        return Regex("""upstream_id: (\d+), local_id: (\d+), mode: (\w+)""").findAll(section)
            .map { Entry(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3]) }.toList()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = args.getOrNull(0)?.let { PrintStream(File(it)) } ?: System.out
        val entries = feroxEntries()
        val (modernDefs, modernOverlays) = ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { it.files(2, 6) to it.files(2, 4) }
        // Floors: the import mapped each modern overlay (definition id = tile value - 1) by main colour only; print what
        // the minimap actually uses (secondary colour / texture / hideUnderlay) for every overlay the import logged.
        Regex("""FLOOR overlay modern=(\d+) rgb=(\w+) tex=(-?\d+) -> local=(\d+)""").findAll(File(IMPORT_LOG).readText()).forEach { m ->
            val id = m.groupValues[1].toInt()
            val def = modernOverlays[id]?.let { ModernFloorDefs.decodeOverlay(it) }
            out.println(
                "MODERN_OVERLAY id=$id rgb=${def?.rgb?.let { "%06x".format(it) }} texture=${def?.texture} " +
                    "secondary=${def?.secondaryRgb?.let { if (it == -1) "-" else "%06x".format(it) }} hideUnderlay=${def?.hideUnderlay} importedAs=${m.groupValues[4]}",
            )
        }
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val local = Rev667RegionProbeTool.locTypes(library)
            out.println("FEROX_LOCS ${entries.size}")
            entries.forEach { e ->
                val modern = modernDefs[e.upstream]?.let { ModernObjectDef.decode(e.upstream, it) }
                val loc = local[e.local]
                out.println(
                    "FEROX local=${e.local} upstream=${e.upstream} mode=${e.mode} name='${modern?.name}' " +
                        "osrsMapScene=${modern?.mapSceneId} osrsMapArea=${modern?.mapAreaId} localMsi=${loc?.msi} localMapElement=${loc?.mapElement}",
                )
            }
            val feroxIds = entries.map { it.local }.toSet()
            val byName = local.values.filter { it.id !in feroxIds }.groupBy { it.name.lowercase() }
            REFERENCE_NAMES.forEach { key ->
                val matches = byName.filterKeys { it == key || it.contains(key) }.values.flatten()
                val pairs = matches.groupBy { it.msi to it.mapElement }.entries.sortedByDescending { it.value.size }.take(8)
                out.println(
                    "REF '$key' types=${matches.size} " +
                        pairs.joinToString("; ") { (k, v) -> "msi=${k.first} mapElement=${k.second} x${v.size} e.g. ${v.take(3).map { "${it.id}:'${it.name}'" }}" },
                )
            }
            local.values.filter { it.mapElement >= 0 }.groupBy { it.mapElement }.entries.sortedBy { it.key }.forEach { (element, types) ->
                val names = types.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(4).map { "${it.key}(${it.value})" }
                out.println("MAPELEMENT $element x${types.size} names=$names")
            }
        } finally {
            library.close()
            if (out !== System.out) out.close()
        }
    }
}
