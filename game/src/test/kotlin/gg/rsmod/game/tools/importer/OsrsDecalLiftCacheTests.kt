package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N32 (Onderzoeker A5 10-16: 215 imported OSRS meshes still hid detail faces behind the cloth - the A5 lift could not move a face
 * that shares a vertex with the cloth). Class guard over every imported OSRS item mesh in both caches: nothing left to lift.
 */
class OsrsDecalLiftCacheTests {
    /** A cloth quad (priority 0) at z=0 and an emblem triangle (priority 5) 4 units behind it, sharing one cloth vertex. */
    private fun clothWithSharedEmblem(): ModelData {
        val m = ModelData(5, 3, 0)
        val xs = intArrayOf(0, 20, 20, 0, 10)
        val ys = intArrayOf(0, 0, 20, 20, 10)
        val zs = intArrayOf(0, 0, 0, 0, -4)
        xs.copyInto(m.vertexX); ys.copyInto(m.vertexY); zs.copyInto(m.vertexZ)
        intArrayOf(0, 0, 4).copyInto(m.faceA); intArrayOf(1, 2, 1).copyInto(m.faceB); intArrayOf(2, 3, 2).copyInto(m.faceC)
        m.facePriority = byteArrayOf(0, 0, 5)
        m.vertexLabel = intArrayOf(1, 1, 1, 1, 2)
        return m
    }

    @Test
    fun `a detail face sharing a vertex with the cloth gets its own copy and comes in front, the cloth stays`() {
        val m = clothWithSharedEmblem()
        assertNull(OsrsDecalLiftTool.lift(clothWithSharedEmblem()), "the 10-11 lift could not move it (shared vertices 1 and 2)")
        val (out, lift) = assertNotNull(OsrsDecalLiftTool.liftAll(m))
        assertEquals(1, lift.faces)
        assertEquals(7, out.vertexCount, "vertices 1 and 2 copied for the emblem")
        assertEquals(listOf(0, 0, 0, 0), (0..3).map { out.vertexZ[it] }, "the cloth never moves")
        val emblem = intArrayOf(out.faceA[2], out.faceB[2], out.faceC[2])
        assertTrue(emblem.sumOf { out.vertexZ[it] } / 3.0 >= OsrsDecalLiftTool.CLEARANCE, "the emblem's centre is in front of the cloth")
        assertEquals(listOf(1, 1), emblem.filter { it >= 5 }.map { out.vertexLabel!![it] }, "copies keep the label of the vertex they copy")
        assertNull(OsrsDecalLiftTool.liftAll(out), "a second lift finds nothing")
    }

    @Test
    fun `no imported OSRS item mesh still hides a liftable detail face`() {
        val models = OsrsDecalLiftTool.importedModels()
        assertTrue(models.size >= 1400, "read ${models.size} imported meshes")
        for (cache in OsrsItemImportTool.TARGETS.filter { File(it).isDirectory }) {
            val library = CacheLibrary(cache)
            try {
                val hidden =
                    models.filter { id ->
                        val bytes = library.data(7, id, 0) ?: return@filter false
                        val model = runCatching { Rev667ModelDecoder.decode(bytes) }.getOrNull() ?: return@filter false
                        Rev667ModelEncoder.encode(model).contentEquals(bytes) && OsrsDecalLiftTool.liftAll(model) != null
                    }
                assertTrue(hidden.isEmpty(), "$cache: ${hidden.size} meshes hide detail faces: ${hidden.take(20)}")
            } finally {
                library.close()
            }
        }
    }
}
