package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.N108InterfaceImportTool
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.V
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestIds
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld

/**
 * The Drop Viewer window (N108 + A37, owner design 2026-10-24 GOEDGEKEURD 01-03): left the search field and the groups
 * (Favourites, Deadman, Bosses, Monsters A-Z), right the entry's model, name, favourite star, level + kill count, place labels and
 * "Show on map", the player's own bracket, the Always items and every other line with his chance and the normal chance - rarest
 * first, nested tables folding open in place. What can drop and how likely: [DropSources] (the reward code's own numbers).
 *
 * Typing happens in the window (client RspsUi): every change arrives as `::uisearch <text>` ([search]); a search finds monsters by
 * name and items by name - the item group, then every entry that can give the chosen item with the player's chance.
 */
object DropViewer {
    const val INTERFACE = N108InterfaceImportTool.VIEWER

    val SELECTED = AttributeKey<String>(persistenceKey = "drop_viewer_selected")
    val FAVOURITES = AttributeKey<String>(persistenceKey = "drop_viewer_favourites")
    val RECENT = AttributeKey<String>(persistenceKey = "drop_viewer_recent")

    private val OPEN_GROUPS = AttributeKey<MutableSet<String>>()
    private val OPEN_LINES = AttributeKey<MutableSet<String>>()
    private val QUERY = AttributeKey<String>()
    private val PICKED_ITEM = AttributeKey<Int>()
    private val LIST = AttributeKey<List<Slot>>()
    private val ROWS = AttributeKey<List<String>>()

    private const val EVENTS_OP1 = 0x2

    /** King Black Dragon first (the design's example), else the first boss. */
    private const val DEFAULT_KEY = "npc:50"

    private val DEFAULT_OPEN = setOf("FAVOURITES", "BOSSES")

    /** The Deadman group: the chest and its guardian first, then the breach monsters by name. */
    private val DEADMAN_ORDER = listOf(DropSources.Kind.CHEST, DropSources.Kind.GUARDIAN, DropSources.Kind.BREACH)

    class Slot(val header: Boolean, val key: String, val text: String, val sub: String?, val icon: String?, val open: Boolean, val gold: Boolean, val model: Int, val item: Int = -1, val selected: Boolean = false)

    fun isOpen(player: Player): Boolean = player.interfaces.isVisible(INTERFACE)

    fun favourites(player: Player): List<String> = (player.attr[FAVOURITES] ?: "").split(';').filter { it.isNotBlank() }

    fun recent(player: Player): List<String> = (player.attr[RECENT] ?: "").split(';').filter { it.isNotBlank() }

    private fun openGroups(player: Player): MutableSet<String> = player.attr[OPEN_GROUPS] ?: DEFAULT_OPEN.toMutableSet().also { player.attr[OPEN_GROUPS] = it }

    private fun openLines(player: Player): MutableSet<String> = player.attr[OPEN_LINES] ?: HashSet<String>().also { player.attr[OPEN_LINES] = it }

    fun open(player: Player, key: String? = null) {
        val sources = DropSources.all(player)
        val chosen = DropSources.byKey(player, key) ?: DropSources.byKey(player, player.attr[SELECTED]) ?: DropSources.byKey(player, DEFAULT_KEY) ?: sources.firstOrNull() ?: return
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        N108Ui.reset(player, INTERFACE)
        player.setInterfaceEvents(INTERFACE, V.CLOSE, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, V.SEARCH_HIT, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, V.STAR_ON, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, V.STAR_OFF, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, V.MAP, -1..-1, EVENTS_OP1)
        for (r in 0 until V.ROW_SLOTS) player.setInterfaceEvents(INTERFACE, V.row(r) + V.R_TOGGLE, -1..-1, EVENTS_OP1)
        for (s in 0 until V.LIST_SLOTS) {
            player.setInterfaceEvents(INTERFACE, V.slot(s) + V.L_VIEW, -1..-1, EVENTS_OP1)
            player.setInterfaceEvents(INTERFACE, V.slot(s) + V.L_TOGGLE, -1..-1, EVENTS_OP1)
        }
        player.attr.remove(QUERY)
        player.attr.remove(PICKED_ITEM)
        N108Ui.op(player, INTERFACE, V.SEARCH_CLEAR, "Clear")
        player.setInterfaceEvents(INTERFACE, V.SEARCH_CLEAR, -1..-1, EVENTS_OP1)
        N108Ui.field(player, INTERFACE, V.SEARCH_TEXT, V.SEARCH_HIT, V.SEARCH_ON, V.SEARCH_CLEAR, "Search monster or item", "")
        show(player, chosen.key)
    }

