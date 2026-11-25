package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * N72C: append the approved OSRS spells to the existing 667 books. Default is a paired, read-only
 * preflight. Claude owns --apply. Components, sprites, hooks and all three native sort modes are
 * changed in ONE CacheTransaction; no clientscript, filter or autocast table is replaced.
 *
 * Source: OSRS 2686 descriptors/icons, N72-DEELC-ONDERZOEK sections 1/4. New ids are deliberately
 * fixed: a different import taking an id is a conflict, never a reason to silently reallocate it.
 * Four icon sizes/states are staged; native CS2 6 uses the two small states. The client resize
 * implementation can use the two large states from the printed mapping.
 */
object SpellbookCompletionCacheTool {
    const val SPRITE_BASE = 9200
    const val MONSTER_INFO_INTERFACE = 1250
    const val MONSTER_INFO_SPRITE_BASE = 9240
    const val MONSTER_TAB_SELECTED = 1079

    /** Exact OSRS 2686 group 522 decode (ClueWidgetProbe); native 667 encoding and fonts.
     * OSRS scripts 1179/1181 only fill four text components and select/scroll a tab. Those
     * operations are performed by the server through the existing native scrollbar script.
     * No OSRS clientscript is installed. Source component numbers are retained.
     */
    fun monsterInfoPlan(library: CacheLibrary, sourceJson: File? = null): List<CacheMutation> {
        val input = sourceJson?.inputStream() ?: javaClass.getResourceAsStream("/importer/n72c-monster-info-2686.json")
            ?: error("N72C interface source missing; supply --interface-json <2686 decode>")
        // Git core.autocrlf is enabled on the owner's machine. Pin the logical source
        // across Windows checkouts and Linux build resources, not its newline encoding.
        val bytes = input.use { it.readBytes() }.toString(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(digest == "82b7cd98877b7bd392b79f93511c23ae2d54abbdec0ddb46e983431abb3b1255") { "N72C interface source differs from the reviewed 2686 decode" }
        val root = com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes)
        check(root.size() == 28 && root["3"]["text"].asText() == "Monster name")
        val fonts = mapOf(494 to "p11_full", 495 to "p12_full", 496 to "b12_full", 497 to "q8_full")
            .mapValues { (_, name) -> requireNotNull(library.index(8).archive(name)) { "font $name missing" }.id }
        val spriteIds = root.elements().asSequence().filter { it["type"].asInt() == 5 }
            .map { it["spriteId"].asInt() }.plus(sequenceOf(MONSTER_TAB_SELECTED)).distinct().sorted().toList()
        val sprites = spriteIds.mapIndexed { i, id -> id to (MONSTER_INFO_SPRITE_BASE + i) }.toMap()
        val mutations = mutableListOf<CacheMutation>()
        fun create(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
            val old = library.data(index, group, file)
            require(old == null || old.contentEquals(bytes)) { "$index:$group:$file already reserved by another import" }
            mutations += CacheMutation(index, group, file, bytes, label)
        }
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
            repeat(28) { requireNotNull(source.file(3, 522, it)) { "OSRS 2686 522:$it missing" } }
            sprites.forEach { (osrs, local) -> create(8, local, 0, requireNotNull(source.file(8, osrs, 0)), "N72C NPC-info sprite OSRS $osrs -> $local") }
        }
        repeat(28) { id ->
            val n = root[id.toString()]
            check(n["id"].asInt() == ((522 shl 16) or id) && n["type"].asInt() in setOf(0, 4, 5, 9))
            fun i(key: String) = n[key].asInt()
            fun b(key: String) = n[key].asBoolean()
            val text = id in listOf(20, 22, 24, 26)
            val parent = i("parentId").let { if (it < 0) -1 else it and 65535 }
            val component = OsrsInterfacePortTool.C(id, i("type"), i("originalX"), i("originalY"), i("originalWidth"),
                if (text) 560 else i("originalHeight"), parent, i("widthMode"), if (text) 0 else i("heightMode"),
                i("xPositionMode"), i("yPositionMode"), b("isHidden"), i("scrollWidth"), i("scrollHeight"), b("noClickThrough"),
                sprite = if (i("type") == 5) sprites.getValue(i("spriteId")) else -1,
                tiling = b("spriteTiling"), opacity = i("opacity"), font = if (i("type") == 4) fonts.getValue(i("fontId")) else -1,
                text = n["text"]?.asText().orEmpty(), lineHeight = i("lineHeight"), alignX = i("xTextAlignment"), alignY = i("yTextAlignment"),
                shadow = b("textShadowed"), colour = i("textColor"), filled = b("filled"), lineWidth = i("lineWidth"),
                ops = n["actions"]?.map { it.asText() }.orEmpty())
            val bytes = OsrsInterfacePortTool.encode(component)
            // Ops in the existing encoder are enabled with the normal server interface-events packet.
            create(3, MONSTER_INFO_INTERFACE, id, bytes, "N72C NPC-info OSRS522:$id -> $MONSTER_INFO_INTERFACE:$id")
        }
        println("PLAN N72C NPC-info sprites=$sprites selected=${sprites.getValue(MONSTER_TAB_SELECTED)}")
        return mutations
    }

    data class Spell(
        val book: Int, val component: Int, val template: Int, val name: String, val level: Int,
        val description: String, val runes: List<Pair<Int, Int>>, val icons: List<Int>,
    ) {
        val packed: Int get() = (book shl 16) or component
    }

    val SPELLS = listOf(
        Spell(192, 101, 29, "Jewellery Enchantments", 7, "Choose a jewellery enchantment", emptyList(), listOf(366, 416, 1803, 1883)),
        Spell(192, 100, 47, "Monster Inspect", 42, "View a monster's combat information", listOf(559 to 2, 558 to 2), listOf(368, 418, 1805, 1885)),
        Spell(192, 102, 88, "Lvl-7 Enchant", 93, "For use on zenyte jewellery", listOf(564 to 1, 566 to 20, 565 to 20), listOf(361, 411, 1771, 1851)),
        Spell(192, 103, 40, "Minigame Teleport", 1, "Teleport to a minigame once every 20 minutes", emptyList(), listOf(7479, 7504, 1808, 1888)),
        Spell(193, 55, 40, "Minigame Teleport", 1, "Teleport to a minigame once every 20 minutes", emptyList(), listOf(7479, 7504, 1808, 1888)),
        Spell(430, 80, 45, "Spin Flax", 76, "Turn flax into bowstring", listOf(9075 to 1, 561 to 2, 556 to 5), listOf(585, 635, 1979, 2029)),
        Spell(430, 81, 45, "Tan Leather", 78, "Tan up to 5 hides", listOf(9075 to 2, 561 to 1, 554 to 5), listOf(583, 633, 1977, 2027)),
        Spell(430, 82, 45, "Recharge Dragonstone", 89, "Recharges enchanted dragonstone jewellery", listOf(9075 to 1, 566 to 1, 555 to 4), listOf(584, 634, 1978, 2028)),
        Spell(430, 83, 45, "Geomancy", 65, "View the status of your farming patches", listOf(9075 to 3, 561 to 3, 557 to 8), listOf(563, 613, 1967, 2017)),
        Spell(430, 84, 43, "Minigame Teleport", 1, "Teleport to a minigame once every 20 minutes", emptyList(), listOf(7479, 7504, 1808, 1888)),
    )

    val ICONS = SPELLS.flatMap { it.icons }.distinct()
    val DESCRIPTORS = listOf(27089, 28180, 19475, 33138, 33139, 20589, 1741, 2529, 6797, 33141)
    fun sprite(osrs: Int): Int = SPRITE_BASE + ICONS.indexOf(osrs).also { require(it >= 0) }

    /** Inspect uses an NPC-target template, but belongs to the native utility category. */
    fun sortTemplate(spell: Spell): Int = if (spell.name == "Monster Inspect") 38 else spell.template

    private fun hook(args: List<Any>): ByteArray {
        val bytes = ByteArrayOutputStream()
        bytes.write(args.size)
        args.forEach { arg ->
            bytes.write(if (arg is String) 1 else 0)
            if (arg is String) {
                bytes.write(arg.toByteArray(Charsets.ISO_8859_1)); bytes.write(0)
            } else bytes.write(ByteBuffer.allocate(4).putInt(arg as Int).array())
        }
        return bytes.toByteArray()
    }

    private fun withOps(bytes: ByteArray, ops: List<String>): ByteArray {
        val old = InterfaceHookProbeTool.componentOps(bytes)
        require(old.isNotEmpty() && old.none { it == null } && ops.size < 16)
        fun section(labels: List<String?>) = byteArrayOf(labels.size.toByte()) +
            labels.joinToString("") { "$it\u0000" }.toByteArray(Charsets.ISO_8859_1)
        val from = section(old)
        val at = SpellRuneArgsTool.indexOf(bytes, from)
        require(at >= 0 && SpellRuneArgsTool.indexOf(bytes, from, at + 1) < 0)
        return (bytes.copyOfRange(0, at) + section(ops) + bytes.copyOfRange(at + from.size, bytes.size)).also {
            check(InterfaceHookProbeTool.componentOps(it) == ops)
        }
    }

    /** Replace complete decoded hooks, never a coincidental integer/string elsewhere in a component. */
    fun component(original: ByteArray, spell: Spell): ByteArray {
        val hooks = InterfaceHookProbeTool.componentHooks(original)
        require(original[1].toInt() == 5 && original[0].toInt() == -1)
        val oldArgs = hooks["onLoad"] ?: error("missing spell onLoad")
        require(oldArgs.size == 17 && oldArgs.first() == 6)
        val args = oldArgs.toMutableList()
        args[2] = (spell.book shl 16) or when (spell.book) { 192 -> 97; 193 -> 54; 430 -> 66; else -> error("book") }
        args[3] = sprite(spell.icons[0]); args[4] = sprite(spell.icons[1])
        args[5] = spell.level; args[7] = spell.name; args[8] = spell.description
        SpellRuneArgsTool.padded(spell.runes).forEachIndexed { index, value -> args[9 + index] = value }
        var bytes = original.copyOf()
        // Every copy uses the source book's own tooltip/hover/target infrastructure.
        hooks.values.filterNotNull().distinct().filter { it == oldArgs }.forEach { old ->
            val from = hook(old); val to = hook(args)
            var offset = SpellRuneArgsTool.indexOf(bytes, from)
            require(offset >= 0)
            while (offset >= 0) {
                bytes = bytes.copyOfRange(0, offset) + to + bytes.copyOfRange(offset + from.size, bytes.size)
                offset = SpellRuneArgsTool.indexOf(bytes, from, offset + to.size)
            }
        }
        ByteBuffer.wrap(bytes).putInt(19, sprite(spell.icons[1]))
        if (spell.name == "Monster Inspect") {
            // Legacy GRAPHIC header ends at 38; events 0x1000 = NPC target only, no Autocast op.
            bytes[38] = 0; bytes[39] = 0x10; bytes[40] = 0
            bytes = withOps(bytes, emptyList())
        }
        // Jewellery menu is a button, not an item-targeting enchantment.
        if (spell.name == "Jewellery Enchantments") {
            // Target fields require a structural encoder, so this component uses the button template
            // supplied by plan(), not the enchant-target template used for its category/layout.
            require(InterfaceHookProbeTool.componentOps(bytes).isNotEmpty())
        }
        if (spell.name == "Tan Leather") {
            check(InterfaceHookProbeTool.componentOps(bytes) == listOf("Cast"))
            bytes = withOps(bytes, listOf("Cast", "Setup"))
            bytes[38] = 0; bytes[39] = 0; bytes[40] = 6 // Cast + Setup
            check(InterfaceHookProbeTool.componentOps(bytes) == listOf("Cast", "Setup"))
        }
        check(InterfaceHookProbeTool.componentHooks(bytes)["onLoad"] == args)
        return bytes
    }

    /** OSRS Jewellery subpage (5285) + Farming Patch Status (179) expressed with native667 widgets.
     * Menu targets retain native script6 tooltips, inventory/stat refresh and target masks.
     */
    fun restPanelsPlan(library: CacheLibrary): List<CacheMutation> {
        val out = mutableListOf<CacheMutation>()
        val p11 = requireNotNull(library.index(8).archive("p11_full")).id
        val b12 = requireNotNull(library.index(8).archive("b12_full")).id
        fun add(group: Int, id: Int, bytes: ByteArray) {
            val old = library.data(3, group, id)
            require(old == null || old.contentEquals(bytes)) { "panel $group:$id already taken" }
            out += CacheMutation(3, group, id, bytes, "N72C native panel $group:$id", old?.let(CacheItemProbeTool::sha1))
        }
        fun c(group: Int, id: Int, type: Int, x: Int, y: Int, w: Int, h: Int, parent: Int = 0,
              text: String = "", ops: List<String> = emptyList(), scroll: Int = 0, hidden: Boolean = false) {
            add(group, id, OsrsInterfacePortTool.encode(OsrsInterfacePortTool.C(id, type, x, y, w, h, parent,
                0, 0, 0, 0, hidden, scrollH = scroll, font = if (type == 4) p11 else -1,
                text = text, lineHeight = 14, colour = 0xff981f, shadow = true, ops = ops)))
        }
        c(1251, 0, 0, 0, 0, 190, 261, -1)
        c(1251, 1, 3, 0, 0, 190, 261)
        c(1251, 2, 4, 10, 35, 43, 30, text = "Back", ops = listOf("Back"))
        val native = listOf(29, 41, 53, 61, 76, 88, 102)
        native.forEachIndexed { i, id ->
            var bytes = if (id == 102) component(requireNotNull(library.data(3, 192, 88)), SPELLS.first { it.name == "Lvl-7 Enchant" })
                else requireNotNull(library.data(3, 192, id)).copyOf()
            val old = requireNotNull(InterfaceHookProbeTool.componentHooks(bytes)["onLoad"])
            val args = old.toMutableList().also { it[2] = 1251 shl 16 }
            // script6 is both onLoad and onVarTransmit. Replace both complete hooks structurally.
            val from = hook(old); val to = hook(args)
            var at = SpellRuneArgsTool.indexOf(bytes, from)
            while (at >= 0) {
                bytes = bytes.copyOfRange(0, at) + to + bytes.copyOfRange(at + from.size, bytes.size)
                at = SpellRuneArgsTool.indexOf(bytes, from, at + to.size)
            }
            val cell = i + 1
            ByteBuffer.wrap(bytes).putShort(4, (20 + (cell % 3) * 64).toShort()).putShort(6, (35 + (cell / 3) * 64).toShort())
                .putShort(8, 32).putShort(10, 32).putShort(16, 0)
            check(InterfaceHookProbeTool.componentHooks(bytes)["onLoad"] == args)
            add(1251, 3 + i, bytes)
        }
        // A native six-tab, three-column scrolling overview, with real per-player patch data filled by Geomancy.
        // No source scripts with hardcoded OSRS interface hashes are copied into the667 script table.
        c(1252, 0, 0, 0, 0, 490, 334, -1)
        c(1252, 1, 4, 8, 5, 430, 22, text = "Farming Patch Status")
        c(1252, 2, 4, 463, 5, 22, 22, text = "X", ops = listOf("Close"))
        c(1252, 3, 0, 8, 65, 456, 260, scroll = 1800)
        c(1252, 4, 0, 465, 65, 16, 260)
        listOf("Allotments", "Herbs", "Hops", "Bushes", "Trees", "Special").forEachIndexed { i, name ->
            c(1252, 5 + i, 4, 10 + i * 79, 38, 78, 22, text = name, ops = listOf("Select"))
        }
        // 40 cards cover every one of the34 existing patches and4 compost bins.
        repeat(40) { i ->
            c(1252, 11 + i * 3, 0, (i % 3) * 152, (i / 3) * 125, 150, 123, parent = 3, hidden = true)
            c(1252, 12 + i * 3, 4, 3, 1, 145, 120, parent = 11 + i * 3)
            c(1252, 13 + i * 3, 3, 0, 0, 150, 123, parent = 11 + i * 3)
        }
        return out
    }

    fun plan(sourceJson: File? = null, monsterInfoOnly: Boolean = false, restReady: Boolean = false): List<CacheMutation> {
        val mutations = mutableListOf<CacheMutation>()
        val activeSpells = when {
            monsterInfoOnly -> SPELLS.filter { it.name == "Monster Inspect" }
            restReady -> SPELLS.filter { it.name in setOf("Jewellery Enchantments", "Lvl-7 Enchant", "Geomancy", "Minigame Teleport") }
            else -> SPELLS
        }
        val descriptions = mutableMapOf<Int, String>()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            fun add(index: Int, group: Int, file: Int, bytes: ByteArray, description: String) {
                val old = library.data(index, group, file)
                mutations += CacheMutation(index, group, file, bytes, description, old?.let(CacheItemProbeTool::sha1))
            }
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
                SPELLS.zip(DESCRIPTORS).filter { it.first in activeSpells }.forEach { (spell, id) ->
                    val params = ModernItemDefDecoder.decode(id, requireNotNull(source.file(2, 10, id))).params
                    check(params[601] == spell.name && maxOf(1, params[604] as Int) == spell.level) { "OSRS descriptor $id name/level differs" }
                    val runes = (365..371 step 2).mapNotNull { key -> (params[key] as? Int)?.let { it to (params[key + 1] as Int) } }
                    check(runes.toMap() == spell.runes.toMap()) { "OSRS descriptor $id runes differ: $runes / ${spell.runes}" }
                    check((597..600).map { params[it] } == spell.icons) { "OSRS descriptor $id icons differ" }
                    check(params[336] == when (spell.book) { 192 -> 0; 193 -> 1; else -> 2 }) { "OSRS descriptor $id belongs to another book" }
                    descriptions[spell.packed] = params[602] as String
                }
                activeSpells.flatMap { it.icons }.distinct().forEach { id ->
                    val bytes = requireNotNull(source.file(8, id, 0)) { "missing OSRS sprite $id" }
                    val old = library.data(8, sprite(id), 0)
                    require(old == null || old.contentEquals(bytes)) { "sprite ${sprite(id)} already belongs to another import" }
                    add(8, sprite(id), 0, bytes, "N72C sprite OSRS $id -> ${sprite(id)}")
                }
            }
            // Keep unsupported Lunar slots inert while retaining the reviewed component ids. A gap crashes the 667 client.
            if (restReady) for (id in 80..82) {
                val reserved = OsrsInterfacePortTool.encode(OsrsInterfacePortTool.C(id, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, true))
                val old = library.data(3, 430, id)
                require(old == null || old.contentEquals(reserved)) { "430:$id already has content; review its import before replacing it" }
                add(3, 430, id, reserved, "N72C reserved hidden430:$id (no icon/op/cast until source exists)")
            }
            // 25-11 Claude: a component id with a missing lower id leaves a null in the client's component array -> NPE in
            // executeOnLoad when the book opens (192:101 without 192:100 broke login). Every new id must follow the existing ones.
            activeSpells.groupBy { it.book }.forEach { (book, spells) ->
                val present = (library.index(3).archive(book)?.fileIds()?.toSet() ?: emptySet()) + mutations.filter { it.indexId == 3 && it.groupId == book }.map { it.fileId }
                spells.sortedBy { it.component }.fold(present) { have, spell ->
                    check(spell.component in have || (spell.component - 1) in have) {
                        "$book:${spell.component} would leave a gap (highest existing ${have.maxOrNull()}); import the lower ids first"
                    }
                    have + spell.component
                }
            }
            activeSpells.forEach { spell ->
                val template = if (spell.name == "Jewellery Enchantments") 27 else spell.template
                val original = requireNotNull(library.data(3, spell.book, template))
                // Use the pinned source's exact effect/tooltip text; the owner's no-unlock policy
                // is enforced by server requirements, never by importing OSRS quest/diary hooks.
                val bytes = component(original, spell.copy(description = descriptions.getValue(spell.packed)))
                val existing = library.data(3, spell.book, spell.component)
                require(existing == null || existing.contentEquals(bytes)) { "${spell.book}:${spell.component} already taken" }
                add(3, spell.book, spell.component, bytes, "N72C ${spell.book}:${spell.component} ${spell.name} L${spell.level} icons=${spell.icons.map(::sprite)} runes=${spell.runes} ops=${InterfaceHookProbeTool.componentOps(bytes)}")
            }
            fun enum(id: Int): RemovedContentCacheTool.EnumDef {
                val bytes = requireNotNull(library.data(17, id ushr 8, id and 255))
                return RemovedContentCacheTool.EnumDef.decode(bytes).also { check(it.encode().contentEquals(bytes)) }
            }
            val categories = enum(727)
            activeSpells.forEach { spell -> categories.entries[spell.packed] = categories.entries.getValue((spell.book shl 16) or sortTemplate(spell)) }
            add(17, 727 ushr 8, 727 and 255, categories.encode(), "N72C enum727 categories (no autocast729/1170 changes)")
            val roots = mapOf(192 to 2167, 193 to 2163, 430 to 2171)
            roots.forEach { (book, root) ->
                val leaves = enum(root).entries.values.flatMap { branch -> enum(branch as Int).entries.values.map { it as Int } }.distinct()
                leaves.forEach { leaf ->
                    val def = enum(leaf)
                    val original = def.entries.toMap()
                    val submenu = if (restReady && book == 192) listOf(29, 41, 53, 61, 76, 88, 102).map { (192 shl 16) or it }.toSet() else emptySet()
                    val additions = activeSpells.filter { it.book == book && it.packed !in submenu && ((book shl 16) or sortTemplate(it)) in def.entries.values }
                    if (additions.isNotEmpty() || def.entries.values.any { it in submenu }) {
                        val ordered = def.entries.entries.sortedBy { it.key }.map { it.value as Int }.filterNot { it in submenu }.toMutableList()
                        fun level(packed: Int): Int = SPELLS.firstOrNull { it.packed == packed }?.level ?: run {
                            val bytes = requireNotNull(library.data(3, packed ushr 16, packed and 65535))
                            (InterfaceHookProbeTool.componentHooks(bytes)["onLoad"]?.getOrNull(5) as? Int) ?: 99
                        }
                        additions.forEach { spell ->
                            if (spell.packed !in ordered) {
                                // OSRS Minigame's descriptor level is zero (the actual skill check is 1).
                                // Keep it directly after Home, before level-1 combat spells.
                                val home = (book shl 16) or when (book) { 192 -> 24; 193 -> 48; else -> 39 }
                                val before = if (spell.name == "Minigame Teleport") ordered.indexOf(home).let { if (it < 0) 0 else it + 1 }
                                    else ordered.indexOfFirst { level(it) > spell.level }
                                ordered.add(if (before < 0) ordered.size else before, spell.packed)
                            }
                        }
                        def.entries.clear()
                        ordered.forEachIndexed { i, packed -> def.entries[i * 10] = packed }
                        val rebuilt = RemovedContentCacheTool.EnumDef(def.header, def.trailer, def.tableOpcode, maxOf(def.arraySize, ordered.size * 10), def.entries)
                        if (original != def.entries) add(17, leaf ushr 8, leaf and 255, rebuilt.encode(), "N72C sort enum$leaf add ${additions.map { it.packed }} (existing relative order retained)")
                    }
                }
            }
            if (!restReady) mutations += monsterInfoPlan(library, sourceJson)
            if (restReady) mutations += restPanelsPlan(library)
        }
        finally { library.close() }
        return mutations
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.toList() == listOf("--source-probe")) {
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
                SPELLS.zip(DESCRIPTORS).forEach { (spell, id) ->
                    val def = ModernItemDefDecoder.decode(id, requireNotNull(source.file(2, 10, id)))
                    println("SOURCE ${spell.book}:${spell.component} OSRS$id params=${def.params}")
                }
            }
            return
        }
        val jsonAt = args.indexOf("--interface-json")
        val sourceJson = if (jsonAt >= 0) File(args.getOrNull(jsonAt + 1) ?: error("--interface-json requires a path")) else null
        val modes = args.filterIndexed { i, _ -> i != jsonAt && (jsonAt < 0 || i != jsonAt + 1) }
        require(modes.all { it in setOf("--dry-run", "--apply", "--monster-info-only", "--rest-ready") }) {
            "Usage: [--dry-run|--apply] [--monster-info-only] [--interface-json <2686 decode>]"
        }
        require(!("--apply" in args && "--dry-run" in args))
        require(!("--rest-ready" in args && "--monster-info-only" in args)) { "choose one batch mode" }
        val mutations = plan(sourceJson, "--monster-info-only" in args, "--rest-ready" in args)
        mutations.forEach { println("PLAN ${it.label}") }
        val transaction = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val preflight = transaction.preflight()
        println("PREFLIGHT mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val errors = transaction.blockingErrors(preflight)
        errors.forEach { println("BLOCKED $it") }
        check(errors.isEmpty()) { "paired preflight blocked; nothing written" }
        if ("--apply" !in args) { println("DRY_RUN_OK no cache writes"); return }
        val result = transaction.apply(preflight)
        check(transaction.verify().isEmpty()) { "verify failed: ${transaction.journalDir}" }
        println("APPLIED ${result.applied} skipped=${result.skipped} journal=${result.journalDir}")
    }
}
