package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [OsrsBodyFrame]: an OSRS worn mesh is moved from the 2007 body onto the 667 one (owner 2026-09-29, OSRS gear clipping into
 * walls, floors and ceilings), stored as a version-13 mesh the 667 client does not upscale, and never moved twice.
 */
class OsrsBodyFrameTests {
    private fun mesh(vararg labels: Int): ModelData {
        val model = ModelData(vertexCount = labels.size, faceCount = 1, texSpaceCount = 0)
        labels.forEachIndexed { i, _ ->
            model.vertexX[i] = i
            model.vertexY[i] = 6
            model.vertexZ[i] = -i
        }
        model.vertexLabel = labels.copyOf()
        model.faceA[0] = 0
        model.faceB[0] = 1
        model.faceC[0] = 2
        return model
    }

    @Test
    fun `feet move up onto the 667 floor and the mesh becomes version 13`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(255, 45, 48), slot = 10)
        assertEquals(13, model.version)
        // label 255: (0, -24, 0) on 6 * 4 = 24 -> 0; feet 45 / 48: -26 / -27 from 24.
        assertEquals(listOf(0, -2, -3), model.vertexY.toList())
        assertEquals(listOf(0, 4 - 2, 8 - 2), model.vertexX.toList())
    }

    @Test
    fun `a shield on the OSRS wrist label moves to the 667 shield label`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(28, 28, 161), slot = 5)
        assertEquals(listOf(161, 161, 161), model.vertexLabel!!.toList())
        assertEquals(24 + 38, model.vertexY[0])
    }

    @Test
    fun `a weapon follows the 667 grip and its hands keep the hand shift`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(50, 55, 27), slot = 3)
        assertEquals(listOf(24 + 28, 24 + 28, 24 - 30), model.vertexY.toList())
    }

    @Test
    fun `version 13 survives the 667 codec and a converted mesh is never converted again`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(255, 8, 8), slot = 4)
        val decoded = Rev667ModelDecoder.decode(Rev667ModelEncoder.encode(model))
        assertEquals(13, decoded.version)
        assertEquals(model.vertexY.toList(), decoded.vertexY.toList())
        assertEquals(12, Rev667ModelDecoder.decode(Rev667ModelEncoder.encode(mesh(8, 8, 8))).version)
        val refused = runCatching { OsrsBodyFrame.toRev667Frame(decoded, slot = 4) }
        assertTrue("a version-13 mesh must be refused", refused.isFailure)
    }

    @Test
    fun `the OSRS shin is fitted from the knee to the 667 ankle and from there to the floor`() {
        val male = OsrsBodyFrame.MALE_SHIN
        assertEquals(-300, OsrsBodyFrame.shinY(-300, male))
        assertEquals(-210, OsrsBodyFrame.shinY(-210, male))
        assertEquals(-80, OsrsBodyFrame.shinY(-108, male))
        assertEquals(0, OsrsBodyFrame.shinY(0, male))
        assertEquals(-100, OsrsBodyFrame.shinY(-104, OsrsBodyFrame.FEMALE_SHIN))
        // A legs mesh needs its gender; its shin (label 32: 24 - 36 = -12) is fitted, the thigh (43) only moved.
        assertTrue(runCatching { OsrsBodyFrame.toRev667Frame(mesh(32, 32, 43), slot = 7) }.isFailure)
        val legs = OsrsBodyFrame.toRev667Frame(mesh(32, 32, 43), slot = 7, female = false)
        assertEquals(listOf(-9, -9, 24 - 10), legs.vertexY.toList())
    }

    @Test
    fun `a hands mesh needs its gender and its hand lands on the 667 hand of that gender`() {
        // Owner 2026-10-06 (invisible hands): one row for both genders moved every hand -30; the kits measure male +16, female -50.
        assertTrue(runCatching { OsrsBodyFrame.toRev667Frame(mesh(27, 28, 19), slot = 9) }.isFailure)
        val male = OsrsBodyFrame.toRev667Frame(mesh(27, 28, 19), slot = 9, female = false)
        assertEquals(listOf(24 + 16, 24 + 16, 24 + 14), male.vertexY.toList())
        val female = OsrsBodyFrame.toRev667Frame(mesh(27, 28, 19), slot = 9, female = true)
        assertEquals(listOf(24 - 50, 24 - 50, 24 - 67), female.vertexY.toList())
        val legacy = OsrsBodyFrame.toRev667Frame(mesh(27, 28, 19), slot = 9, legacyHands = true)
        assertEquals(listOf(24 - 30, 24 - 30, 24 - 33), legacy.vertexY.toList())
    }

    /** OSRS identity kit models (opcode 5: count, then 4-byte model ids). */
    private fun osrsKitModels(data: ByteArray): List<Int> {
        val models = mutableListOf<Int>()
        var p = 0
        while (p < data.size) {
            when (val op = data[p++].toInt() and 0xFF) {
                0 -> return models
                1 -> p++
                3 -> {}
                5 -> repeat(data[p++].toInt() and 0xFF) {
                    models += ((data[p].toInt() and 0xFF) shl 24) or ((data[p + 1].toInt() and 0xFF) shl 16) or ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
                    p += 4
                }
                40, 41 -> p += 1 + 4 * (data[p].toInt() and 0xFF)
                else -> error("osrs idk opcode $op")
            }
        }
        return models
    }

    @Test
    fun `the shin heights are those of the default legs kits of both caches`() {
        val gameCache = File("../data/cache")
        assumeTrue(File(OsrsItemImportTool.SOURCE_CACHE).isDirectory && gameCache.isDirectory)
        val library = CacheLibrary(gameCache.path)
        try {
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                val osrsIdk = reader.files(ModernCacheReader.INDEX_CONFIG, 3)
                fun shinOf(meshes: List<ModelData>): Pair<Int, Int> {
                    val knee = meshes.flatMap { m -> (0 until m.vertexCount).filter { m.vertexLabel!![it] == 31 }.map { m.vertexY[it] } }
                    val ankle = meshes.flatMap { m -> (0 until m.vertexCount).filter { m.vertexLabel!![it] in OsrsBodyFrame.SHIN_LABELS }.map { m.vertexY[it] } }
                    return (knee.minOrNull()!! + knee.maxOrNull()!!) / 2 to ankle.maxOrNull()!!
                }
                listOf(false to OsrsBodyFrame.MALE_SHIN, true to OsrsBodyFrame.FEMALE_SHIN).forEach { (female, shin) ->
                    val kit = (if (female) Appearance.DEFAULT_FEMALE else Appearance.DEFAULT).looks[5]
                    val osrs =
                        osrsKitModels(osrsIdk.getValue(kit)).map {
                            OsrsBodyFrame.shiftToRev667(Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, it, mutableListOf())), OsrsBodyFrame.LEGS_SLOT)
                        }
                    val models = mutableListOf<Int>()
                    Rev667ModelReferenceWalkers.walkIdk(kit, library.data(2, 3, kit)!!) { id, role -> if (role == "idk_model") models += id }
                    val rev667 = models.map { Rev667ModelDecoder.decode(library.data(7, it, 0)!!, geometryOnly = true) }
                    val (osrsKnee, osrsAnkle) = shinOf(osrs)
                    val (knee, ankle) = shinOf(rev667)
                    assertEquals("OSRS knee of legs kit $kit", shin.knee, osrsKnee)
                    assertEquals("OSRS ankle of legs kit $kit", shin.osrsAnkle, osrsAnkle)
                    assertEquals("667 ankle of legs kit $kit", shin.ankle, ankle)
                    assertTrue("667 knee $knee of legs kit $kit", Math.abs(knee - shin.knee) <= 2)
                }
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `every imported OSRS legs mesh in the cache is the shin-fitted conversion`() {
        val gameCache = File("../data/cache")
        assumeTrue(File(OsrsItemImportTool.SOURCE_CACHE).isDirectory && gameCache.isDirectory)
        val library = CacheLibrary(gameCache.path)
        try {
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                val targets = OsrsShinTool.targets(reader, ImportBatchOrchestrator.readExistingMapping(File(OsrsItemImportTool.ASSET_MAP)))
                assertTrue("only ${targets.size} legs meshes", targets.size >= 62)
                // T5: a legs mesh whose emblem/detail faces lay behind the cloth also got the owner's A5 decal lift on 2026-10-11
                // (OsrsDecalLiftTool, tx-20261011-073852: Black d'hide chaps (g), Rangers' tights) - shin fit first, lift on top.
                fun lifted(fitted: ByteArray): ByteArray? {
                    val model = Rev667ModelDecoder.decode(fitted)
                    return OsrsDecalLiftTool.lift(model)?.let { Rev667ModelEncoder.encode(model) }
                }
                val stale =
                    targets.filterNot { t ->
                        val current = library.data(7, t.local, 0)!!
                        val fitted = OsrsModelConversion.convert(reader, t.upstream, mutableListOf(), OsrsBodyFrame.LEGS_SLOT, t.female)
                        current.contentEquals(fitted) || lifted(fitted)?.contentEquals(current) == true
                    }
                assertTrue("legs meshes without the shin fit: ${stale.map { "${it.local} ${it.item}" }}", stale.isEmpty())
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `the real OSRS Pegasian boots no longer reach through the floor`() {
        assumeTrue(File(OsrsItemImportTool.SOURCE_CACHE).isDirectory)
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val before = Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, 29252, mutableListOf()))
            val after = Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, 29252, mutableListOf(), wornSlot = 10))
            assertEquals(24, before.vertexY.maxOrNull()!! * 4)
            assertTrue("soles at ${after.vertexY.maxOrNull()}", after.vertexY.maxOrNull()!! <= 0)
            assertEquals(13, after.version)
        }
    }
}
