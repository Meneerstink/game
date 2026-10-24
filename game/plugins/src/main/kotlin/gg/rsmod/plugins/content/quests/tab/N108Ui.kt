package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import java.io.File
import java.util.Locale

/**
 * Shared pieces of the night job's screens (Drop Viewer, Collection Log, quest tab panels, Deadman Tasks window): the art's sprite
 * ids, text widths in the game fonts, the client hooks and a per-component "send only what changed" cache.
 */
object N108Ui {
    const val SPRITE_BASE = 8600
    const val MANIFEST = "./data/cfg/n108_sprites.json"

    private val names: List<String> by lazy {
        val text = runCatching { File(MANIFEST).readText() }.getOrDefault("")
        Regex("\"([a-z0-9_]+)\":\\s*\\[").findAll(text).map { it.groupValues[1] }.toList().sorted()
    }

    private val sizes: Map<String, IntArray> by lazy {
        val text = runCatching { File(MANIFEST).readText() }.getOrDefault("")
        Regex("\"([a-z0-9_]+)\":\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]").findAll(text).associate { it.groupValues[1] to intArrayOf(it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
    }

    fun sprite(name: String): Int = names.indexOf(name).let { if (it < 0) -1 else SPRITE_BASE + it }

    fun width(name: String): Int = sizes[name]?.get(0) ?: 0

    // ---- fonts

    private val GLYPHS = java.util.concurrent.ConcurrentHashMap<String, IntArray>()

    private fun glyphs(world: World, font: String): IntArray? =
        GLYPHS[font] ?: runCatching {
            val id = world.filestore.index(8).archive(font)!!.id
            val data = world.filestore.data(13, id, 0)!!
            IntArray(256) { data[2 + it].toInt() and 0xFF }
        }.getOrNull()?.also { GLYPHS[font] = it }

    private val TAGS = Regex("<[^>]*>")

    /** The width of [text] (colour tags left out) in [font] ("p11_full", "p12_full", "b12_full"), as the client draws it. */
    fun textWidth(world: World, font: String, text: String): Int {
        val g = glyphs(world, font) ?: return text.length * 6
        return TAGS.replace(text, "").sumOf { g[it.code and 0xFF] }
    }

    fun p11(world: World, text: String) = textWidth(world, "p11_full", text)

    /** [text] cut with "..." so it is at most [max] px wide in p11 (colour tags kept out of the count, then dropped). */
    fun fitP11(world: World, text: String, max: Int): String {
        if (p11(world, text) <= max) return text
        val plain = TAGS.replace(text, "")
        var cut = plain
        while (cut.isNotEmpty() && p11(world, "$cut...") > max) cut = cut.dropLast(1)
        return cut.trimEnd() + "..."
    }

    fun b12(world: World, text: String) = textWidth(world, "b12_full", text)

    // ---- chances

    /** "Always", "1/1,000", "1/12.3K", "1/33.4" - the design's format (dropviewer.html `fmt`). */
    fun chance(p: Double): String {
        if (p >= 0.9999) return "Always"
        if (p <= 0.0) return "-"
        val d = 1.0 / p
        return when {
            d >= 100_000 -> "1/${Math.round(d / 1000)}K"
            d >= 10_000 -> "1/${String.format(Locale.US, "%.1f", d / 1000).removeSuffix(".0")}K"
            d >= 1000 -> "1/${String.format(Locale.US, "%,d", Math.round(d))}"
            d >= 100 -> "1/${Math.round(d)}"
            else -> "1/${String.format(Locale.US, "%.1f", d).removeSuffix(".0")}"
        }
    }

    /** Wiki rarity colours (design): Always light blue, Common green (<= 1/25), Uncommon yellow, Rare orange (1/100+), Very rare red (1/1,000+). */
    fun rarityColour(p: Double): String {
        if (p >= 0.9999) return "afeeee"
        if (p <= 0.0) return "7a7064"
        val d = 1.0 / p
        return when {
            d <= 25 -> "56e156"
            d < 100 -> "ffed4c"
            d < 1000 -> "ff863c"
            else -> "ff6262"
        }
    }

    fun number(n: Int): String = String.format(Locale.US, "%,d", n)

    // ---- client hooks (RspsUi in the client)

    const val PREFIX = "__RSPS_UI__:"

    fun hash(interfaceId: Int, component: Int) = (interfaceId shl 16) or component

    private fun send(player: Player, line: String) = player.message(PREFIX + line, type = ChatMessageType.CONSOLE)

    /**
     * The console line travels in MESSAGE_GAME, a VARIABLE_BYTE packet (255 bytes with its header): a batch is split so no line is
     * longer than [MAX_LINE] characters.
     */
    const val MAX_LINE = 200

    private fun sendBatched(player: Player, head: String, parts: List<String>) {
        val line = StringBuilder()
        for (part in parts) {
            val p = part.take(MAX_LINE - PREFIX.length - head.length - 1)
            if (line.isNotEmpty() && PREFIX.length + head.length + line.length + 1 + p.length > MAX_LINE) {
                send(player, head + line)
                line.setLength(0)
            }
            if (line.isNotEmpty()) line.append(';')
            line.append(p)
        }
        if (line.isNotEmpty()) send(player, head + line)
    }

    /**
     * Registers the in-window text field: [text] shows what is typed (the placeholder while empty), a click on [hit] focuses it
     * and shows [onGraphic]; every change goes to the server as `::uisearch <text>`.
     */
    fun field(player: Player, interfaceId: Int, text: Int, hit: Int, onGraphic: Int, clear: Int, placeholder: String, value: String) =
        send(player, "field|${hash(interfaceId, text)}|${hash(interfaceId, hit)}|${hash(interfaceId, onGraphic)}|${hash(interfaceId, clear)}|$placeholder|$value")

    /** The first right-click option of a static component that has none in the cache (client RspsUi `op`). */
    fun op(player: Player, interfaceId: Int, component: Int, text: String) = send(player, "op|${hash(interfaceId, component)},$text")

    /** Full npc models on model components: (component hash, npc, zoom, x angle, spin). Sent in batches. */
    class Model(val hash: Int, val npc: Int, val zoom: Int, val xan: Int, val spin: Boolean)

    fun models(player: Player, models: List<Model>) {
        sendBatched(player, "npc|", models.map { "${it.hash},${it.npc},${it.zoom},${it.xan},${if (it.spin) 1 else 0}" })
    }

    private val FONT_IDS = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun fontId(world: World, font: String): Int =
        FONT_IDS[font] ?: runCatching { world.filestore.index(8).archive(font)!!.id }.getOrDefault(-1).also { if (it >= 0) FONT_IDS[font] = it }

    /** Text components drawn in another font ([interfaceId], component, "b12_full" / "p11_full"); only changes are sent. */
    fun fonts(player: Player, interfaceId: Int, wanted: List<Pair<Int, String>>) {
        val changed = wanted.filter { (c, font) -> cache(player)[key(interfaceId, c, 6)] != font }
        if (changed.isEmpty()) return
        changed.forEach { (c, font) -> cache(player)[key(interfaceId, c, 6)] = font }
        sendBatched(player, "font|", changed.map { "${hash(interfaceId, it.first)},${fontId(player.world, it.second)}" })
    }

    /** Tooltips (component hash -> text, '\n' = new line) shown by the client while the mouse is on the component. */
    fun tooltips(player: Player, tips: List<Pair<Int, String>>) {
        sendBatched(player, "tip|", tips.map { "${it.first}," + it.second.replace(";", ",").replace("|", "/") })
    }

    /** Sets the scroll height of [layer] and rebuilds its scrollbar (DeathsOffice SCROLL_SCRIPT). */
    fun scroll(player: Player, interfaceId: Int, layer: Int, scrollbar: Int, height: Int) {
        val k = key(interfaceId, layer, 5)
        if (cache(player)[k] == height.toString()) return
        cache(player)[k] = height.toString()
        player.runClientScript(gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT, hash(interfaceId, layer), hash(interfaceId, scrollbar), height)
    }

    // ---- send-only-changes

    private val SENT = AttributeKey<HashMap<Long, String>>()

    private fun cache(player: Player): HashMap<Long, String> = player.attr[SENT] ?: HashMap<Long, String>().also { player.attr[SENT] = it }

    private fun key(i: Int, c: Int, kind: Int) = (i.toLong() shl 32) or (c.toLong() shl 4) or kind.toLong()

    /** Forget what was sent for [interfaceId] (it was opened again: the client starts from the cache layout). */
    fun reset(player: Player, interfaceId: Int) {
        cache(player).keys.removeIf { (it ushr 32).toInt() == interfaceId }
    }

    fun text(player: Player, i: Int, c: Int, value: String) {
        val k = key(i, c, 0)
        if (cache(player)[k] == value) return
        cache(player)[k] = value
        player.setComponentText(i, c, value)
    }

    fun hidden(player: Player, i: Int, c: Int, hidden: Boolean) {
        val k = key(i, c, 1)
        val v = if (hidden) "1" else "0"
        if (cache(player)[k] == v) return
        cache(player)[k] = v
        player.setComponentHidden(i, c, hidden)
    }

    fun pos(player: Player, i: Int, c: Int, x: Int, y: Int) {
        val k = key(i, c, 2)
        val v = "$x,$y"
        if (cache(player)[k] == v) return
        cache(player)[k] = v
        player.setComponentPosition(i, c, x, y)
    }

    fun sprite(player: Player, i: Int, c: Int, name: String) {
        val k = key(i, c, 3)
        if (cache(player)[k] == name) return
        cache(player)[k] = name
        // A component that showed an item draws the item until it is cleared.
        if (cache(player)[key(i, c, 4)] != null) {
            cache(player).remove(key(i, c, 4))
            player.setComponentItem(i, c, -1, 0)
        }
        player.setComponentSprite(i, c, sprite(name))
        sizes[name]?.let { size(player, i, c, it[0], it[1]) }
    }

    /** Resizes a component (client RspsUi `size`): a sprite is drawn at the component's size, so it gets the sprite's own. */
    fun size(player: Player, i: Int, c: Int, w: Int, h: Int) {
        val k = key(i, c, 7)
        val v = "$w,$h"
        if (cache(player)[k] == v) return
        cache(player)[k] = v
        send(player, "size|${hash(i, c)},$w,$h")
    }

    fun item(player: Player, i: Int, c: Int, item: Int, amount: Int = 1) {
        val k = key(i, c, 4)
        val v = "$item,$amount"
        if (cache(player)[k] == v) return
        cache(player)[k] = v
        cache(player).remove(key(i, c, 3))
        player.setComponentItem(i, c, item, amount)
    }
}
