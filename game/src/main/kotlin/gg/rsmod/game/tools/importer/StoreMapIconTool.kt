package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.JsonParser
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Minimap and world-map icons for the three 78 Store npcs in the Royal Hall (owner 2026-09-29: "Alle shops in de mansion
 * moeten een MINIMAP EN WORLDMAP icoon hebben op de exacte positie ... moet ook meebewegen als ik de npcs in de toekomst wil
 * verplaatsen ... geef alle 3 een custom high quality icoon").
 *
 * **Minimap.** The client draws a minimap-visible, interactive npc's `mapElement` (NPCType opcode 142) at the npc's live
 * position (`Minimap.draw`), so the icon is hung on the npc type itself - exactly like the casino croupier
 * ([CasinoMinimapIconTool]) - and follows the npc wherever `data/world_edits.json` posts it, with no cache change.
 *
 * **World map.** The world map (`WorldMap.java`) knows nothing of live npcs: it draws its own prebuilt data plus the
 * `main_staticelements` files of the world-map index (u32 packed coord, u16 element, u8 members - [RoyalHallLocTool] put the
 * hall's icon there the same way). This tool therefore reads every npc post of the three store npcs out of
 * `data/world_edits.json` (the one source of every hall npc: spawns and removes replayed in order) and writes one static
 * element per post at that tile. After moving a store npc, re-run `apply`: an earlier placement of the same icon is moved,
 * never duplicated.
 *
 * **Icons.** Three 15 x 15 badges (the size of every 667 map icon: sprite 1361, the Grand Exchange icon, is 15 x 15) painted
 * here at 8x and box-filtered down, in the store themes of [StoreArtTool]: Donator a gold gem on royal purple, Deadman a skull
 * on blood red, Loyalty a gold star on deep blue, each in the game's black outline ring. OSRS was checked (openrs2 2686 map
 * elements): it has only the text label "Wilderness Emblem Trader" and no shop icon, so all three are custom.
 * Each MapElementType is the Grand Exchange element 637 (sprite, category, opcode 8, flags 3 = enabled + shown on the
 * minimap) with the sprite replaced.
 *
 * Usage: `./gradlew :game:runStoreMapIconTool --args="plan|apply [--png=<dir>]"`.
 */
object StoreMapIconTool {
    class Store(val name: String, val npc: Int, val sprite: Int, val element: Int, val paint: (Graphics2D) -> Unit)

    const val DONATOR_SPRITE = 8195
    const val DEADMAN_SPRITE = 8196
    const val LOYALTY_SPRITE = 8197
    const val DONATOR_ELEMENT = 1109
    const val DEADMAN_ELEMENT = 1110
    const val LOYALTY_ELEMENT = 1111

    val STORES =
        listOf(
            Store("Donator Store", 14436, DONATOR_SPRITE, DONATOR_ELEMENT, ::paintDonator),
            Store("Deadman Store", 14437, DEADMAN_SPRITE, DEADMAN_ELEMENT, ::paintDeadman),
            Store("Loyalty Store (Xuan)", 13727, LOYALTY_SPRITE, LOYALTY_ELEMENT, ::paintLoyalty),
        )

    const val WORLD_EDITS = "C:/RSPS/game/game/data/world_edits.json"
    val TARGETS = OsrsItemImportTool.TARGETS

    private const val SIZE = 15
    private const val SCALE = 8
    private const val CANVAS = SIZE * SCALE
    private const val SPRITE_INDEX = 8
    private const val CONFIG_INDEX = 2
    private const val MAP_ELEMENT_GROUP = 36
    private const val GE_ELEMENT = 637
    private const val NPC_INDEX = 18
    private const val WORLD_MAP_INDEX = 23
    private const val STATIC_ELEMENTS = "main_staticelements"

    // ---------------------------------------------------------------- painting

    private fun canvas(): Pair<BufferedImage, Graphics2D> {
        val image = BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        return image to g
    }

    /** The game's icon badge: a black outline ring, a themed disc with a soft top light, and a thin inner rim. */
    private fun badge(g: Graphics2D, fill: Int, fillDark: Int, rim: Int) {
        val c = CANVAS / 2.0
        g.color = Color(0x0A0A0A)
        g.fill(Ellipse2D.Double(c - 58, c - 58, 116.0, 116.0))
        g.paint = RadialGradientPaint(c.toFloat() - 12f, c.toFloat() - 16f, 62f, floatArrayOf(0f, 1f), arrayOf(Color(fill), Color(fillDark)))
        g.fill(Ellipse2D.Double(c - 50, c - 50, 100.0, 100.0))
        g.color = Color(rim)
        g.stroke = BasicStroke(5f)
        g.draw(Ellipse2D.Double(c - 47.5, c - 47.5, 95.0, 95.0))
    }

    /** Donator: a faceted gold gem on royal purple (the Donator Store theme: purple window, gold accents). */
    fun paintDonator(g: Graphics2D) {
        badge(g, 0x6A2E9A, 0x2A1846, 0xC99A2E)
        val c = CANVAS / 2.0
        val gem = Path2D.Double()
        gem.moveTo(c - 40, c - 12)
        gem.lineTo(c - 24, c - 36)
        gem.lineTo(c + 24, c - 36)
        gem.lineTo(c + 40, c - 12)
        gem.lineTo(c, c + 42)
        gem.closePath()
        g.paint = GradientPaint(c.toFloat(), (c - 36).toFloat(), Color(0xFFF6C8), c.toFloat(), (c + 42).toFloat(), Color(0xD39C1E))
        g.fill(gem)
        // Facets: the crown line and the three lower facet edges.
        g.color = Color(0x8A5E10)
        g.stroke = BasicStroke(3.5f)
        g.draw(gem)
        g.drawLine((c - 40).toInt(), (c - 12).toInt(), (c + 40).toInt(), (c - 12).toInt())
        g.drawLine((c - 24).toInt(), (c - 36).toInt(), (c - 14).toInt(), (c - 12).toInt())
        g.drawLine((c + 24).toInt(), (c - 36).toInt(), (c + 14).toInt(), (c - 12).toInt())
        g.drawLine((c - 14).toInt(), (c - 12).toInt(), c.toInt(), (c + 42).toInt())
        g.drawLine((c + 14).toInt(), (c - 12).toInt(), c.toInt(), (c + 42).toInt())
        // Sparkle on the top-left facet.
        g.color = Color(0xFFFFFF)
        g.fill(Ellipse2D.Double(c - 28, c - 31, 8.0, 8.0))
    }

    /** Deadman: a bone-white skull on blood red (the Deadman Store theme and the Deadman's skull). */
    fun paintDeadman(g: Graphics2D) {
        badge(g, 0x7A1A20, 0x2A0608, 0xB03038)
        val c = CANVAS / 2.0
        val bone = Color(0xFFFFFF)
        val shade = Color(0xDDD5C2)
        // Cranium and jaw: as large as the disc allows, so the skull reads at 15 px.
        g.paint = RadialGradientPaint((c - 10).toFloat(), (c - 26).toFloat(), 50f, floatArrayOf(0f, 1f), arrayOf(bone, shade))
        g.fill(Ellipse2D.Double(c - 36, c - 44, 72.0, 64.0))
        g.fill(RoundRectangle2D.Double(c - 24, c + 6, 48.0, 32.0, 12.0, 12.0))
        // Eye sockets, nose, teeth in the badge's dark red.
        g.color = Color(0x2A0608)
        g.fill(Ellipse2D.Double(c - 29, c - 26, 24.0, 26.0))
        g.fill(Ellipse2D.Double(c + 5, c - 26, 24.0, 26.0))
        val nose = Path2D.Double()
        nose.moveTo(c, c - 2)
        nose.lineTo(c - 7, c + 11)
        nose.lineTo(c + 7, c + 11)
        nose.closePath()
        g.fill(nose)
        g.stroke = BasicStroke(3.5f)
        for (x in listOf(-14, -5, 4, 13)) g.drawLine((c + x).toInt(), (c + 20).toInt(), (c + x).toInt(), (c + 36).toInt())
        g.drawLine((c - 24).toInt(), (c + 22).toInt(), (c + 24).toInt(), (c + 22).toInt())
    }

    /** Loyalty: a gold five-point star on deep blue (the Loyalty Store theme: blue window, light-blue glow). */
    fun paintLoyalty(g: Graphics2D) {
        badge(g, 0x1F6FB8, 0x071A30, 0x5FC4FF)
        val c = CANVAS / 2.0
        val star = Path2D.Double()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 45.0 else 19.0
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = c + Math.cos(a) * r
            val y = c + 2 + Math.sin(a) * r
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.closePath()
        g.paint = GradientPaint(c.toFloat(), (c - 40).toFloat(), Color(0xFFF3B8), c.toFloat(), (c + 40).toFloat(), Color(0xD9A21E))
        g.fill(star)
        g.color = Color(0x8A5E10)
        g.stroke = BasicStroke(3.5f)
        g.draw(star)
        g.color = Color(0xFFFFFF)
        g.fill(Ellipse2D.Double(c - 12, c - 14, 7.0, 7.0))
    }

    /** Box-filters the 8x canvas down to the 15 x 15 sprite (alpha-weighted, so the ring edge stays clean). */
    private fun downscale(source: BufferedImage): BufferedImage {
        val out = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                var a = 0L
                var r = 0L
                var gr = 0L
                var b = 0L
                for (sy in 0 until SCALE) {
                    for (sx in 0 until SCALE) {
                        val p = source.getRGB(x * SCALE + sx, y * SCALE + sy)
                        val pa = (p ushr 24) and 0xFF
                        a += pa
                        r += ((p shr 16) and 0xFF) * pa
                        gr += ((p shr 8) and 0xFF) * pa
                        b += (p and 0xFF) * pa
                    }
                }
                val n = SCALE * SCALE
                val alpha = (a / n).toInt()
                // Map icons have hard edges: anything mostly covered is opaque, the rest transparent.
                val colour =
                    if (a == 0L) 0 else ((r / a).toInt() shl 16) or ((gr / a).toInt() shl 8) or (b / a).toInt()
                out.setRGB(x, y, if (alpha >= 96) (0xFF shl 24) or colour else 0)
            }
        }
        return out
    }

    fun icon(store: Store): BufferedImage {
        val (image, g) = canvas()
        try {
            store.paint(g)
        } finally {
            g.dispose()
        }
        return downscale(image)
    }

    // ---------------------------------------------------------------- npc posts

    class Post(val x: Int, val z: Int, val level: Int)

    /** Every current post of [npc] in `world_edits.json`: the spawns minus the removes, replayed in file order. */
    fun posts(npc: Int, worldEdits: File = File(WORLD_EDITS)): List<Post> {
        val root = JsonParser().parse(worldEdits.readText()).asJsonObject
        val edits = root.getAsJsonArray("edits") ?: error("$worldEdits has no 'edits' array")
        val current = mutableListOf<Post>()
        edits.forEach { e ->
            val o = e.asJsonObject
            if (o["id"]?.asInt != npc) return@forEach
            val post = Post(o["x"].asInt, o["z"].asInt, o["level"]?.asInt ?: 0)
            when (o["op"]?.asString) {
                "npc_spawn" -> current += post
                "npc_remove" -> current.removeAll { it.x == post.x && it.z == post.z && it.level == post.level }
            }
        }
        return current
    }

    private fun staticEntry(element: Int, post: Post): ByteArray {
        val coord = (post.level shl 28) or (post.x shl 14) or post.z
        return byteArrayOf((coord ushr 24).toByte(), (coord ushr 16).toByte(), (coord ushr 8).toByte(), coord.toByte(), (element shr 8).toByte(), element.toByte(), 0)
    }

    private fun withSprite(def: ByteArray, sprite: Int): ByteArray {
        check(def[0] == 1.toByte()) { "expected the sprite first" }
        return def.copyOf().also {
            it[1] = (sprite shr 8).toByte()
            it[2] = sprite.toByte()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply [--png=<dir>]" }
        args.firstOrNull { it.startsWith("--png=") }?.substringAfter('=')?.let { dir ->
            File(dir).mkdirs()
            STORES.forEach { s ->
                val small = icon(s)
                ImageIO.write(small, "png", File(dir, "${s.sprite}.png"))
                val big = BufferedImage(SIZE * 8, SIZE * 8, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until SIZE * 8) for (x in 0 until SIZE * 8) big.setRGB(x, y, small.getRGB(x / 8, y / 8))
                ImageIO.write(big, "png", File(dir, "${s.sprite}_x8.png"))
            }
            println("PNG written to $dir")
        }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(TARGETS[0])
        try {
            val geElement = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, GE_ELEMENT) ?: error("map element $GE_ELEMENT missing")
            val staticGroup = library.index(WORLD_MAP_INDEX).archive(STATIC_ELEMENTS) ?: error("$STATIC_ELEMENTS missing")
            var nextStaticFile = (staticGroup.fileIds().maxOrNull() ?: -1) + 1
            STORES.forEach { store ->
                // 1. The sprite.
                val sprite = StoreArtTool.encode(icon(store))
                val currentSprite = library.data(SPRITE_INDEX, store.sprite, 0)
                when {
                    currentSprite == null -> println("CREATE sprite ${store.sprite} (${store.name} map icon)")
                    currentSprite.contentEquals(sprite) -> println("SPRITE ${store.sprite} already in place")
                    else -> println("REPLACE sprite ${store.sprite} (${store.name} map icon redrawn)")
                }
                if (currentSprite == null || !currentSprite.contentEquals(sprite)) {
                    mutations += CacheMutation(SPRITE_INDEX, store.sprite, 0, sprite, "${store.name} map icon sprite", currentSprite?.let { CacheItemProbeTool.sha1(it) })
                }
                // 2. The map element: 637 with this sprite.
                val element = withSprite(geElement, store.sprite)
                val currentElement = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, store.element)
                when {
                    currentElement == null -> {
                        println("CREATE map element ${store.element} (637 with sprite ${store.sprite})")
                        mutations += CacheMutation(CONFIG_INDEX, MAP_ELEMENT_GROUP, store.element, element, "${store.name} map element", null)
                    }
                    currentElement.contentEquals(element) -> println("MAP ELEMENT ${store.element} already in place")
                    else -> error("map element ${store.element} already exists with other content; refusing to overwrite it")
                }
                // 3. The npc carries the element on the minimap (opcode 142, appended like CasinoMinimapIconTool).
                val npcBytes = library.data(NPC_INDEX, store.npc ushr 7, store.npc and 0x7F) ?: error("npc ${store.npc} missing")
                check(npcBytes.last() == 0.toByte()) { "npc ${store.npc} does not end with the opcode-0 terminator" }
                val before = CasinoMinimapIconTool.appendedMapElement(npcBytes)
                when (before) {
                    store.element -> println("NPC ${store.npc} already carries map element ${store.element}")
                    -1 -> {
                        val patched = CasinoMinimapIconTool.patched(npcBytes, store.element)
                        check(CasinoMinimapIconTool.appendedMapElement(patched) == store.element) { "npc ${store.npc} patch re-read mismatch" }
                        val def = NpcDef(store.npc).also { it.decode(Unpooled.wrappedBuffer(patched)) }
                        check(def.mapElement == store.element && def.interactable) { "npc ${store.npc} decodes wrong after the patch (${def.mapElement}, ${def.interactable})" }
                        println("NPC ${store.npc} '${def.name}' mapElement -1 -> ${store.element}")
                        mutations += CacheMutation(NPC_INDEX, store.npc ushr 7, store.npc and 0x7F, patched, "${store.name} npc ${store.npc} mapelement=${store.element}", CacheItemProbeTool.sha1(npcBytes))
                    }
                    else -> error("npc ${store.npc} already carries map element $before; refusing")
                }
                // 4. The world map: one static element per current post of the npc.
                val posts = posts(store.npc)
                check(posts.isNotEmpty()) { "${store.name}: npc ${store.npc} has no post in $WORLD_EDITS" }
                val wanted = posts.map { staticEntry(store.element, it) }
                val existing = mutableListOf<Pair<Int, ByteArray>>()
                for (f: Int in staticGroup.fileIds()) {
                    val d: ByteArray = library.data(WORLD_MAP_INDEX, staticGroup.id, f) ?: continue
                    if (d.size == 7 && d[4] == (store.element shr 8).toByte() && d[5] == store.element.toByte()) existing += Pair(f, d)
                }
                wanted.forEachIndexed { i, entry ->
                    val post = posts[i]
                    val same = existing.indexOfFirst { it.second.contentEquals(entry) }
                    if (same >= 0) {
                        println("WORLD MAP ${store.name} icon already at ${post.x},${post.z},${post.level} (file ${existing[same].first})")
                        existing.removeAt(same)
                        return@forEachIndexed
                    }
                    if (existing.isNotEmpty()) {
                        val (file, old) = existing.removeAt(0)
                        println("MOVE world map ${store.name} icon: file $file to ${post.x},${post.z},${post.level}")
                        mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, file, entry, "${store.name} world map icon (moved)", CacheItemProbeTool.sha1(old))
                    } else {
                        val file = nextStaticFile++
                        println("CREATE world map ${store.name} icon: $STATIC_ELEMENTS file $file, element ${store.element} at ${post.x},${post.z},${post.level}")
                        mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, file, entry, "${store.name} world map icon", null)
                    }
                }
                existing.forEach { (file, _) -> println("WARNING stale world map ${store.name} icon in $STATIC_ELEMENTS file $file (npc no longer posted there); left in place") }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING TO DO: every icon, element, npc and world map entry is in place")
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
