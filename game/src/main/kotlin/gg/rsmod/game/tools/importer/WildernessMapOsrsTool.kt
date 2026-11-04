package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N529/N562/N563 (owner 2026-11-04: 'worldmap iconen ook revcave en naam van de boss of cave net als osrs' + 'minimap enzo worldmap zo
 * volledig mogelijk'). The OSRS world map (cache openrs2-2686, index 19 compositemap 0 'Gielinor Surface' and 26 'Wilderness Dungeons',
 * AreaDefinitions index 2 group 35) read with team/dossiers/N529-notities/inventaris.py, reproduced in the 667 world map (index 23):
 *
 * 1. **Wilderness Dungeons layout** (details file [DETAILS_FILE]) exactly like OSRS 19/0/26: the Revenant Caves at their own place
 *    (3136-3263 x 10048-10239, no more +64 shift), the King Black Dragon lair moved to the OSRS display 3088-3135 x 10240-10287 (it
 *    covered the caves), and the OSRS projections we have content for: Callisto's Den, Escape Caves, Vet'ion's Rest (level 1), Silk
 *    Chasm (level 2) and the Scorpion Pit. Hunter's End, Skeletal Tomb and the Web Chasm have no OSRS map (OSRS 19/0 projects no map
 *    on regions 6580/7092/7604) and get none here. Area-snapshot records follow (moved or built from the landscape of their level).
 * 2. **Labels** (OSRS AreaDefinitions 873-878, 139, 371: category 1129, size 0, white) in the 667 small-label style of 'Bandit Camp'
 *    (element 131), at the OSRS positions. 'Wilderness Volcano' (667 element 282; the volcano is gone, OSRS has no label) goes.
 * 3. **Map links** (OSRS area 13 'Open Map' on the surface = 667 sprite-1772 elements, area 14 in the dungeon = 667 sprite-1773
 *    elements) at every OSRS link of content we have. The client runs the OP_MAPELEMENT1 script of the element (group name hash
 *    `10 | element << 10`, ClientScriptList.trigger), a copy of the 667 link script (push coord, gosub 304) aimed at the place the
 *    link leads to in our server (RevenantCaves / ScorpiaPit / WildernessLairs). The old Forinthry links 1043/1044 are re-aimed.
 *
 * Usage: `./gradlew :game:runWildernessMapOsrsTool --args="plan|apply"`
 */
object WildernessMapOsrsTool {
    const val WORLD_MAP_INDEX = 23
    const val CONFIG_INDEX = 2
    const val MAP_ELEMENT_GROUP = 36
    const val SCRIPT_INDEX = 12
    const val DETAILS_FILE = WildernessDungeonsMapTool.DETAILS_FILE
    const val OP_MAPELEMENT1 = 10

    /** Loc ids below this are shared by the 667 and OSRS caches (the OSRS cache forked in 2007); higher ids differ per game. */
    const val SAME_ID_LIMIT = 20000

    const val UP_TEMPLATE = 1044 // 667 surface link: sprite 1772/1774, category 949, op 'Open Map'
    const val DOWN_TEMPLATE = 66 // 667 dungeon link: sprite 1773/1775, category 948
    const val LABEL_TEMPLATE = 131 // 667 'Bandit<br>Camp': size 0, white, category 950
    const val RUINS_ELEMENT = 133
    const val VOLCANO_LABEL = 282
    const val LINK_SCRIPT_TEMPLATE = 3726 // OP_MAPELEMENT1 of element 541: push coord, gosub 304, return

    fun coord(level: Int, x: Int, z: Int) = (level shl 28) or (x shl 14) or z

    class Pos(val level: Int, val x: Int, val z: Int) {
        val packed get() = coord(level, x, z)

        override fun toString() = "$level,$x,$z"
    }

    /** A map link: element, where it is drawn (source tile), where it leads (our server's landing tile), its script group. */
    class Link(val element: Int, val at: Pos, val target: Pos, val script: Int, val up: Boolean, val note: String)

