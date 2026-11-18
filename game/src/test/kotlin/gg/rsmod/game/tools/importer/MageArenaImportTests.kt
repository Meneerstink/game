package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.test.*

/** Source decoding only. Never opens either target cache for writing or executes an import. */
class MageArenaImportTests {
    @Test fun `deployable bindings refuse incomplete imports and use finalized ids without numeric fallbacks`() {
        val missing = MageArenaBindingsTool.plan(emptyMap(), emptyMap())
        assertTrue(missing.missing.isNotEmpty())
        assertFailsWith<IllegalStateException> { missing.text() }
        val items = missing.missing.filter { it.startsWith("item:") }.associate { "upstream_item:${it.substringAfter(':')}" to (60_000 + it.substringAfter(':').toInt()) }
        val fx = missing.missing.filter { !it.startsWith("item:") && !it.startsWith("variant:") }
            .associateWith { 70_000 + it.substringAfter(':').toInt() }
        assertEquals(MageArenaBindingsTool.CLONES.map { "variant:$it" }.toSet(), MageArenaBindingsTool.plan(items, fx).missing.toSet())
        val clones = MageArenaBindingsTool.CLONES.mapIndexed { index, name -> name to (90_000 + index) }.toMap()
        val plan = MageArenaBindingsTool.plan(items, fx, clones)
        assertTrue(plan.missing.isEmpty())
        assertEquals(missing.missing.size, plan.rows.size)
        assertTrue("item\t34633\t94633" in plan.text())
        assertTrue("npc\t7858\t77858" in plan.text())
        assertTrue("variant\tancient-cape-locked-broken\t90000" in plan.text())
    }
    @Test fun `regular guardians are distinct source actors with their OSRS levels sizes and healer`() {
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { source ->
            val expected = mapOf(7858 to ("Justiciar Zachariah" to 348), 7859 to ("Derwen" to 235), 7860 to ("Porazdir" to 235))
            val roster = OsrsNpcImportTool.BATCHES.getValue("mage-arena")
            expected.forEach { (id, e) ->
                assertTrue(id in roster)
                val def = OsrsNpcProbeTool.decodeOsrs(id, source.file(2, 9, id)!!)
                assertEquals(e.first, def.name)
                assertEquals(e.second, def.combat)
                assertEquals(3, def.size)
                assertTrue(def.ops.any { it == "Attack" })
                assertTrue(def.models.isNotEmpty())
            }
            assertTrue(7514 in roster)
            val healer = OsrsNpcProbeTool.decodeOsrs(7514, source.file(2, 9, 7514)!!)
            assertTrue(OsrsNpcProbeTool.movementSeqs(healer).isEmpty())
            assertTrue(7514 in OsrsNpcImportTool.STATIC_NPCS, "a stationary orb must not acquire humanoid animations")
            assertTrue(roster.none { it in OsrsNpcImportTool.BATCHES.getValue("deadman-breach") })
            val forms = listOf(1605, 1606, 1607, 1608, 1609).map {
                assertTrue(it in roster)
                OsrsNpcProbeTool.decodeOsrs(it, source.file(2, 9, it)!!)
            }
            assertTrue(forms.all { it.name == "Kolodion" && it.models.isNotEmpty() })
            assertTrue(forms[2].models.toSet() != forms[3].models.toSet())
        }
    }

    @Test fun `symbol and remains decode from pinned source and purple cape cannot be faked from that source`() {
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { source ->
            val expected = mapOf(21797 to "Justiciar's hand", 21798 to "Ent's roots", 21799 to "Demon's heart", 21800 to "Enchanted symbol")
            assertEquals(expected.keys, OsrsItemImportTool.BATCHES.getValue("mage-arena").map { it.upstreamId }.toSet())
            expected.forEach { (id, name) ->
                val def = ModernItemDefDecoder.decode(id, source.file(2, 10, id)!!)
                assertEquals(name, def.name)
            }
            assertNull(source.file(2, 10, 34633))
            assertNull(source.file(2, 10, 34641))
        }
    }

    @Test fun `MA2 special graphics and all extra combat sequences exist in pinned source`() {
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { source ->
            OsrsNpcImportTool.EXTRA_SEQS.getValue("mage-arena").forEach { id ->
                val seq = OsrsFxImportTool.decodeOsrsSeq(source.file(2, 12, id)!!)
                assertTrue(seq.frames.isNotEmpty(), "sequence $id needs real frames")
            }
            OsrsFxImportTool.BATCHES.getValue("mage-arena").forEach { id ->
                assertNotNull(source.file(2, 13, id), "spotanim $id")
            }
        }
    }
}