    /** Shows [key] on the right and redraws the list (its selection follows). */
    fun show(player: Player, key: String) {
        val source = DropSources.byKey(player, key) ?: return
        if (player.attr[SELECTED] != key) openLines(player).clear()
        player.attr[SELECTED] = key
        player.attr[RECENT] = (listOf(key) + recent(player).filter { it != key }).take(3).joinToString(";")
        drawList(player)
        drawHead(player, source)
        drawRows(player, source)
        QuestTab.refreshDropsPanel(player)
    }

    // ------------------------------------------------------------------ the list

    private fun groupOf(source: DropSources.Source): String = source.group.name

    fun buildList(player: Player): List<Slot> {
        val world = player.world
        val query = player.attr[QUERY]?.trim()?.lowercase().orEmpty()
        val selected = player.attr[SELECTED]
        val sources = DropSources.all(player)
        val out = ArrayList<Slot>()
        fun entry(s: DropSources.Source, sub: String? = null) =
            Slot(false, s.key, s.name, sub, null, false, false, s.modelNpc, selected = s.key == selected && sub == null)
        if (query.length >= 2) {
            val npcs = sources.filter { it.name.lowercase().contains(query) }.sortedBy { it.name.lowercase() }
            if (npcs.isNotEmpty()) {
                out += Slot(true, "Q:MONSTERS", "Monsters", null, null, true, true, -1)
                npcs.take(40).forEach { out += entry(it) }
            }
            // Items: every item a source can give whose name holds the text (one row per item).
            val items = LinkedHashMap<Int, String>()
            for (s in sources) {
                for (item in DropSources.itemChances(DropSources.view(player, s)).keys) {
                    if (item in items) continue
                    val name = DropSources.itemName(world, item)
                    if (name.lowercase().contains(query)) items[item] = name
                }
            }
            if (items.isNotEmpty()) {
                val sorted = items.entries.sortedWith(compareBy({ !it.value.lowercase().startsWith(query) }, { it.value.lowercase() })).take(30)
                val picked = player.attr[PICKED_ITEM]?.takeIf { it in items } ?: sorted.first().key
                player.attr[PICKED_ITEM] = picked
                out += Slot(true, "Q:ITEM", "Item", null, null, true, true, -1)
                sorted.forEach { (id, name) -> out += Slot(false, "item:$id", name, null, null, false, false, -1, item = id, selected = id == picked) }
                out += Slot(true, "Q:BY", "Dropped by", null, null, true, false, -1)
                val by = sources.mapNotNull { s ->
                    val c = DropSources.itemChances(DropSources.view(player, s))[picked] ?: return@mapNotNull null
                    Triple(s, c.first, c.second)
                }.sortedByDescending { it.second }
                by.take(60).forEach { (s, your, _) ->
                    val prefix = if (s.kind == DropSources.Kind.BREACH) "<col=ff5a3c>Breach</col><col=9a9083> · </col>" else "<col=9a9083>Your chance </col>"
                    out += Slot(false, s.key, s.name, prefix + "<col=${N108Ui.rarityColour(your)}>${N108Ui.chance(your)}</col>", null, false, false, s.modelNpc, selected = s.key == selected)
                }
            } else {
                player.attr.remove(PICKED_ITEM)
            }
            if (out.isEmpty()) out += Slot(true, "Q:NONE", "Nothing found", null, null, false, false, -1)
            return out.take(V.LIST_SLOTS)
        }
        val open = openGroups(player)
        fun group(id: String, title: String, icon: String?, gold: Boolean, members: List<DropSources.Source>) {
            val isOpen = id in open
            out += Slot(true, "G:$id", title, null, icon, isOpen, gold, -1)
            if (isOpen) members.forEach { out += entry(it) }
        }
        val favs = favourites(player).mapNotNull { k -> sources.firstOrNull { it.key == k } }
        group("FAVOURITES", "Favourites", "star_gold", true, favs)
        group("DEADMAN", "Deadman", "skull12", true, sources.filter { it.group == DropSources.Group.DEADMAN }.sortedWith(compareBy({ DEADMAN_ORDER.indexOf(it.kind) }, { it.name.lowercase() })))
        group("BOSSES", "Bosses", null, false, sources.filter { it.group == DropSources.Group.BOSSES }.sortedBy { it.name.lowercase() })
        val monsters = sources.filter { it.group == DropSources.Group.MONSTERS }.sortedWith(compareBy({ it.name.lowercase() }, { it.level }))
        val monstersOpen = "MONSTERS" in open
        out += Slot(true, "G:MONSTERS", "Monsters", null, null, monstersOpen, false, -1)
        if (monstersOpen) {
            monsters.groupBy { it.name.first().uppercaseChar().let { c -> if (c in 'A'..'Z') c.toString() else "#" } }.toSortedMap().forEach { (letter, list) ->
                val id = "L:$letter"
                val isOpen = id in open
                out += Slot(true, "G:$id", "   $letter", null, null, isOpen, false, -1)
                if (isOpen) list.forEach { s -> out += entry(s).let { e -> if (list.count { it.name == s.name } > 1) Slot(false, e.key, "${s.name} (${s.level})", null, null, false, false, s.modelNpc, selected = e.selected) else e } }
            }
        }
        return out.take(V.LIST_SLOTS)
    }

