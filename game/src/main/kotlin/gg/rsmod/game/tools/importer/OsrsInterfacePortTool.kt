package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.RspsPaths
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N462 ToA (2026-11-06): ports OSRS if3 interfaces to revision-667 if3 interfaces - generically, for every static component - so the
 * ToA windows (481 HUD, 482 Raid Summary, 771 rewards, 772 Party List, 773 lobby overlay, 774 party details, 775 statistics, 776
 * invocations, 777 supply bundles, 778 supplies bag) need no hand-built tool each.
 *
 * Source: ChatGPT-A's strict decode of OSRS cache 2686 index 3 (team/dossiers/N462-notities/toa/cache/interfaces.json - every property of
 * every component, RuneLite InterfaceLoader.decodeIf3 field names). Rules:
 *  - OSRS component N stays component N (server code can use the OSRS numbers); parents, positions, size/position modes (OSRS and 667
 *    share the 0 abs / 1 minus / 2 proportional and 0..5 position constants), hidden, scroll size, no-click-through, ops (actions) and
 *    the op name (name) are copied.
 *  - Types: 0 layer, 3 rectangle, 4 text, 5 graphic, 9 line. Type 6 (model) becomes an empty layer + REPORT (model ids need an import).
 *  - Fonts: OSRS 495 p12_full / 496 b12_full / 494 p11_full / 497 q8_full -> the 667 font sprite of the same name.
 *  - Sprites: every OSRS sprite group a component or a rebuilt script uses is copied byte for byte (same container format, see
 *    LootKeyInterfaceImportTool) to new 667 sprite ids from max(cache max + 1, [SPRITE_FLOOR]) - 8813-8818 are reserved for N543.
 *  - Scripts: OSRS onLoad scripts cannot run in 667. The frame scripts are rebuilt as extra static components after the OSRS ones,
 *    exactly as their source does it (sources/osrs-dumps/script): [proc,steelborder] (clientscript 227: tradebacking, four corners,
 *    four edges, divider, title b12 0xff981f, close button 535/536 with op "Close") and [proc,thinbox] (clientscript 714: two
 *    unfilled rectangles 0x0e0e0c / 0x474745). Every other onLoad script is REPORTED (the server fills those parts).
 * Output: team/dossiers/N462-notities/toa/94-toa-interfaces-claude.tsv (OSRS group -> 667 interface, extra components, reports).
 *
 * Usage: `./gradlew :game:runOsrsInterfacePortTool --args="<group,group,...|toa> [--apply]"` with the servers stopped.
 */
object OsrsInterfacePortTool {
    val SOURCE_CACHE = OsrsItemImportTool.SOURCE_CACHE
    val TARGETS = OsrsItemImportTool.TARGETS
    val JSON = File(RspsPaths.root("team/dossiers/N462-notities/toa/cache/interfaces.json"))
    val REPORT = File(RspsPaths.root("team/dossiers/N462-notities/toa/94-toa-interfaces-claude.tsv"))
    const val INDEX_INTERFACES = 3
    const val INDEX_SPRITES = 8
    const val SPRITE_FLOOR = 8819
    val TOA = listOf(481, 482, 771, 772, 773, 774, 775, 776, 777, 778)

    // OSRS sprite groups (RuneLite gameval SpriteID) used by the rebuilt frame scripts.
    const val TRADEBACKING = 297
    val STEELBORDER = intArrayOf(310, 311, 312, 313)
    val STEELBORDER2 = intArrayOf(314, 315)
    const val MISCGRAPHICS_2 = 172
    const val MISCGRAPHICS_3 = 173
    const val STEELBORDER_DIVIDER = 2546
    const val CLOSE_BUTTON = 535
    const val CLOSE_BUTTON_HOVER = 536
    // orb_filler,15 (normal) / ,16 (status 31 full) / ,17 (primary) - RuneLite gameval SpriteID.OrbFiller (toa_hud_statusrefresh 6583).
    // tradebacking_light 1040 + v2_stone_button_out,0..7 929-936 (RuneLite gameval SpriteID; [proc,v2_stone_button_out_filled] 208).
    const val TRADEBACKING_LIGHT = 1040
    val STONE_BUTTON = intArrayOf(929, 930, 931, 932, 933, 934, 935, 936)
    const val ORB_FILLER = 3574
    const val ORB_FILLER_FULL = 3575
    const val ORB_FILLER_PRIMARY = 3576

    class C(
        val id: Int, var type: Int, val x: Int, val y: Int, val w: Int, val h: Int, val parent: Int,
        val wMode: Int, val hMode: Int, val xMode: Int, val yMode: Int, val hidden: Boolean,
        val scrollW: Int = 0, var scrollH: Int = 0, val noClickThrough: Boolean = false,
        var sprite: Int = -1, val tiling: Boolean = false, val opacity: Int = 0,
        val font: Int = -1, val text: String = "", val lineHeight: Int = 0, val alignX: Int = 0, val alignY: Int = 0,
        val shadow: Boolean = false, val colour: Int = 0, val filled: Boolean = false, val lineWidth: Int = 1,
        val ops: List<String> = emptyList(), val opBase: String = "",
        val model: Int = -1, val originX: Int = 0, val originY: Int = 0, val xan: Int = 0, val yan: Int = 0, val zan: Int = 0,
        val zoom: Int = 0, val ortho: Boolean = false, val flipH: Boolean = false,
        val mouseOver: List<Int> = emptyList(), val mouseLeave: List<Int> = emptyList(),
    )

    /** OSRS model -> local model recorded by an import (OsrsLocImportTool fx_kind osrs_loc_model, region import role loc_model). */
    fun localModel(osrs: Int): Int? {
        val text = File(OsrsItemImportTool.ASSET_MAP).readText()
        Regex("""- fx_kind: osrs_loc_model\s+upstream_fx_id: $osrs\s+local_fx_id: (\d+)""").find(text)?.let { return it.groupValues[1].toInt() }
        Regex("""upstream_id: $osrs, local_id: (\d+), role: loc_model""").find(text)?.let { return it.groupValues[1].toInt() }
        return null
    }

    private class Out {
        val b = ByteArrayOutputStream()
        fun p1(v: Int) = b.write(v and 0xFF)
        fun p2(v: Int) { p1(v shr 8); p1(v) }
        fun p3(v: Int) { p1(v shr 16); p2(v) }
        fun p4(v: Int) { p2(v shr 16); p2(v) }
        fun str(s: String) { b.write(s.toByteArray(Charsets.ISO_8859_1)); p1(0) }
    }