    class Label(val element: Int, val text: String)

    // ---- labels (OSRS 2/35 names) -------------------------------------------------------------------------------------------
    val CALLISTO = Label(1118, "Callisto's<br>Den")
    val SILK = Label(1119, "Silk<br>Chasm")
    val VETION = Label(1120, "Vet'ion's<br>Rest")
    val HUNTER = Label(1121, "Hunter's<br>End")
    val WEB = Label(1122, "Web<br>Chasm")
    val SKELETAL = Label(1123, "Skeletal<br>Tomb")
    val SCORPIONS = Label(1124, "Beware of<br>the scorpions")
    val LABELS = listOf(CALLISTO, SILK, VETION, HUNTER, WEB, SKELETAL, SCORPIONS)

    /** Surface labels: OSRS 19/1/0 display positions (surface: display = source). */
    val SURFACE_LABELS =
        listOf(
            CALLISTO.element to Pos(0, 3293, 3848), SILK.element to Pos(0, 3321, 3793), VETION.element to Pos(0, 3223, 3786),
            HUNTER.element to Pos(0, 3116, 3675), WEB.element to Pos(0, 3184, 3743), SKELETAL.element to Pos(0, 3181, 3682),
            RUINS_ELEMENT to Pos(0, 3164, 3735), // OSRS 'Ruins' in square 49_58 (OSRS surface since tx wilderness-volcano)
        )

    /** Wilderness Dungeons labels: OSRS 19/1/26 display positions = source (the projections are 1:1 there). */
    val DUNGEON_LABELS =
        listOf(
            CALLISTO.element to Pos(0, 3359, 10328), VETION.element to Pos(1, 3295, 10202), SILK.element to Pos(2, 3423, 10203),
            SCORPIONS.element to Pos(0, 3235, 10347),
        )

    // ---- links (OSRS 19/1/0 area 13 and 19/1/26 area 14 positions; targets = our server) ----------------------------------------
    val LINKS =
        listOf(
            // surface -> Wilderness Dungeons
            Link(1044, Pos(0, 3075, 3655), Pos(0, 3197, 10056), 4121, true, "Revenant Caves south cavern (RevenantCaves.ENTRANCES)"),
            Link(1043, Pos(0, 3125, 3832), Pos(0, 3241, 10233), 4120, true, "Revenant Caves north cavern"),
            Link(1125, Pos(0, 3068, 3741), Pos(0, 3187, 10127), 5402, true, "Revenant Caves crevice (level 26)"),
            Link(1126, Pos(0, 3293, 3850), Pos(0, 3359, 10334), 5403, true, "Callisto's Den (WildernessLairs)"),
            Link(1127, Pos(0, 3222, 3788), Pos(1, 3295, 10199), 5404, true, "Vet'ion's Rest"),
            Link(1128, Pos(0, 3321, 3795), Pos(2, 3424, 10212), 5405, true, "Silk Chasm (LAIR_ARRIVAL)"),
            Link(1129, Pos(0, 3232, 3936), Pos(0, 3233, 10332), 5406, true, "Scorpion Pit cavern south (ScorpiaPit)"),
            Link(1130, Pos(0, 3231, 3952), Pos(0, 3232, 10351), 5407, true, "Scorpion Pit cavern north-west"),
            Link(1131, Pos(0, 3244, 3949), Pos(0, 3243, 10351), 5408, true, "Scorpion Pit cavern north-east"),
            Link(1132, Pos(0, 3283, 3775), Pos(0, 3359, 10246), 5409, true, "Escape Caves (WildernessLairs.ESCAPE_ROUTES)"),
            Link(1133, Pos(0, 3285, 3808), Pos(0, 3360, 10273), 5410, true, "Escape Caves"),
            Link(1134, Pos(0, 3321, 3831), Pos(0, 3381, 10286), 5411, true, "Escape Caves"),
            Link(1135, Pos(0, 3261, 3833), Pos(0, 3338, 10286), 5412, true, "Escape Caves"),
            // Wilderness Dungeons -> surface (or, for the lair exits, the Escape Caves they lead to)
            Link(1136, Pos(0, 3218, 10058), Pos(0, 3103, 3654), 5413, false, "Revenant Caves south stairs (RevenantCaves.EXITS)"),
            Link(1137, Pos(0, 3245, 10215), Pos(0, 3122, 3804), 5414, false, "Revenant Caves north stairs"),
            Link(1138, Pos(0, 3233, 10331), Pos(0, 3233, 3938), 5415, false, "Scorpion Pit exit (ScorpiaPit)"),
            Link(1139, Pos(0, 3232, 10352), Pos(0, 3233, 3950), 5416, false, "Scorpion Pit exit"),
            Link(1140, Pos(0, 3243, 10352), Pos(0, 3242, 3948), 5417, false, "Scorpion Pit exit"),
            Link(1141, Pos(0, 3359, 10244), Pos(0, 3282, 3774), 5418, false, "Escape Caves exit"),
            Link(1142, Pos(0, 3360, 10273), Pos(0, 3284, 3807), 5419, false, "Escape Caves exit"),
            Link(1143, Pos(0, 3383, 10288), Pos(0, 3320, 3830), 5420, false, "Escape Caves exit"),
            Link(1144, Pos(0, 3336, 10286), Pos(0, 3260, 3832), 5421, false, "Escape Caves exit"),
            Link(1145, Pos(0, 3359, 10313), Pos(0, 3360, 10295), 5422, false, "Callisto's Den exit -> Escape Caves (LAIR_EXIT_POINTS)"),
            Link(1146, Pos(1, 3295, 10189), Pos(0, 3341, 10255), 5423, false, "Vet'ion's Rest exit -> Escape Caves"),
            Link(1147, Pos(2, 3423, 10182), Pos(0, 3376, 10255), 5424, false, "Silk Chasm exit -> Escape Caves"),
        )

