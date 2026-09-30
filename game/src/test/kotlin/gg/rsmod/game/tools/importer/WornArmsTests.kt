package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-30: the forearms of the player (Eclipse / Masori / Crystal bodies, gloves floating) and the Quest Tutor's body
 * were invisible. Both were a worn outfit missing a part: the lower forearm, which OSRS draws with the hands kit and 667 with
 * the arms kit an OSRS body hides ([WornForearm]), and an npc dressed with only the first wear model of an item whose torso
 * is its second.
 */
class WornArmsTests {
    private fun gameCache() = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())

    @Test
    fun `every imported OSRS item hides the arms kit exactly when OSRS does`() {
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val wanted =
            try {
                val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
                OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).mapNotNull { (upstream, local) ->
                    itemFiles[upstream]?.let { local to OsrsItemImportTool.removesArms(ModernItemDefDecoder.decode(upstream, it)) }
                }.toMap()
            } finally {
                reader.close()
            }
        val lines = File(OsrsArmsKitRepairTool.ITEMS_YML).readLines()
        val (_, stillWrong) = OsrsArmsKitRepairTool.rewrite(lines, wanted)
        assertTrue(stillWrong.isEmpty(), "items.yml remove_arms differs from the OSRS arms-model rule for $stillWrong")
        // OSRS wearPos2 = 6 on the Eclipse moon chestplate and the Crystal body: the 667 arm must not show through them.
        assertEquals(true, wanted[23102])
        assertEquals(true, wanted[22739])
    }

    @Test
    fun `every imported OSRS item that hides the arms draws the forearm as far down as the 667 arm it hides`() {
        val library = gameCache()
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        try {
            val pieces = WornForearm.pieces(library)
            val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
            var checked = 0
            val bare = mutableListOf<String>()
            OsrsFxImportTool.importedItems(File(OsrsItemImportTool.ASSET_MAP)).forEach { (upstream, local) ->
                val def = itemFiles[upstream]?.let { ModernItemDefDecoder.decode(upstream, it) } ?: return@forEach
                if (!OsrsItemImportTool.removesArms(def)) return@forEach
                val ops = OsrsForearmTool.shortOps(CacheItemProbeTool.itemData(library, local)!!)
                pieces.forEach { piece ->
                    val meshes = piece.wornOps.mapNotNull { ops[it] }.map { Rev667ModelDecoder.decode(library.data(7, it, 0)!!, geometryOnly = true) }
                    val end = WornForearm.forearmEnd(meshes)
                    if (end == null || end < piece.kitForearmEnd) bare += "$local ${def.name} ${if (piece.female) "female" else "male"} ends at $end"
                    checked++
                }
            }
            assertTrue(checked >= 68, "only $checked item genders checked")
            assertTrue(bare.isEmpty(), "lower forearm left bare: $bare")
        } finally {
            reader.close()
            library.close()
        }
    }

    @Test
    fun `the forearm pieces are cut from the 667 default arms kits, whose forearm ends at the wrist`() {
        val library = gameCache()
        try {
            val (male, female) = WornForearm.pieces(library)
            assertEquals(151 to -416, male.kitModel to male.kitForearmEnd)
            assertEquals(332 to -480, female.kitModel to female.kitForearmEnd)
            val mesh = Rev667ModelDecoder.decode(male.bytes)
            assertTrue(mesh.vertexLabel!!.all { it in WornForearm.FOREARM_LABELS })
            assertTrue(mesh.faceColour.all { it.toInt() == 4550 }, "the bare forearm is skin (recoloured to the player's skin)")
        } finally {
            library.close()
        }
    }

    @Test
    fun `cut keeps only the faces whose three vertices are forearm vertices`() {
        val kit = ModelData(5, 3, 0)
        kit.version = 15
        kit.vertexLabel = intArrayOf(17, 19, 19, 22, 22)
        (0 until 5).forEach { kit.vertexY[it] = -500 + it * 10 }
        // Face 0 touches the elbow (label 17) and is left out; faces 1 and 2 are all forearm.
        (0 until 3).forEach { f ->
            kit.faceA[f] = f
            kit.faceB[f] = f + 1
            kit.faceC[f] = f + 2
            kit.faceColour[f] = (4550 + f).toShort()
        }
        val piece = WornForearm.cut(kit)
        assertEquals(4, piece.vertexCount)
        assertEquals(listOf(4551, 4552), piece.faceColour.map { it.toInt() })
        assertEquals(listOf(-490, -480, -470, -460), piece.vertexY.toList())
        assertEquals(listOf(0, 1, 2), listOf(piece.faceA[0], piece.faceB[0], piece.faceC[0]))
        assertEquals(13, piece.version)
    }

    @Test
    fun `the forearm goes into the first free wear model only when the item leaves a gap`() {
        val piece = WornForearm.Piece(female = false, kitModel = 151, kitForearmEnd = -416, bytes = ByteArray(0))
        fun sleeve(end: Int) =
            ModelData(1, 0, 0).also {
                it.vertexLabel = intArrayOf(19)
                it.vertexY[0] = end
                it.version = 13
            }
        assertEquals(24, WornForearm.opcodeFor(piece, setOf(23), listOf(sleeve(-483))))
        assertEquals(78, WornForearm.opcodeFor(piece, setOf(23, 24), listOf(sleeve(-483))))
        assertEquals(null, WornForearm.opcodeFor(piece, setOf(23), listOf(sleeve(-403))))
        assertEquals(24, WornForearm.opcodeFor(piece, setOf(23), listOf(ModelData(0, 0, 0))), "no forearm at all")
    }

    @Test
    fun `the repair only touches remove_arms of the named items`() {
        val lines = listOf("- id: 1", "  equipment:", "    remove_arms: true", "- id: 2", "  equipment:", "    remove_arms: true")
        val (out, changed) = OsrsArmsKitRepairTool.rewrite(lines, mapOf(2 to false))
        assertEquals(listOf(2), changed)
        assertEquals(lines.take(5) + "    remove_arms: false", out)
    }

    @Test
    fun `the Quest Tutor wears every wear model of every item it is dressed in`() {
        val library = gameCache()
        try {
            QuestGuideNpcTool.WORN_ITEMS.forEach { item ->
                val models =
                    ItemDefCodec.describeOpcodes(library.data(19, item ushr 8, item and 0xFF)!!)
                        .map { it.split('=') }
                        .filter { it[0].toInt() in setOf(23, 24, 78) }
                        .map { it[1].toInt() }
                assertTrue(models.isNotEmpty(), "item $item has no male wear model")
                models.forEach { assertTrue(it in QuestGuideNpcTool.MODELS, "item $item wear model $it is missing from the Quest Tutor") }
            }
            assertTrue(151 !in QuestGuideNpcTool.MODELS, "the Mystic robe top hides the arms kit (remove_arms), as on a player")
        } finally {
            library.close()
        }
    }
}
