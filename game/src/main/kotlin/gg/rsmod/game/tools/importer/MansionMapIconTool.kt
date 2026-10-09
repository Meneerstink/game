package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Grand Exchange / Royal Hall map icons, owner reports of 2026-10-09:
 *  - 201954 / 202116 (screenshot 202018): "verwijder herblore icoon, runecraft icoon ... verwijder de iconen die ik met rood
 *    heb gemarkeerd, verwijder ook de NPCS DIE DAARBIJ HOREN". The five Grand Exchange trading-area icons (map elements
 *    638-641, 643 on Jagex's invisible markers 27991-27996) and the music icon at 3145,3472 (marker 43522, element 225).
 *    GeMapIconCleanupTool took their placements out of the landscape (minimap) on 2026-09-22, but the world map draws its
 *    own prebuilt copy of every map-square loc (`main` / `area`, client `WorldMap.decodeTile` + `method5075`), so they
 *    stayed there. This tool drops exactly those six loc entries from that file; the loc types stay (43522 marks 31 other
 *    music spots). The npcs beside them (Relobo Blinyo, the Musician) are taken out of spawns_12598.
 *  - 202308: "mansion custom iconen in de worldmap zegt die grand exchange dit moet zijn dus gambler of donatorstore". The
 *    world map's hover text is the element category's MAP_ELEMENT_MOUSEREPEAT client script (trigger 17, group name
 *    17 | (category + 65536) << 10, ClientScriptList.trigger), which shows the name of one struct (Jagex: category 1228 ->
 *    script 3864 -> struct 478 "Grand Exchange", param 596). Every custom icon was a copy of the Grand Exchange element and
 *    kept its category. Each now gets its own category with its own struct and script, built exactly like Jagex's:
 *    [CATEGORIES].
 *  - 213724 / 214334: "custom minimap iconen lijken verkeerd omdat ik devmode heb gebruikt om npcs te verplaatsen ... zorg
 *    ervoor dat de iconen meebewegen". The store npcs carry their element (NPCType op 142), which the minimap draws at the
 *    npc's live position on its level - but StoreMapIconTool also wrote world-map static elements at the npcs' posts of
 *    2026-09-29, and the minimap draws those too (Minimap.draw, elementCoords), so after a devmode move the old spot kept an
 *    icon. Those static entries are deleted; the world map now shows npc icons at their live posts (NpcMapIcons ->
 *    client NpcWorldMapIcons).
 *  - 212919: "geef kuradal in de mansion een slayer icon dat met hem meebeweegt": Kuradal 9085 carries Jagex's Slayer Master
 *    element 607 (category 1201, hover "Slayer Master"), on the npc type, so it follows him.
 *  - 212609: "center the gambling custom icon in the center of the minimap ... in gambling area". The casino storey is
 *    covered by the opulent rug, a ground decoration on every tile, and the minimap takes one loc per tile (ground decoration
 *    first, Minimap.drawLevel), so no marker loc can stand in its middle. Jagex's own way for an icon on one storey is a
 *    static element: the minimap draws those only on their level (Minimap.drawLevel, coord >> 28). [GAMBLING_ELEMENT] is the
 *    gold activity star (635) with flags 2 = minimap only (MapElementType op 7: bit 1 enables it on the world map, bit 2 on
 *    the minimap), at the storey's centre 3165,3491 - the tile the Royal Hall crown uses below. The old marker loc 62899
 *    on the south doorway leaves home_decor.txt (baked by RoyalHallDecorBakeTool).
 *
 * One [CacheTransaction] over both production caches (servers stopped), idempotent.
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.MansionMapIconTool plan|apply`
 */
object MansionMapIconTool {
    val TARGETS = OsrsItemImportTool.TARGETS

    private const val CONFIG_INDEX = 2
    private const val STRUCT_GROUP = 26
    private const val MAP_ELEMENT_GROUP = 36
    private const val SCRIPT_INDEX = 12
    private const val NPC_INDEX = 18
    private const val WORLD_MAP_INDEX = 23
    private const val MOUSEREPEAT_TRIGGER = 17

    /** Jagex's Grand Exchange category script and struct, the template of every new name. */
    private const val GE_CATEGORY_SCRIPT = 3864
    private const val GE_STRUCT = 478
    private const val ACTIVITY_STAR_ELEMENT = 635

    /** Jagex's Slayer Master map element (sprite 1334, category 1201 -> struct 456 "Slayer Master"). */
    const val SLAYER_MASTER_ELEMENT = 607
    const val KURADAL = 9085

    /** The casino star: element 635's star (sprite 1377) under the "Gambling" category, minimap only. */
    const val GAMBLING_ELEMENT = 1112
    const val GAMBLING_X = 3165
    const val GAMBLING_Z = 3491
    const val GAMBLING_LEVEL = 2

    /** MapElementType op 7 flags: 1 = shown on the world map (client `enabled`), 2 = shown on the minimap. */
    private const val MINIMAP_ONLY = 2

    class Category(val id: Int, val name: String, val sprite: Int, val struct: Int, val script: Int, val elements: List<Int>)

    /** Category ids 2600+ and the structs / scripts after the cache's last ones (2757 / 5380) are unused in rev 667. */
    val CATEGORIES =
        listOf(
            Category(2600, "Donator Store", StoreMapIconTool.DONATOR_SPRITE, 2758, 5381, listOf(StoreMapIconTool.DONATOR_ELEMENT)),
            Category(2601, "Deadman Store", StoreMapIconTool.DEADMAN_SPRITE, 2759, 5382, listOf(StoreMapIconTool.DEADMAN_ELEMENT)),
            Category(2602, "Gambling", 1377, 2760, 5383, listOf(GAMBLING_ELEMENT)),
            Category(2603, "Mansion", RoyalHallLocTool.ICON_SPRITE, 2761, 5384, listOf(RoyalHallLocTool.ICON_ELEMENT)),
        )

    fun categoryOf(element: Int): Int? = CATEGORIES.firstOrNull { element in it.elements }?.id

    /** World-map loc entries removed: x, z, loc id. */
    val WORLD_MAP_REMOVALS =
        listOf(
            Triple(3156, 3501, 27991),
            Triple(3174, 3482, 27992),
            Triple(3165, 3481, 27993),
            Triple(3174, 3501, 27994),
            Triple(3185, 3492, 27996),
            Triple(3145, 3472, 43522),
        )

    /** The store elements whose world-map static entries go (the npcs carry them; NpcMapIcons shows them on the world map). */
    private val NPC_STATIC_ELEMENTS = setOf(StoreMapIconTool.DONATOR_ELEMENT, StoreMapIconTool.DEADMAN_ELEMENT, StoreMapIconTool.LOYALTY_ELEMENT)

    // ------------------------------------------------------------------------------------------------ map element codec

    /** [def] (a MapElementType) with opcode 19 (category) set to [category]: replaced in place, or added before the end. */
    fun withCategory(
        def: ByteArray,
        category: Int,
    ): ByteArray {
        var pos = 0
        while (pos < def.size) {
            val op = def[pos].toInt() and 0xFF
            if (op == 0) break
            if (op == 19) {
                return def.copyOf().also {
                    it[pos + 1] = (category shr 8).toByte()
                    it[pos + 2] = category.toByte()
                }
            }
            pos += 1 + elementOperandSize(def, pos)
        }
        check(pos == def.size - 1) { "map element does not end with opcode 0" }
        return def.copyOfRange(0, pos) + byteArrayOf(19, (category shr 8).toByte(), category.toByte(), 0)
    }

    /** Client MapElementType.decode operand sizes. */
    private fun elementOperandSize(
        def: ByteArray,
        pos: Int,
    ): Int {
        fun string(from: Int): Int {
            var p = from
            while (def[p] != 0.toByte()) p++
            return p - from + 1
        }
        return when (val op = def[pos].toInt() and 0xFF) {
            1, 2, 18, 19 -> 2
            3, 17, in 10..14 -> string(pos + 1)
            4, 5 -> 3
            6, 7, 8 -> 1
            9 -> 12
            16 -> 0
            20 -> 12
            21, 22 -> 4
            23 -> 3
            24 -> 4
            249 -> {
                var p = pos + 1
                val count = def[p++].toInt() and 0xFF
                repeat(count) {
                    val isString = def[p] == 1.toByte()
                    p += 4
                    p += if (isString) string(p) else 4
                }
                p - pos - 1
            }
            else -> error("map element opcode $op not handled")
        }
    }

    /** [def] with its op 7 flags set to [flags] (in place; the element must carry op 7). */
    fun withFlags(
        def: ByteArray,
        flags: Int,
    ): ByteArray {
        var pos = 0
        while (pos < def.size) {
            val op = def[pos].toInt() and 0xFF
            if (op == 0) break
            if (op == 7) return def.copyOf().also { it[pos + 1] = flags.toByte() }
            pos += 1 + elementOperandSize(def, pos)
        }
        error("map element has no op 7")
    }

    /** Category element of [def]'s opcode 19, -1 when none. */
    fun categoryIn(def: ByteArray): Int {
        var pos = 0
        while (pos < def.size) {
            val op = def[pos].toInt() and 0xFF
            if (op == 0) return -1
            if (op == 19) return ((def[pos + 1].toInt() and 0xFF) shl 8) or (def[pos + 2].toInt() and 0xFF)
            pos += 1 + elementOperandSize(def, pos)
        }
        return -1
    }

    // ------------------------------------------------------------------------------------------------ structs, scripts

    /** A struct like Jagex's 478: param 595 sprite, 596 name, 597 0, 477 category. */
    fun struct(category: Category): ByteArray =
        ByteArrayOutputStream().use { out ->
            fun int(param: Int, value: Int) {
                out.write(0)
                out.write(param ushr 16)
                out.write(param ushr 8)
                out.write(param)
                out.write(value ushr 24)
                out.write(value ushr 16)
                out.write(value ushr 8)
                out.write(value)
            }
            out.write(249)
            out.write(4)
            int(595, category.sprite)
            out.write(1)
            out.write(596 ushr 16)
            out.write(596 ushr 8)
            out.write(596 and 0xFF)
            out.write(category.name.toByteArray(Charsets.ISO_8859_1))
            out.write(0)
            int(597, 0)
            int(477, category.id)
            out.write(0)
            out.toByteArray()
        }

    /** Jagex's category script 3864 with [struct] in place of 478 (first instruction: PUSH_CONSTANT_INT 478). */
    fun script(
        template: ByteArray,
        struct: Int,
    ): ByteArray {
        fun intAt(p: Int) =
            ((template[p].toInt() and 0xFF) shl 24) or ((template[p + 1].toInt() and 0xFF) shl 16) or
                ((template[p + 2].toInt() and 0xFF) shl 8) or (template[p + 3].toInt() and 0xFF)
        check(template[0] == 0.toByte() && template[1] == 0.toByte() && template[2] == 0.toByte() && intAt(3) == GE_STRUCT) {
            "script $GE_CATEGORY_SCRIPT does not start with PUSH_CONSTANT_INT $GE_STRUCT"
        }
        return template.copyOf().also {
            it[3] = (struct ushr 24).toByte()
            it[4] = (struct ushr 16).toByte()
            it[5] = (struct ushr 8).toByte()
            it[6] = struct.toByte()
        }
    }

    fun scriptNameHash(category: Int): Int = MOUSEREPEAT_TRIGGER or ((category + 65536) shl 10)

    // ------------------------------------------------------------------------------------------------ world map area

    /** A `_staticelements` file: u32 coord (level << 28 | x << 14 | z), u16 element, u8 members (MapElementList.load). */
    fun staticEntry(
        element: Int,
        x: Int,
        z: Int,
        level: Int,
    ): ByteArray {
        val coord = (level shl 28) or (x shl 14) or z
        return byteArrayOf((coord ushr 24).toByte(), (coord ushr 16).toByte(), (coord ushr 8).toByte(), coord.toByte(), (element shr 8).toByte(), element.toByte(), 0)
    }

    class AreaLoc(val x: Int, val z: Int, val level: Int, val id: Int, val countPos: Int, val entryPos: Int)

    /** Every loc entry of a world-map `area` file (client WorldMap.method5080 / decodeTile), with its byte positions. */
    fun areaLocs(area: ByteArray): List<AreaLoc> {
        val out = ArrayList<AreaLoc>()
        var p = 0
        fun g1() = area[p++].toInt() and 0xFF
        repeat(g1()) { g1() }
        repeat(g1()) { g1() }
        fun tile(
            x: Int,
            z: Int,
        ) {
            val data = g1()
            if (data and 1 == 0) {
                val k = (data shr 2) and 0x3F
                if (k != 62) {
                    if (k == 63) g1()
                    if (data and 2 != 0) g1()
                }
                return
            }
            val levels = ((data shr 1) and 3) + 1
            val overlay = data and 8 != 0
            val locs = data and 0x10 != 0
            for (level in 0 until levels) {
                g1()
                if (overlay) {
                    g1()
                    g1()
                }
                if (!locs) continue
                val countPos = p
                val count = g1()
                repeat(count) {
                    val entryPos = p
                    val id = (g1() shl 8) or g1()
                    g1()
                    out += AreaLoc(x, z, level, id, countPos, entryPos)
                }
            }
        }
        while (p < area.size) {
            if (g1() == 0) {
                val mx = g1()
                val mz = g1()
                for (a in 0 until 64) for (b in 0 until 64) tile(mx * 64 + a, mz * 64 + b)
            } else {
                val mx = g1()
                val mz = g1()
                val cx = g1()
                val cz = g1()
                for (a in 0 until 8) for (b in 0 until 8) tile(mx * 64 + cx * 8 + a, mz * 64 + cz * 8 + b)
            }
        }
        check(p == area.size) { "area file overran: $p of ${area.size}" }
        return out
    }

    /** [area] without [remove]: each entry's 3 bytes dropped and its level's count lowered. */
    fun withoutLocs(
        area: ByteArray,
        remove: List<AreaLoc>,
    ): ByteArray {
        val dropped = remove.map { it.entryPos }.toSet()
        val lowered = remove.groupingBy { it.countPos }.eachCount()
        val out = ByteArrayOutputStream(area.size)
        var p = 0
        while (p < area.size) {
            when {
                p in dropped -> p += 3
                p in lowered -> {
                    val count = (area[p].toInt() and 0xFF) - lowered.getValue(p)
                    check(count >= 0) { "negative loc count at $p" }
                    out.write(count)
                    p++
                }
                else -> out.write(area[p++].toInt())
            }
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------------------------------------ main

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(TARGETS[0])
        try {
            fun replace(
                index: Int,
                group: Int,
                file: Int,
                wanted: ByteArray,
                label: String,
                groupNameHash: Int? = null,
            ) {
                val current = library.data(index, group, file)
                when {
                    current == null -> {
                        println("CREATE $label")
                        mutations += CacheMutation(index, group, file, wanted, label, null, groupNameHash = groupNameHash)
                    }
                    current.contentEquals(wanted) -> println("IN PLACE $label")
                    else -> {
                        println("REPLACE $label")
                        mutations += CacheMutation(index, group, file, wanted, label, CacheItemProbeTool.sha1(current))
                    }
                }
            }

            // 1. Names: one struct + one MAP_ELEMENT_MOUSEREPEAT script per category (Jagex's 478 / 3864 pattern).
            val template = library.data(SCRIPT_INDEX, GE_CATEGORY_SCRIPT, 0) ?: error("script $GE_CATEGORY_SCRIPT missing")
            val scriptIndex = library.index(SCRIPT_INDEX)
            CATEGORIES.forEach { cat ->
                replace(CONFIG_INDEX, STRUCT_GROUP, cat.struct, struct(cat), "struct ${cat.struct} '${cat.name}' (category ${cat.id})")
                val hash = scriptNameHash(cat.id)
                val holder = scriptIndex.archives().firstOrNull { it.hashName == hash }
                check(holder == null || holder.id == cat.script) { "category ${cat.id} already has script ${holder!!.id}" }
                replace(SCRIPT_INDEX, cat.script, 0, script(template, cat.struct), "script ${cat.script} = mouse-over of category ${cat.id} '${cat.name}'", hash)
            }

            // 2. Elements: the custom icons get their own category; the casino's own star element.
            val star = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, ACTIVITY_STAR_ELEMENT) ?: error("element $ACTIVITY_STAR_ELEMENT missing")
            CATEGORIES.forEach { cat ->
                cat.elements.forEach { element ->
                    val wanted =
                        if (element == GAMBLING_ELEMENT) {
                            withFlags(withCategory(star, cat.id), MINIMAP_ONLY)
                        } else {
                            withCategory(library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, element) ?: error("element $element missing"), cat.id)
                        }
                    replace(CONFIG_INDEX, MAP_ELEMENT_GROUP, element, wanted, "map element $element -> category ${cat.id} '${cat.name}'")
                }
            }

            // 3. Kuradal carries the Slayer Master element.
            val kuradal = library.data(NPC_INDEX, KURADAL ushr 7, KURADAL and 0x7F) ?: error("npc $KURADAL missing")
            when (val before = CasinoMinimapIconTool.appendedMapElement(kuradal)) {
                SLAYER_MASTER_ELEMENT -> println("IN PLACE Kuradal $KURADAL carries element $SLAYER_MASTER_ELEMENT")
                -1 -> {
                    val patched = CasinoMinimapIconTool.patched(kuradal, SLAYER_MASTER_ELEMENT)
                    val def = NpcDef(KURADAL).also { it.decode(Unpooled.wrappedBuffer(patched)) }
                    check(def.mapElement == SLAYER_MASTER_ELEMENT && def.interactable) { "Kuradal decodes wrong after the patch" }
                    println("REPLACE Kuradal $KURADAL '${def.name}' mapElement -1 -> $SLAYER_MASTER_ELEMENT")
                    mutations += CacheMutation(NPC_INDEX, KURADAL ushr 7, KURADAL and 0x7F, patched, "Kuradal slayer map element", CacheItemProbeTool.sha1(kuradal))
                }
                else -> error("Kuradal already carries element $before")
            }

            // 4. World map: the static store icons go (the npcs carry them), the six marked loc entries go.
            val worldMap = library.index(WORLD_MAP_INDEX)
            val staticGroup = worldMap.archive("main_staticelements") ?: error("main_staticelements missing")
            for (f in staticGroup.fileIds()) {
                val d = library.data(WORLD_MAP_INDEX, staticGroup.id, f) ?: continue
                if (d.size != 7) continue
                val element = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
                if (element in NPC_STATIC_ELEMENTS) {
                    println("DELETE world map static element $element (file $f) - the npc carries it")
                    mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, f, null, "static store icon $element (file $f)", CacheItemProbeTool.sha1(d))
                }
            }
            val gamblingEntry = staticEntry(GAMBLING_ELEMENT, GAMBLING_X, GAMBLING_Z, GAMBLING_LEVEL)
            val gamblingFiles =
                staticGroup.fileIds().filter { f ->
                    library.data(WORLD_MAP_INDEX, staticGroup.id, f)?.let { it.size == 7 && it[4] == gamblingEntry[4] && it[5] == gamblingEntry[5] } == true
                }
            when {
                gamblingFiles.any { library.data(WORLD_MAP_INDEX, staticGroup.id, it)!!.contentEquals(gamblingEntry) } ->
                    println("IN PLACE casino star at $GAMBLING_X,$GAMBLING_Z level $GAMBLING_LEVEL")
                gamblingFiles.isNotEmpty() -> {
                    val f = gamblingFiles.first()
                    println("MOVE casino star (file $f) to $GAMBLING_X,$GAMBLING_Z level $GAMBLING_LEVEL")
                    mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, f, gamblingEntry, "casino star (moved)", CacheItemProbeTool.sha1(library.data(WORLD_MAP_INDEX, staticGroup.id, f)!!))
                }
                else -> {
                    val f = (staticGroup.fileIds().maxOrNull() ?: -1) + 1
                    println("CREATE casino star: main_staticelements file $f, element $GAMBLING_ELEMENT at $GAMBLING_X,$GAMBLING_Z level $GAMBLING_LEVEL")
                    mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, f, gamblingEntry, "casino star", null)
                }
            }
            val mainGroup = worldMap.archive("main") ?: error("world map group main missing")
            val areaFile = mainGroup.file("area") ?: error("main/area missing")
            val area = library.data(WORLD_MAP_INDEX, mainGroup.id, areaFile.id) ?: error("main/area unreadable")
            val locs = areaLocs(area)
            val remove = locs.filter { l -> WORLD_MAP_REMOVALS.any { (x, z, id) -> l.x == x && l.z == z && l.id == id } }
            WORLD_MAP_REMOVALS.forEach { (x, z, id) ->
                val n = remove.count { it.x == x && it.z == z && it.id == id }
                println(if (n == 0) "IN PLACE world map loc $id at $x,$z already gone" else "REMOVE world map loc $id at $x,$z ($n entr${if (n == 1) "y" else "ies"})")
            }
            if (remove.isNotEmpty()) {
                val edited = withoutLocs(area, remove)
                val after = areaLocs(edited)
                check(after.size == locs.size - remove.size) { "world map edit lost or kept the wrong entries" }
                val kept = locs.filter { it !in remove }
                check(kept.indices.all { kept[it].x == after[it].x && kept[it].z == after[it].z && kept[it].level == after[it].level && kept[it].id == after[it].id }) {
                    "world map edit changed other entries"
                }
                println("world map main/area ${area.size} -> ${edited.size} bytes, ${locs.size} -> ${after.size} locs")
                mutations += CacheMutation(WORLD_MAP_INDEX, mainGroup.id, areaFile.id, edited, "world map: GE trading + music icons removed", CacheItemProbeTool.sha1(area))
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING TO DO")
            return
        }
        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        plan.forEach { println("PREFLIGHT $it") }
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${transaction.id} mutations=${mutations.size} (nothing written)")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) {
            println("VERIFY_OK transaction=${transaction.id}")
        } else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