    private fun drawList(player: Player) {
        val slots = buildList(player)
        player.attr[LIST] = slots
        val i = INTERFACE
        var y = 0
        val models = ArrayList<N108Ui.Model>()
        val fonts = ArrayList<Pair<Int, String>>()
        for (s in 0 until V.LIST_SLOTS) {
            val base = V.slot(s)
            val slot = slots.getOrNull(s)
            if (slot == null) {
                for (c in 0 until V.LIST_STRIDE) N108Ui.hidden(player, i, base + c, true)
                continue
            }
            if (slot.header) {
                N108Ui.sprite(player, i, base + V.L_BG, "dv_grp")
                N108Ui.pos(player, i, base + V.L_BG, 0, y)
                N108Ui.hidden(player, i, base + V.L_BG, false)
                val arrow = slot.open
                N108Ui.sprite(player, i, base + V.L_ARROW, if (arrow) "ar_open" else "ar_shut")
                N108Ui.pos(player, i, base + V.L_ARROW, if (arrow) 5 else 7, y + if (arrow) 7 else 5)
                N108Ui.hidden(player, i, base + V.L_ARROW, slot.key.startsWith("Q:"))
                var textX = if (slot.key.startsWith("Q:")) 6 else 18
                if (slot.icon != null) {
                    N108Ui.sprite(player, i, base + V.L_ICON, slot.icon)
                    N108Ui.pos(player, i, base + V.L_ICON, textX, y + if (slot.icon == "star_gold") 0 else 2)
                    N108Ui.hidden(player, i, base + V.L_ICON, false)
                    textX += N108Ui.width(slot.icon) + 3
                } else {
                    N108Ui.hidden(player, i, base + V.L_ICON, true)
                }
                N108Ui.hidden(player, i, base + V.L_THUMB, true)
                N108Ui.hidden(player, i, base + V.L_MODEL, true)
                N108Ui.text(player, i, base + V.L_TEXT, "<col=${if (slot.gold) "ffcf4a" else "ff981f"}>${slot.text}</col>")
                fonts += (base + V.L_TEXT) to "b12_full"
                N108Ui.pos(player, i, base + V.L_TEXT, textX, y - 4)
                N108Ui.hidden(player, i, base + V.L_TEXT, false)
                N108Ui.hidden(player, i, base + V.L_SUB, true)
                N108Ui.hidden(player, i, base + V.L_VIEW, true)
                N108Ui.pos(player, i, base + V.L_TOGGLE, 0, y)
                N108Ui.hidden(player, i, base + V.L_TOGGLE, slot.key.startsWith("Q:"))
                y += 17
                continue
            }
            // An entry: 24 high, the thumb and model at the left, one line or name + chance.
            N108Ui.sprite(player, i, base + V.L_BG, "dv_lisel")
            N108Ui.pos(player, i, base + V.L_BG, 0, y)
            N108Ui.hidden(player, i, base + V.L_BG, !slot.selected)
            N108Ui.hidden(player, i, base + V.L_ARROW, true)
            N108Ui.hidden(player, i, base + V.L_ICON, true)
            N108Ui.pos(player, i, base + V.L_THUMB, 4, y + 1)
            N108Ui.hidden(player, i, base + V.L_THUMB, false)
            N108Ui.pos(player, i, base + V.L_MODEL, 5, y + 2)
            if (slot.item >= 0) {
                N108Ui.item(player, i, base + V.L_MODEL, slot.item, 1)
                N108Ui.hidden(player, i, base + V.L_MODEL, false)
            } else if (slot.model >= 0) {
                models += N108Ui.Model(N108Ui.hash(i, base + V.L_MODEL), slot.model, thumbZoom(player, slot.model), 0, false)
                N108Ui.hidden(player, i, base + V.L_MODEL, false)
            } else {
                N108Ui.hidden(player, i, base + V.L_MODEL, true)
            }
            val colour = if (slot.selected) "fff4d8" else "ece2cf"
            fonts += (base + V.L_TEXT) to "p11_full"
            if (slot.sub != null) {
                N108Ui.text(player, i, base + V.L_TEXT, "<col=$colour>${N108Ui.fitP11(player.world, slot.text, 100)}</col>")
                N108Ui.pos(player, i, base + V.L_SUB, 30, y + 12)
                N108Ui.text(player, i, base + V.L_SUB, slot.sub)
                N108Ui.hidden(player, i, base + V.L_SUB, false)
                // the name on the upper line
                N108Ui.pos(player, i, base + V.L_TEXT, 30, y - 6)
            } else {
                N108Ui.pos(player, i, base + V.L_TEXT, 30, y)
                N108Ui.text(player, i, base + V.L_TEXT, "<col=${if (slot.item >= 0) "ffe7a8" else colour}>${N108Ui.fitP11(player.world, slot.text, 100)}</col>")
                N108Ui.hidden(player, i, base + V.L_SUB, true)
            }
            N108Ui.hidden(player, i, base + V.L_TEXT, false)
            N108Ui.pos(player, i, base + V.L_VIEW, 0, y)
            N108Ui.hidden(player, i, base + V.L_VIEW, false)
            N108Ui.hidden(player, i, base + V.L_TOGGLE, true)
            y += 24
        }
        if (models.isNotEmpty()) N108Ui.models(player, models)
        N108Ui.fonts(player, i, fonts)
        scroll(player, V.LIST, V.LIST_SCROLLBAR, maxOf(y, V.LIST_H))
    }

