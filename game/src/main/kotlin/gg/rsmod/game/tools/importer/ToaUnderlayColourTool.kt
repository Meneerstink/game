package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N606 12-11 (owner, Zebak: 'de omgeving heeft niet echt een afscheiding met de rest van de room ... graphical glitch'):
 * toa-map-hd (N601) maps every OSRS underlay to the NEAREST COLOUR among 16 pyramid HD underlays (sand/rock). For four source
 * underlays no candidate is near, and the colour - and with it the room's meaning - is lost (census 12-11 22:4x,
 * work/claude-scripts/toa-patch/toa-underlay-census.txt, colour distance >2000, all others <=1101):
 *  - OSRS 133 #077156 (Zebak moat water, 874 tiles in 15700) -> became #404040 grey rock: the platform no longer stands in water;
 *  - OSRS 57 #eeeeee (314 tiles in 14672) -> #d0c074 sand; OSRS 117 #752222 (15698) -> #3c2823; OSRS 123 #b8b098 (14164) -> #d0c074.
 * Each source colour exists EXACTLY in our 667 cache (117 and 123 even with their HD texture). Retarget only those tiles'
 * underlay; every height, overlay, shape, flag and environment byte stays as the later ToA floor tools left it.
 * Usage: plan|apply
 */
object ToaUnderlayColourTool {
    /** OSRS zero-based underlay -> (local zero-based underlay now written by toa-map-hd, local underlay with the exact OSRS colour). */
    val FIXES: Map<Int, Pair<Int, Int>> = mapOf(133 to (144 to 82), 57 to (61 to 135), 117 to (140 to 117), 123 to (61 to 123),
        // 12-11 23:4x ronde 2 (fotoboek Zebak: platform GRIJS i.p.v. zand): nearest TEXTURED local underlay over the WHOLE cache instead of
        // the 16 pyramid candidates (an agent's choice, not the owner's) - keeps N601 '667-HD' and gets the OSRS colour back:
        109 to (54 to 134), 4 to (55 to 4), 151 to (54 to 104),
        132 to (0 to 161), 131 to (102 to 163))

    /**
     * N609 13-11 (eigenaar 'er gaat nog veel mis grafisch'; screens/N609/baba-compare.png): round 2 sent OSRS sand 155/77 (#d9bf93/#d9bb93,
     * Ba-Ba square 15188, 696 tiles) to local 77 - right colour, but its HD texture 58 draws a white/grey CHECKERBOARD where OSRS (Kaoz
     * Entry guide 1380/1440 s) shows plain sand. A new underlay with OSRS colour + sand texture is impossible: the rev-667 map stores an
     * underlay as one opcode 81+id (max 174) and all 174 ids exist. First try 61 (pyramid sand, texture 128, #d0c074): no checkerboard
     * but on screen hue ~55 deg and too light vs OSRS sand hue ~36 deg, V 170 (Kaoz 1380, screens/N609/baba-sand-before-after.png). So the
     * textured local underlay with the OSRS hue: 101 (#b89b67, hue 37 deg, texture 301; darker base offsets our brighter tile lighting).
     * OSRS underlay -> (locals it may hold now: round 2 77 / first try 61, local wanted).
     */
    val TEXTURED: Map<Int, Pair<Set<Int>, Int>> = mapOf(155 to (setOf(77, 61) to 101), 77 to (setOf(77, 61) to 101),
        // N609 17:0x Apmeken puzzle: OSRS 116 (#665f47, the darker sand patches) went to 129 (texture 596 = COBBLESTONES, OSRS: smooth darker
        // sand, Kaoz 1180 s). ToaUnderlayTrialTool stripes 129/134/96/91/150 (screens/N609/apmeken-trial-stripes.png): 150 blue slate,
        // 91 pebbles, 96/134 smooth; 134 (#766b51, texture 300) on screen H/S/V 19/101/80 vs OSRS dark patches 19/85/72 -> 134.
        // Squares 14162, 15186, 15698. 'from' includes the trial candidates.
        116 to (setOf(129, 134, 96, 91, 150) to 134),
        // N609 17:2x Kephri arena: OSRS 89 (#85665f) went to 104 (texture 681 = dark grey DOTS; OSRS: plain light pinkish grey, Kaoz 870 s,
        // screen H/S/V 11/77/130). Trial stripes 104/73/134/105 (screens/N609/kephri-trial.png): 104 2/39/97, 73 4/61/140, 134 19/69/127,
        // 105 18/61/153 -> 73 (#995566, texture 607) keeps the pink tone. Squares 14164 (+ wherever OSRS 89 sat on 54/104).
        89 to (setOf(54, 104, 73, 134, 105) to 73))

