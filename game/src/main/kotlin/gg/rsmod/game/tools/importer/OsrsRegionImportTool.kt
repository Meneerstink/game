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
        /**
         * N462 ToA (2026-11-06): write every square this many squares (dx, dz) away from its OSRS position - a whole block moves by one
         * offset, so squares keep their OSRS neighbours and every coordinate inside stays OSRS + (64*dx, 64*dz). For OSRS squares whose
         * own position is taken by rev-667 content in use (the ToA raid rooms sit on rev-667 dungeons). Null = OSRS position.
         */
        val squareOffset: Pair<Int, Int>? = null,
        /**
         * N462 ToA (2026-11-06): an untextured OSRS overlay only maps onto an untextured rev-667 overlay (the nearest-colour match
         * ignores textures and gave the ToA lobby's plain stone floors rev-667 textures 3/17/508/514/598...). Textured OSRS overlays keep
         * the normal match. Off for the older batches so their re-runs stay byte-equal.
         */
        val plainOverlaysStayPlain: Boolean = false,
        /** Squares of a [squareOffset] batch that stay on their OSRS position (the ToA lobby next to the moved raid block). */
        val keepInPlace: Set<Int> = emptySet(),
        /**
         * N462 ToA: rev-667 overlay ids (a tile stores the overlay in ONE byte, so ids stop at 254) that get plain overlay definitions
         * with the exact OSRS colour - given to the OSRS overlays whose best plain rev-667 match is worst, weighted by the number of tiles
         * that use them. Every other OSRS overlay maps onto the best plain one (old or new). A slot is either free, or an existing
         * definition that NO tile of any map square outside this batch's own target squares uses (checked over every square at plan
         * time; the replace is pinned on its current bytes).
         */
        val newOverlaySlots: List<Int>? = null,
        /**
         * Second pass of a [squareOffset] batch that already exists: rewrite only the tile files of the target squares (+ the overlay
         * definitions of [newOverlaySlots]); loc files, loc types and models stay as the first pass wrote them.
         */
        val retileTargets: Boolean = false,
        /**
         * N462 ToA: rev-667 underlay ids (tile value = opcode - 81, so definitions stop at 173) that get PLAIN underlay definitions with the
         * exact OSRS colour. Every rev-667 underlay carries a texture (opcode 2) while OSRS underlays are plain colour - the ToA floors
         * came out mottled and dark. Given to the OSRS underlays with the most tiles; the rest maps onto the nearest of those plain ones
         * (never onto a textured rev-667 one). Same free/unused proof as [newOverlaySlots].
         */
        val newUnderlaySlots: List<Int>? = null,
    ) {
        init {
            require(squareOffset == null || (replaceSquares.isEmpty() && patch == null && locsOnlyWithout == null && !tilesOnly)) {
                "squareOffset only creates new squares"
            }
        }

        /** Where OSRS square [square] is written. */
        fun target(square: Int): Int =
            squareOffset?.takeIf { square !in keepInPlace }?.let { (dx, dz) -> (((square ushr 8) + dx) shl 8) or ((square and 0xFF) + dz) } ?: square

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
            // N462 ToA map (owner 2026-11-06 17:5x 'kaart importeren'; ChatGPT-A N462-notities\toa\40-*.tsv, cache 2499 squares byte-equal to
            // 2686): the raid block - nexus 14160, Scabaras 14162/Kephri 14164, Het 14674/Akkha 14676, Apmeken 15186/Ba-Ba 15188,
            // Crondis 15698/Zebak 15700, Wardens 15184/15696, rewards 14672 (OSRS squares 55..61 x 80..84). Five of them are rev-667
            // dungeons in use, so the whole block moves +9,+24 squares to the free bank 64..70 x 104..108 (border 63..71 x 103..109 free in
            // both caches, no code or spawns there). Lessons N522/N523: OSRS tile flag 0x8 = 'do not draw' in rev 667, hidden overlays out.
            "toa-map" to Batch(listOf(13454, 14160, 14162, 14164, 14672, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700), emptyList(),
                "Tombs of Amascut lobby (in place) + raid rooms (moved +9,+24)", clearTileFlags = 0x8, dropHiddenOverlays = true, squareOffset = 9 to 24,
                keepInPlace = setOf(13454), plainOverlaysStayPlain = true, newOverlaySlots = (245..254).toList()),
            // Second pass (owner 'kwaliteitswerk'): 10 slots left ~1067 of 4275 overlay tiles visibly off (testclient: grey-brown sand in the
            // Zebak room). 19 rev-667 overlay definitions are used by NO tile of any of our 2267 map squares (census 2026-11-06): 16 17 23 27
            // 31 40 45 46 66 127 129 147 157 174 175 181 183 226 237 - reused (the tool re-checks that), so 29 exact OSRS colours in total.
            "toa-map-floors" to Batch(listOf(13454, 14160, 14162, 14164, 14672, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700), emptyList(),
                "Tombs of Amascut tiles, 29 exact overlays", clearTileFlags = 0x8, dropHiddenOverlays = true, squareOffset = 9 to 24,
                keepInPlace = setOf(13454), plainOverlaysStayPlain = true, retileTargets = true,
                newOverlaySlots = listOf(16, 17, 23, 27, 31, 40, 45, 46, 66, 127, 129, 147, 157, 174, 175, 181, 183, 226, 237) + (245..254),
                // underlay census 2026-11-06: definitions no tile of another square uses (2 39 40 42 82 131 132 135 141 151 152 153) + the free
                // 166..173 (a tile stores underlay = opcode - 81, so 173 is the last one)
                newUnderlaySlots = listOf(2, 39, 40, 42, 82, 131, 132, 135, 141, 151, 152, 153) + (166..173)),
            "toa-raid" to Batch(listOf(14160, 14162, 14164, 14672, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700), emptyList(),
                "Tombs of Amascut raid rooms", clearTileFlags = 0x8, dropHiddenOverlays = true, squareOffset = 9 to 24, plainOverlaysStayPlain = true),
            // The ToA lobby (Osmumten's tomb entrance hall, Grouping Obelisk 46068, Invocation Board 46073): OSRS 13454 = 52_142, absent in
            // both rev-667 caches - same coordinates.
            "toa-lobby" to Batch(listOf(13454), emptyList(), "Tombs of Amascut lobby", clearTileFlags = 0x8, dropHiddenOverlays = true, plainOverlaysStayPlain = true),
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
        if (!batch.retileTargets) batch.squares.filterNot { it in batch.replaceSquares }.forEach { zeroXteaKey(batch.target(it)) }
        if (!batch.tilesOnly && batch.locsOnlyWithout == null && !batch.retileTargets) appendAssetMap(name, batch, plan, tx.id)
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
                    if (batch.retileTargets) {
                        val t = batch.target(square)
                        check(maps.archive(mapName(t)) != null) { "${mapName(t)} missing; retileTargets only rewrites squares the first pass wrote" }
                    } else if (square in batch.replaceSquares) {
                        check(maps.archive(mapName(square)) != null) { "${mapName(square)} missing; replaceSquares only overwrites existing squares" }
                    } else {
                        val t = batch.target(square)
                        check(maps.archive(mapName(t)) == null && maps.archive(locName(t)) == null) {
                            "${mapName(t)} / ${locName(t)} already exist; this tool only fills empty squares"
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
                val overlayUse = HashMap<Int, Int>()
                val overlayMap = HashMap<Int, Int>()
                /** New plain underlays (local id -> rgb) written by this batch ([Batch.newUnderlaySlots]); OSRS tile value -> local id. */
                val newUnderlays = LinkedHashMap<Int, Int>()
                val forcedUnderlay = HashMap<Int, Int>()
                fun mapUnderlay(tileValue: Int): Int =
                    underlayMap.getOrPut(tileValue) {
                        forcedUnderlay[tileValue]?.let { return@getOrPut it + 1 }
                        val rgb = modernUnderlays[tileValue - 1]?.rgb ?: error("modern underlay ${tileValue - 1} missing")
                        val candidates = if (batch.newUnderlaySlots != null) newUnderlays else localUnderlays
                        candidates.minByOrNull { Rev667FloorCodec.colourDistance(it.value, rgb) }!!.key + 1
                    }
                /** New plain overlays (local id -> def) written by this batch ([Batch.newOverlaySlots]); OSRS tile value -> local id. */
                val newOverlays = LinkedHashMap<Int, Rev667OverlayDef>()
                val forcedOverlay = HashMap<Int, Int>()
                val reusedSlots = batch.newOverlaySlots.orEmpty().toSet()
                fun overlayCandidates(def: ModernOverlayDef): Map<Int, Rev667OverlayDef> {
                    // a slot being (re)written must not be matched with its old definition
                    val all = localOverlays.filterKeys { it !in reusedSlots } + newOverlays
                    return if (batch.plainOverlaysStayPlain && def.texture == -1) all.filterValues { it.texture == -1 }.ifEmpty { all } else all
                }
                fun mapOverlay(tileValue: Int): Int =
                    overlayMap.getOrPut(tileValue) {
                        batch.overlayOverrides[tileValue - 1]?.let { return@getOrPut it + 1 }
                        forcedOverlay[tileValue]?.let { return@getOrPut it + 1 }
                        val def = modernOverlays[tileValue - 1] ?: error("modern overlay ${tileValue - 1} missing")
                        FeroxImportTool.bestOverlay(def, overlayCandidates(def)) + 1
                    }

                // ---- loc types: every one imported new ----
                val regions = batch.squares.associateWith { ModernRegion.load(modern, it, keys[it] ?: error("No modern key for $it")) }
                batch.newUnderlaySlots?.let { slots ->
                    val underlayIndex = library.index(Rev667RegionProbeTool.CONFIG_INDEX).archive(Rev667RegionProbeTool.UNDERLAY_GROUP)!!
                    slots.forEach { check(it in 0..173) { "underlay $it is not a tile value (opcode - 81 <= 174)" } }
                    val ownTargets = batch.squares.map { mapName(batch.target(it)) }.toSet()
                    val tileNames = HashMap<Int, String>()
                    for (x in 0 until 256) for (z in 0 until 256) "m${x}_$z".let { tileNames[it.hashCode()] = it }
                    val usedElsewhere = HashMap<Int, String>()
                    var scanned = 0
                    maps.archives().forEach { archive ->
                        val n = tileNames[archive.hashName] ?: return@forEach
                        if (n in ownTargets) return@forEach
                        val data = maps.archive(archive.id)?.file(0)?.data ?: error("tile file $n unreadable - cannot prove the underlay slots free")
                        val decoded = Rev667TileCodec.decode(data)
                        scanned++
                        for (pl in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
                            val v = decoded.tiles[pl][x][z].underlayId
                            if (v != 0 && (v - 1) in slots) usedElsewhere.putIfAbsent(v - 1, n)
                        }
                    }
                    slots.forEach { slot -> check(underlayIndex.file(slot) == null || slot !in usedElsewhere) { "underlay $slot is used by ${usedElsewhere[slot]} - not free to reuse" } }
                    println("UNDERLAY_SLOTS scanned $scanned other tile files; ${slots.size} slots: free=${slots.filter { underlayIndex.file(it) == null }} reused-unused=${slots.filter { underlayIndex.file(it) != null }}")
                    val uses = HashMap<Int, Int>()
                    regions.forEach { (square, region) ->
                        for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                            if (!batch.inPatch(square, lx, lz) || (batch.plane0Only && plane > 0) || plane + batch.planeShift > 3) continue
                            val v = region.tiles[plane][lx][lz].underlayId
                            if (v != 0) uses.merge(v, 1, Int::plus)
                        }
                    }
                    // Greedy coverage: each slot goes to the OSRS underlay that lowers sum(tiles x squared distance to the nearest chosen colour)
                    // the most - so one slot is not spent on a colour next to another chosen one while a distinct floor colour stays off.
                    val rgbOf = uses.keys.associateWith { modernUnderlays[it - 1]?.rgb ?: error("modern underlay ${it - 1} missing") }
                    val chosen = ArrayList<Int>()
                    fun cost(set: List<Int>): Long = uses.entries.sumOf { (v, n) ->
                        n.toLong() * (set.minOfOrNull { Rev667FloorCodec.colourDistance(rgbOf.getValue(it), rgbOf.getValue(v)) } ?: 3 * 255 * 255).toLong()
                    }
                    repeat(minOf(slots.size, uses.size)) {
                        chosen += uses.keys.filter { it !in chosen }.minByOrNull { cost(chosen + it) }!!
                    }
                    println("UNDERLAY_COVERAGE remaining error (tiles x squared distance) = ${cost(chosen)}")
                    chosen.map { v -> uses.entries.first { it.key == v } }.zip(slots).forEach { (use, slot) ->
                        val rgb = modernUnderlays[use.key - 1]?.rgb ?: error("modern underlay ${use.key - 1} missing")
                        newUnderlays[slot] = rgb
                        forcedUnderlay[use.key] = slot
                        val bytes = byteArrayOf(1, ((rgb shr 16) and 0xFF).toByte(), ((rgb shr 8) and 0xFF).toByte(), (rgb and 0xFF).toByte(), 0)
                        val back = Rev667FloorCodec.decodeUnderlay(bytes)
                        check(back.rgb == rgb && back.texture == -1) { "underlay $slot encode/decode mismatch" }
                        val current = underlayIndex.file(slot)?.data
                        mutations += CacheMutation(Rev667RegionProbeTool.CONFIG_INDEX, Rev667RegionProbeTool.UNDERLAY_GROUP, slot, bytes, "$name underlay $slot = OSRS underlay ${use.key - 1} rgb ${Integer.toHexString(rgb)} plain (${use.value} tiles)",
                            expectedCurrentSha1 = current?.takeIf { !it.contentEquals(bytes) }?.let { CacheItemProbeTool.sha1(it) })
                        println("NEW_UNDERLAY $slot <- OSRS ${use.key - 1} rgb=${Integer.toHexString(rgb)} tiles=${use.value}")
                    }
                    println("UNDERLAYS ${uses.size} used; ${minOf(uses.size, slots.size)} exact, ${maxOf(0, uses.size - slots.size)} to the nearest plain one")
                }
                batch.newOverlaySlots?.let { slots ->
                    val overlayIndex = library.index(Rev667RegionProbeTool.CONFIG_INDEX).archive(Rev667RegionProbeTool.OVERLAY_GROUP)!!
                    slots.forEach { check(it in 0..254) { "overlay $it is not a one-byte tile value" } }
                    // Every tile of every map square outside this batch's targets: an existing slot may only be rewritten when none uses it.
                    val ownTargets = batch.squares.map { mapName(batch.target(it)) }.toSet()
                    // group names are stored as hashes (String.hashCode): every possible tile-file name m<x>_<z>
                    val tileNames = HashMap<Int, String>()
                    for (x in 0 until 256) for (z in 0 until 256) "m${x}_$z".let { tileNames[it.hashCode()] = it }
                    val usedElsewhere = HashMap<Int, String>()
                    var scanned = 0
                    maps.archives().forEach { archive ->
                        val n = tileNames[archive.hashName] ?: return@forEach
                        if (n in ownTargets) return@forEach
                        val data = maps.archive(archive.id)?.file(0)?.data ?: error("tile file $n unreadable - cannot prove the overlay slots free")
                        val decoded = Rev667TileCodec.decode(data)
                        scanned++
                        for (pl in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
                            val v = decoded.tiles[pl][x][z].overlayId and 0xFF
                            if (v != 0 && (v - 1) in slots) usedElsewhere.putIfAbsent(v - 1, n)
                        }
                    }
                    slots.forEach { slot -> check(overlayIndex.file(slot) == null || slot !in usedElsewhere) { "overlay $slot is used by ${usedElsewhere[slot]} - not free to reuse" } }
                    println("OVERLAY_SLOTS scanned $scanned other tile files; ${slots.size} slots: free=${slots.filter { overlayIndex.file(it) == null }} reused-unused=${slots.filter { overlayIndex.file(it) != null }}")
                    // tiles per OSRS overlay that will be drawn (same filter as the tile loop below)
                    val uses = HashMap<Int, Int>()
                    regions.forEach { (square, region) ->
                        for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                            if (!batch.inPatch(square, lx, lz) || (batch.plane0Only && plane > 0) || plane + batch.planeShift > 3) continue
                            val v = region.tiles[plane][lx][lz].overlayId
                            if (v == 0) continue
                            val hidden = batch.dropHiddenOverlays && modernOverlays[v - 1]?.let { it.rgb == 0xFF00FF && it.texture == -1 } == true
                            if (!hidden && (v - 1) !in batch.overlayOverrides) uses.merge(v, 1, Int::plus)
                        }
                    }
                    val ranked = uses.entries.mapNotNull { (v, n) ->
                        val def = modernOverlays[v - 1] ?: return@mapNotNull null
                        if (def.texture != -1) return@mapNotNull null
                        val best = FeroxImportTool.bestOverlay(def, overlayCandidates(def))
                        val distance = Rev667FloorCodec.colourDistance(def.rgb, (localOverlays[best] ?: error("overlay $best")).rgb)
                        Triple(v, n.toLong() * distance, distance)
                    }.filter { it.third > 0 }.sortedByDescending { it.second }
                    ranked.take(slots.count()).zip(slots.toList()).forEach { (pick, slot) ->
                        val def = modernOverlays[pick.first - 1]!!
                        val local = Rev667OverlayDef(def.rgb, -1, def.hideUnderlay, def.secondaryRgb)
                        newOverlays[slot] = local
                        forcedOverlay[pick.first] = slot
                        val out = java.io.ByteArrayOutputStream()
                        out.write(1); out.write((def.rgb shr 16) and 0xFF); out.write((def.rgb shr 8) and 0xFF); out.write(def.rgb and 0xFF)
                        if (!def.hideUnderlay) out.write(5)
                        if (def.secondaryRgb != -1) { out.write(7); out.write((def.secondaryRgb shr 16) and 0xFF); out.write((def.secondaryRgb shr 8) and 0xFF); out.write(def.secondaryRgb and 0xFF) }
                        out.write(11); out.write(0x7F) // blend priority of the plain rev-667 overlays (e.g. 43, 145)
                        out.write(0)
                        val bytes = out.toByteArray()
                        val back = Rev667FloorCodec.decodeOverlay(bytes)
                        check(back.rgb == def.rgb && back.texture == -1 && back.occludes == def.hideUnderlay) { "overlay $slot encode/decode mismatch" }
                        val current = overlayIndex.file(slot)?.data
                        mutations += CacheMutation(Rev667RegionProbeTool.CONFIG_INDEX, Rev667RegionProbeTool.OVERLAY_GROUP, slot, bytes, "$name overlay $slot = OSRS overlay ${pick.first - 1} rgb ${Integer.toHexString(def.rgb)} (${uses[pick.first]} tiles)",
                            expectedCurrentSha1 = current?.takeIf { !it.contentEquals(bytes) }?.let { CacheItemProbeTool.sha1(it) })
                        println("NEW_OVERLAY $slot <- OSRS ${pick.first - 1} rgb=${Integer.toHexString(def.rgb)} tiles=${uses[pick.first]} best-old-distance=${pick.third}")
                    }
                }
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
                // retileTargets: loc types and models exist from the first pass - nothing new to allocate.
                if (!batch.retileTargets) neededModels.forEach { modernId ->
                    check(free.hasNext()) { "Out of proven-free model ids." }
                    val localId = free.next()
                    modelIdMap[modernId] = localId
                    mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localId, 0, OsrsModelConversion.convert(modern, modernId, dropped), "$name model osrs=$modernId -> $localId")
                }

                // Every loc animation must be imported first (OsrsFxImportTool); list them all at once instead of failing on the first.
                val missingSeqs = importedDefs.values.map { it.animationId }.filter { it != -1 && seqs["seq:$it"] == null }.distinct().sorted()
                check(missingSeqs.isEmpty()) { "loc animations not imported yet (add an OsrsFxImportTool SEQ_BATCHES entry): $missingSeqs" }
                if (!batch.retileTargets) importedDefs.forEach { (modernId, def) ->
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
                        if (dst.overlayId != 0) overlayUse.merge(src.overlayId, 1, Int::plus)
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
                    if (batch.retileTargets) {
                        val t = batch.target(square)
                        val mapArchive = maps.archive(mapName(t))!!
                        val current = mapArchive.file(0)!!.data!!
                        // keep the environment records (MapEnvironmentTool) the square has now
                        tiles.trailing = Rev667TileCodec.decode(current).trailing
                        mapGroup = mapArchive.id
                        locGroup = -1
                        val bytes = Rev667TileCodec.encode(tiles)
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, mapGroup, 0, bytes, "$name tiles ${mapName(t)} (OSRS ${mapName(square)}, retile)",
                            current.takeIf { !it.contentEquals(bytes) }?.let { CacheItemProbeTool.sha1(it) })
                    } else if (square in batch.replaceSquares) {
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
                        val t = batch.target(square)
                        mapGroup = nextGroup++
                        locGroup = nextGroup++
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, mapGroup, 0, Rev667TileCodec.encode(tiles), "$name tiles ${mapName(t)} (OSRS ${mapName(square)})", groupName = mapName(t))
                        mutations += CacheMutation(FeroxImportTool.MAP_INDEX, locGroup, 0, locBytes, "$name locs ${locName(t)} (OSRS ${locName(square)}, unencrypted)", groupName = locName(t))
                    }
                    squareGroups[square] = mapGroup to locGroup
                    println("SQUARE $square ${mapName(square)} -> ${mapName(batch.target(square))}=$mapGroup ${locName(batch.target(square))}=$locGroup locs=${locs.size}")
                }
                dropped.distinct().forEach { println("DROPPED $it") }
                underlayMap.forEach { (m, l) -> println("FLOOR underlay osrs=${m - 1} rgb=${Integer.toHexString(modernUnderlays[m - 1]?.rgb ?: -1)} -> local ${l - 1} rgb=${Integer.toHexString(newUnderlays[l - 1] ?: localUnderlays[l - 1] ?: -1)}${if (newUnderlays.containsKey(l - 1)) " NEW plain" else ""}") }
                overlayMap.forEach { (m, l) -> println("FLOOR overlay osrs=${m - 1} tiles=${overlayUse[m] ?: 0} rgb=${Integer.toHexString(modernOverlays[m - 1]?.rgb ?: -1)} tex=${modernOverlays[m - 1]?.texture} -> local ${l - 1} rgb=${Integer.toHexString((newOverlays[l - 1] ?: localOverlays[l - 1])?.rgb ?: -1)} tex=${(newOverlays[l - 1] ?: localOverlays[l - 1])?.texture}${if (newOverlays.containsKey(l - 1)) " NEW" else ""}") }
                println("PLAN $name squares=${batch.squares} types=${importedDefs.size} models=${modelIdMap.size} floors underlay=${underlayMap.size} overlay=${overlayMap.size} mutations=${mutations.size}")
                val planned =
                    when {
                        batch.retileTargets -> mutations.filter { (it.indexId == FeroxImportTool.MAP_INDEX && it.label.contains(" tiles ")) || it.label.startsWith("$name overlay ") || it.label.startsWith("$name underlay ") }
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
        sb.appendLine("  - name: OSRS region import '$name' - ${batch.note.replace(": ", ", ")} (squares ${batch.squares}, ${batch.squareOffset?.let { "moved ${it.first},${it.second} squares -> ${batch.squares.map { s -> batch.target(s) }}" } ?: "same coordinates"})")
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
