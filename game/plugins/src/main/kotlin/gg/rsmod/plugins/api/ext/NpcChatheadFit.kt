package gg.rsmod.plugins.api.ext

import gg.rsmod.game.message.impl.IfSetAngleMessage
import gg.rsmod.game.message.impl.IfSetPositionMessage
import gg.rsmod.game.model.entity.Player
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

/**
 * Owner A4 (bugs\bug-20261011-041436-anudd.txt, foto\dy.png: Smol Heredit's winged helmet runs off the top of the chat
 * dialogue and the Follower Details frame; "check all osrs pets and fix them where needed to make them fitting").
 *
 * Donor route: 2009scape `DialogueInterpreter.sendDialogues` (npc dialogue) sends, with every npc head, the head's position
 * (`sendRepositionOnInterface`, IF_SETPOSITION) and its camera (`sendAngleOnInterface(if, 2, 2150, 40, 1882)`, IF_SETANGLE);
 * Void (`NPCDialogue.kt` + npc param `large_head`) keeps "this npc's head is modelled at another scale" as per-npc DATA.
 * Here the data is `data\cfg\pets\pet_chathead_extents.tsv`: per pet npc and surface, the largest box its posed head covers
 * over its whole talking animation at the cache camera (pixels from the head's anchor: up/down/left/right), measured in the
 * real client (InterfaceManager.measureNpcHead, `petheads` sweep; tools\cache667\chathead_extents.py). From it the server
 * picks the smallest zoom-out (and, if the head would still cross an edge, a move inside the frame) that keeps the whole
 * head inside the frame. A head that already fits - every native 667 head, every npc without a row - gets exactly the
 * cache camera and position.
 */
object NpcChatheadFit {
    /** One head slot: its cache camera, its cache box inside the parent (anchor = box centre) and the visible frame. */
    data class Surface(
        val name: String,
        val zoom: Int,
        val xan: Int,
        val yan: Int,
        val boxX: Int,
        val boxY: Int,
        val boxWidth: Int,
        val boxHeight: Int,
        val frameX1: Int,
        val frameY1: Int,
        val frameX2: Int,
        val frameY2: Int,
    ) {
        val anchorX get() = boxX + boxWidth / 2
        val anchorY get() = boxY + boxHeight / 2
    }

    /** Pixels the posed head covers from its anchor at the surface's cache zoom. */
    data class Extents(val up: Int, val down: Int, val left: Int, val right: Int)

    /** What the server sends: the zoom and the box position inside the parent ([moved] false = cache position kept). */
    data class Fit(val zoom: Int, val x: Int, val y: Int, val moved: Boolean)

    /**
     * Npc dialogue 241-244:2 (cache: 32x32 box at x=45,y=46 of the 504x124 chat dialogue - x proportional 1474/16384,
     * y centred; zoom 2150, xan 40, yan 1882, the same values 2009scape re-sends). Frame: the dialogue minus its 4-px border,
     * and the head stays left of the text column (the dialogue text starts ~128 px in).
     */
    val DIALOGUE = Surface("dialogue", 2150, 40, 1882, 45, 46, 32, 32, 4, 4, 124, 120)

    /**
     * Follower Details 662:1 (cache: 78x86 box at 53,13 in the 178x114 model layer at 5,26; zoom 2168, xan 2033, yan 1833).
     * Frame: the dark model box inside the frame border, 662:2 (14,31 161x105) = 9,5 .. 170,110 in the layer.
     */
    val PANEL = Surface("panel", 2168, 2033, 1833, 53, 13, 78, 86, 9, 5, 170, 110)

    /** Projection is not exactly linear in the zoom (the near half of a head shrinks a little less): keep 4% spare. */
    private const val MARGIN = 1.04

    /**
     * A head that covers less than this share of its frame (on its fuller axis) is drawn too small: it is zoomed in. N397 sweep
     * 10-29: at 0.6 a head that is wide but low (Yami's horns, Little Nightmare's wings, Bran/Ric's flames) stayed at the cache
     * camera and looked tiny next to the heads brought in to [FILL].
     */
    const val SMALL = 0.75

    /** The share of the frame a zoomed-in head fills on its fuller axis. */
    const val FILL = 0.85

    /** A sweep samples the talking animation, it can miss its widest frame by a pixel or two: every side counts 2 px more. */
    private const val PAD = 2

    const val TABLE_NAME = "pet_chathead_extents.tsv"
    private val TABLE_CANDIDATES = listOf("./data/cfg/pets/$TABLE_NAME", "../../data/cfg/pets/$TABLE_NAME")

    @Volatile private var table: Map<String, Map<Int, Extents>>? = null

