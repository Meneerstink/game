package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetPositionMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.setComponentNpcHead
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A4 class test (owner bugs\bug-20261011-041436-anudd.txt, foto\dy.png): EVERY OSRS pet head - all 180 variants of
 * pets.tsv - fits the npc dialogue (241-244:2) and the Follower Details frame (662:1), in every frame of its talking
 * animation. The head sizes are the measurements in data\cfg\pets\pet_chathead_extents.tsv (real client, `petheads`
 * sweep, largest box over the animation at the cache camera); the camera and position are whatever the server sends.
 * The frames are cache facts (decoded from index 3), kept here on purpose instead of read from the server code:
 *  - 241-244:2: 32x32 box at 45,46 of the 504x124 dialogue (x proportional 1474/16384, y centred), zoom 2150, xan 40,
 *    yan 1882; visible inside the 4-px border and left of the text (x 4..124, y 4..120).
 *  - 662:1: 78x86 box at 53,13 of the 178x114 model layer (5,26), zoom 2168; visible inside 662:2 (14,31 161x105) =
 *    x 9..170, y 5..110 of the layer.
 * A head drawn at zoom Z covers its measured pixels x (cache zoom / Z) around its anchor (the box centre).
 * Neighbours: a native 667 npc head and a native familiar keep the exact cache camera and position.
 */
class PetChatheadFitTests {
    private data class Frame(val zoom: Int, val boxX: Int, val boxY: Int, val box: Int, val boxH: Int, val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()
        private val EXTENTS = File(DIR, "pet_chathead_extents.tsv")
        private val DIALOGUE = Frame(2150, 45, 46, 32, 32, 4, 4, 124, 120)
        private val PANEL = Frame(2168, 53, 13, 78, 86, 9, 5, 170, 110)

        @BeforeClass
        @JvmStatic
        fun load() {
            assertTrue(Pets.load(PetsTests.TABLE, PetsTests.TIERS) > 0, "data/cfg/pets/pets.tsv is missing")
        }
    }