    /** Model camera distance (client zoom2d: bigger = further away): base + per extra tile of npc size. `::dvzoom` tunes it live. */
    @Volatile var THUMB_BASE = 1500
    @Volatile var THUMB_PER = 1100
    @Volatile var HEAD_BASE = 1500
    @Volatile var HEAD_PER = 1100

    private fun size(player: Player, npc: Int) = player.world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, npc)?.size ?: 1

    /** Zoom of a full npc model in a 20 x 20 thumb: bigger npcs further away. */
    fun thumbZoom(player: Player, npc: Int): Int = THUMB_BASE + THUMB_PER * (size(player, npc) - 1)

    fun headZoom(player: Player, npc: Int): Int = HEAD_BASE + HEAD_PER * (size(player, npc) - 1)

    private fun scroll(player: Player, layer: Int, scrollbar: Int, height: Int) = N108Ui.scroll(player, INTERFACE, layer, scrollbar, height)

    /** A click in the list. */
    fun listClick(player: Player, slotIndex: Int, toggle: Boolean) {
        val slot = player.attr[LIST]?.getOrNull(slotIndex) ?: return
        if (toggle || slot.header) {
            if (!slot.key.startsWith("G:")) return
            val id = slot.key.removePrefix("G:")
            val open = openGroups(player)
            if (!open.remove(id)) {
                // One letter of the Monsters list open at a time (the list holds [V.LIST_SLOTS] lines).
                if (id.startsWith("L:")) open.removeIf { it.startsWith("L:") }
                open += id
            }
            drawList(player)
            return
        }
        if (slot.key.startsWith("item:")) {
            player.attr[PICKED_ITEM] = slot.key.removePrefix("item:").toInt()
            drawList(player)
            DropSources.byKey(player, player.attr[SELECTED])?.let { drawRows(player, it) }
            return
        }
        show(player, slot.key)
    }

    // ------------------------------------------------------------------ the head

    private fun drawHead(player: Player, source: DropSources.Source) {
        val world = player.world
        val i = INTERFACE
        // Model: the npc; the chest has none of its own - Zemouregal, who comes with it.
        val modelNpc = if (source.kind == DropSources.Kind.CHEST) DeadmanChestIds.guardianNpc else source.modelNpc
        if (modelNpc > 0) {
            N108Ui.models(player, listOf(N108Ui.Model(N108Ui.hash(i, V.MODEL), modelNpc, headZoom(player, modelNpc), 0, true)))
            N108Ui.hidden(player, i, V.MODEL, false)
        } else {
            N108Ui.hidden(player, i, V.MODEL, true)
        }
        N108Ui.text(player, i, V.NAME, source.name)
        val starX = V.NAME_X + N108Ui.b12(world, source.name) + 4
        val fav = source.key in favourites(player)
        N108Ui.pos(player, i, V.STAR_ON, starX, 30)
        N108Ui.pos(player, i, V.STAR_OFF, starX, 30)
        N108Ui.hidden(player, i, V.STAR_ON, !fav)
        N108Ui.hidden(player, i, V.STAR_OFF, fav)
        N108Ui.text(player, i, V.SUB, subLine(player, source))
        // Labels.
        val place = DropViewerPlaces.of(world, source)
        val tags = ArrayList<Triple<String, String, String>>() // sprite, text, colour
        if (source.kind == DropSources.Kind.BREACH) {
            tags += Triple("tag_breach", "Breach", "ff5a3c")
            if (place?.multi != false) tags += Triple("tag_multi", "Multi", "ffdc6a") else tags += Triple("tag_singles", "Singles", "ece2cf")
            tags += Triple("tag_top16", "Top 16 loot", "ffcf4a")
        } else if (place != null) {
            if (place.wilderness > 0) tags += Triple("tag_wild", "Wild ${place.wilderness}", "ff5a3c")
            tags += if (place.multi) Triple("tag_multi", "Multi", "ffdc6a") else Triple("tag_singles", "Singles", "ece2cf")
            tags += if (place.safe) Triple("tag_safe", "Safe", "56e156") else Triple("tag_dangerous", "Dangerous", "ff5a3c")
        }
        var x = V.NAME_X
        for (t in 0 until V.TAGS) {
            val box = V.TAG_FIRST + t * 2
            val tag = tags.getOrNull(t)
            N108Ui.hidden(player, i, box, tag == null)
            N108Ui.hidden(player, i, box + 1, tag == null)
            if (tag == null) continue
            val w = N108Ui.width(tag.first)
            N108Ui.sprite(player, i, box, tag.first)
            N108Ui.pos(player, i, box, x, V.TAGS_Y)
            val design = mapOf("tag_wild" to "Wild 44", "tag_multi" to "Multi")[tag.first]
            val textX = if (design != null) x + w - 5 - N108Ui.p11(world, design) else x + 5
            N108Ui.pos(player, i, box + 1, textX, V.TAGS_Y)
            N108Ui.text(player, i, box + 1, "<col=${tag.third}>${tag.second}</col>")
            x += w + 3
        }
        // No place (a breach that is closed, an npc nobody spawned): the button stays, greyed out, without its option.
        N108Ui.hidden(player, i, V.MAP, place == null)
        N108Ui.hidden(player, i, V.MAP_TEXT, false)
        N108Ui.text(player, i, V.MAP_TEXT, if (place == null) "<col=6f6556>Show on map</col>" else "Show on map")
        // The player's own bracket only (owner).
        val tier = AnnihilationTier.of(player)
        val boosted = source.kind in setOf(DropSources.Kind.NPC, DropSources.Kind.BREACH, DropSources.Kind.BARROWS)
        N108Ui.text(player, i, V.BRK_A, "Your bracket:")
        val a = N108Ui.p11(world, "Your bracket:")
        N108Ui.pos(player, i, V.BRK_B, 184 + a + 4, 106)
        N108Ui.text(player, i, V.BRK_B, tier.label)
        val b = N108Ui.b12(world, tier.label)
        N108Ui.pos(player, i, V.BRK_C, 184 + a + 4 + b + 4, 106)
        N108Ui.size(player, i, V.BRK_C, 230, 20)
        val rate = tier.dropRate.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
        N108Ui.text(player, i, V.BRK_C, if (boosted) "·  ${rate}x chance on rare drops" else "·  no boost on this loot")
        N108Ui.text(player, i, V.COMBAT, "Combat ${player.combatLevel}")
    }

    private fun subLine(player: Player, source: DropSources.Source): String {
        val kc = KillCounts.get(player, source.key)
        val dot = " <col=9a9083>·</col> "
        return when (source.kind) {
            DropSources.Kind.CHEST -> {
                val active = DeadmanChestWorld.activeTimed()
                val where = if (active != null) "Now at <col=ffffff>${active.name}</col>" else if (DeadmanChestWorld.ticksUntilTimed >= 0) "Next in <col=ffffff>${DeadmanTabValues.duration((DeadmanChestWorld.ticksUntilTimed * 6L + 9) / 10)}</col>" else "Every 30 min, random spot"
                "$where${dot}Opened <col=ffffff>${N108Ui.number(kc)}</col>"
            }
            DropSources.Kind.BARROWS -> "All six brothers${dot}Chests <col=ffffff>${N108Ui.number(kc)}</col>"
            else -> {
                val level = if (source.level > 0) "<col=ffff00>Level ${source.level}</col>$dot" else ""
                "${level}Kill count <col=ffffff>${N108Ui.number(kc)}</col>"
            }
        }
    }

    // ------------------------------------------------------------------ the lines

    class Row(val line: DropSources.Line, val level: Int)

    fun rowsOf(player: Player, view: DropSources.View): List<Row> {
        val open = openLines(player)
        val picked = player.attr[PICKED_ITEM]
        val out = ArrayList<Row>()
        fun add(line: DropSources.Line, level: Int) {
            out += Row(line, level)
            if (line.tag != DropSources.Tag.TABLE) return
            // A table opens when clicked, or by itself when it holds the item searched for.
            val holds = picked != null && line.kids.any { it.item == picked || it.kids.any { k -> k.item == picked } }
            if (line.key in open || holds) line.kids.forEach { add(it, level + 1) }
        }
        view.lines.forEach { add(it, 0) }
        return out
    }

    private fun drawRows(player: Player, source: DropSources.Source) {
        val world = player.world
        val i = INTERFACE
        val view = DropSources.view(player, source)
        // Always bar.
        var x = 169 + N108Ui.p11(world, "Always:") + 7
        for (a in 0 until V.ALWAYS) {
            val base = V.ALWAYS_FIRST + a * 3
            val line = view.always.getOrNull(a)
            N108Ui.hidden(player, i, base, line == null)
            N108Ui.hidden(player, i, base + 1, line == null && !(a == 0 && view.always.isEmpty()))
            N108Ui.hidden(player, i, base + 2, true)
            if (line == null) {
                if (a == 0 && view.always.isEmpty()) {
                    N108Ui.pos(player, i, base + 1, x, 126)
                    N108Ui.text(player, i, base + 1, "<col=7a7064>nothing</col>")
                }
                continue
            }
            N108Ui.item(player, i, base, DropSources.picture(world, line.item), 1)
            N108Ui.pos(player, i, base, x, 128)
            x += 18
            val text = line.name + (line.note?.let { " <col=9a9083>($it)</col>" } ?: "")
            N108Ui.pos(player, i, base + 1, x, 126)
            N108Ui.text(player, i, base + 1, text)
            x += N108Ui.p11(world, text) + 9
        }
        val rows = rowsOf(player, view)
        player.attr[ROWS] = rows.map { it.line.key }
        val picked = player.attr[PICKED_ITEM]
        val have = CollectionLog.entries(player).keys
        val logItems = runCatching { CollectionLogPages.allItems(player) }.getOrDefault(emptySet())
        var odd = false
        for (r in 0 until V.ROW_SLOTS) {
            val base = V.row(r)
            val row = rows.getOrNull(r)
            if (row == null) {
                for (c in 0 until V.ROW_STRIDE) N108Ui.hidden(player, i, base + c, true)
                continue
            }
            val l = row.line
            val y = r * V.ROW_H
            val hl = picked != null && l.item == picked && l.tag != DropSources.Tag.TABLE
            val bg = when {
                hl -> "dv_row_hl"
                row.level >= 2 -> "dv_row_kid2"
                row.level == 1 -> "dv_row_kid"
                odd -> "dv_row_odd"
                else -> "dv_row_even"
            }
            if (row.level == 0) odd = !odd
            N108Ui.sprite(player, i, base + V.R_BG, bg)
            N108Ui.hidden(player, i, base + V.R_BG, false)
            val indent = row.level * 12
            N108Ui.item(player, i, base + V.R_ITEM, DropSources.picture(world, l.item), 1)
            N108Ui.pos(player, i, base + V.R_ITEM, 4 + indent, y)
            N108Ui.hidden(player, i, base + V.R_ITEM, l.item < 0)
            // The name never runs into the Qty column (its right edge 182 minus the quantity), the tag counted in.
            val tagRoom = when {
                l.tag == DropSources.Tag.PET -> N108Ui.width("tag_pet") + 4
                l.tag == DropSources.Tag.TABLE -> 10
                else -> 14
            }
            val room = 182 - N108Ui.p11(world, l.quantity) - 8 - (30 + indent) - tagRoom
            val name = N108Ui.fitP11(world, l.name + (l.note?.let { " <col=9a9083>($it)</col>" } ?: ""), room)
            N108Ui.pos(player, i, base + V.R_NAME, 30 + indent, y)
            N108Ui.text(player, i, base + V.R_NAME, "<col=${if (row.level > 0) "d8ccb4" else "ece2cf"}>$name</col>")
            N108Ui.hidden(player, i, base + V.R_NAME, false)
            val tagX = 30 + indent + N108Ui.p11(world, name) + 4
            val tag =
                when {
                    l.tag == DropSources.Tag.PET -> "tag_pet"
                    l.tag == DropSources.Tag.TABLE -> if (l.key in openLines(player) || (picked != null && l.kids.any { it.item == picked })) "tw_open" else "tw_shut"
                    l.item in logItems && l.item in have -> "ok"
                    else -> null
                }
            N108Ui.hidden(player, i, base + V.R_TAG, tag == null)
            if (tag != null) {
                N108Ui.sprite(player, i, base + V.R_TAG, tag)
                val ty = when (tag) {
                    "tw_open" -> y + 7
                    "tw_shut" -> y + 5
                    "ok" -> y + 3
                    else -> y + 3
                }
                N108Ui.pos(player, i, base + V.R_TAG, tagX + if (tag.startsWith("tw")) 1 else 0, ty)
            }
            N108Ui.text(player, i, base + V.R_QTY, l.quantity)
            N108Ui.hidden(player, i, base + V.R_QTY, false)
            N108Ui.text(player, i, base + V.R_YOUR, "<col=${N108Ui.rarityColour(l.your)}>${N108Ui.chance(l.your)}</col>")
            N108Ui.hidden(player, i, base + V.R_YOUR, false)
            val same = Math.abs(l.your - l.normal) < 1e-12
            N108Ui.text(player, i, base + V.R_NORMAL, if (same) "-" else N108Ui.chance(l.normal))
            N108Ui.hidden(player, i, base + V.R_NORMAL, false)
            N108Ui.hidden(player, i, base + V.R_TOGGLE, l.tag != DropSources.Tag.TABLE)
        }
        N108Ui.hidden(player, i, V.EMPTY, rows.isNotEmpty())
        if (rows.isEmpty()) N108Ui.text(player, i, V.EMPTY, if (view.always.isEmpty()) "${source.name} drops nothing." else "Only the Always items.")
        scroll(player, V.ROWS, V.ROWS_SCROLLBAR, maxOf(rows.size * V.ROW_H, V.ROWS_H))
    }

    fun rowClick(player: Player, index: Int) {
        val key = player.attr[ROWS]?.getOrNull(index) ?: return
        val open = openLines(player)
        if (!open.remove(key)) open += key
        DropSources.byKey(player, player.attr[SELECTED])?.let { drawRows(player, it) }
    }

    // ------------------------------------------------------------------ search, favourite, map

    /** `::uisearch <text>` from the window's text field (client RspsUi). */
    fun search(player: Player, text: String) {
        if (!isOpen(player)) return
        val q = text.take(30)
        if (q == (player.attr[QUERY] ?: "")) return
        player.attr[QUERY] = q
        player.attr.remove(PICKED_ITEM)
        drawList(player)
        DropSources.byKey(player, player.attr[SELECTED])?.let { drawRows(player, it) }
    }

    fun toggleFavourite(player: Player) {
        val key = player.attr[SELECTED] ?: return
        val favs = favourites(player).toMutableList()
        if (!favs.remove(key)) favs += key
        player.attr[FAVOURITES] = favs.joinToString(";")
        DropSources.byKey(player, key)?.let { drawHead(player, it) }
        drawList(player)
    }

    fun showOnMap(player: Player) {
        val source = DropSources.byKey(player, player.attr[SELECTED]) ?: return
        val place = DropViewerPlaces.of(player.world, source) ?: return
        WorldMapPoint.open(player, place.tile)
    }

    fun itemName(player: Player, id: Int): String = player.world.definitions.getNullable(ItemDef::class.java, id)?.name ?: ""

    /** The kill rate the viewer's "Your chance" uses (the roll's own). */
    fun rate(player: Player): Double = DropTableFactory.killRate(player)
}