    // ---- Wilderness Dungeons projections ---------------------------------------------------------------------------------------
    private fun square(level: Int, sx: Int, sz: Int) = WildernessDungeonsMapTool.Chunk(level, sx * 64, sz * 64, sx * 64 + 63, sz * 64 + 63, sx * 64, sz * 64, sx * 64 + 63, sz * 64 + 63)

    /** OSRS 19/0/26 section 'plane 0 [3136,10048,3263,10239]' = the six revenant squares, 1:1. */
    val REV_SQUARES = WildernessDungeonsMapTool.NEW_SQUARES

    /** New 1:1 squares with the level the client projects (WorldMapChunk.sourceContains(level, x, z)). */
    val NEW_SQUARES =
        listOf(
            Triple(0, 52, 161), // Callisto's Den 13473
            Triple(0, 52, 160), // Escape Caves 13472
            Triple(1, 51, 159), // Vet'ion's Rest 13215 (level 1)
            Triple(2, 53, 159), // Silk Chasm 13727 (level 2)
            Triple(0, 50, 161), // Scorpion Pit 12961 (OSRS projects 3136-3327 x 10304-10367; only this square has our content)
        )

    /** King Black Dragon lair: 667 display offset (+856,+5536), OSRS (+840,+5568) -> chunk records move (-2,+4) chunks. */
    val KBD_SOURCE = intArrayOf(2248, 4672, 2295, 4719)
    val KBD_OSRS = WildernessDungeonsMapTool.Chunk(0, 2248, 4672, 2295, 4719, 3088, 10240, 3135, 10287)

    private fun g4(d: ByteArray, p: Int) = ((d[p].toInt() and 0xFF) shl 24) or ((d[p + 1].toInt() and 0xFF) shl 16) or ((d[p + 2].toInt() and 0xFF) shl 8) or (d[p + 3].toInt() and 0xFF)