    fun parse(lines: List<String>): Map<String, Map<Int, Extents>> {
        val out = HashMap<String, HashMap<Int, Extents>>()
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('\t').map { it.trim() }
            if (f.size < 6) return@forEach
            val npc = f[0].toIntOrNull() ?: return@forEach
            out.getOrPut(f[1]) { HashMap() }[npc] = Extents(f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt())
        }
        return out
    }

    private fun table(): Map<String, Map<Int, Extents>> =
        table ?: run {
            val file = TABLE_CANDIDATES.map(::File).firstOrNull { it.isFile }
            if (file == null) {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INTERFACE) { "chathead fit: no $TABLE_NAME - every head keeps the cache camera" }
            }
            (file?.let { parse(it.readLines()) } ?: emptyMap()).also { table = it }
        }

    fun extents(surface: Surface, npc: Int): Extents? =
        table()[surface.name]?.let { it[npc] ?: it[gg.rsmod.plugins.content.skills.summoning.Pets.baseNpc(npc)] } // N423: an Upgraded pet has its pet's head

    /** The camera and position that keep [extents] inside [surface]'s frame; the cache values when it already fits. */
    fun fit(surface: Surface, measured: Extents?): Fit {
        val cache = Fit(surface.zoom, surface.boxX, surface.boxY, false)
        if (measured == null) return cache
        val extents = Extents(measured.up + PAD, measured.down + PAD, measured.left + PAD, measured.right + PAD)
        val roomUp = surface.anchorY - surface.frameY1
        val roomDown = surface.frameY2 - surface.anchorY
        val roomLeft = surface.anchorX - surface.frameX1
        val roomRight = surface.frameX2 - surface.anchorX
        val needed =
            max(
                (extents.up + extents.down).toDouble() / (roomUp + roomDown),
                (extents.left + extents.right).toDouble() / (roomLeft + roomRight),
            )
        val fits = extents.up <= roomUp && extents.down <= roomDown && extents.left <= roomLeft && extents.right <= roomRight
        if (fits && needed >= SMALL) return cache
        // K02 N397: an OSRS pet head that no longer plays the human poses (which scaled it up) is drawn far too small at the cache
        // camera - it is brought in until it fills [FILL] of the frame, the way the human-posed heads did.
        val scale = if (needed > 1.0) needed * MARGIN else if (needed < SMALL) needed / FILL else 1.0
        val zoom = ceil(surface.zoom * scale).toInt()
        val shrink = surface.zoom.toDouble() / zoom
        val anchorY = centre(surface.anchorY, extents.up * shrink, extents.down * shrink, surface.frameY1, surface.frameY2)
        val anchorX = centre(surface.anchorX, extents.left * shrink, extents.right * shrink, surface.frameX1, surface.frameX2)
        val x = anchorX - surface.boxWidth / 2
        val y = anchorY - surface.boxHeight / 2
        return Fit(zoom, x, y, x != surface.boxX || y != surface.boxY)
    }

    /** Keeps the anchor where it is when both sides fit, otherwise centres the head's span in the frame on that axis. */
    private fun centre(anchor: Int, before: Double, after: Double, low: Int, high: Int): Int {
        if (before <= anchor - low && after <= high - anchor) return anchor
        val gap = ((high - low) - before - after) / 2.0
        return low + ceil(gap + before).toInt()
    }

    fun dialogue(npc: Int): Fit = fit(DIALOGUE, extents(DIALOGUE, npc))

    fun panel(npc: Int): Fit = fit(PANEL, extents(PANEL, npc))

    /** Sends [surface]'s camera for [npc] on [interfaceId]:[component] (always, like 2009scape) and the position when moved. */
    fun send(
        player: Player,
        surface: Surface,
        interfaceId: Int,
        component: Int,
        npc: Int,
        resetPosition: Boolean,
    ) {
        val fit = fit(surface, extents(surface, npc))
        val hash = (interfaceId shl 16) or component
        player.write(IfSetAngleMessage(hash = hash, zoom = fit.zoom, xan = surface.xan, yan = surface.yan))
        if (fit.moved || resetPosition) {
            player.write(IfSetPositionMessage(hash = hash, x = fit.x, y = fit.y))
        }
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.INTERFACE) {
            "chathead fit npc $npc on $interfaceId:$component zoom ${fit.zoom} at ${fit.x},${fit.y}${if (fit.moved) " (moved)" else ""}"
        }
    }

    /** True for the npc dialogue head components 241-244:2. */
    fun isDialogueHead(
        interfaceId: Int,
        component: Int,
    ): Boolean = interfaceId in 241..244 && component == 2
}
