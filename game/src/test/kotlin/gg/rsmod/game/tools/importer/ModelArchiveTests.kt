package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N462 (2026-11-06, owner "structurele fix voor de tekorte modelnummers"): model ids from 65536 up live in archive 37 and are
 * addressed by the 78 client's wide (int) model opcodes. These tests run the real CacheTransaction / codecs against throwaway
 * caches shaped like the 667 ones (indexes 0..36, archive 37 absent until the first wide model).
 */
class ModelArchiveTests {
    @get:Rule
    val scratch = TemporaryFolder()

    // The census hands out archive-37 ids upward from 65536 (group 0, 1, 2 ...). displee 6.8 cannot read back a LONE high group of a
    // fresh, almost empty test index (group ~4000 in a 1-group cache reads null; contiguous 0..5000 and 2000 groups on a copy of the
    // real cache all read back - 11-06 23:3x jshell), so the tests use a low group like the real allocation does.
    private val wideId = ModelArchive.EXT_BASE + 7
    private val mesh = "WIDE-MODEL-BYTES".toByteArray()

    @Test
    fun aModelMutationAbove65535AddressesArchive37() {
        val wide = CacheMutation(ModelArchive.MODELS, wideId, 0, mesh, "wide")
        assertEquals(ModelArchive.MODELS_EXT, wide.indexId)
        assertEquals(wideId - ModelArchive.EXT_BASE, wide.groupId)
        val narrow = CacheMutation(ModelArchive.MODELS, 65_535, 0, mesh, "narrow")
        assertEquals(ModelArchive.MODELS, narrow.indexId)
        assertEquals(65_535, narrow.groupId)
        val otherIndex = CacheMutation(19, wideId, 0, mesh, "item index")
        assertEquals(19, otherIndex.indexId)
        assertEquals(wideId, otherIndex.groupId)
    }

    @Test
    fun theFirstWideModelCreatesArchive37AndARerunIsANoOp() {
        val targets = listOf(cache667Shape("game"), cache667Shape("fs"))
        val tx = { CacheTransaction(targets, listOf(CacheMutation(ModelArchive.MODELS, wideId, 0, mesh, "wide")), scratch.newFolder()) }

        val first = tx()
        val plan = first.preflight()
        assertTrue("absent archive 37 = CREATE: ${plan.map { it.outcome }}", plan.all { it.outcome == MutationOutcome.CREATE })
        first.apply(plan)
        assertTrue(first.verify().toString(), first.verify().isEmpty())

        targets.forEach { target ->
            CacheLibrary(target).use { library ->
                assertTrue(library.exists(ModelArchive.MODELS_EXT))
                assertArrayEquals(mesh, ModelArchive.read(library, wideId))
                assertNull("archive 7 must not get the wide model", library.data(ModelArchive.MODELS, wideId - ModelArchive.EXT_BASE, 0))
                assertTrue(wideId in ModelArchive.physicalIds(library))
            }
        }
        val again = tx()
        assertTrue(again.preflight().all { it.outcome == MutationOutcome.NO_OP })
    }

    @Test
    fun theCensusCountsArchive37AndOffersTheRangeAboveIt() {
        val game = cache667Shape("game")
        val fs = cache667Shape("fs")
        listOf(game, fs).forEach { target ->
            CacheLibrary(target).use { library ->
                library.put(ModelArchive.MODELS, 65_535, 0, mesh)
                library.update()
            }
        }
        val before = ModelNamespaceCensusTool.census(game, fs, File(scratch.root, "none.yml"))
        assertEquals(65_536, before.safeFreeRange.first)
        assertEquals(ModelArchive.MAX_MODEL_ID, before.safeFreeRange.last)

        CacheTransaction(listOf(game, fs), listOf(CacheMutation(ModelArchive.MODELS, wideId, 0, mesh, "wide")), scratch.newFolder()).apply()
        val after = ModelNamespaceCensusTool.census(game, fs, File(scratch.root, "none.yml"))
        assertEquals(wideId, after.highestPhysicalId)
        assertEquals(wideId + 1, after.safeFreeRange.first)
    }