    /** Same layout as [LootKeyInterfaceImportTool.encode] (667 Component.decode, version 255), plus the layer scroll size. */
    fun encode(c: C): ByteArray {
        val o = Out()
        // CLUE: type flag 0x40 extends only MODEL's id to g4. Legacy encodings stay byte-identical.
        val wideModel = c.type == 6 && ModelArchive.isWide(c.model)
        require(c.model <= ModelArchive.MAX_MODEL_ID) { "Interface model ${c.model} exceeds the model namespace" }
        o.p1(255); o.p1(c.type or if (wideModel) 0x40 else 0); o.p2(0)
        o.p2(c.x); o.p2(c.y); o.p2(c.w); o.p2(c.h)
        o.p1(c.wMode); o.p1(c.hMode); o.p1(c.xMode); o.p1(c.yMode)
        o.p2(if (c.parent < 0) 65535 else c.parent)
        o.p1(if (c.hidden) 1 else 0)
        when (c.type) {
            0 -> { o.p2(c.scrollW); o.p2(c.scrollH); o.p1(if (c.noClickThrough) 1 else 0) }
            // 667 Component.decode TYPE_GRAPHIC: graphic, angle, flags (tiling), transparency, outline, shadow, horizontalFlip, verticalFlip.
            5 -> { o.p4(c.sprite); o.p2(0); o.p1(if (c.tiling) 1 else 0); o.p1(c.opacity); o.p1(0); o.p4(0); o.p1(if (c.flipH) 1 else 0); o.p1(0); o.p4(0) }
            4 -> { o.p2(if (c.font < 0) 65535 else c.font); o.str(c.text); o.p1(c.lineHeight); o.p1(c.alignX); o.p1(c.alignY); o.p1(if (c.shadow) 1 else 0); o.p4(c.colour); o.p1(c.opacity) }
            3 -> { o.p4(c.colour); o.p1(if (c.filled) 1 else 0); o.p1(c.opacity) }
            9 -> { o.p1(c.lineWidth); o.p4(c.colour); o.p1(0) }
            6 -> {
                // 667 Component.decode (client Component.java:824): flag 0x1 = the 2D camera block (origin x/y, xan/yan/zan, zoom) follows,
                // 0x4 = orthographic. The camera block is always written below, so 0x1 is always set (07-11: writing 0 for a
                // non-orthographic model shifted every later field - interface 771 comp 3 read past its end).
                if (wideModel) o.p4(c.model) else o.p2(if (c.model < 0) 65535 else c.model)
                o.p1(0x1 or if (c.ortho) 0x4 else 0)
                o.p2(c.originX); o.p2(c.originY); o.p2(c.xan); o.p2(c.yan); o.p2(c.zan); o.p2(c.zoom); o.p2(65535)
                if (c.wMode != 0) o.p2(0)
                if (c.hMode != 0) o.p2(0)
            }
            else -> error("unsupported type ${c.type}")
        }
        o.p3(0); o.p1(0); o.str(c.opBase); o.p1(c.ops.size); c.ops.forEach { o.str(it) }
        o.str(""); o.p1(0); o.p1(0); o.p1(0); o.str("")
        repeat(20) { hook ->
            val arguments = when (hook) { 1 -> c.mouseOver; 2 -> c.mouseLeave; else -> emptyList() }
            o.p1(arguments.size)
            arguments.forEach { o.p1(0); o.p4(it) }
        }
        repeat(5) { o.p1(0) }
        return o.b.toByteArray()
    }

    class Ported(val osrsGroup: Int, val components: List<C>, val extras: List<String>, val reports: List<String>, val sprites: Set<Int>)

    /** One invocation struct (enum 4664 order): param 1160 name, 1161 category, 1162 raid level, 1262 text, 1297 / 1298 active / inactive icon. */
    class Invocation(val order: Int, val name: String, val category: Int, val raidLevel: Int, val text: String, val iconOn: Int, val iconOff: Int)

    /** ChatGPT-A 10-invocations.tsv (cache 2686 enum 4664 -> structs); category 15 = Event/Leagues, not shown on a normal world (6609/6749). */
    fun invocations(): List<Invocation> {
        val file = File(RspsPaths.root("team/dossiers/N462-notities/toa/10-invocations.tsv"))
        val mapper = ObjectMapper()
        val order = HashMap<String, Int>()
        val params = HashMap<String, JsonNode>()
        file.readLines().drop(1).forEach { line ->
            val f = line.split('\t')
            if (f.size < 3) return@forEach
            when (f[1]) {
                "order" -> order[f[0]] = f[2].trim().toInt()
                "params" -> params[f[0]] = mapper.readTree(f[2].trim().removeSurrounding("\"").replace("\"\"", "\""))
            }
        }
        return params.map { (name, p) ->
            Invocation(order.getValue(name), p["1160"].asText(), p["1161"].asInt(), p["1162"].asInt(), p["1262"].asText(), p["1297"].asInt(), p["1298"].asInt())
        }.sortedBy { it.order }.filter { it.category != 15 }
    }