    /**
     * N609 17:5x: the same OSRS underlay can need a different local per room - OSRS blends underlay colours with their neighbours, the 667
     * HD texture does not. OSRS 116 is darker sand next to sand (Apmeken: 134 measured right) but dark olive swamp next to OSRS 132 and
     * water in Zebak (Kaoz 580-700 s). (OSRS square, OSRS underlay) -> (locals it may hold, local wanted); wins over [TEXTURED].
     */
    val TEXTURED_PER_SQUARE: Map<Pair<Int, Int>, Pair<Set<Int>, Int>> = mapOf(
        // Zebak bank (plane 1 above bridge-flagged water, 15700): trial stripes 134/129/161/0/97 (cam-crondis-room-1759) -> 161 H/S/V
        // 32/109/91 and 0 31/110/83 match OSRS dark olive 35/124/41 in hue; 161 = the local of the neighbouring OSRS 132 swamp it blends into.
        (15700 to 116) to (setOf(129, 134, 161, 0, 97) to 161),
        // N609 14-11 15:0x (eigenaar 'ik zie groene ondergrond waarom'; photobook-1504 zebak-room-a vs Kaoz 668 s): OSRS 108 (#958b67) in the
        // Zebak arena stayed local 125 (sand #a3964b) = a pale tan SQUARE mid-arena; OSRS blends it into the dark swamp like 116 above
        // (13-11 note 'rechtsboven nog tan = OSRS 108 -> 125') -> the same local 161.
        (15700 to 108) to (setOf(125, 161) to 161),
        // N609 14-11 ToA LOBBY (13454): OSRS 116 sand (#665f47) was local 55 (#4d4d4d + texture 497 = grey ROCK, 988 tiles) - Kaoz 60 s shows
        // sand + navy floor. Trial stripes 134/161/101/55 (screens/N609/lobby-trial-0419) -> 134, the same local as the raid squares.
        (13454 to 116) to (setOf(55, 134, 161, 101) to 134),
        // N609 14-11 NEXUS (14160): OSRS 104 navy #212a3e (360 tiles) was local 56 (#2e2e2e grey rock) and OSRS 103 #343b4b (137) local 144
        // (#404040) - big grey patches where Kaoz 1090 s shows a dark navy floor. Trial 166/94/154/56 (screens/N609/nexus104in-0511): 94 too
        // light blue, 154/56 grey; 166/172 are the EXACT OSRS colours (plain, as OSRS draws underlays) -> 166 and 172.
        (14160 to 104) to (setOf(56, 166, 94, 154) to 166),
        (14160 to 103) to (setOf(144, 172, 154, 94) to 172))

    internal fun retargetTextured(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>, square: Int = -1): Pair<ByteArray, Map<Int, Int>> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        val counts = sortedMapOf<Int, Int>()
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val osrs = source[p][x][z].underlayId - 1
            val (from, want) = TEXTURED_PER_SQUARE[square to osrs] ?: TEXTURED[osrs] ?: continue
            val tile = map.tiles[p][x][z]
            val current = (tile.underlayId and 255) - 1
            if (current == want) continue // already applied
            check(current in from) { "Unexpected underlay $current (want $from) at $p/$x/$z for OSRS $osrs" }
            tile.underlayId = want + 1
            counts.merge(osrs, 1, Int::plus)
        }
        return Rev667TileCodec.encode(map) to counts
    }
    val TARGETS = ToaUpperTileFlagTool.targets("raid") + ToaUpperTileFlagTool.targets("lobby") // N609 14-11: + lobby 13454

    internal fun retarget(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>): Pair<ByteArray, Map<Int, Int>> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        val counts = sortedMapOf<Int, Int>()
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val src = source[p][x][z]
            val fix = FIXES[src.underlayId - 1] ?: continue
            val tile = map.tiles[p][x][z]
            val current = (tile.underlayId and 255) - 1
            if (current == fix.second) continue // already applied
            check(current == fix.first) { "Unexpected underlay $current (want ${fix.first}) at $p/$x/$z for OSRS ${src.underlayId - 1}" }
            tile.underlayId = fix.second + 1
            counts.merge(src.underlayId - 1, 1, Int::plus)
        }
        return Rev667TileCodec.encode(map) to counts
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply" }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val game = CacheLibrary(paths[0])
            val files = CacheLibrary(paths[1])
            try {
                for ((osrs, fix) in FIXES) {
                    val want = ModernFloorDefs.decodeUnderlay(requireNotNull(reader.file(2, 1, osrs))).rgb
                    for (cache in listOf(game, files)) {
                        val local = Rev667FloorCodec.decodeUnderlay(requireNotNull(cache.data(2, 1, fix.second)))
                        val dist = Rev667FloorCodec.colourDistance(local.rgb, want)
                        check(dist <= 400) { "Local underlay ${fix.second} rgb %06x too far from OSRS $osrs %06x (d=$dist)".format(local.rgb, want) }
                    }
                }
                for ((square, name) in TARGETS) {
                    val source = ModernRegion.decodeTiles(requireNotNull(reader.file(5, square, 0)))
                    val archive = requireNotNull(game.index(5).archive(name)) { "Missing $name" }
                    val other = requireNotNull(files.index(5).archive(name)) { "Fileserver missing $name" }
                    val before = requireNotNull(archive.file(0)?.data)
                    check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                    // N609 14-11: the round-2 FIXES were measured in the raid squares only - the lobby gets just its per-square TEXTURED rule.
                    val (mid, counts1) = if (square in ToaUpperTileFlagTool.targets("raid")) retarget(before, source) else before to emptyMap()
                    val (after, counts2) = retargetTextured(mid, source, square)
                    val counts = counts1 + counts2
                    println("UNDERLAY_COLOUR source=$square target=$name retargeted=$counts1 textured=$counts2")
                    if (!before.contentEquals(after)) mutations += CacheMutation(5, archive.id, 0, after,
                        "ToA $name: exact-colour underlays for OSRS ${counts.keys}", CacheItemProbeTool.sha1(before))
                }
            } finally { game.close(); files.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} maps=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("UNDERLAY_COLOUR transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
