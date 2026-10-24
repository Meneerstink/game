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
    /** N267 (owner 2026-10-24): "Recently viewed" is gone - nothing writes it any more; the key stays so older saves load as before. */
    val RECENT = AttributeKey<String>(persistenceKey = "drop_viewer_recent")

    private val OPEN_GROUPS = AttributeKey<MutableSet<String>>()
    private val QUERY = AttributeKey<String>()
    private val PICKED_ITEM = AttributeKey<Int>()
    private val LIST = AttributeKey<List<Slot>>()

    private const val EVENTS_OP1 = 0x2

    /** King Black Dragon first (the design's example), else the first boss. */
    private const val DEFAULT_KEY = "npc:50"

    private val DEFAULT_OPEN = setOf("FAVOURITES", "BOSSES")

    /** The Deadman group: the chest and its guardian first, then the breach monsters by name. */
    private val DEADMAN_ORDER = listOf(DropSources.Kind.CHEST, DropSources.Kind.GUARDIAN, DropSources.Kind.BREACH)

    /** [indent] 1 = a God Wars bodyguard under its general (N311). */
    class Slot(val header: Boolean, val key: String, val text: String, val sub: String?, val icon: String?, val open: Boolean, val gold: Boolean, val model: Int, val item: Int = -1, val selected: Boolean = false, val indent: Int = 0)

    /**
     * N311 (owner 2026-10-24): the God Wars bodyguards stand right under their general in Bosses, a little indented, each with its
     * own drops - read from the bodyguards the server spawns with each general ([GodWarsBodyguards.BY_GENERAL]).
     */
    fun minionsOf(sources: List<DropSources.Source>, boss: DropSources.Source): List<DropSources.Source> {
        val guards = boss.npcs.flatMap { gg.rsmod.plugins.content.areas.godwars.GodWarsBodyguards.BY_GENERAL[it].orEmpty() }.map { it.id }
        return guards.mapNotNull { id -> sources.firstOrNull { it.group == DropSources.Group.MONSTERS && it.npcs.contains(id) } }.distinct()
    }

    fun isOpen(player: Player): Boolean = player.interfaces.isVisible(INTERFACE)

    fun favourites(player: Player): List<String> = (player.attr[FAVOURITES] ?: "").split(';').filter { it.isNotBlank() }

    fun recent(player: Player): List<String> = (player.attr[RECENT] ?: "").split(';').filter { it.isNotBlank() }

    private fun openGroups(player: Player): MutableSet<String> = player.attr[OPEN_GROUPS] ?: DEFAULT_OPEN.toMutableSet().also { player.attr[OPEN_GROUPS] = it }

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
        // N319: another entry starts at the top of its table.
        if (player.attr[SELECTED] != key) N108Ui.scrollTop(player, INTERFACE, V.ROWS, V.ROWS_SCROLLBAR, V.ROWS_H)
        player.attr[SELECTED] = key
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
            // Items: every item a source can give whose name holds the text (one row per item), from the item index.
            val index = DropSources.itemIndex(player)
            val items = index.names.filter { it.value.lowercase().contains(query) }
            if (items.isNotEmpty()) {
                val sorted = items.entries.sortedWith(compareBy({ !it.value.lowercase().startsWith(query) }, { it.value.lowercase() })).take(30)
                val picked = player.attr[PICKED_ITEM]?.takeIf { it in items } ?: sorted.first().key
                player.attr[PICKED_ITEM] = picked
                out += Slot(true, "Q:ITEM", "Item", null, null, true, true, -1)
                sorted.forEach { (id, name) -> out += Slot(false, "item:$id", name, null, null, false, false, -1, item = DropSources.picture(world, id), selected = id == picked) }
                out += Slot(true, "Q:BY", "Dropped by", null, null, true, false, -1)
                val by = index.sources[picked].orEmpty().sortedByDescending { it.second }
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
        val bosses = sources.filter { it.group == DropSources.Group.BOSSES }.sortedBy { it.name.lowercase() }
        val minions = bosses.associateWith { minionsOf(sources, it) }
        val bossesOpen = "BOSSES" in open
        out += Slot(true, "G:BOSSES", "Bosses", null, null, bossesOpen, false, -1)
        if (bossesOpen) {
            bosses.forEach { b ->
                out += entry(b)
                minions.getValue(b).forEach { m -> out += entry(m).let { e -> Slot(false, e.key, e.text, null, null, false, false, e.model, selected = e.selected, indent = 1) } }
            }
        }
        val underBoss = minions.values.flatten().map { it.key }.toSet()
        val monsters = sources.filter { it.group == DropSources.Group.MONSTERS && it.key !in underBoss }.sortedWith(compareBy({ it.name.lowercase() }, { it.level }))
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
                // N279 design B5: every heading the same quiet gold over a thin line.
                N108Ui.text(player, i, base + V.L_TEXT, "<col=ffcf4a>${slot.text}</col>")
                fonts += (base + V.L_TEXT) to "b12_full"
                N108Ui.pos(player, i, base + V.L_TEXT, textX, y - 3)
                N108Ui.hidden(player, i, base + V.L_TEXT, false)
                N108Ui.hidden(player, i, base + V.L_SUB, true)
                N108Ui.hidden(player, i, base + V.L_VIEW, true)
                N108Ui.pos(player, i, base + V.L_TOGGLE, 0, y)
                N108Ui.hidden(player, i, base + V.L_TOGGLE, slot.key.startsWith("Q:"))
                y += 18
                continue
            }
            // An entry: 24 high, the thumb and model at the left, one line or name + chance; a bodyguard 10 px in (N311).
            val dx = slot.indent * 10
            N108Ui.sprite(player, i, base + V.L_BG, "dv_lisel")
            N108Ui.pos(player, i, base + V.L_BG, 0, y)
            N108Ui.hidden(player, i, base + V.L_BG, !slot.selected)
            N108Ui.hidden(player, i, base + V.L_ARROW, true)
            N108Ui.hidden(player, i, base + V.L_ICON, true)
            N108Ui.pos(player, i, base + V.L_THUMB, 4 + dx, y + 1)
            N108Ui.hidden(player, i, base + V.L_THUMB, false)
            N108Ui.pos(player, i, base + V.L_MODEL, 5 + dx, y + 2)
            if (slot.item >= 0) {
                N108Ui.item(player, i, base + V.L_MODEL, slot.item)
                N108Ui.hidden(player, i, base + V.L_MODEL, false)
            } else if (slot.key == "chest" && chestModel(player) >= 0) {
                N108Ui.plainModel(player, N108Ui.hash(i, base + V.L_MODEL), chestModel(player), CHEST_ZOOM, CHEST_XAN, false, CHEST_YAN)
                N108Ui.hidden(player, i, base + V.L_MODEL, false)
            } else if (slot.model >= 0) {
                models += N108Ui.Model(N108Ui.hash(i, base + V.L_MODEL), slot.model, thumbZoom(player, slot.model), MODEL_XAN, false, MODEL_YAN)
                N108Ui.hidden(player, i, base + V.L_MODEL, false)
            } else {
                N108Ui.hidden(player, i, base + V.L_MODEL, true)
            }
            val colour = if (slot.selected) "fff4d8" else "f3ead6"
            fonts += (base + V.L_TEXT) to "p11_full"
            if (slot.sub != null) {
                N108Ui.text(player, i, base + V.L_TEXT, "<col=$colour>${N108Ui.fitP11(player.world, slot.text, 100 - dx)}</col>")
                N108Ui.pos(player, i, base + V.L_SUB, 30 + dx, y + 12)
                N108Ui.text(player, i, base + V.L_SUB, slot.sub)
                N108Ui.hidden(player, i, base + V.L_SUB, false)
                // the name on the upper line
                N108Ui.pos(player, i, base + V.L_TEXT, 30 + dx, y - 6)
            } else {
                N108Ui.pos(player, i, base + V.L_TEXT, 30 + dx, y)
                N108Ui.text(player, i, base + V.L_TEXT, "<col=${if (slot.item >= 0) "ffe7a8" else colour}>${listName(player.world, slot.text, 100 - dx)}</col>")
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

    /**
     * The chest's picture: the OPEN chest's model (loc 62947 -> 4970). The closed chest's (4969) carries the tall light beam over
     * it - fitted to the box, the chest itself came out a few pixels small at the bottom.
     */
    fun chestModel(player: Player): Int = N108Ui.locModel(player.world, DeadmanChestIds.openLoc)

    /** The sub line under the name: from [V.NAME_X] to 4 px before the window's right edge (V.SUB is 232 wide). */
    private const val SUB_WIDTH = 228

    /** The chest's camera: a 4 x 4 loc seen from a little above (the client fits it to the box). */
    private const val CHEST_ZOOM = 2600
    private const val CHEST_XAN = 160

    /**
     * N283 (owner 2026-10-24: the model must not turn; "the preview can be better"): every model stands still, seen a little from
     * above and turned to a 3/4 view like the OSRS Wiki's pictures (client zoom2d units: 2048 = a full turn). `::dvangle` tunes it.
     */
    @Volatile var MODEL_XAN = 64
    @Volatile var MODEL_YAN = 1850
    @Volatile var CHEST_YAN = 1850

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
        // Model: the npc; the Deadman's Chest its own loc model (N277, owner 2026-10-24 - it showed Zemouregal before).
        val chestModel = if (source.kind == DropSources.Kind.CHEST) chestModel(player) else -1
        val modelNpc = if (source.kind == DropSources.Kind.CHEST && chestModel < 0) DeadmanChestIds.guardianNpc else source.modelNpc
        if (chestModel >= 0) {
            // N283 (owner 2026-10-24): the model stands still.
            N108Ui.plainModel(player, N108Ui.hash(i, V.MODEL), chestModel, CHEST_ZOOM, CHEST_XAN, false, CHEST_YAN)
            N108Ui.hidden(player, i, V.MODEL, false)
        } else if (modelNpc > 0) {
            N108Ui.models(player, listOf(N108Ui.Model(N108Ui.hash(i, V.MODEL), modelNpc, headZoom(player, modelNpc), MODEL_XAN, false, MODEL_YAN)))
            N108Ui.hidden(player, i, V.MODEL, false)
        } else {
            N108Ui.hidden(player, i, V.MODEL, true)
        }
        // N279: a long name is cut so the star stays clear of "Show on map" (the whole name in the tooltip).
        val name = headName(world, source.name)
        N108Ui.text(player, i, V.NAME, name)
        // Always sent: the client keeps a tooltip until it is replaced, so a short name must overwrite a long one's.
        N108Ui.tooltips(player, listOf(N108Ui.hash(i, V.NAME) to source.name))
        val starX = starX(world, name)
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
        // N314 (owner 2026-10-24): no place (a breach that is closed, an npc nobody spawned) = no button at all, not a grey one.
        N108Ui.hidden(player, i, V.MAP, place == null)
        N108Ui.hidden(player, i, V.MAP_TEXT, place == null)
        N108Ui.text(player, i, V.MAP_TEXT, "Show on map")
        // The player's own bracket only (owner), as gold text in the head (N279 design B5); no "Combat 138" (N284).
        val (a, b, c) = bracketLine(player, source)
        N108Ui.text(player, i, V.BRK_A, a)
        val bx = BRACKET_X + N108Ui.p11(world, a) + 4
        N108Ui.pos(player, i, V.BRK_B, bx, BRACKET_Y)
        N108Ui.text(player, i, V.BRK_B, b)
        val cx = bx + N108Ui.b12(world, b) + 4
        N108Ui.pos(player, i, V.BRK_C, cx, BRACKET_Y)
        N108Ui.size(player, i, V.BRK_C, WINDOW_RIGHT - cx, 17)
        N108Ui.text(player, i, V.BRK_C, c)
    }

    /** "Show on map" starts at x 408: the star (12 wide) ends 3 px before it, 3 px after the name. */
    private const val MAP_X = 408
    private const val STAR_W = 12

    fun starX(world: gg.rsmod.game.model.World, shownName: String): Int = V.NAME_X + N108Ui.b12(world, shownName) + 3

    /** The name as the head shows it: whole, or cut with "..." so name, star and the gaps fit before "Show on map". */
    fun headName(world: gg.rsmod.game.model.World, name: String): String = N108Ui.fit(world, "b12_full", name, MAP_X - 3 - STAR_W - 3 - V.NAME_X)

    /** The Always bar's first item x: after "Always:" (p11, 37 px) and 5 px. */
    const val ALWAYS_X = 169 + 37 + 5

    /**
     * N310 / N319 (owner 2026-10-24, A: rows 26 px, "scherpe plaatjes ~30x26"): an item picture is 28 x 25 - the item's 36 x 32
     * sprite shrunk by the client's area filter (RspsObjSmooth: every source pixel averaged in, no dropped rows), so it fills the
     * row under its 1 px line without reaching into the next one. Older clients draw the same box the 667 way.
     */
    const val PIC_W = 28
    const val PIC_H = 25

    /** An Always item's picture and the gap after it. */
    const val ALWAYS_PIC = PIC_W + 3

    /** The gap after an Always item's text. */
    const val ALWAYS_GAP = 10

    /** The bar's right edge: 3 px inside the table. */
    const val ALWAYS_RIGHT = 489

    /**
     * The smaller second line under an Always item: its quantity when it is more than one ("5,000-25,000" coins of Nex, N319: the
     * bar showed no amount) and its note (N307: "(250+ dmg, 1x per breach)").
     */
    fun alwaysNote(l: DropSources.Line): String? =
        listOfNotNull(l.quantity.takeIf { it != "1" }?.let { qtyLabel(it) }, l.note?.let { "($it)" }).joinToString(" ").ifEmpty { null }

    /** A quantity written out for a text line: "5000-25000" -> "5,000-25,000". */
    fun qtyLabel(quantity: String): String =
        quantity.replace(",", "").split('-').joinToString("-") { p -> p.trim().toIntOrNull()?.let { String.format(java.util.Locale.US, "%,d", it) } ?: p }

    /** The width an Always item takes in the bar: picture, then the wider of its name (p12) and note (p11). */
    fun alwaysWidth(world: gg.rsmod.game.model.World, l: DropSources.Line): Int =
        ALWAYS_PIC + maxOf(N108Ui.textWidth(world, "p12_full", l.name), alwaysNote(l)?.let { N108Ui.p11(world, it) } ?: 0)

    /** Whether all Always items fit the bar (at most [V.ALWAYS], inside the table); else they head the table as a group. */
    fun alwaysInBar(world: gg.rsmod.game.model.World, always: List<DropSources.Line>): Boolean {
        if (always.size > V.ALWAYS) return false
        var x = ALWAYS_X
        always.forEach { l -> x += alwaysWidth(world, l) + ALWAYS_GAP }
        return x - ALWAYS_GAP <= ALWAYS_RIGHT
    }

    /** The bracket line's x (after the skull) and y, and the window's inner right edge. */
    const val BRACKET_X = V.NAME_X + 16
    const val BRACKET_Y = 86
    const val WINDOW_RIGHT = 493

    /** "Your bracket", the bracket ("96+"), "·  5x on rare drops" (or "no boost on this loot" where the bracket does not count). */
    fun bracketLine(player: Player, source: DropSources.Source): Triple<String, String, String> {
        val tier = AnnihilationTier.of(player)
        val boosted = source.kind in setOf(DropSources.Kind.NPC, DropSources.Kind.BREACH, DropSources.Kind.BARROWS)
        val rate = tier.dropRate.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
        return Triple("Your bracket", tier.label, if (boosted) "·  ${rate}x on rare drops" else "·  no boost on this loot")
    }

    fun subLine(player: Player, source: DropSources.Source): String {
        val kc = KillCounts.get(player, source.key)
        val dot = " <col=9a9083>·</col> "
        return when (source.kind) {
            DropSources.Kind.CHEST -> {
                val active = DeadmanChestWorld.activeTimed()
                val opened = "${dot}Opened <col=ffffff>${N108Ui.number(kc)}</col>"
                // N278: the place fits the line with "Opened N" (it ran out of the window): in full, its short form, or cut.
                val room = SUB_WIDTH - N108Ui.p11(player.world, "Now at ") - N108Ui.p11(player.world, opened)
                val where = if (active != null) "Now at <col=ffffff>${QuestTab.placeLabel(player.world, active.name, room)}</col>" else if (DeadmanChestWorld.ticksUntilTimed >= 0) "Next in <col=ffffff>${DeadmanTabValues.duration((DeadmanChestWorld.ticksUntilTimed * 6L + 9) / 10)}</col>" else "Every 30 min, random spot"
                "$where$opened"
            }
            DropSources.Kind.BARROWS -> "All six brothers${dot}Chests <col=ffffff>${N108Ui.number(kc)}</col>"
            else -> {
                val level = if (source.level > 0) "<col=ffff00>Level ${source.level}</col>$dot" else ""
                "${level}Kill count <col=ffffff>${N108Ui.number(kc)}</col>"
            }
        }
    }

    // ------------------------------------------------------------------ the lines

    /** One row of the table: a group heading ([header]) or a line at [level] (1 = inside a drop table, 2 = a table in a table). */
    class Row(val line: DropSources.Line?, val level: Int, val header: String? = null)

    /** The source's uniques: the items of its Collection Log page(s) (a boss's OSRS log list; none for the chest and breaches). */
    fun uniques(player: Player, source: DropSources.Source): Set<Int> {
        if (source.kind == DropSources.Kind.CHEST || source.kind == DropSources.Kind.BREACH || source.kind == DropSources.Kind.GUARDIAN) return emptySet()
        return runCatching { CollectionLogPages.pagesFor(player, source.key).flatMap { it.items }.toSet() }.getOrDefault(emptySet())
    }

    /**
     * N279 (owner 2026-10-24, design B5): the lines in their groups ([DropGroups]), each under its heading, the rarest first; every
     * drop table is open, its lines indented right under it (owner: the Rare drop table always open).
     */
    fun rowsOf(player: Player, source: DropSources.Source, view: DropSources.View): List<Row> {
        val out = ArrayList<Row>()
        fun add(line: DropSources.Line, level: Int) {
            out += Row(line, level)
            if (line.tag == DropSources.Tag.TABLE) line.kids.sortedWith(DropGroups.RAREST_FIRST).forEach { add(it, level + 1) }
        }
        // Always items that do not fit the bar head the table (the Wiki lists them first too).
        if (view.always.isNotEmpty() && !alwaysInBar(player.world, view.always)) {
            out += Row(null, 0, DropGroups.Group.ALWAYS.title)
            view.always.forEach { add(it, 0) }
        }
        for ((group, lines) in DropGroups.grouped(player.world, view.lines, uniques(player, source))) {
            out += Row(null, 0, group.title)
            lines.forEach { add(it, 0) }
        }
        return out
    }

    /** The Wiki rarity cell behind "Your chance" (none for a chance of 0, e.g. a chest pet the player owns). */
    fun cellSprite(p: Double): String? {
        if (p <= 0.0) return null
        if (p >= 0.9999) return "zj_c_always"
        val d = 1.0 / p
        return when {
            d <= 25 -> "zj_c_common"
            d < 100 -> "zj_c_uncommon"
            d < 1000 -> "zj_c_rare"
            else -> "zj_c_vrare"
        }
    }

    /** N308: the Normal column's lighter cell of the same Wiki band. */
    fun normalCellSprite(p: Double): String? = cellSprite(p)?.replace("zj_c_", "zk_n_")

    /** The x of a row's name: the item column (one level in = 10 px more). */
    fun nameX(level: Int): Int = V.COL_NAME + level * 10

    /**
     * The room for a row's name: to 3 px before the Qty column; a drop table's own row has no quantity (N312), so its name and its
     * second line may use the Qty column too (to 3 px before Your chance).
     */
    fun nameRoom(level: Int, table: Boolean = false): Int = (if (table) V.COL_YOUR - 1 else V.COL_QTY - 1) - 3 - nameX(level)

    /**
     * N319 ("leesbaar"): a list name on one line when it fits, else on two lines broken between words ("Thermonuclear smoke" over
     * "devil"); only what does not fit two lines is cut with "...".
     */
    fun listName(world: gg.rsmod.game.model.World, name: String, room: Int): String = wrapTwo(world, "p11_full", name, room).first

    /** [text] in [font] on one line, else two lines broken between words; the flag says whether it had to be cut. */
    fun wrapTwo(world: gg.rsmod.game.model.World, font: String, text: String, room: Int): Pair<String, Boolean> {
        if (N108Ui.textWidth(world, font, text) <= room) return text to false
        val words = text.split(' ')
        var first = ""
        var at = 0
        while (at < words.size) {
            val next = if (first.isEmpty()) words[at] else "$first ${words[at]}"
            if (N108Ui.textWidth(world, font, next) > room) break
            first = next
            at++
        }
        if (first.isEmpty()) return N108Ui.fit(world, font, text, room) to true
        val rest = words.drop(at).joinToString(" ")
        val second = N108Ui.fit(world, font, rest, room)
        return "$first<br>$second" to (second != rest)
    }

    /** A row's name as drawn: its text (lines joined with <br>) and font. */
    class RowName(val text: String, val font: String, val cut: Boolean)

    /**
     * N319 (owner 2026-10-24: "leesbaar"): a row's name in p12 on one line; a name too long for that ("Deadman emblem (tier 1)")
     * in p11 on two lines, broken between words like the Wiki's table; only a name that does not fit two lines is cut with "...".
     * A row with a second line of its own ([DropSources.Line.sub]) keeps its name on one line.
     */
    fun rowName(world: gg.rsmod.game.model.World, l: DropSources.Line, room: Int, hasSub: Boolean = false): RowName {
        val plain = plainName(l.name) + (l.note?.let { " ($it)" } ?: "")
        val coloured = plainName(l.name) + (l.note?.let { " <col=5e4e38>($it)</col>" } ?: "")
        if (N108Ui.textWidth(world, "p12_full", plain) <= room) return RowName(coloured, "p12_full", false)
        if (hasSub) return RowName(N108Ui.fit(world, "p12_full", plain, room), "p12_full", true)
        val (text, cut) = wrapTwo(world, "p11_full", plain, room)
        return RowName(text, "p11_full", cut)
    }

    /** The name without a note's " (noted)" - N315 / OSRS Wiki: "(noted)" goes to the Qty column. */
    fun plainName(name: String): String = name.removeSuffix(NOTED)

    fun isNoted(name: String): Boolean = name.endsWith(NOTED)

    private const val NOTED = " (noted)"

    /**
     * N309 (owner 2026-10-24, A): the quantity as it fits the 46 px Qty column - whole while it fits (1 px clear of the column line), else every
     * number of 1,000 and more in thousands ("9000-11000" -> "9K-11K"); when that is still too wide, a range in thousands carries
     * the K once ("20500-21000" -> "20.5-21K"); a range that fits none of these takes two lines ("20.1K-" over "20.6K").
     */
    fun qtyText(world: gg.rsmod.game.model.World, quantity: String): String {
        val q = quantity.replace(",", "")
        if (N108Ui.p11(world, q) <= QTY_ROOM) return q
        val parts = q.split('-').map { it.trim() }
        val numbers = parts.map { it.toIntOrNull() }
        fun k(n: Int) = if (n % 1000 == 0) "${n / 1000}" else String.format(java.util.Locale.ROOT, "%.1f", n / 1000.0).removeSuffix(".0")
        val ks = parts.indices.map { i -> numbers[i]?.let { n -> if (n < 1000) "$n" else k(n) + "K" } ?: parts[i] }
        val each = ks.joinToString("-")
        if (N108Ui.p11(world, each) <= QTY_ROOM) return each
        if (numbers.none { it == null || it < 1000 }) {
            val shared = numbers.joinToString("-") { k(it!!) } + "K"
            if (N108Ui.p11(world, shared) <= QTY_ROOM) return shared
        }
        return if (ks.size == 2) "${ks[0]}-<br>${ks[1]}" else each
    }

    /** The Qty text's room: the 47 px between the column lines, 1 px clear of the right one. */
    const val QTY_ROOM = V.QTY_W - 1

    /** The Qty cell as drawn: the quantity, "(noted)" under it (N315); a two-line quantity of a note carries "(n)" on its 2nd line. */
    fun qtyCell(world: gg.rsmod.game.model.World, l: DropSources.Line): String {
        val q = qtyText(world, l.quantity)
        if (!isNoted(l.name)) return q
        return if ("<br>" in q) "$q <col=5e4e38>(n)</col>" else "$q<br><col=5e4e38>(noted)</col>"
    }

    /** The height of [rows] in the table (headings [V.HEAD_H], lines [V.ROW_H]). */
    fun height(rows: List<Row>): Int = rows.sumOf { if (it.line == null) V.HEAD_H else V.ROW_H }

    private fun drawRows(player: Player, source: DropSources.Source) {
        val world = player.world
        val i = INTERFACE
        val view = DropSources.view(player, source)
        // Always bar (p12 names on the parchment, the items in their own size, a note on a smaller second line - N307); when they
        // do not all fit, the bar points to the table's Always group.
        var x = ALWAYS_X
        val inBar = alwaysInBar(world, view.always)
        val barY = V.ALWAYS_Y
        for (a in 0 until V.ALWAYS) {
            val base = V.ALWAYS_FIRST + a * 3
            val line = if (inBar) view.always.getOrNull(a) else null
            val empty = a == 0 && (view.always.isEmpty() || !inBar)
            val note = line?.let { alwaysNote(it) }
            N108Ui.hidden(player, i, base, line == null)
            N108Ui.hidden(player, i, base + 1, line == null && !empty)
            N108Ui.hidden(player, i, base + 2, note == null)
            if (line == null) {
                if (empty) {
                    N108Ui.pos(player, i, base + 1, x, barY)
                    N108Ui.size(player, i, base + 1, ALWAYS_RIGHT - x, V.ALWAYS_H)
                    N108Ui.text(player, i, base + 1, if (view.always.isEmpty()) "<col=5e4e38>nothing</col>" else "${view.always.size} items<col=5e4e38> - listed below</col>")
                }
                continue
            }
            N108Ui.item(player, i, base, DropSources.picture(world, line.item))
            N108Ui.size(player, i, base, PIC_W, PIC_H)
            N108Ui.pos(player, i, base, x, barY + 1)
            // The text boxes take the measured width (the cache boxes are narrower than "Archaic emblem (tier 5)"), so nothing wraps.
            N108Ui.pos(player, i, base + 1, x + ALWAYS_PIC, if (note != null) barY - 6 else barY)
            N108Ui.size(player, i, base + 1, N108Ui.textWidth(world, "p12_full", line.name) + 4, V.ALWAYS_H)
            N108Ui.text(player, i, base + 1, line.name)
            if (note != null) {
                N108Ui.pos(player, i, base + 2, x + ALWAYS_PIC, barY + 7)
                N108Ui.size(player, i, base + 2, N108Ui.p11(world, note) + 4, V.ALWAYS_H)
                N108Ui.text(player, i, base + 2, note)
            }
            x += alwaysWidth(world, line) + ALWAYS_GAP
        }
        val rows = rowsOf(player, source, view)
        val picked = player.attr[PICKED_ITEM]
        val fonts = ArrayList<Pair<Int, String>>()
        var y = 0
        for (r in 0 until V.ROW_SLOTS) {
            val base = V.row(r)
            val pic = V.item(r)
            val row = rows.getOrNull(r)
            if (row == null) {
                for (c in 0 until V.ROW_STRIDE) N108Ui.hidden(player, i, base + c, true)
                N108Ui.hidden(player, i, pic, true)
                continue
            }
            val l = row.line
            if (l == null) {
                // A group heading: bold black text on the light band.
                N108Ui.sprite(player, i, base + V.R_BG, "zj_ghd")
                N108Ui.pos(player, i, base + V.R_BG, 0, y)
                N108Ui.hidden(player, i, base + V.R_BG, false)
                for (c in listOf(V.R_CELL, V.R_NCELL, V.R_SUB, V.R_QTY, V.R_YOUR, V.R_NORMAL)) N108Ui.hidden(player, i, base + c, true)
                N108Ui.hidden(player, i, pic, true)
                N108Ui.pos(player, i, base + V.R_NAME, 5, y + (V.HEAD_H - V.ROW_H) / 2)
                // The name box takes a row name's width when that row used it: a heading gets the whole row back (never wraps).
                N108Ui.size(player, i, base + V.R_NAME, V.ROWS_W - 10, V.ROW_H)
                N108Ui.text(player, i, base + V.R_NAME, "<col=000000>${row.header}</col>")
                fonts += (base + V.R_NAME) to "b12_full"
                N108Ui.hidden(player, i, base + V.R_NAME, false)
                y += V.HEAD_H
                continue
            }
            val hl = picked != null && l.item == picked && l.tag != DropSources.Tag.TABLE
            val bg = when {
                hl -> "dv_row_hl"
                row.level >= 2 -> "dv_row_kid2"
                row.level == 1 -> "dv_row_kid"
                else -> "dv_row_even"
            }
            N108Ui.sprite(player, i, base + V.R_BG, bg)
            N108Ui.pos(player, i, base + V.R_BG, 0, y)
            N108Ui.hidden(player, i, base + V.R_BG, false)
            // N310 / N319: the item's own 36 x 32 sprite, unscaled, centred on the row (drawn over every row, V.ITEM_FIRST).
            N108Ui.item(player, i, pic, DropSources.picture(world, l.item))
            N108Ui.size(player, i, pic, PIC_W, PIC_H)
            N108Ui.pos(player, i, pic, 2, y + 1)
            N108Ui.hidden(player, i, pic, l.item < 0)
            // The name (p12) never runs into the Qty column; "(noted)" goes to the Qty column like the OSRS Wiki (N315); N317 no tick.
            val table = l.tag == DropSources.Tag.TABLE
            val x0 = nameX(row.level)
            val room = nameRoom(row.level, table)
            val sub = l.sub?.let { N108Ui.fit(world, "p11_full", it, room) }
            val shown = rowName(world, l, room, sub != null)
            N108Ui.pos(player, i, base + V.R_NAME, x0, if (sub != null) y - 6 else y)
            N108Ui.size(player, i, base + V.R_NAME, room + 3, V.ROW_H)
            N108Ui.text(player, i, base + V.R_NAME, "<col=4a2508>${shown.text}</col>")
            fonts += (base + V.R_NAME) to shown.font
            N108Ui.hidden(player, i, base + V.R_NAME, false)
            // N312: a drop table's explanation on a smaller second line.
            N108Ui.hidden(player, i, base + V.R_SUB, sub == null)
            if (sub != null) {
                N108Ui.pos(player, i, base + V.R_SUB, x0, y + 13)
                N108Ui.text(player, i, base + V.R_SUB, sub)
                fonts += (base + V.R_SUB) to "p11_full"
            }
            // N309: the quantity in the 46 px column (thousands as K where the whole number does not fit), "(noted)" under it; a drop
            // table's own row has none (it is one pick of the table).
            N108Ui.hidden(player, i, base + V.R_QTY, table)
            if (!table) {
                N108Ui.pos(player, i, base + V.R_QTY, V.COL_QTY, y)
                N108Ui.text(player, i, base + V.R_QTY, qtyCell(world, l))
            }
            val cell = cellSprite(l.your)
            N108Ui.hidden(player, i, base + V.R_CELL, cell == null)
            if (cell != null) {
                N108Ui.sprite(player, i, base + V.R_CELL, cell)
                N108Ui.pos(player, i, base + V.R_CELL, V.COL_YOUR, y + 1)
            }
            N108Ui.pos(player, i, base + V.R_YOUR, V.COL_YOUR, y)
            N108Ui.text(player, i, base + V.R_YOUR, if (cell == null) "<col=5e4e38>${N108Ui.chance(l.your)}</col>" else N108Ui.chance(l.your))
            N108Ui.hidden(player, i, base + V.R_YOUR, false)
            // Normal always filled in, on the same Wiki band in a lighter tint (N308).
            val ncell = normalCellSprite(l.normal)
            N108Ui.hidden(player, i, base + V.R_NCELL, ncell == null)
            if (ncell != null) {
                N108Ui.sprite(player, i, base + V.R_NCELL, ncell)
                N108Ui.pos(player, i, base + V.R_NCELL, V.COL_NORMAL, y + 1)
            }
            N108Ui.pos(player, i, base + V.R_NORMAL, V.COL_NORMAL, y)
            N108Ui.text(player, i, base + V.R_NORMAL, "<col=3a2c1c>${N108Ui.chance(l.normal)}</col>")
            N108Ui.hidden(player, i, base + V.R_NORMAL, false)
            y += V.ROW_H
        }
        N108Ui.fonts(player, i, fonts)
        N108Ui.hidden(player, i, V.EMPTY, rows.isNotEmpty())
        if (rows.isEmpty()) N108Ui.text(player, i, V.EMPTY, if (view.always.isEmpty()) "${source.name} drops nothing." else "Only the Always items.")
        scroll(player, V.ROWS, V.ROWS_SCROLLBAR, maxOf(y, V.ROWS_H))
    }

    // ------------------------------------------------------------------ search, favourite, map

    /** `::uisearch <text>` from the window's text field (client RspsUi). */
    fun search(player: Player, text: String) {
        if (!isOpen(player)) return
        val q = text.take(30)
        if (q == (player.attr[QUERY] ?: "")) return
        player.attr[QUERY] = q
        player.attr.remove(PICKED_ITEM)
        val start = System.nanoTime()
        drawList(player)
        val ms = (System.nanoTime() - start) / 1_000_000
        if (ms > 50) gg.rsmod.game.Server.logger.info("Drop Viewer search '{}' took {} ms.", q, ms)
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