    fun port(group: Int, node: JsonNode, fonts: Map<Int, Int>): Ported {
        val list = ArrayList<C>()
        val extras = ArrayList<String>()
        val reports = ArrayList<String>()
        val sprites = LinkedHashSet<Int>()
        // An onLoad script can size a component the loop has not added yet (776: component 0 runs 6609 for component 5), so such
        // writes wait until every component of the group is in the list.
        val afterComponents = ArrayList<() -> Unit>()
        val ids = node.fieldNames().asSequence().map { it.toInt() }.sorted().toList()
        require(ids == (0 until ids.size).toList()) { "group $group: component ids not 0..${ids.size - 1}" }
        var next = ids.size
        fun i(n: JsonNode, k: String, d: Int = 0) = if (n.has(k) && !n[k].isNull) n[k].asInt() else d
        fun font(osrs: Int): Int = fonts[osrs] ?: error("group $group: OSRS font $osrs has no 667 counterpart")
        /** [proc,steelborder](component, title, 0) as static children of [p]; returns the title text component. */
        fun steelborder(p: Int, title: String): Int {
            fun g(x: Int, y: Int, xm: Int, ym: Int, w: Int, h: Int, wm: Int, hm: Int, s: Int, ops: List<String> = emptyList()) {
                sprites += s
                list += C(next, 5, x, y, w, h, p, wm, hm, xm, ym, false, sprite = s, tiling = true, ops = ops)
                next++
            }
            val first = next
            g(0, 0, 1, 1, 2, 2, 1, 1, TRADEBACKING)
            val titleId = next
            list += C(next, 4, 0, 6, 12, 24, p, 1, 0, 1, 0, false, font = font(496), text = title, alignX = 1, alignY = 1, shadow = true, colour = 0xff981f)
            next++
            g(0, 0, 0, 0, 25, 30, 0, 0, STEELBORDER[0]); g(0, 0, 2, 0, 25, 30, 0, 0, STEELBORDER[1])
            g(0, 0, 0, 2, 25, 30, 0, 0, STEELBORDER[2]); g(0, 0, 2, 2, 25, 30, 0, 0, STEELBORDER[3])
            g(-15, 0, 0, 1, 36, 60, 0, 1, MISCGRAPHICS_2); g(-15, 0, 2, 1, 36, 60, 0, 1, STEELBORDER2[1])
            g(0, -15, 1, 0, 50, 36, 1, 0, STEELBORDER2[0]); g(0, -15, 1, 2, 50, 36, 1, 0, MISCGRAPHICS_3)
            g(0, 35 - 21, 1, 0, 10, 26, 1, 0, STEELBORDER_DIVIDER)
            sprites += CLOSE_BUTTON_HOVER
            val close = next
            g(3, 6, 2, 0, 26, 23, 0, 0, CLOSE_BUTTON, ops = listOf("Close"))
            extras += "steelborder parent=$p components=$first..${next - 1} title=$titleId '$title' close=$close (op 1 Close; hover 536 not swapped)"
            return titleId
        }
        /**
         * [proc,script1558]/[proc,script1560] ([proc,v2_stone_button_out_filled] 208 or, [pressed], v2_stone_button_in): a button layer at
         * (x, y, w, h) in [p] carrying op 1 = [label] (if_setop(1, removetags(label))) - or no op when [op] is false - with tradebacking_light,
         * the 9-slice frame and the p12 0xff981f centred shadowed label. 11 components; returns the layer (the click target).
         */
        fun stoneButton(p: Int, x: Int, y: Int, w: Int, h: Int, xm: Int, ym: Int, label: String, pressed: Boolean = false,
                        hidden: Boolean = false, op: Boolean = true, text: String = label): Int {
            val layer = next
            list += C(next++, 0, x, y, w, h, p, 0, 0, xm, ym, hidden, ops = if (op) listOf(label.replace(Regex("<[^>]*>"), "")) else emptyList())
            val frame = if (pressed) intArrayOf(921, 922, 923, 924, 925, 926, 927, 928) else STONE_BUTTON
            fun s(sx: Int, sy: Int, sw: Int, sh: Int, wm: Int, hm: Int, xm2: Int, ym2: Int, sprite: Int) {
                sprites += sprite
                list += C(next++, 5, sx, sy, sw, sh, layer, wm, hm, xm2, ym2, false, sprite = sprite, tiling = true)
            }
            s(0, 0, 2, 2, 1, 1, 1, 1, TRADEBACKING_LIGHT)
            s(0, 0, 9, 9, 0, 0, 0, 0, frame[0]); s(w - 9, 0, 9, 9, 0, 0, 0, 0, frame[1])
            s(0, h - 9, 9, 9, 0, 0, 0, 0, frame[2]); s(w - 9, h - 9, 9, 9, 0, 0, 0, 0, frame[3])
            s(0, 9, 9, h - 18, 0, 0, 0, 0, frame[4]); s(9, 0, w - 18, 9, 0, 0, 0, 0, frame[5])
            s(w - 9, 9, 9, h - 18, 0, 0, 0, 0, frame[6]); s(9, h - 9, w - 18, 9, 0, 0, 0, 0, frame[7])
            list += C(next++, 4, 0, 0, 0, 0, layer, 1, 1, 1, 1, false, font = font(495), text = text, alignX = 1, alignY = 1,
                shadow = true, colour = 0xff981f)
            return layer
        }
        /** Replaces OSRS component [id] (already in the list) by a copy with [ops] - the click target of a server-drawn tab/button. */
        fun withOps(id: Int, ops: List<String>) {
            val index = list.indexOfFirst { it.id == id }
            val o = list[index]
            list[index] = C(o.id, o.type, o.x, o.y, o.w, o.h, o.parent, o.wMode, o.hMode, o.xMode, o.yMode, o.hidden, o.scrollW, o.scrollH,
                o.noClickThrough, ops = ops)
        }
        for (id in ids) {
            val n = node[id.toString()]
            val type = i(n, "type")
            val parent = i(n, "parent", 65535).let { if (it == 65535) -1 else it }
            val ops = n["actions"]?.map { if (it.isNull) "" else it.asText() }?.dropLastWhile { it.isEmpty() } ?: emptyList()
            val common = { t: Int -> C(id, t, i(n, "x"), i(n, "y"), i(n, "width"), i(n, "height"), parent, i(n, "widthMode"), i(n, "heightMode"),
                i(n, "xMode"), i(n, "yMode"), i(n, "hidden") != 0, i(n, "scrollWidth"), i(n, "scrollHeight"), i(n, "noClickThrough") != 0,
                ops = ops, opBase = n["name"]?.asText().orEmpty()) }
            val c = when (type) {
                0 -> common(0)
                5 -> common(5).let { b -> C(id, 5, b.x, b.y, b.w, b.h, parent, b.wMode, b.hMode, b.xMode, b.yMode, b.hidden, ops = ops, opBase = b.opBase,
                    sprite = i(n, "sprite", -1), tiling = i(n, "tiling") != 0, opacity = i(n, "opacity")) }.also { if (it.sprite >= 0) sprites += it.sprite
                    if (i(n, "angle") != 0 || i(n, "flipH") != 0 || i(n, "flipV") != 0 || i(n, "shadow") != 0 || i(n, "border") != 0) reports += "$group:$id graphic angle/flip/shadow/border dropped" }
                4 -> C(id, 4, i(n, "x"), i(n, "y"), i(n, "width"), i(n, "height"), parent, i(n, "widthMode"), i(n, "heightMode"), i(n, "xMode"), i(n, "yMode"),
                    i(n, "hidden") != 0, ops = ops, opBase = n["name"]?.asText().orEmpty(), font = font(i(n, "font", 495)), text = n["text"]?.asText().orEmpty(),
                    lineHeight = i(n, "lineHeight"), alignX = i(n, "alignX"), alignY = i(n, "alignY"), shadow = i(n, "textShadow") != 0,
                    colour = i(n, "colour"), opacity = i(n, "opacity"))
                3 -> C(id, 3, i(n, "x"), i(n, "y"), i(n, "width"), i(n, "height"), parent, i(n, "widthMode"), i(n, "heightMode"), i(n, "xMode"), i(n, "yMode"),
                    i(n, "hidden") != 0, ops = ops, opBase = n["name"]?.asText().orEmpty(), colour = i(n, "colour"), filled = i(n, "filled") != 0, opacity = i(n, "opacity"))
                9 -> C(id, 9, i(n, "x"), i(n, "y"), i(n, "width"), i(n, "height"), parent, i(n, "widthMode"), i(n, "heightMode"), i(n, "xMode"), i(n, "yMode"),
                    i(n, "hidden") != 0, ops = ops, colour = i(n, "colour"), lineWidth = i(n, "lineWidth", 1).coerceAtLeast(1))
                6 -> {
                    val osrsModel = i(n, "model", -1)
                    val imported = if (osrsModel >= 0) localModel(osrsModel) else null
                    // N462: a 667 interface component carries its model as u16 (client Component decoder); a model in archive 37
                    // (id above 0xFFFF) cannot be addressed there, so it is reported like a missing import instead of truncated.
                    val local = imported?.takeIf { !ModelArchive.isWide(it) }
                    if (imported != null && local == null) reports += "$group:$id model $osrsModel -> local $imported is in archive 37 (u16 interface field)"
                    if (local == null) common(0).also { reports += "$group:$id model $osrsModel not imported -> empty layer" }
                    else C(id, 6, i(n, "x"), i(n, "y"), i(n, "width"), i(n, "height"), parent, i(n, "widthMode"), i(n, "heightMode"), i(n, "xMode"),
                        i(n, "yMode"), i(n, "hidden") != 0, ops = ops, opBase = n["name"]?.asText().orEmpty(), model = local,
                        originX = i(n, "offsetX"), originY = i(n, "offsetY"), xan = i(n, "rotX"), yan = i(n, "rotY"), zan = i(n, "rotZ"),
                        zoom = i(n, "zoom"), ortho = i(n, "orthogonal") != 0)
                        .also { if (i(n, "seq", 65535) != 65535) reports += "$group:$id model seq ${i(n, "seq")} dropped" }
                }
                else -> common(0).also { reports += "$group:$id type $type -> empty layer" }
            }
            list += c
            n["events"]?.fields()?.forEach { (event, args) ->
                val script = args[0].asInt()
                if (event == "onLoad" && script == 227) {
                    steelborder(id, args.get(2)?.asText().orEmpty())
                } else if (event == "onLoad" && script == 714) {
                    // [proc,thinbox](component)
                    val first = next
                    list += C(next++, 3, 0, 0, 0, 0, id, 1, 1, 1, 1, false, colour = 0x0e0e0c)
                    list += C(next++, 3, 0, 0, 2, 2, id, 1, 1, 1, 1, false, colour = 0x474745)
                    extras += "thinbox parent=$id components=$first..${next - 1}"
                } else if (event == "onLoad" && script == 1191) {
                    // [clientscript,thinstonebox] -> [proc,thinstonebox](component, 0): two unfilled rectangles 0x383023 / 0x5a5245.
                    val first = next
                    list += C(next++, 3, 0, 0, 0, 0, id, 1, 1, 1, 1, false, colour = 0x383023)
                    list += C(next++, 3, 0, 0, 2, 2, id, 1, 1, 1, 1, false, colour = 0x5a5245)
                    extras += "thinstonebox parent=$id components=$first..${next - 1}"
                } else if (event == "onLoad" && script == 6609) {
                    // [clientscript,toa_invocations_init](scrollbar, invocations) -> [proc,script6610] per invocation: 5 children, row 7*12+3 = 87.
                    val parent = args[2].asInt() and 0xFFFF
                    val first = next
                    var bottom = 0
                    invocations().forEachIndexed { row, inv ->
                        val h = 7 * 12 + 3
                        val y = row * h
                        list += C(next++, 3, 0, y, 0, h, parent, 1, 0, 0, 0, false, colour = 0, filled = true, opacity = if (row % 2 == 0) 200 else 220)
                        list += C(next++, 9, 113, y, 0, h, parent, 0, 0, 0, 0, false, colour = 0x494034)
                        sprites += inv.iconOn
                        list += C(next++, 5, 39, y + (h - 55) / 2, 35, 35, parent, 0, 0, 0, 0, false, sprite = inv.iconOn)
                        list += C(next++, 4, 7, y + 39 + (h - 55) / 2, 97, 20, parent, 0, 0, 0, 0, false, font = font(495), text = inv.name,
                            alignX = 1, alignY = 1, shadow = true, colour = 0xff981f)
                        val rl = inv.raidLevel
                        val text = when {
                            rl < 0 -> "${inv.text}<br><br>Decreases Raid Level by ${-rl}."
                            rl > 0 -> "${inv.text}<br><br>Increases Raid Level by $rl."
                            else -> inv.text
                        }
                        list += C(next++, 4, 121, y + 6, 129, (7 - 1) * 12 + 3, parent, 1, 0, 0, 0, false, font = font(495), text = text,
                            alignX = 0, alignY = 0, shadow = true, colour = 0xff981f)
                        bottom = y + h
                    }
                    val scrollHeight = bottom
                    afterComponents += { list.first { it.id == parent }.scrollH = scrollHeight }
                    extras += "invocation info rows (6609/6610) parent=$parent components=$first..${next - 1} (5 per row: back,line,icon,name,text; " +
                        "order = enum 4664 without category 15) scrollHeight=$bottom; scrollbar ${args[1].asInt() and 0xFFFF} not drawn (mouse wheel scrolls)"
                } else {
                    reports += "$group:$id $event script $script ${args.drop(1).joinToString(",")} NOT RUN (server fills this part)"
                }
            }
        }
        afterComponents.forEach { it() }
        if (group == 481) {
            // [proc,toa_hud_status_init](container, filler) per party slot 0..7 (containers 6+4i, fillers 9+4i): a p12 shadowed text filling
            // the container and a 26x26 tiling graphic at the bottom of the filler. 667 has no server IF_SETSIZE, so the filler graphic keeps
            // its 26x26 and the server slides it down by (26 - height) with IF_SETPOSITION; the 26x26 filler layer clips it.
            val first = next
            for (slot in 0 until 8) {
                list += C(next++, 4, 0, 0, 0, 0, 6 + 4 * slot, 1, 1, 1, 1, false, font = font(495), alignX = 1, alignY = 1, shadow = true, colour = 0xffffff)
                sprites += ORB_FILLER
                list += C(next++, 5, 0, 0, 26, 26, 9 + 4 * slot, 0, 0, 1, 0, false, sprite = ORB_FILLER, tiling = true)
            }
            sprites += ORB_FILLER_FULL; sprites += ORB_FILLER_PRIMARY
            // N462 12-11: [proc,toa_hud_draw] 6579 sets toa_difficulty_icons,1/,2 (4661/4662, Normal/Expert) at run time - only ,0 (4660)
            // is a component default, so without these the server had no id and Normal/Expert kept the Entry icon.
            sprites += 4661; sprites += 4662
            extras += "hud party slots (6577) components=$first..${next - 1} (per slot: text, filler graphic; slot i text=$first+2i, filler=$first+2i+1)"
        }
        if (group == 771) {
            // toa_chests:items (771:10, 92x119): the script that draws the chest's items (inv 811) is not in the dump. ONZEKER: twelve 36x32 item
            // slots, 3 columns x 4 rows (x 0/28/56, y 0/29/58/87) - one roll holds up to 10 lines (3 commons, pet, thread, jewel, clue, 3 kits,
            // remnant; ultrareview #4: 6 slots hid the rest); compare with an OSRS screenshot in the test client.
            val first = next
            for (slot in 0 until 12) list += C(next++, 5, (slot % 3) * 28, (slot / 3) * 29, 36, 32, 10, 0, 0, 0, 0, true)
            extras += "chest items (ONZEKER layout) components=$first..${next - 1}"
        }
        if (group == 778) {
            // [proc,toa_midraidloot_bag_draw] (6784) into toa_midraidloot_bag:items (778:5): one 36x32 item graphic per bag slot at
            // ((i % 4) * 44, (i / 4) * 32), ops Withdraw-1 / Withdraw-5 / Withdraw-All / Drop (+ Examine as op 9 in OSRS; 667 lists ops 1..5
            // here: Examine as op 5). 28 slots pre-made, hidden; the server sets the objects of inv 810 and unhides.
            val first = next
            for (slot in 0 until 28) list += C(next++, 5, (slot % 4) * 44, (slot / 4) * 32, 36, 32, 5, 0, 0, 0, 0, true,
                ops = listOf("Withdraw-1", "Withdraw-5", "Withdraw-All", "Drop", "Examine"))
            extras += "bag slots (6784) components=$first..${next - 1}"
        }
        if (group == 772) {
            // [clientscript,toa_partylist_addline] 6601 / [proc,toa_partylist_fillline] 6602 / [proc,toa_partylist_entry_init] 6603: party row r
            // (772:17+r, 45 rows) = size (0,20) minus/abs at (0, r*20) centre/top in the list 772:16, op "View Party:", a filled background
            // (black, trans 242 even / 228 odd) and texts at the sort-header columns (members 50, name 124, completions 50, invocations 50,
            // raid level 50, difficulty 56; age right-aligned in the rest) p12 0xff981f centred shadowed (age 0x9f9f9f). Rows start hidden;
            // the server fills and shows them. One hidden "No parties are currently listed." text in the list.
            val first = next
            val columns = listOf(0 to 50, 50 to 124, 174 to 50, 224 to 50, 274 to 50, 324 to 56)
            for (r in 0 until 45) {
                val row = 17 + r
                val index = list.indexOfFirst { it.id == row }
                list[index] = C(row, 0, 0, r * 20, 0, 20, 16, 1, 0, 1, 0, true, ops = listOf("View Party:"))
                list += C(next++, 3, 0, 0, 0, 0, row, 1, 1, 1, 1, false, colour = 0, filled = true, opacity = if (r % 2 == 0) 242 else 228)
                columns.forEach { (x, w) ->
                    list += C(next++, 4, x, 0, w, 0, row, 0, 1, 0, 1, false, font = font(495), alignX = 1, alignY = 1, shadow = true, colour = 0xff981f)
                }
                list += C(next++, 4, 0, 0, 380, 0, row, 1, 1, 2, 1, false, font = font(495), alignX = 1, alignY = 1, shadow = true, colour = 0x9f9f9f)
            }
            list.first { it.id == 16 }.scrollH = 45 * 20
            val none = next
            // In the fixed list area 772:15, not the 900-px scroll list 16: centred there it sat 450 px down, out of view (N604 testclient).
            list += C(next++, 4, 0, 0, 0, 0, 15, 1, 1, 1, 1, true, font = font(495), text = "No parties are currently listed.", alignX = 1,
                alignY = 1, shadow = true, colour = 0xff981f)
            extras += "party rows (6601-6603) components=$first..${none - 1} (per row 17+r: background, members, name, completions, invocations, " +
                "raid level, difficulty, age = 8 each from $first + 8r) none-text=$none"
            // N604 (12-11): [proc,toa_partylist_setsort] -> [proc,toa_partylist_sortbutton_draw] per sort header 772:8..14: the arrow
            // "sortarrows,0" (1050) 7x5 at (0, 2) centre/bottom - trans 0 on the default sort (%raids_listsort_list 0 = members), 100 on the
            // others - and the icon 19x18 at (0, 2) centre/top: toa_grouping_icons 0 members, 1 name, 2 completions, 3 invocations,
            // 4 raid level, 14 mode, 5 age (graphic.sym 4565..4570, 4579). Sorting by click is not ported (no op, nothing dead).
            val sortFirst = next
            listOf(8 to 4565, 9 to 4566, 10 to 4567, 11 to 4568, 12 to 4569, 13 to 4579, 14 to 4570).forEach { (header, icon) ->
                sprites += 1050; sprites += icon
                list += C(next++, 5, 0, 2, 7, 5, header, 0, 0, 1, 2, false, sprite = 1050, opacity = if (header == 8) 0 else 100)
                list += C(next++, 5, 0, 2, 19, 18, header, 0, 0, 1, 0, false, sprite = icon)
            }
            extras += "sort headers components=$sortFirst..${next - 1} (per header 8+k: arrow $sortFirst+2k, icon $sortFirst+2k+1)"
            // [proc,script1558] for toa_partylist:refresh (63) "Refresh", :myparty (64) "Make Party" | "My Party" ([proc,toa_partylist_setpartybutton]
            // on %toa_mycontroller) and :filter (65) "Friends/Clan Only" (out; [proc,script4161] = pressed when %toa_partylist_filter = 1).
            // 116x32 each; the two-state ones are two stacked layers, the server shows one.
            val refresh = stoneButton(63, 0, 0, 116, 32, 0, 0, "Refresh")
            val make = stoneButton(64, 0, 0, 116, 32, 0, 0, "Make Party")
            val mine = stoneButton(64, 0, 0, 116, 32, 0, 0, "My Party", hidden = true)
            val filterOut = stoneButton(65, 0, 0, 116, 32, 0, 0, "Friends/Clan Only")
            val filterIn = stoneButton(65, 0, 0, 116, 32, 0, 0, "Friends/Clan Only", pressed = true, hidden = true)
            extras += "buttons (script1558, 11 components each, layer = click target) refresh=$refresh make=$make mine=$mine filterOut=$filterOut filterIn=$filterIn"
        }
        if (group == 777) {
            // [clientscript,toa_midraidloot_init] (6777): per bundle (777:4/7/10, 154x201) [proc,toa_midraidloot_draw_bundle] (6778) = thinbox +
            // one 36x32 item graphic per stack from (20, 40), 44 apart, 3 per row, rows 40 apart (9 pre-made, hidden; the server sets the
            // objects of inv 807/808/809 and unhides); per button (777:6/9/12, 130x28) [proc,toa_midraidloot_drawbutton] (6779) =
            // [proc,v2_stone_button_out_filled] (208) 9-slice + "Choose" p12 0xff981f centred.
            for ((bundle, button) in listOf(4 to 6, 7 to 9, 10 to 12)) {
                val first = next
                list += C(next++, 3, 0, 0, 0, 0, bundle, 1, 1, 1, 1, false, colour = 0x0e0e0c)
                list += C(next++, 3, 0, 0, 2, 2, bundle, 1, 1, 1, 1, false, colour = 0x474745)
                for (slot in 0 until 9) {
                    list += C(next++, 5, 20 + (slot % 3) * 44, 40 + (slot / 3) * 40, 36, 32, bundle, 0, 0, 0, 0, true, ops = listOf("Examine"))
                }
                val w = 130; val h = 28
                fun s(x: Int, y: Int, ww: Int, hh: Int, wm: Int, hm: Int, xm: Int, ym: Int, sprite: Int) {
                    sprites += sprite
                    list += C(next++, 5, x, y, ww, hh, button, wm, hm, xm, ym, false, sprite = sprite, tiling = true)
                }
                s(0, 0, 2, 2, 1, 1, 1, 1, TRADEBACKING_LIGHT)
                s(0, 0, 9, 9, 0, 0, 0, 0, STONE_BUTTON[0]); s(w - 9, 0, 9, 9, 0, 0, 0, 0, STONE_BUTTON[1])
                s(0, h - 9, 9, 9, 0, 0, 0, 0, STONE_BUTTON[2]); s(w - 9, h - 9, 9, 9, 0, 0, 0, 0, STONE_BUTTON[3])
                s(0, 9, 9, h - 18, 0, 0, 0, 0, STONE_BUTTON[4]); s(9, 0, w - 18, 9, 0, 0, 0, 0, STONE_BUTTON[5])
                s(w - 9, 9, 9, h - 18, 0, 0, 0, 0, STONE_BUTTON[6]); s(9, h - 9, w - 18, 9, 0, 0, 0, 0, STONE_BUTTON[7])
                list += C(next++, 4, 0, 0, 0, 0, button, 1, 1, 1, 1, false, font = font(495), text = "Choose", alignX = 1, alignY = 1,
                    shadow = true, colour = 0xff981f)
                extras += "supply bundle $bundle components=$first..${next - 1} (thinbox 2, items 9 = ${first + 2}..${first + 10}, button $button: 9 sprites + text)"
            }
        }
        if (group == 482) {
            // N462 Raid Summary player table: OSRS builds one row per member into the empty column layers 482:46..50 (titles, deaths,
            // damage taken, damage dealt, name) with a script our dump does not hold. Here 8 pre-made rows per column (party max 8),
            // hidden until the server fills them. ONZEKER (no source script): row pitch 20 below the 24-px header line (482:51 y=24),
            // p11 0xff981f centred - checked against the wiki screenshot in the testclient.
            val first = next
            // Names go in 482:45 (wmode 1: parent width - 274 = 102, full height); 482:50 is only the 24-px 'Name' header cell and
            // clipped every row (testclient round 11: empty name column). Cache2686 482:45..50 decoded with tools/cache667/osrs_if3.py.
            for (column in listOf(45, 49, 48, 47, 46)) {
                for (row in 0 until 8) {
                    list += C(next++, 4, 0, 26 + row * 20, 0, 18, column, 1, 0, 0, 0, true, font = font(494), text = "", alignX = 1,
                        alignY = 1, shadow = true, colour = 0xff981f)
                }
            }
            extras += "raid summary rows components=$first..${next - 1} (columns name 45, dealt 49, taken 48, deaths 47, title 46; 8 each, column c row r = $first + 8c + r)"
        }
        if (group == 774) {
            // [clientscript,toa_partydetails_addmember]: ~steelborder(toa_partydetails:frame (774:3), "Party of <leader>" | "Party Details", 0).
            steelborder(3, "Party Details")
            // [proc,script6749]/[proc,script6750] into toa_partydetails:invocations_content (774:52), the default side-panel layout
            // (%toa_invocations_layout_rewards_hidden 0/1): 3 columns 96 apart from x 4, rows 74, tile 82x63, icon +24,+10, name -7,+47 p11.
            // Built inactive (1298 icon, 0x9f9f9f, op "Activate"); the server switches a chosen one to 1297 / 0xd39f34 / "Deactivate".
            val parent = 52
            val first = next
            var y = 0
            invocations().forEachIndexed { i, inv ->
                val x = 4 + (i % 3) * 96
                y = (i / 3) * 74
                sprites += inv.iconOn; sprites += inv.iconOff
                list += C(next++, 3, x + 1, y + 4, 82, 63, parent, 0, 0, 0, 0, false, colour = 0xffffff, filled = true, opacity = 255,
                    ops = listOf("Activate"), opBase = "<col=ff9040>${inv.name}</col>")
                list += C(next++, 5, x + 24, y + 10, 35, 35, parent, 0, 0, 0, 0, false, sprite = inv.iconOff, opacity = 55)
                list += C(next++, 4, x - 7, y + 47, 97, 20, parent, 0, 0, 0, 0, false, font = font(494), text = inv.name, alignX = 1, alignY = 1,
                    shadow = true, colour = 0x9f9f9f)
            }
            list.first { it.id == parent }.scrollH = y + 73
            extras += "invocation tiles (6749/6750) parent=$parent components=$first..${next - 1} (3 per invocation: click rect 'Activate'/'Deactivate', " +
                "icon 1298 off / 1297 on, name 0x9f9f9f off / 0xd39f34 on) scrollHeight=${y + 73}"
            // N604 (12-11) - the members/applicants view (owner 11-06 A: no invocations; the leader picks the mode at the obelisk).
            // [proc,toa_partydetails_back_button] (6765): "menu_buttons,4" (1021) 21x21 in toa_partydetails:back (774:4). ONZEKER: its label
            // comes from a server script call not in the dump -> op "Back".
            val back = next
            sprites += 1021
            list += C(next++, 5, 0, 0, 21, 21, 4, 0, 0, 1, 1, false, sprite = 1021, ops = listOf("Back"))
            // [proc,toa_partydetails_cleartabs] -> [proc,script3723]/[proc,script3726] per tab (members 774:6, applicants 774:7): left cap 20x21,
            // tiled middle (w-40)x21 from x 20, right cap h-flipped, p12 text at (0,1) size (0,19) minus/abs. Not selected = tabs_modal_short,2/3
            // (2566/2567) text 0xff981f; selected = ,0/,1 (2564/2565) text 0xffa82f - the server swaps sprites + colour. Op 1 = the tab label.
            val tabFirst = next
            listOf(6 to "Members", 7 to "Applicants").forEach { (tab, label) ->
                withOps(tab, listOf(label))
                sprites += 2564; sprites += 2565; sprites += 2566; sprites += 2567
                list += C(next++, 5, 0, 0, 20, 21, tab, 0, 0, 0, 0, false, sprite = 2566)
                list += C(next++, 5, 20, 0, 40, 21, tab, 1, 0, 0, 0, false, sprite = 2567, tiling = true)
                list += C(next++, 5, 0, 0, 20, 21, tab, 0, 0, 2, 0, false, sprite = 2566, flipH = true)
                list += C(next++, 4, 0, 1, 0, 19, tab, 1, 0, 1, 0, false, font = font(495), text = label, alignX = 1, alignY = 1, shadow = true,
                    colour = 0xff981f)
            }
            extras += "tabs components=$tabFirst..${next - 1} (members 6 / applicants 7: left, middle, right, text = 4 each)"
            // [proc,toa_partydetails_header_icon_draw] (6769): a 19x18 graphic centred in each column header. ONZEKER mapping (checked in the test
            // client): name = toa_grouping_icons,1 (NAME), combat..prayer = ,6..,13, completions = ,2.
            val headerFirst = next
            val headerIcons = listOf(4566, 4571, 4572, 4573, 4574, 4575, 4576, 4577, 4578, 4567)
            for (base in listOf(21, 37)) headerIcons.forEachIndexed { k, icon ->
                sprites += icon
                list += C(next++, 5, 0, 0, 19, 18, base + k, 0, 0, 1, 1, false, sprite = icon)
            }
            extras += "header icons components=$headerFirst..${next - 1} (members 21..30 then applicants 37..46)"
            // [clientscript,toa_partydetails_addmember] (6722) / [clientscript,toa_partydetails_addapplicant] (6727): 8 rows of 22 px each in
            // toa_partydetails:members_list (774:32) and :applicants_list (774:48): a black filled background (trans 246 even / 228 odd), a click
            // rect for the leader's ops (members: "Kick", own row "Quit Party"; applicants: "Accept", "Reject"; hidden until the viewer leads)
            // and 10 p11 centred shadowed cells at the header columns (name 114, combat..prayer 31/23x7, completions 111).
            val widths = listOf(114, 31, 23, 23, 23, 23, 23, 23, 23, 111)
            val rowFirst = next
            for ((listComp, applicants) in listOf(32 to false, 48 to true)) {
                for (r in 0 until 8) {
                    list += C(next++, 3, 0, r * 22, 0, 22, listComp, 1, 0, 1, 0, true, colour = 0, filled = true, opacity = if (r % 2 == 0) 246 else 228)
                    val ops = when { applicants -> listOf("Accept", "Reject"); r == 0 -> listOf("Quit Party"); else -> listOf("Kick") }
                    list += C(next++, 3, 0, r * 22, 0, 22, listComp, 1, 0, 1, 0, true, colour = 0, filled = false, opacity = 255, ops = ops)
                    var x = 0
                    widths.forEach { w ->
                        list += C(next++, 4, x, r * 22, w, 22, listComp, 0, 0, 0, 0, true, font = font(494), alignX = 1, alignY = 1, shadow = true,
                            colour = 0xff981f)
                        x += w
                    }
                }
            }
            extras += "rows components=$rowFirst..${next - 1} (members 774:32 then applicants 774:48, 8 rows x 12: background, op rect, 10 cells)"
            // [proc,toa_partydetails_bottom_modify] (6730) in toa_partydetails:bottom (774:60), buttons y 2 h 32 gap 4: a viewer who is not the
            // leader gets Refresh | Apply / Withdraw / Leave (Apply greyed when he cannot apply) | Completions text (96, 96, 114); the leader
            // Refresh | Disband | Unblock | Completions with op "Set" (64, 64, 64, 109). The server shows one layout.
            val bottomFirst = next
            stoneButton(60, 0, 2, 96, 32, 0, 0, "Refresh", hidden = true)
            stoneButton(60, 100, 2, 96, 32, 0, 0, "Apply", hidden = true)
            stoneButton(60, 100, 2, 96, 32, 0, 0, "Withdraw", hidden = true)
            stoneButton(60, 100, 2, 96, 32, 0, 0, "Leave", hidden = true)
            stoneButton(60, 100, 2, 96, 32, 0, 0, "Apply", hidden = true, op = false, text = "<col=7f7f7f><str=9f9f9f>Apply</str></col>")
            stoneButton(60, 0, 2, 64, 32, 0, 0, "Refresh", hidden = true)
            stoneButton(60, 68, 2, 64, 32, 0, 0, "Disband", hidden = true)
            stoneButton(60, 136, 2, 64, 32, 0, 0, "Unblock", hidden = true)
            // [proc,script6740]/[proc,script6741]/[proc,script6742]: "Completions: -" | "Completions: <n>" | leader "Set Completions" (op "Set").
            list += C(next++, 4, 200, 2, 114, 32, 60, 0, 0, 0, 0, true, font = font(495), text = "Completions: -", alignX = 1, alignY = 1, shadow = true,
                colour = 0xff981f)
            list += C(next++, 4, 204, 2, 109, 32, 60, 0, 0, 0, 0, true, font = font(495), text = "Set Completions", alignX = 1, alignY = 1, shadow = true,
                colour = 0xff981f, ops = listOf("Set"), opBase = "<col=ff9040>Completions</col>")
            extras += "bottom components=$bottomFirst..${next - 1} (8 buttons x 11: refresh, apply, withdraw, leave, apply-grey, leader refresh, " +
                "disband, unblock; then completions text, leader completions text)"
            // Owner 11-06 A (3 modes instead of invocations): the leader's "Set" on the mode text toa_partydetails:raid_mode (774:74), the same
            // control as the Completions "Set" ([proc,script6742]) - a hidden click rect over it, shown for the leader only.
            val modeText = list.first { it.id == 74 }
            val modeSet = next
            list += C(next++, 3, modeText.x, modeText.y, modeText.w, modeText.h, modeText.parent, modeText.wMode, modeText.hMode, modeText.xMode,
                modeText.yMode, true, colour = 0, filled = false, opacity = 255, ops = listOf("Set"), opBase = "<col=ff9040>Mode</col>")
            extras += "mode set rect=$modeSet"
            extras += "back=$back"
        }
        return Ported(group, list, extras, reports, sprites)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val groups = args.first().let { if (it == "toa") TOA else it.split(',').map(String::toInt) }
        val apply = "--apply" in args
        val root = ObjectMapper().readTree(JSON)
        val library = CacheLibrary(TARGETS[0])
        val fonts: Map<Int, Int>
        val firstInterface: Int
        val firstSprite: Int
        try {
            val sprIndex = library.index(INDEX_SPRITES)
            fun fontId(name: String) = sprIndex.archive(name)?.id ?: error("font '$name' missing in target cache")
            fonts = mapOf(494 to fontId("p11_full"), 495 to fontId("p12_full"), 496 to fontId("b12_full"), 497 to fontId("q8_full"))
            firstInterface = (library.index(INDEX_INTERFACES).archiveIds().maxOrNull() ?: -1) + 1
            firstSprite = maxOf((sprIndex.archiveIds().maxOrNull() ?: -1) + 1, SPRITE_FLOOR)
        } finally {
            library.close()
        }
        val ported = groups.map { g -> port(g, root[g.toString()] ?: error("group $g not in $JSON"), fonts) }
        // --reuse (07-11): re-port onto the interface/sprite ids of the previous run (data/cfg/toa/interfaces.tsv) - the server and the
        // ported scripts already point at them; only groups/sprites not in that table get new ids.
        val previous = if ("--reuse" in args) File(RspsPaths.data("cfg/toa/interfaces.tsv")).takeIf { it.isFile }?.readLines()
            ?.filter { it.isNotBlank() && !it.startsWith("#") }?.map { it.split('\t') }.orEmpty() else emptyList()
        val oldInterfaces = previous.filter { it[0] == "interface" }.associate { it[1].toInt() to it[2].toInt() }
        val oldSprites = previous.filter { it[0] == "sprite" }.associate { it[1].toInt() to it[2].toInt() }
        val spriteMap = LinkedHashMap<Int, Int>()
        var newSprites = 0
        ported.flatMap { it.sprites }.distinct().forEach { s -> spriteMap[s] = oldSprites[s] ?: (firstSprite + newSprites++) }
        var newInterfaces = 0
        val interfaceOf = ported.associate { p -> p.osrsGroup to (oldInterfaces[p.osrsGroup] ?: (firstInterface + newInterfaces++)) }
        val mutations = ArrayList<CacheMutation>()
        ModernCacheReader(File(SOURCE_CACHE)).use { src ->
            spriteMap.forEach { (osrs, local) ->
                mutations += CacheMutation(INDEX_SPRITES, local, 0, src.file(INDEX_SPRITES, osrs, 0) ?: error("OSRS sprite $osrs missing"), "sprite osrs:$osrs -> 667:$local")
            }
        }
        val lines = ArrayList<String>()
        lines += "osrs_group\tlocal_interface\tcomponents\textra\treport"
        // --reuse replaces a component this tool wrote before: the current bytes are the expected ones (a guarded overwrite, not a conflict).
        val current = CacheLibrary(TARGETS[0])
        try {
            ported.forEach { p ->
                val iface = interfaceOf.getValue(p.osrsGroup)
                p.components.forEach { c ->
                    if (c.type == 5 && c.sprite >= 0) c.sprite = spriteMap.getValue(c.sprite)
                    val bytes = encode(c)
                    val old = if (previous.isNotEmpty()) current.data(INDEX_INTERFACES, iface, c.id) else null
                    val expected = old?.takeIf { !it.contentEquals(bytes) }?.let { CacheItemProbeTool.sha1(it) }
                    mutations += CacheMutation(INDEX_INTERFACES, iface, c.id, bytes, "interface $iface:${c.id} (osrs ${p.osrsGroup}) type=${c.type}",
                        expectedCurrentSha1 = expected)
                }
            }
        } finally {
            current.close()
        }
        ported.forEach { p ->
            val iface = interfaceOf.getValue(p.osrsGroup)
            lines += "${p.osrsGroup}\t$iface\t${p.components.size}\t${p.extras.joinToString(" | ")}\t${p.reports.joinToString(" | ")}"
            println("PLAN osrs ${p.osrsGroup} -> interface $iface: ${p.components.size} components")
            p.extras.forEach { println("  EXTRA $it") }
            p.reports.forEach { println("  REPORT $it") }
        }
        spriteMap.forEach { (o, l) -> lines += "sprite\t$o\t$l\t\t" }
        val tx = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = tx.preflight()
        val errors = tx.blockingErrors(plan)
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) { println("DRY_RUN interfaces ${interfaceOf.values.minOrNull()}..${interfaceOf.values.maxOrNull()} sprites ${spriteMap.values.minOrNull()}..${spriteMap.values.maxOrNull()}"); return }
        val result = tx.apply(plan)
        val problems = tx.verify()
        problems.forEach { println("  VERIFY_FAILURE: $it") }
        check(problems.isEmpty()) { "verify failed - journal ${result.journalDir}" }
        REPORT.writeText(lines.joinToString("\n", postfix = "\n"))
        // The server reads the ids at start (activity/toa/ui/ToaInterfaces.kt): interface per OSRS group, sprite per OSRS sprite.
        // N604 (12-11): MERGE into the table - a run for some groups used to overwrite it with only those groups and their sprites, so
        // the server lost the ids of every other ported window (481, 482, 771, 773, 775-778) until the file was merged by hand.
        val table = File(RspsPaths.data("cfg/toa/interfaces.tsv")).apply { parentFile.mkdirs() }
        val merged = LinkedHashMap<Pair<String, String>, String>()
        if (table.isFile) table.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('\t'); if (f.size >= 3) merged[f[0] to f[1]] = line
        }
        ported.forEach { p -> merged["interface" to p.osrsGroup.toString()] = "interface\t${p.osrsGroup}\t${interfaceOf.getValue(p.osrsGroup)}" }
        spriteMap.forEach { (o, l) -> merged["sprite" to o.toString()] = "sprite\t$o\t$l" }
        val lines2 = merged.values.filter { it.startsWith("interface") } + merged.values.filter { it.startsWith("sprite") }
        table.writeText(lines2.joinToString("\n", prefix = "# N462 OsrsInterfacePortTool, last transaction ${tx.id} (merged) - generated, do not edit\n",
            postfix = "\n"))
        println("APPLIED transaction=${tx.id} writes=${result.applied} VERIFY_OK report=$REPORT")
    }
}