    private fun be4(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    /** The link script with its single pushed coord replaced. */
    fun linkScript(
        template: ByteArray,
        oldCoord: Int,
        newCoord: Int,
    ): ByteArray {
        val needle = be4(oldCoord)
        val hits = (0..template.size - 4).filter { i -> (0 until 4).all { template[i + it] == needle[it] } }
        check(hits.size == 1) { "template script holds coord $oldCoord ${hits.size} times" }
        return template.copyOf().also { out -> be4(newCoord).copyInto(out, hits.single()) }
    }

    fun labelElement(
        template: ByteArray,
        text: String,
    ): ByteArray {
        check(template[0].toInt() == 3) { "label template does not start with op 3" }
        val end = template.indexOf(0.toByte())
        return ByteArrayOutputStream().use {
            it.write(3)
            it.write(text.toByteArray(Charsets.ISO_8859_1))
            it.write(template, end, template.size - end)
            it.toByteArray()
        }
    }

    /** encodeSquare for any [level]: the record draws the tiles and walls/scenes of that level (Vet'ion level 1, Silk Chasm level 2). */
    fun encodeLevel(
        squareX: Int,
        squareZ: Int,
        level: Int,
        tiles: Rev667TileMap,
        locs: List<Rev667Loc>,
        area: FeroxWorldMapTool.Area,
        def: (Int) -> gg.rsmod.game.fs.def.ObjectDef?,
    ): ByteArray {
        if (level == 0) return FeroxWorldMapTool.encodeSquare(squareX, squareZ, tiles, locs, area, def)
        // shift the wanted level down to 0 and reuse the level-0 encoder
        val shifted = Rev667TileMap()
        for (x in 0 until 64) for (z in 0 until 64) {
            val s = tiles.tiles[level][x][z]
            val d = shifted.tiles[0][x][z]
            d.underlayId = s.underlayId
            d.overlayId = s.overlayId
            d.overlayShape = s.overlayShape
            d.overlayRotation = s.overlayRotation
        }
        val lv = locs.filter { it.plane == level }.map { Rev667Loc(it.id, it.localX, it.localZ, 0, it.type, it.rotation) }
        return FeroxWorldMapTool.encodeSquare(squareX, squareZ, shifted, lv, area, def)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            fun put(
                index: Int,
                group: Int,
                file: Int,
                wanted: ByteArray?,
                label: String,
                groupNameHash: Int? = null,
            ) {
                val current = library.data(index, group, file)
                when {
                    current == null && wanted == null -> println("GONE $label")
                    current == null -> {
                        println("CREATE $label")
                        mutations += CacheMutation(index, group, file, wanted, label, null, groupNameHash = groupNameHash)
                    }
                    wanted != null && current.contentEquals(wanted) -> println("IN PLACE $label")
                    else -> {
                        println("${if (wanted == null) "DELETE" else "REPLACE"} $label")
                        mutations += CacheMutation(index, group, file, wanted, label, CacheItemProbeTool.sha1(current))
                    }
                }
            }

            // ---- elements --------------------------------------------------------------------------------------------------
            val upTemplate = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, UP_TEMPLATE)!!
            val downTemplate = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, DOWN_TEMPLATE)!!
            val labelTemplate = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, LABEL_TEMPLATE)!!
            LABELS.forEach { put(CONFIG_INDEX, MAP_ELEMENT_GROUP, it.element, labelElement(labelTemplate, it.text), "element ${it.element} label '${it.text}'") }
            LINKS.forEach { l ->
                put(CONFIG_INDEX, MAP_ELEMENT_GROUP, l.element, if (l.up) upTemplate else downTemplate, "element ${l.element} ${if (l.up) "surface" else "dungeon"} link (${l.note})")
            }

            // ---- link scripts ----------------------------------------------------------------------------------------------
            val scriptIndex = library.index(SCRIPT_INDEX)
            val template = library.data(SCRIPT_INDEX, LINK_SCRIPT_TEMPLATE, 0)!!
            val templateCoord = coord(0, 3045, 10329) // script 3726 (element 541, Deep Wilderness Dungeon) - not touched here
            check((0..template.size - 4).count { g4(template, it) == templateCoord } == 1) { "script $LINK_SCRIPT_TEMPLATE changed" }
            LINKS.forEach { l ->
                val hash = OP_MAPELEMENT1 or (l.element shl 10)
                val holder = scriptIndex.archives().firstOrNull { it.hashName == hash }
                check(holder == null || holder.id == l.script) { "element ${l.element} already has script ${holder!!.id}" }
                put(SCRIPT_INDEX, l.script, 0, linkScript(template, templateCoord, l.target.packed), "script ${l.script} = Open Map of element ${l.element} -> ${l.target}", if (holder == null) hash else null)
            }

            // ---- static elements -------------------------------------------------------------------------------------------
            val worldMap = library.index(WORLD_MAP_INDEX)
            fun statics(name: String): Pair<Int, Map<Int, ByteArray>> {
                val g = worldMap.archive(name)!!
                return g.id to g.fileIds().associateWith { library.data(WORLD_MAP_INDEX, g.id, it)!! }
            }
            fun elementOf(d: ByteArray) = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
            fun place(
                name: String,
                wanted: List<Pair<Int, Pos>>,
                remove: Set<Int>,
            ) {
                val (gid, files) = statics(name)
                var next = (files.keys.maxOrNull() ?: -1) + 1
                files.filter { elementOf(it.value) in remove }.forEach { (f, d) ->
                    mutations += CacheMutation(WORLD_MAP_INDEX, gid, f, null, "$name file $f element ${elementOf(d)} removed", CacheItemProbeTool.sha1(d))
                    println("DELETE $name file $f element ${elementOf(d)}")
                }
                wanted.forEach { (element, pos) ->
                    val entry = MansionMapIconTool.staticEntry(element, pos.x, pos.z, pos.level)
                    val same = files.filter { elementOf(it.value) == element }
                    when {
                        same.values.any { it.contentEquals(entry) } -> println("IN PLACE $name element $element at $pos")
                        // one file per moved link element (1043/1044); labels shared by several places only ever get new files
                        same.size == 1 && LINKS.any { it.element == element } -> {
                            val (f, d) = same.entries.single()
                            println("MOVE $name file $f element $element to $pos")
                            mutations += CacheMutation(WORLD_MAP_INDEX, gid, f, entry, "$name element $element moved to $pos", CacheItemProbeTool.sha1(d))
                        }
                        else -> {
                            println("CREATE $name file $next element $element at $pos")
                            mutations += CacheMutation(WORLD_MAP_INDEX, gid, next++, entry, "$name element $element at $pos", null)
                        }
                    }
                }
            }
            place("main_staticelements", SURFACE_LABELS + LINKS.filter { it.up }.map { it.element to it.at }, setOf(VOLCANO_LABEL))
            place("wilderness_dungeons_staticelements", DUNGEON_LABELS + LINKS.filter { !it.up }.map { it.element to it.at }, emptySet())

            // ---- Wilderness Dungeons projections + area snapshot --------------------------------------------------------------
            val detailsGroup = worldMap.archive("details")!!
            val detailsBytes = library.data(WORLD_MAP_INDEX, detailsGroup.id, DETAILS_FILE)!!
            val details = WildernessDungeonsMapTool.decodeDetails(detailsBytes)
            val isKbd = { c: WildernessDungeonsMapTool.Chunk -> c.sx1 >= KBD_SOURCE[0] && c.sx2 <= KBD_SOURCE[2] && c.sz1 >= KBD_SOURCE[1] && c.sz2 <= KBD_SOURCE[3] }
            val isRev = { c: WildernessDungeonsMapTool.Chunk -> c.sourceSquares().all { it in REV_SQUARES } }
            val kbdOld = details.chunks.filter(isKbd)
            val kbdMoved = kbdOld.none { it.dx1 == KBD_OSRS.dx1 && it.dz1 == KBD_OSRS.dz1 }
            check(kbdOld.isNotEmpty() && (if (kbdMoved) kbdOld.all { it.dx1 - it.sx1 == 856 && it.dz1 - it.sz1 == 5536 } else kbdOld.size == 1)) {
                "KBD projections unexpected: $kbdOld"
            }
            val ownSquares = NEW_SQUARES.map { (_, x, z) -> x to z }.toSet()
            val kept = details.chunks.filterNot { isKbd(it) || isRev(it) || it.sourceSquares().all { s -> s in ownSquares } }
            val newChunks =
                kept + KBD_OSRS + REV_SQUARES.map { (x, z) -> square(0, x, z) } + NEW_SQUARES.map { (lv, x, z) -> square(lv, x, z) }
            // no two display rects overlap
            newChunks.forEachIndexed { i, a ->
                newChunks.drop(i + 1).forEach { b ->
                    check(!(a.dx1 <= b.dx2 && b.dx1 <= a.dx2 && a.dz1 <= b.dz2 && b.dz1 <= a.dz2)) { "display overlap $a / $b" }
                }
            }
            val newDetails = WildernessDungeonsMapTool.Details(details.file, details.name, details.origin, details.colour, details.enabled, details.zoom, details.unknown, newChunks)
            newChunks.forEach { println("  CHUNK $it") }
            put(WORLD_MAP_INDEX, detailsGroup.id, DETAILS_FILE, newDetails.encode(), "wilderness_dungeons details: OSRS layout (${details.chunks.size} -> ${newChunks.size} projections)")

            val areaGroup = worldMap.archive("wilderness_dungeons")!!
            val areaFile = areaGroup.file("area")!!
            val areaBytes = library.data(WORLD_MAP_INDEX, areaGroup.id, areaFile.id)!!
            val area = FeroxWorldMapTool.parse(areaBytes)
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            val def = FeroxWorldMapTool.objectDefs(library)
            val out = ByteArrayOutputStream(areaBytes.size + 65536)
            out.write(areaBytes, 0, area.records.firstOrNull()?.start ?: areaBytes.size)
            val revWasShifted = details.chunks.any { isRev(it) && it.dx1 != it.sx1 }
            val revShifted = if (revWasShifted) REV_SQUARES.map { (x, z) -> x + WildernessDungeonsMapTool.DISPLAY_SHIFT_X / 64 to z }.toSet() else emptySet()
            // every 1:1 square is rebuilt from the landscape of its level (so loc icons added below - the pillar shortcuts - are drawn)
            val rebuild = NEW_SQUARES + REV_SQUARES.map { (x, z) -> Triple(0, x, z) }
            val newSquares = rebuild.map { (_, x, z) -> x to z }.toSet()
            val kbdDisplay = kbdOld.map { intArrayOf(it.dx1, it.dz1, it.dx2, it.dz2) }
            var movedKbd = 0
            var movedRev = 0
            val written = HashSet<String>()
            area.records.forEach { r ->
                val bytes = areaBytes.copyOfRange(r.start, r.end)
                if (r.square && (r.x to r.z) in revShifted) {
                    // first: a shifted revenant record (51_159 is also Vet'ion's square - it moves, it is not dropped)
                    bytes[1] = (r.x - WildernessDungeonsMapTool.DISPLAY_SHIFT_X / 64).toByte()
                    movedRev++
                } else if (r.square && (r.x to r.z) in newSquares) {
                    println("  REBUILD record ${r.x}_${r.z}")
                    return@forEach
                } else if (!r.square && kbdMoved) {
                    val cx = bytes[3].toInt() and 0xFF
                    val cz = bytes[4].toInt() and 0xFF
                    val ax = r.x * 64 + cx * 8
                    val az = r.z * 64 + cz * 8
                    if (kbdDisplay.any { d -> ax in d[0]..d[2] && az in d[1]..d[3] }) {
                        val nx = (ax - 16) / 8
                        val nz = (az + 32) / 8
                        bytes[1] = (nx / 8).toByte()
                        bytes[2] = (nz / 8).toByte()
                        bytes[3] = (nx % 8).toByte()
                        bytes[4] = (nz % 8).toByte()
                        movedKbd++
                    }
                } else if (r.square && kbdMoved && kbdDisplay.any { d -> r.x * 64 + 63 >= d[0] && r.x * 64 <= d[2] && r.z * 64 + 63 >= d[1] && r.z * 64 <= d[3] }) {
                    error("KBD display square ${r.x}_${r.z} is a full-square record")
                }
                fun u(i: Int) = bytes[i].toInt() and 0xFF
                val key = if (u(0) == 0) "S${u(1)}_${u(2)}" else "c${u(1)}_${u(2)}_${u(3)}_${u(4)}"
                check(written.add(key)) { "duplicate record $key" }
                out.write(bytes)
            }
            rebuild.forEach { (lv, x, z) ->
                val (tiles, locs) = FeroxWorldMapTool.landscape(library, keys, x, z)
                check(written.add("S${x}_$z")) { "record ${x}_$z exists" }
                out.write(encodeLevel(x, z, lv, tiles, locs, area, def))
            }
            val newArea = out.toByteArray()
            val reparsed = FeroxWorldMapTool.parse(newArea)
            println("  area records ${area.records.size} -> ${reparsed.records.size}; moved KBD chunks $movedKbd, revenant squares $movedRev")
            put(WORLD_MAP_INDEX, areaGroup.id, areaFile.id, newArea, "wilderness_dungeons area: OSRS layout (KBD moved, caves 1:1, lairs + Scorpion Pit)")

            // ---- minimap: same-id proof for imported locs whose OSRS map area stayed BLOCKED ---------------------------------
            // OsrsLocMapDataTool only carries an area across on a pixel match (<= 0.01). OSRS area 71 (agility shortcut, sprite 1518)
            // is 0.026 from 667 element 625, so the Revenant Caves pillars (marker loc 17030 -> 62965) show no shortcut icon. But
            // loc 17030 is the same Jagex loc in both caches (pre-2007 id, same name) and the 667 one carries element 625: that is the
            // 667 picture of the same thing. Every imported loc in that situation gets the element of its 667 namesake.
            val local = Rev667RegionProbeTool.locTypes(library)
            ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
                val modernDefs = modern.files(2, 6)
                OsrsLocMapDataTool.locEntries().distinctBy { it.local }.sortedBy { it.local }.forEach { e ->
                    val src = modernDefs[e.upstream]?.let { ModernObjectDef.decode(e.upstream, it) } ?: return@forEach
                    if (src.mapAreaId < 0 || e.upstream >= SAME_ID_LIMIT) return@forEach
                    val mine = local[e.local] ?: return@forEach
                    if (mine.mapElement >= 0) return@forEach
                    val namesake = local[e.upstream] ?: return@forEach
                    if (namesake.mapElement < 0 || namesake.name != src.name) return@forEach
                    val current = library.data(16, e.local ushr 8, e.local and 0xFF)!!
                    val updated = FeroxMinimapTool.patched(current, null, namesake.mapElement)
                    val after = Rev667LocType.decode(e.local, updated)
                    check(after.mapElement == namesake.mapElement && after.name == mine.name && after.msi == mine.msi) { "re-decode mismatch ${e.local}" }
                    println("MINIMAP loc ${e.local} '${mine.name}' <- osrs ${e.upstream} area ${src.mapAreaId}: element ${namesake.mapElement} of 667 loc ${e.upstream} [${e.section.take(50)}]")
                    put(16, e.local ushr 8, e.local and 0xFF, updated, "loc ${e.local} mapelement ${namesake.mapElement} (667 namesake ${e.upstream})")
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("DRY_RUN transaction=${transaction.id} mutations=${mutations.size}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
