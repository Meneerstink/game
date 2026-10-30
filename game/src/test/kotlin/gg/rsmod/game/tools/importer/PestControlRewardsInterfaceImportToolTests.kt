package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.PestControlRewardsInterfaceImportTool as Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N459: the Void Knight deflector row (267:127) had the item's 3D inventory offsets (-12, 72) written into the component's 2D
 * projection centre, so the shield was drawn 68 px below its box. A copied model slot keeps its template's centre, and a cache
 * built before the fix is repaired on the origin only.
 */
class PestControlRewardsInterfaceImportToolTests {
    /** A legacy ortho-2d model component body: model at 19, flags 1 at 21, origin at 22/24, xan 26, yan 28, zoom 32. */
    private fun model(model: Int, originX: Int, originY: Int, xan: Int = 375, yan: Int = 3, zoom: Int = 1040): ByteArray {
        val b = ByteArray(72)
        fun put2(at: Int, v: Int) {
            b[at] = (v shr 8).toByte()
            b[at + 1] = v.toByte()
        }
        b[0] = 0xFF.toByte()
        b[1] = 6
        put2(19, model)
        b[21] = 1
        put2(22, originX)
        put2(24, originY)
        put2(26, xan)
        put2(28, yan)
        put2(32, zoom)
        return b
    }

    private fun s2(b: ByteArray, at: Int): Int = (((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)).let { if (it > Short.MAX_VALUE) it - 65536 else it }

    @Test
    fun `a copied model slot keeps the template's projection centre`() {
        val out = Tool.withLook(model(Tool.VOID_TOP.model, 0, -1), Tool.VOID_TOP, Tool.DEFLECTOR)
        assertEquals(0, s2(out, 22))
        assertEquals(-1, s2(out, 24), "not -1 - 3 + 72 = 68 (the shield 68 px below its box)")
        assertEquals(Tool.DEFLECTOR.model, s2(out, 19) and 0xFFFF)
    }

    @Test
    fun `an old cache gets only the origins of the wrong slots set back`() {
        val current = HashMap<Int, ByteArray>()
        for (id in 0 until Tool.COMPONENT_COUNT) current[id] = ByteArray(72)
        current[18] = model(Tool.VOID_TOP.model, 0, -1)
        current[19] = model(Tool.VOID_ROBE.model, 0, -1, xan = 261, yan = 2041, zoom = 1808)
        current[Tool.ELITE_TOP_MODEL] = model(Tool.ELITE_TOP.model, 0, -1, xan = 402, yan = 108, zoom = 1104)
        current[Tool.ELITE_ROBE_MODEL] = model(Tool.ELITE_ROBE.model, 0, -4, xan = 210, yan = 1858, zoom = 1559)
        current[Tool.DEFLECTOR_MODEL] = model(Tool.DEFLECTOR.model, -12, 68, xan = 198, yan = 1895, zoom = 814)

        val fixed = Tool.repairOrigins(current)

        assertEquals(setOf(Tool.ELITE_ROBE_MODEL, Tool.DEFLECTOR_MODEL), fixed.keys, "the elite top is already right")
        val deflector = fixed.getValue(Tool.DEFLECTOR_MODEL)
        assertEquals(0, s2(deflector, 22))
        assertEquals(-1, s2(deflector, 24))
        val before = current.getValue(Tool.DEFLECTOR_MODEL)
        assertTrue((0 until 72).filter { it !in 22..25 }.all { deflector[it] == before[it] }, "nothing but the origin changes")
        assertTrue(Tool.repairOrigins(current + fixed).isEmpty(), "a repaired cache needs nothing")
    }
}