    @Test
    fun aLocWithAWideModelUsesOpcode241AndDecodesBack() {
        val src = ModernObjectDef(1).apply {
            name = "Wide"
            models = listOf(1234, wideId)
            modelTypes = listOf(10, 10)
        }
        val bytes = Rev667LocType.encodeFromModern(src, { it }, mutableListOf())
        assertEquals(ModelArchive.LOC_WIDE_MODELS, bytes[0].toInt() and 0xFF)
        val back = Rev667LocType.decode(1, bytes)
        assertEquals(listOf(1234, wideId), back.allModels)
        assertEquals("Wide", back.name)

        val narrow = Rev667LocType.encodeFromModern(ModernObjectDef(2).apply { models = listOf(1234) }, { it }, mutableListOf())
        assertEquals("an id <= 0xFFFF keeps opcode 1", 1, narrow[0].toInt() and 0xFF)
    }

    @Test
    fun npcWideOpcodesAreWalked() {
        val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun i32(v: Int) {
            u8(v ushr 24)
            u8(v ushr 16)
            u8(v ushr 8)
            u8(v)
        }
        u8(ModelArchive.NPC_WIDE_MODELS)
        u8(2)
        i32(500)
        i32(wideId)
        u8(ModelArchive.NPC_WIDE_HEADS)
        u8(1)
        i32(wideId + 1)
        u8(0)
        val seen = mutableListOf<Pair<Int, String>>()
        Rev667ModelReferenceWalkers.walkNpc(1, out.toByteArray()) { id, role -> seen += id to role }
        assertEquals(listOf(500 to "npc_model", wideId to "npc_model", wideId + 1 to "npc_head_model"), seen)
    }

    @Test
    fun anItemModelAbove65535BecomesOpcode240AndCanBeReplacedAgain() {
        // donor: opcode 1 (inventory model 100), opcode 23 (male worn 200), name, terminator
        val donor = byteArrayOf(1, 0, 100, 23, 0, (200).toByte(), 2) + "Donor".toByteArray() + byteArrayOf(0, 0)
        val wide = ItemDefCodec.cloneWithOverrides(donor, emptyMap(), shortOverrides = mapOf(1 to wideId, 23 to 300))
        val described = ItemDefCodec.describeOpcodes(wide)
        assertTrue(described.toString(), "1=$wideId" in described && "23=300" in described)
        assertEquals("the 16-bit opcode 1 is dropped: $described", 1, described.count { it.startsWith("1=") })

        val again = ItemDefCodec.cloneWithOverrides(wide, emptyMap(), shortOverrides = mapOf(1 to wideId + 5))
        val described2 = ItemDefCodec.describeOpcodes(again)
        assertEquals(described2.toString(), listOf("1=${wideId + 5}"), described2.filter { it.startsWith("1=") })

        val narrow = ItemDefCodec.cloneWithOverrides(wide, emptyMap(), shortOverrides = mapOf(1 to 400))
        assertEquals(listOf("1=400"), ItemDefCodec.describeOpcodes(narrow).filter { it.startsWith("1=") })
    }

    /** Indexes 0..36 like the 667 caches (36 = VIDEOS, empty), so displee appends archive 37 next. */
    private fun cache667Shape(name: String): String {
        val dir = scratch.newFolder(name)
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        CacheLibrary.create(dir.absolutePath).use { library ->
            while (!library.exists(ModelArchive.MODELS_EXT - 1)) library.createIndex()
            library.put(ModelArchive.MODELS, 1, 0, "NARROW".toByteArray())
            library.update()
        }
        return dir.absolutePath
    }

    private fun <T> CacheLibrary.use(block: (CacheLibrary) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
