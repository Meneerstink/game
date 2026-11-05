package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Imports whole OSRS map squares that are EMPTY in the rev-667 caches onto the same coordinates (N522, owner 2026-11-01: "alle osrs
 * wilderness bosses met hun locations ... ook de osrs rev cave"; "importeren waar mogelijk, geen custom dingen"). Same route as
 * [DeathsOfficeMapImportTool] (floors mapped to the nearest rev-667 floor, every OSRS loc type of the square imported as a NEW loc
 * type with its models converted by [OsrsModelConversion], loc group written unencrypted with a zero XTEA key), except that the
 * square keeps its OSRS position, so server code, spawns and transports use the OSRS Wiki / donor coordinates unchanged.
 *
 * A batch may also import loc types that live OUTSIDE the squares (e.g. the Scorpion Pit "Cavern" entrances on the rev-667 surface);
 * the server places those.
 *
 * Usage: `./gradlew :game:runOsrsRegionImportTool --args="<batch> [--apply]"`
 */
object OsrsRegionImportTool {
    class Batch(
        val squares: List<Int>,
        val extraLocTypes: List<Int> = emptyList(),
        val note: String,
        /**
         * Squares that DO exist in the rev-667 map but hold nothing in use (checked per batch: no code, spawn or transport there);
         * their groups are overwritten in place, the loc file keeps its own XTEA key (CacheTransaction re-encrypts with it).
         */
        val replaceSquares: Set<Int> = emptySet(),
        /**
         * Tile flag bits cleared on import. N522 bundle 4a: OSRS sets 0x8 on whole cave floors (the Escape Caves), but in the rev-667
         * client 0x8 means "do not draw this tile" (Static409 draws a level only where (tileFlags and 0x8) == 0) - the cave, the
         * player and every npc in it stayed black.
         */
        val clearTileFlags: Int = 0,
        /** Rewrite only the tile files of [replaceSquares] (no loc types, models, loc files or asset map entries). */
        val tilesOnly: Boolean = false,
        /** Keep only plane 0 of the tile file (planes 1-3 left empty) - an Escape Caves rendering experiment. */
        val plane0Only: Boolean = false,
        /** Every written tile gets this height (an Escape Caves rendering experiment); null keeps the OSRS heights. */
        val flatHeight: Int? = null,
        /** Leave out the OSRS "hidden" overlays (rgb ff00ff, no texture) - an Escape Caves rendering experiment. */
        val dropHiddenOverlays: Boolean = false,
        /**
         * Escape Caves experiment: rewrite only the loc file of [replaceSquares] from the locs ALREADY in our cache, without the
         * placements of these local loc ids (null = normal import).
         */
        val locsOnlyWithout: Set<Int>? = null,
        /** Every tile and loc moved this many planes up (planes past 3 dropped); with [locsOnlyWithout] both files are rewritten. */
        val planeShift: Int = 0,
        /**
         * Patch only this absolute rectangle (minX, minZ, maxX, maxZ, all planes) of a [replaceSquares] square that is IN USE: the
         * OSRS tiles and the OSRS locs whose origin lies inside replace ours there; every other tile and loc of the square is kept
         * byte for byte. N541: the Edgeville jail, a closed decor block in the rev-667 map.
         */
        val patch: IntArray? = null,
        /**
         * OSRS overlay id -> rev-667 overlay id, ahead of the nearest-colour match. The nearest-colour match ignores textures: OSRS
         * overlay 11 (rgb 0, texture 46) landed on rev-667 overlay 38 (rgb 0, texture 17) and showed lilac (owner 2026-11-04). The low
         * overlay ids are the shared pre-2008 floors (OSRS 9 and rev-667 9 are both rgb 505050), so 11 -> 11 (rgb 665d58, stone).
         */
        val overlayOverrides: Map<Int, Int> = emptyMap(),
        /** OSRS loc types left out of the squares (the server places its own working copy, or the content is not ours). */
        val excludeOsrsLocs: Set<Int> = emptySet(),
    ) {
        fun inPatch(square: Int, lx: Int, lz: Int): Boolean {
            val p = patch ?: return true
            val x = (square ushr 8) * 64 + lx
            val z = (square and 0xFF) * 64 + lz
            return x in p[0]..p[2] && z in p[1]..p[3]
        }
    }

    val BATCHES: Map<String, Batch> =
        mapOf(
            // N522 bundle 2: OSRS Wiki "Scorpia" (cave in the Scorpion Pit, x 3233 y 10341 -> square 12961, empty in the 667 map) and
            // the three surface entrances "Cavern" 26762 (OSRS square 12861: 3231,3936 / 3231,3951 / 3241,3949); the exits "Crevice"
            // 26763 are inside 12961. Transports: Kronos-184 activities/wilderness/Scorpia.java.
            "scorpia-pit" to Batch(listOf(12961), listOf(26762), "Scorpion Pit cave (Scorpia)"),
            // N522 bundle 2: the "Volcanic Forge" 26755 where the odium / malediction shards become wards (OSRS Wiki "Volcanic Forge";
            // OSRS square 13373 places two at 3366,3937 and 3367,3937 rot 2). Only the loc type: the rev-667 surface stays, the
            // server places it (Kronos-184 activities/wilderness/Volcano.java).
            "volcanic-forge" to Batch(emptyList(), listOf(26755), "Volcanic Forge (wards)"),
            // N522 bundle 3: the OSRS Revenant Caves (x 3136-3263, y 10048-10239: 12704/12960 have no OSRS map; Kronos-184 RevCaves.java exits 3194,10055 and
            // 3240,10235, pillars 3180-3241 x 10086-10209) and the surface entrances 31555 (3073,3654) / 31556 (3124,3831).
            // 12957 exists in the rev-667 map but only holds nameless filler (loc 37098 x1467, 39105-39135); nothing references it.
            "rev-caves" to Batch(listOf(12701, 12702, 12703, 12957, 12958, 12959), listOf(31555, 31556), "Revenant Caves", replaceSquares = setOf(12957)),
            // The 2020 exits come out at two surface "Trapdoor"s (OSRS Wiki "Trapdoor (Revenant Caves)": 3103,3655 rot 3 and 3122,3805
            // rot 1 in OSRS squares 12345 / 12347, "it only opens from the inside"); the server places them.
            "rev-caves-trapdoors" to Batch(emptyList(), listOf(31557), "Revenant Caves exit trapdoors"),
            // N522 bundle 3c: the level 26 "Crevice" (OSRS 40386 WILD_CAVE_ENTRANCE_MID, 3067,3740 rot 3 in OSRS square 12090). Imported
            // (tx-20261102-074444 -> loc 62996) but NOT placed: its landing is in no source - 40387 WILD_CAVE_EXIT_MID is the "Blocked
            // opening" at both cavern arrivals (3194,10055 / 3240,10235), not a third exit.
            "rev-caves-crevice" to Batch(emptyList(), listOf(40386), "Revenant Caves level 26 crevice"),
            // N522 bundle 4a: Venenatis' Silk Chasm (OSRS Wiki / Near Reality SilkChasm 3404-3442 x 10183-10226 plane 2 -> square 13727) and
            // the Escape Caves (Near Reality EscapeCavesArea 3327-3392 x 10241-10303 -> square 13472; 13728 has no OSRS map); surface locs: the lair entrance
            // 47077 WILD_VENANATIS_ENTRANCE01 (3321,3794) and the Escape Caves entrances 47175 WILD_CAVE_EXIT01; the webs 47084-47086
            // WBR_VENENATIS_WEB_* are spawned by the fight (RuneLite gameval ObjectID names).
            // N522 bundle 4a: the Escape Caves tile file again, in place, without the OSRS 0x8 flag (see clearTileFlags).
            "escape-caves-fix" to Batch(listOf(13472), emptyList(), "Escape Caves tiles without flag 0x8", replaceSquares = setOf(13472), clearTileFlags = 0x8, tilesOnly = true),
            "escape-caves-original" to Batch(listOf(13472), emptyList(), "Escape Caves tiles exactly as imported (undo the experiments)", replaceSquares = setOf(13472), tilesOnly = true),
            "escape-caves-nohidden" to Batch(listOf(13472), emptyList(), "Escape Caves tiles, plane 0 only, no hidden overlays", replaceSquares = setOf(13472), tilesOnly = true, plane0Only = true, dropHiddenOverlays = true),
            "escape-caves-flat" to Batch(listOf(13472), emptyList(), "Escape Caves tiles, plane 0 only, flat height 30", replaceSquares = setOf(13472), tilesOnly = true, plane0Only = true, flatHeight = 30),
            "escape-caves-noblocks" to Batch(listOf(13472), emptyList(), "Escape Caves locs without the void blocks 63012", replaceSquares = setOf(13472), locsOnlyWithout = setOf(63012)),
            "escape-caves-nolocs" to Batch(listOf(13472), emptyList(), "Escape Caves without any loc", replaceSquares = setOf(13472), locsOnlyWithout = (0..70000).toSet()),
            "escape-caves-up2" to Batch(listOf(13472), emptyList(), "Escape Caves moved 2 planes up (floor on plane 2 like the Silk Chasm)", replaceSquares = setOf(13472), locsOnlyWithout = emptySet(), planeShift = 2),
            "escape-caves-x" to Batch(listOf(13472), emptyList(), "Escape Caves locs experiment (RSPS_ESC_DROP)", replaceSquares = setOf(13472), locsOnlyWithout = (System.getenv("RSPS_ESC_DROP") ?: "").split(",").filter { it.isNotBlank() }.flatMap { r -> r.split("-").let { (it[0].toInt()..it.last().toInt()).toList() } }.toSet()),
            "escape-caves-alllocs" to Batch(listOf(13472), emptyList(), "Escape Caves locs back as imported", replaceSquares = setOf(13472), locsOnlyWithout = emptySet()),
            "escape-caves-plane0" to Batch(listOf(13472), emptyList(), "Escape Caves tiles, plane 0 only", replaceSquares = setOf(13472), tilesOnly = true, plane0Only = true),
            // N522 bundle 4b: Spindel's Web Chasm (Near Reality WebChasm 1610-1651 x 11524-11571 plane 2 -> square 6580) and its surface
            // entrance 47078 WILD_VENENATIS_SINGLES_ENTRANCE01 (Near Reality 3183,3744).
            // N523 step 1 (owner: 'eerst alleen de GEBIEDEN importeren (kaart + ingang, rondlopen)'): Theatre of Blood - Ver Sinhaza 14642 (empty in
            // the 667 map) and the six room squares (12611/13123 have an empty 667 entry, the rest are absent). The Meiyerditch strip of 14386 later.
            "tob-area" to Batch(listOf(14642, 12613, 13125, 13122, 13123, 12612, 12611), emptyList(), "Theatre of Blood area (Ver Sinhaza + rooms)",
                replaceSquares = setOf(13123, 12611)),
            // N523: the room floors (Bloat, Xarpus, Verzik) carry OSRS tile flag 0x8, which the rev-667 client reads as "do not draw" (as in
            // the Escape Caves, bundle 4a) - rewrite only the tile files without it.
            "tob-area-tiles" to Batch(listOf(14642, 12613, 13125, 13122, 13123, 12612, 12611), emptyList(), "Theatre of Blood tiles without flag 0x8",
                replaceSquares = setOf(14642, 12613, 13125, 13122, 13123, 12612, 12611), clearTileFlags = 0x8, tilesOnly = true),
            "web-chasm" to Batch(listOf(6580), listOf(47078), "Web Chasm (Spindel)"),
            // N541 (owner 2026-11-04: Krystilia must be reachable; the jail roof never hid): the Edgeville jail, OSRS Wiki Krystilia
            // "found in Edgeville's jail north-east of the bank" (3109,3516). Our square 12342 holds the bank and home content, so
            // only the jail rectangle is patched (the rev-667 building was a closed block: every tile unwalkable, no roof flag).
            "edgeville-jail-probe" to Batch(listOf(12342), emptyList(), "Edgeville jail (probe, wide)", replaceSquares = setOf(12342), patch = intArrayOf(3102, 3505, 3116, 3522)),
            // The OSRS jail: walls 1902 (planes 0-1) and 1913 (plane 2) on x 3107-3112 / z 3510-3518, open doorway in the east wall at
            // 3112,3514-3515 (no door in OSRS), Krystilia's barred cell 3108-3109 x 3516-3517 (bars 997/1548), rack, corpses, torches 196
            // (TORCH 481, OsrsFxImportTool 'edgeville-jail-seq'). The rectangle also takes the rev-667 block 3106-3112 x 3509-3518 away.
            "edgeville-jail" to Batch(listOf(12342), emptyList(), "Edgeville jail", replaceSquares = setOf(12342), patch = intArrayOf(3106, 3509, 3112, 3518)),
            // Owner 2026-11-04 ('vanbuiten als je t gebouw in kijkt graphical bugg'): the OSRS "hidden" overlays (rgb ff00ff, no texture -
            // not drawn in OSRS) became a lilac floor here. Tiles of the same rectangle again, without them.
            "edgeville-jail-tiles" to Batch(listOf(12342), emptyList(), "Edgeville jail tiles without hidden overlays", replaceSquares = setOf(12342), patch = intArrayOf(3106, 3509, 3112, 3518), tilesOnly = true, dropHiddenOverlays = true, overlayOverrides = mapOf(11 to 11)),
            // N532/N538 (owner 2026-11-02 'wilderness volcano VOLLEDIG WEG', 2026-11-04 'trapdoor overlapt de wilderness volcano'): the
            // rev-667 volcano sits in squares 12345/12346/12601/12602 (hundreds of locs on planes 1-3 that OSRS does not have; 12347 and
            // 12603 match OSRS). The four squares become the OSRS surface; the Revenant Caves caverns / trapdoors and the Web Chasm entrance
            // are server-placed (spawn_obj), so they stay. N522-WILDERNESS-ONDERZOEK 3.1.
            "wilderness-volcano" to Batch(listOf(12345, 12346, 12601, 12602), emptyList(), "Wilderness volcano removed: OSRS surface", replaceSquares = setOf(12345, 12346, 12601, 12602),
                // OSRS 6 (rgb 0, water texture 25) -> our water overlay 6 (rgb 387b61), not the lilac 38. Left out: the Revenant Caves
                // Cavern 31555 and Trapdoor 31557 and the Web Chasm entrance 47078 (the server places its own working copies) and the
                // Vet'ion's Rest entrance 47141 (owner 2026-11-02: the old Vet'ion without a cave).
                overlayOverrides = mapOf(6 to 6), excludeOsrsLocs = setOf(31555, 31557, 47078, 47141)),
            "silk-chasm" to Batch(listOf(13727, 13472), listOf(47077, 47175, 47084, 47085, 47086), "Silk Chasm (Venenatis) and Escape Caves"),
            // N522 (owner 2026-11-04 17:0x: all four Wilderness boss lairs): Callisto's Den 13473 (plane 0), Vet'ion's Rest 13215 (plane 1),
            // Hunter's End 7092 (Artio, plane 0), Skeletal Tomb 7604 (Calvar'ion, plane 1) - N522-WILDERNESS-ONDERZOEK-2 1b.2 (OSRS cache
            // placements, Near Reality WildernessBossEntrance/Exit). Surface entrances placed by the server: 47140 Callisto (3291,3849 rot 3),
            // 46995 Vet'ion "Crevice" (3221,3787 rot 1), 47141 Artio "Cave Entrance" (3115,3676 rot 0); Calvar'ion's 46996 "Memorial" came
            // with the 'wilderness-volcano' squares.
            // The surface around the lair entrances that stand on rev-667 ground (N564, testclient 2026-11-04: "I can't reach that!"):
            // only these rectangles become OSRS, the rest of the squares (Red Dragon Isle, the Demonic Ruins) stays. The entrances
            // themselves are server-placed, so the OSRS copies are left out.
            "lair-surface-callisto" to Batch(listOf(13116), emptyList(), "Callisto's Den entrance surface", replaceSquares = setOf(13116),
                patch = intArrayOf(3285, 3842, 3301, 3858), excludeOsrsLocs = setOf(47140)),
            "lair-surface-vetion" to Batch(listOf(12859), emptyList(), "Vet'ion's Rest entrance surface", replaceSquares = setOf(12859),
                patch = intArrayOf(3214, 3780, 3229, 3796), excludeOsrsLocs = setOf(46995)),
            "lair-surface-escape" to Batch(listOf(12859), emptyList(), "Escape Caves north-west entrance surface", replaceSquares = setOf(12859),
                patch = intArrayOf(3253, 3824, 3263, 3839), excludeOsrsLocs = setOf(47175)),
            // Owner 2026-11-04 21:5x ('Ja, OSRS-grond'): the rev-667 lava field with rock pillars in squares 13114/13115 (x 3264-3327,
            // z 3712-3839) where OSRS has plain Wilderness - the four Escape Caves entrances and the Silk Chasm entrance stood in it
            // (world map: a black block with a lava rim). Both squares become the OSRS surface like the volcano; the entrances are
            // server-placed (WildernessLairs), so the OSRS copies 47077 (Silk Chasm) and 47175 (Escape Caves) are left out.
            "escape-surface" to Batch(listOf(13114, 13115), emptyList(), "Lava field around the Escape Caves entrances: OSRS surface", replaceSquares = setOf(13114, 13115),
                // OSRS overlay 99 (rgb ff00ff, no texture) is "hidden" - not drawn in OSRS; here it would be a lilac floor (N541).
                overlayOverrides = mapOf(6 to 6), excludeOsrsLocs = setOf(47077, 47175), dropHiddenOverlays = true),
            // The rim of that lava field lies in the neighbouring squares (world map 2026-11-04 21:5x: a straight lava strip around the new
            // ground; only unnamed lava-edge scenery 14427/14495/336/... there). 16-tile strips of those squares become OSRS as well.
            "escape-rim-w58" to Batch(listOf(12858), emptyList(), "Escape Caves lava rim west (12858)", replaceSquares = setOf(12858),
                patch = intArrayOf(3248, 3712, 3263, 3775), overlayOverrides = mapOf(6 to 6), dropHiddenOverlays = true),
            "escape-rim-w59" to Batch(listOf(12859), emptyList(), "Escape Caves lava rim west (12859)", replaceSquares = setOf(12859),
                patch = intArrayOf(3248, 3776, 3263, 3839), overlayOverrides = mapOf(6 to 6), excludeOsrsLocs = setOf(47175), dropHiddenOverlays = true),
            "escape-rim-e58" to Batch(listOf(13370), emptyList(), "Escape Caves lava rim east (13370)", replaceSquares = setOf(13370),
                patch = intArrayOf(3328, 3712, 3343, 3775), overlayOverrides = mapOf(6 to 6), dropHiddenOverlays = true),
            "escape-rim-e59" to Batch(listOf(13371), emptyList(), "Escape Caves lava rim east (13371)", replaceSquares = setOf(13371),
                patch = intArrayOf(3328, 3776, 3343, 3839), overlayOverrides = mapOf(6 to 6), dropHiddenOverlays = true),
            "escape-rim-n" to Batch(listOf(13116), emptyList(), "Escape Caves lava rim north (13116)", replaceSquares = setOf(13116),
                patch = intArrayOf(3264, 3840, 3327, 3855), overlayOverrides = mapOf(6 to 6), excludeOsrsLocs = setOf(47140), dropHiddenOverlays = true),
            // N569 (owner 2026-11-05 06:3x 'OSRS-grond importeren'): Larran's small chest stands in the "Ruins" north-east of the Chaos
            // Temple (OSRS 34831 = multiloc 33343/34828 at 3281,3659 rot 1). Our walls match OSRS, but the 667 ground/models differ and a
            // 667 Skeleton 14603 stood on the chest tile; the OSRS ruins rectangle replaces ours. The chest itself is placed by the server.
            // REVERTED (tx-20261105-065305, SquareJournalRestoreTool): the converted OSRS lava ground decoration (14456/14457/14460...)
            // came out as flat yellow/black triangles; the rev-667 square uses the same loc ids as OSRS and stays. Do not re-apply.
            "larran-ruins" to Batch(listOf(13113), emptyList(), "Larran's small chest: OSRS Ruins", replaceSquares = setOf(13113),
                patch = intArrayOf(3266, 3648, 3300, 3676), overlayOverrides = mapOf(6 to 6), excludeOsrsLocs = setOf(34831), dropHiddenOverlays = true),
            // N569: Larran's big chest (OSRS 34832 = multiloc 34829/34830 at 3018,3955 plane 1 rot 2) on the OSRS ship west of the Pirates'
            // Hideout: Ship's ladders 272/273 (3018,3957 planes 0-1) and 245/246 (3017/3019,3959 planes 1-2), a walkable deck. The 667 ship
            // there is scenery with a blocked deck. Chest placed by the server.
            "larran-ship" to Batch(listOf(12093), emptyList(), "Larran's big chest: OSRS ship", replaceSquares = setOf(12093),
                patch = intArrayOf(3009, 3940, 3028, 3967), overlayOverrides = mapOf(6 to 6), excludeOsrsLocs = setOf(34832), dropHiddenOverlays = true),
            // N526 (owner 2026-11-05: 'zoek het verschil met OSRS'): FeroxImportTool took x 3122-3188; west of the west wall (x 3112-3121)
            // OSRS has the two statues 39674, bushes, ruined wall pieces 23849/23851/12278 where the 667 forest still stood.
            "ferox-west" to Batch(listOf(12344), emptyList(), "Ferox Enclave west approach: OSRS surface", replaceSquares = setOf(12344),
                patch = intArrayOf(3112, 3602, 3121, 3647), overlayOverrides = mapOf(6 to 6), dropHiddenOverlays = true),
            "wildy-lairs" to Batch(listOf(13473, 13215, 7092, 7604), listOf(47140, 46995, 47141), "Wilderness boss lairs (Callisto's Den, Vet'ion's Rest, Hunter's End, Skeletal Tomb)"),
            // N568 (A9 2026-11-05): the lairs' OSRS "hidden" overlays (rgb ff00ff, no texture - OSRS does not draw them) came in as pale
            // floors outside the cave walls; the same tiles again without them (tiles only, the locs and the fog record stay).
            "wildy-lairs-nohidden" to Batch(listOf(13473, 13215, 7092, 7604), emptyList(), "Wilderness boss lairs tiles without hidden overlays",
                replaceSquares = setOf(13473, 13215, 7092, 7604), tilesOnly = true, dropHiddenOverlays = true),
        )

    class Plan(
        val mutations: List<CacheMutation>,
        val locIdMap: Map<Int, Int>,
        val modelIdMap: Map<Int, Int>,
        val dropped: List<String>,
        val squareGroups: Map<Int, Pair<Int, Int>>,
    )

    fun mapName(square: Int) = "m${square ushr 8}_${square and 0xFF}"

    fun locName(square: Int) = "l${square ushr 8}_${square and 0xFF}"

    @JvmStatic
    fun main(args: Array<String>) {
        val name = args.getOrNull(0) ?: error("Usage: <batch> [--apply]; batches: ${BATCHES.keys}")
        val batch = BATCHES[name] ?: error("Unknown batch $name; batches: ${BATCHES.keys}")
        val apply = "--apply" in args
        val plan = buildPlan(name, batch)
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), plan.mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${plan.mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (!apply) {
            println("DRY_RUN transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes and names")
        batch.squares.filterNot { it in batch.replaceSquares }.forEach { zeroXteaKey(it) }
        if (!batch.tilesOnly && batch.locsOnlyWithout == null) appendAssetMap(name, batch, plan, tx.id)
    }

    fun buildPlan(
        name: String,
        batch: Batch,
    ): Plan {
        val keys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        val seqs = OsrsFxImportTool.existingFx(File(FeroxImportTool.ASSET_MAP)).filterKeys { it.startsWith("seq:") }
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val maps = library.index(FeroxImportTool.MAP_INDEX)
                val localKeys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
                batch.squares.forEach { square ->
                    if (square in batch.replaceSquares) {
                        check(maps.archive(mapName(square)) != null) { "${mapName(square)} missing; replaceSquares only overwrites existing squares" }
                    } else {
                        check(maps.archive(mapName(square)) == null && maps.archive(locName(square)) == null) {
                            "${mapName(square)} / ${locName(square)} already exist; this tool only fills empty squares"
                        }
                    }
                }
                var nextGroup = (maps.archiveIds().maxOrNull() ?: -1) + 1

                // ---- floors (same mapping as FeroxImportTool / DeathsOfficeMapImportTool) ----
                val modernUnderlays = modern.files(2, 1).mapValues { ModernFloorDefs.decodeUnderlay(it.value) }
                val modernOverlays = modern.files(2, 4).mapValues { ModernFloorDefs.decodeOverlay(it.value) }
                val localUnderlays = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.UNDERLAY_GROUP) { Rev667FloorCodec.decodeUnderlay(it).rgb }
                val localOverlays = FeroxImportTool.decodeLocalFloors(library, Rev667RegionProbeTool.OVERLAY_GROUP) { Rev667FloorCodec.decodeOverlay(it) }
                val underlayMap = HashMap<Int, Int>()
                val overlayMap = HashMap<Int, Int>()
                fun mapUnderlay(tileValue: Int): Int =
                    underlayMap.getOrPut(tileValue) {
                        val rgb = modernUnderlays[tileValue - 1]?.rgb ?: error("modern underlay ${tileValue - 1} missing")
                        localUnderlays.minByOrNull { Rev667FloorCodec.colourDistance(it.value, rgb) }!!.key + 1
                    }
                fun mapOverlay(tileValue: Int): Int =
                    overlayMap.getOrPut(tileValue) {
                        batch.overlayOverrides[tileValue - 1]?.let { return@getOrPut it + 1 }
                        val def = modernOverlays[tileValue - 1] ?: error("modern overlay ${tileValue - 1} missing")
                        FeroxImportTool.bestOverlay(def, localOverlays) + 1
                    }

                // ---- loc types: every one imported new ----
                val regions = batch.squares.associateWith { ModernRegion.load(modern, it, keys[it] ?: error("No modern key for $it")) }
                val modernLocDefs = modern.files(2, 6)
                if (batch.patch != null) {
                    regions.forEach { (square, region) ->
                        region.locs.filter { batch.inPatch(square, it.localX, it.localZ) }.forEach {
                            val def = ModernObjectDef.decode(it.id, modernLocDefs[it.id]!!)
                            println("PATCH_OSRS_LOC ${it.id} '${def.name}' at ${(square ushr 8) * 64 + it.localX},${(square and 0xFF) * 64 + it.localZ},${it.plane} type=${it.type} rot=${it.rotation} size=${def.sizeX}x${def.sizeY} anim=${def.animationId} ops=${def.options.toList()}")
                        }
                    }
                }
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val locIdMap = LinkedHashMap<Int, Int>()
                val neededModels = sortedSetOf<Int>()
                val importedDefs = LinkedHashMap<Int, ModernObjectDef>()
                (regions.flatMap { (sq, r) -> r.locs.filter { batch.inPatch(sq, it.localX, it.localZ) && it.id !in batch.excludeOsrsLocs }.map { it.id } }.distinct().sorted() + batch.extraLocTypes).forEach { id ->
                    if (id in locIdMap) return@forEach
                    val def = ModernObjectDef.decode(id, modernLocDefs[id] ?: error("modern loc $id missing"))
                    locIdMap[id] = nextLocId++
                    importedDefs[id] = def
                    neededModels.addAll(def.models)
                }

                // ---- models ----
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, File(FeroxImportTool.ASSET_MAP))
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                val free = census.provenFreeHoles.filter { it in 1..0xFFFF }.sorted().iterator()
                val modelIdMap = LinkedHashMap<Int, Int>()
                neededModels.forEach { modernId ->
                    check(free.hasNext()) { "Out of proven-free model ids." }
                    val localId = free.next()
                    modelIdMap[modernId] = localId
                    mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localId, 0, OsrsModelConversion.convert(modern, modernId, dropped), "$name model osrs=$modernId -> $localId")
                }

                // Every loc animation must be imported first (OsrsFxImportTool); list them all at once instead of failing on the first.
                val missingSeqs = importedDefs.values.map { it.animationId }.filter { it != -1 && seqs["seq:$it"] == null }.distinct().sorted()
                check(missingSeqs.isEmpty()) { "loc animations not imported yet (add an OsrsFxImportTool SEQ_BATCHES entry): $missingSeqs" }
                importedDefs.forEach { (modernId, def) ->
                    val localId = locIdMap.getValue(modernId)
                    val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped) { seqs["seq:$it"] }
                    val decoded = Rev667LocType.decode(localId, bytes)
                    check(decoded.allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $modernId encode/decode model mismatch" }
                    if (def.animationId != -1) check(decoded.animation == seqs["seq:${def.animationId}"]) { "loc $modernId animation ${def.animationId} not imported" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "$name loc osrs=$modernId '${def.name}' -> $localId")
                    println("LOC osrs=$modernId '${def.name}' ops=${def.options.toList()} size=${def.sizeX}x${def.sizeY} anim=${def.animationId}->${decoded.animation} -> $localId")
                }

                // ---- the squares, on their own OSRS coordinates ----
                val squareGroups = LinkedHashMap<Int, Pair<Int, Int>>()
                regions.forEach { (square, region) ->
                    // Patch mode starts from our own square and only overwrites the rectangle.
                    val ownTiles = if (batch.patch != null) Rev667TileCodec.decode(maps.archive(mapName(square))!!.file(0)!!.data!!) else null
                    val ownLocs = if (batch.patch != null) Rev667LocCodec.decode(maps.archive(locName(square), localKeys[square])!!.file(0)!!.data!!) else null
                    val tiles = ownTiles ?: Rev667TileMap()
                    // N568: a re-import of a replaced square keeps its rev-667 environment records (MapEnvironmentTool's fog record).
                    if (ownTiles == null && square in batch.replaceSquares) {
                        maps.archive(mapName(square))?.file(0)?.data?.let { tiles.trailing = Rev667TileCodec.decode(it).trailing }
                    }
                    if (batch.patch != null) {
                        ownLocs!!.filter { batch.inPatch(square, it.localX, it.localZ) }.forEach {
                            println("PATCH_DROPS_OWN_LOC ${it.id} at ${(square ushr 8) * 64 + it.localX},${(square and 0xFF) * 64 + it.localZ},${it.plane} type=${it.type} rot=${it.rotation}")
                        }
                    }
                    for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                        if (!batch.inPatch(square, lx, lz)) continue
                        if (batch.plane0Only && plane > 0) continue
                        if (plane + batch.planeShift > 3) continue
                        val src = region.tiles[plane][lx][lz]
                        val dst = tiles.tiles[plane + batch.planeShift][lx][lz]
                        dst.height = batch.flatHeight ?: src.height
                        dst.flags = src.settings and batch.clearTileFlags.inv()
                        dst.overlayShape = src.overlayPath
                        dst.overlayRotation = src.overlayRotation
                        val hidden = batch.dropHiddenOverlays && src.overlayId != 0 && modernOverlays[src.overlayId - 1]?.let { it.rgb == 0xFF00FF && it.texture == -1 } == true
                        dst.overlayId = if (src.overlayId != 0 && !hidden) mapOverlay(src.overlayId) else 0
                        dst.underlayId = if (src.underlayId != 0) mapUnderlay(src.underlayId) else 0
                    }
                    val locs =
                        if (batch.locsOnlyWithout != null) {
                            // the loc ids the 'silk-chasm' import gave these OSRS locs (asset map), minus the left-out ones
                            val text = File(FeroxImportTool.ASSET_MAP).readText()
                            val block = text.substringAfter("OSRS region import 'silk-chasm'").substringBefore("    models:")
                            val existing = Regex("upstream_id: (\\d+), local_id: (\\d+)").findAll(block).associate { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
                            region.locs.filter { it.plane + batch.planeShift <= 3 }
                                .map { Rev667Loc(existing.getValue(it.id), it.localX, it.localZ, it.plane + batch.planeShift, it.type, it.rotation) }
                                .filter { it.id !in batch.locsOnlyWithout }.also { println("LOCS_KEPT ${it.size} of ${region.locs.size}") }
                        } else if (ownLocs != null) {
                            ownLocs.filterNot { batch.inPatch(square, it.localX, it.localZ) } +
                                region.locs.filter { batch.inPatch(square, it.localX, it.localZ) && it.id !in batch.excludeOsrsLocs }
                                    .map { Rev667Loc(locIdMap.getValue(it.id), it.localX, it.localZ, it.plane, it.type, it.rotation) }
                        } else {
                            region.locs.filter { it.id !in batch.excludeOsrsLocs }
                                .map { Rev667Loc(locIdMap.getValue(it.id), it.localX, it.localZ, it.plane, it.type, it.rotation) }
                        }
                    val locBytes = Rev667LocCodec.encode(locs)
                    check(Rev667LocCodec.decode(locBytes).size == locs.size) { "loc encode/decode count mismatch" }
                    val mapGroup: Int
                    val locGroup: Int
                    if (square in batch.replaceSquares) {
                        val key = localKeys[square]
                        val mapArchive = maps.archive(mapName(square))!!
                        val locArchive = maps.archive(locName(square), key) ?: error("${locName(square)} missing or unreadable")
                        mapGroup = mapArchive.id
                        locGroup = locArchive.id
                        val oldTiles = mapArchive.file(0)!!.data!!
                        val oldLocs = locArchive.file(0)!!.data!!
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, mapGroup, 0, Rev667TileCodec.encode(tiles), "$name tiles ${mapName(square)} (replaces rev-667 filler)", CacheItemProbeTool.sha1(oldTiles))
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, locGroup, 0, locBytes, "$name locs ${locName(square)} (replaces rev-667 filler, own key)", CacheItemProbeTool.sha1(oldLocs), xtea = key)
                    } else {
                        mapGroup = nextGroup++
                        locGroup = nextGroup++
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, mapGroup, 0, Rev667TileCodec.encode(tiles), "$name tiles ${mapName(square)}", groupName = mapName(square))
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, locGroup, 0, locBytes, "$name locs ${locName(square)} (unencrypted)", groupName = locName(square))
                    }
                    squareGroups[square] = mapGroup to locGroup
                    println("SQUARE $square ${mapName(square)}=$mapGroup ${locName(square)}=$locGroup locs=${locs.size}")
                }
                dropped.distinct().forEach { println("DROPPED $it") }
                overlayMap.forEach { (m, l) -> println("FLOOR overlay osrs=${m - 1} rgb=${Integer.toHexString(modernOverlays[m - 1]?.rgb ?: -1)} tex=${modernOverlays[m - 1]?.texture} -> local ${l - 1} rgb=${Integer.toHexString(localOverlays[l - 1]?.rgb ?: -1)} tex=${localOverlays[l - 1]?.texture}") }
                println("PLAN $name squares=${batch.squares} types=${importedDefs.size} models=${modelIdMap.size} floors underlay=${underlayMap.size} overlay=${overlayMap.size} mutations=${mutations.size}")
                val planned =
                    when {
                        batch.tilesOnly -> mutations.filter { it.indexId == FeroxImportTool.MAP_INDEX && it.label.contains(" tiles ") }
                        batch.locsOnlyWithout != null && batch.planeShift != 0 -> mutations.filter { it.indexId == FeroxImportTool.MAP_INDEX && (it.label.contains(" locs ") || it.label.contains(" tiles ")) }
                        batch.locsOnlyWithout != null -> mutations.filter { it.indexId == FeroxImportTool.MAP_INDEX && it.label.contains(" locs ") }
                        else -> mutations
                    }
                if (batch.tilesOnly) println("TILES_ONLY ${planned.size} tile file(s): ${planned.joinToString { it.label }}")
                return Plan(planned, locIdMap, modelIdMap, dropped.distinct(), squareGroups)
            } finally {
                library.close()
            }
        }
    }

    private fun zeroXteaKey(square: Int) {
        val file = File(FeroxImportTool.XTEAS_FILE)
        val root = JsonParser().parse(file.readText()).asJsonArray
        if (root.none { it.asJsonObject.get("mapsquare").asInt == square }) {
            root.add(JsonObject().apply {
                addProperty("mapsquare", square)
                add("key", JsonArray().apply { repeat(4) { add(0) } })
            })
            file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
            println("XTEAS zero key added for $square")
        }
    }

    private fun appendAssetMap(
        name: String,
        batch: Batch,
        plan: Plan,
        transactionId: String,
    ) {
        val sb = StringBuilder()
        sb.appendLine("  - name: OSRS region import '$name' - ${batch.note.replace(": ", ", ")} (squares ${batch.squares}, same coordinates)")
        sb.appendLine("    transaction_id: $transactionId")
        sb.appendLine("    source: OpenRS2 cache 2499, map squares ${plan.squareGroups.entries.joinToString { "${it.key} -> groups ${it.value.first}/${it.value.second}" }}")
        sb.appendLine("    loc_ids:")
        plan.locIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, mode: IMPORT }") }
        sb.appendLine("    models:")
        plan.modelIdMap.forEach { (modern, local) -> sb.appendLine("      - { upstream_id: $modern, local_id: $local, role: loc_model }") }
        File(FeroxImportTool.ASSET_MAP).appendText(sb.toString())
        println("ASSET_MAP appended ${plan.locIdMap.size} loc and ${plan.modelIdMap.size} model mappings")
    }
}