    /** npc -> surface -> up, down, left, right (pixels at the cache zoom). */
    private fun measured(): Map<Int, Map<String, IntArray>> {
        assertTrue(EXTENTS.isFile, "data/cfg/pets/pet_chathead_extents.tsv is missing - run the petheads sweep + tools\\cache667\\chathead_extents.py")
        val out = HashMap<Int, HashMap<String, IntArray>>()
        EXTENTS.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('\t')
            out.getOrPut(f[0].toInt()) { HashMap() }[f[1]] = intArrayOf(f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt())
        }
        return out
    }

    private fun capturing(): Pair<Player, MutableList<Message>> {
        val sent = mutableListOf<Message>()
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.varps } returns gg.rsmod.game.model.varp.VarpSet((0 until 8000).toSet())
        every { player.varcs } returns MutableList(4000) { 0 }
        every { player.world.definitions } returns SummoningTestCache.definitions
        every { player.write(*anyVararg<Message>()) } answers { sent += firstArg<Array<Message>>() }
        return player to sent
    }

    /** The camera zoom the client ends up with on [hash]: the last IF_SETANGLE sent, else the cache value. */
    private fun zoomOf(
        sent: List<Message>,
        hash: Int,
        cache: Int,
    ): Int {
        val angle = sent.lastOrNull { it.javaClass.simpleName == "IfSetAngleMessage" && field(it, "hash") == hash } ?: return cache
        return field(angle, "zoom")
    }

    private fun field(
        message: Any,
        name: String,
    ): Int = message.javaClass.getMethod("get" + name.replaceFirstChar { it.uppercase() }).invoke(message) as Int

    /** Why the head does not fit, or null. */
    private fun overflow(
        ext: IntArray,
        frame: Frame,
        sent: List<Message>,
        hash: Int,
    ): String? {
        val zoom = zoomOf(sent, hash, frame.zoom)
        val pos = sent.filterIsInstance<IfSetPositionMessage>().lastOrNull { it.hash == hash }
        val anchorX = (pos?.x ?: frame.boxX) + frame.box / 2
        val anchorY = (pos?.y ?: frame.boxY) + frame.boxH / 2
        val k = frame.zoom.toDouble() / zoom
        val up = ext[0] * k
        val down = ext[1] * k
        val left = ext[2] * k
        val right = ext[3] * k
        val over = mutableListOf<String>()
        if (anchorY - up < frame.y1) over += "top by ${"%.0f".format(frame.y1 - (anchorY - up))} px"
        if (anchorY + down > frame.y2) over += "bottom by ${"%.0f".format(anchorY + down - frame.y2)} px"
        if (anchorX - left < frame.x1) over += "left by ${"%.0f".format(frame.x1 - (anchorX - left))} px"
        if (anchorX + right > frame.x2) over += "right by ${"%.0f".format(anchorX + right - frame.x2)} px"
        return if (over.isEmpty()) null else "zoom $zoom at ${anchorX - frame.box / 2},${anchorY - frame.boxH / 2}: over the ${over.joinToString(", ")}"
    }

    @Test
    fun `every pet head fits every npc dialogue 241-244 in every frame of its talking animation`() {
        val ext = measured()
        val wrong = mutableListOf<String>()
        Pets.variants.forEach { v ->
            val e = ext[v.npc]?.get("dialogue")
            if (e == null) {
                wrong += "${v.name} (npc ${v.npc}): not measured"
                return@forEach
            }
            for (iface in 241..244) {
                val (player, sent) = capturing()
                player.setComponentNpcHead(interfaceId = iface, component = 2, npc = v.npc)
                overflow(e, DIALOGUE, sent, (iface shl 16) or 2)?.let { wrong += "${v.name} (npc ${v.npc}) on $iface:2 $it" }
            }
        }
        if (wrong.isNotEmpty()) fail("${wrong.size} pet dialogue heads do not fit:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun `every pet head fits the Follower Details frame in every frame of its animation`() {
        val ext = measured()
        val wrong = mutableListOf<String>()
        Pets.variants.forEach { v ->
            val e = ext[v.npc]?.get("panel")
            if (e == null) {
                wrong += "${v.name} (npc ${v.npc}): not measured"
                return@forEach
            }
            val (player, sent) = capturing()
            val pet = mockk<Npc>(relaxed = true)
            every { pet.id } returns v.npc
            every { player.world.npcs.contains(pet) } returns true
            player.attr[Pets.PET_ATTR] = WeakReference(pet)
            SummoningUi.settle(player)
            overflow(e, PANEL, sent, (SummoningUi.PANEL shl 16) or 1)?.let { wrong += "${v.name} (npc ${v.npc}) on 662:1 $it" }
        }
        if (wrong.isNotEmpty()) fail("${wrong.size} pet Follower Details heads do not fit:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun `a native npc head keeps the cache camera and position in the dialogue`() {
        // GE clerk 2241, Quest Tutor 14482 (owner Q19: its head stays as it is), Skully 14382.
        for (npc in listOf(2241, 14482, 14382)) {
            for (iface in 241..244) {
                val (player, sent) = capturing()
                player.setComponentNpcHead(interfaceId = iface, component = 2, npc = npc)
                val hash = (iface shl 16) or 2
                assertEquals(DIALOGUE.zoom, zoomOf(sent, hash, DIALOGUE.zoom), "npc $npc on $iface:2 zoom")
                sent.filterIsInstance<IfSetPositionMessage>().filter { it.hash == hash }.forEach {
                    assertEquals(DIALOGUE.boxX to DIALOGUE.boxY, it.x to it.y, "npc $npc on $iface:2 was moved")
                }
                sent.filter { it.javaClass.simpleName == "IfSetAngleMessage" }.forEach {
                    assertEquals(40, field(it, "xan"))
                    assertEquals(1882, field(it, "yan"))
                }
            }
        }
    }

    @Test
    fun `a native head after a moved pet head on the same dialogue is back at the cache camera and position`() {
        // The client keeps a component's IF_SETPOSITION / IF_SETANGLE while the same interface stays open (petheads
        // re-opens 242 for every pet; a conversation re-opens 243 for the next page): Smol Heredit, then the GE clerk.
        for (iface in 241..244) {
            val (player, sent) = capturing()
            val hash = (iface shl 16) or 2
            player.setComponentNpcHead(interfaceId = iface, component = 2, npc = 14652)
            player.setComponentNpcHead(interfaceId = iface, component = 2, npc = 2241)
            assertEquals(DIALOGUE.zoom, zoomOf(sent, hash, DIALOGUE.zoom), "GE clerk after Smol Heredit on $iface:2 zoom")
            val pos = sent.filterIsInstance<IfSetPositionMessage>().lastOrNull { it.hash == hash }
            assertTrue(pos == null || (pos.x to pos.y) == (DIALOGUE.boxX to DIALOGUE.boxY), "GE clerk after Smol Heredit on $iface:2 at ${pos?.x},${pos?.y}")
        }
    }

    @Test
    fun `a native familiar keeps the cache camera and position in Follower Details`() {
        for (pouch in listOf(SummoningPouchData.STEEL_TITAN, SummoningPouchData.WAR_TORTOISE, SummoningPouchData.PACK_YAK)) {
            val (player, sent) = capturing()
            val familiar = mockk<Npc>(relaxed = true)
            every { familiar.id } returns pouch.npc
            every { player.world.npcs.contains(familiar) } returns true
            player.attr[Pets.PET_ATTR] = WeakReference(familiar)
            SummoningUi.settle(player)
            val hash = (SummoningUi.PANEL shl 16) or 1
            assertEquals(PANEL.zoom, zoomOf(sent, hash, PANEL.zoom), "${pouch.name} zoom")
            sent.filterIsInstance<IfSetPositionMessage>().filter { it.hash == hash }.forEach {
                assertEquals(PANEL.boxX to PANEL.boxY, it.x to it.y, "${pouch.name} position")
            }
        }
    }
}
