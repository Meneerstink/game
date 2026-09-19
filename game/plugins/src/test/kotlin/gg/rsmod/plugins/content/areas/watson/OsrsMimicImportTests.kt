package gg.rsmod.plugins.content.areas.watson

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.plugins.api.cfg.Npcs
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner answer Q10: the Mimic encounter npcs (OsrsNpcImportTool batch "mimic", tx-20260914-140716) and its sequences / spotanims
 * (extra seqs 8308-8310, OsrsFxImportTool batch "mimic" tx-20260914-140849) read back from the game cache.
 */
class OsrsMimicImportTests {
    private data class Expected(val id: Int, val name: String, val options: List<String?>, val chatheads: Int)

    private val expected =
        listOf(
            Expected(Npcs.THE_MIMIC, "The Mimic", listOf("Challenge", null, null, null, null), 0),
            Expected(Npcs.THE_MIMIC_14402, "The Mimic", listOf(null, "Attack", null, null, null), 0),
            Expected(Npcs.THIRD_AGE_WARRIOR, "Third Age Warrior", listOf(null, "Attack", null, null, null), 0),
            // Owner 2026-09-19 (Deadman guards 100 % OSRS): 14404/14405 are no longer used as guard posts and were restored to
            // their OSRS definitions by re-running this batch (tx-20260919-150528).
            Expected(Npcs.THIRD_AGE_RANGER, "Third Age Ranger", listOf(null, "Attack", null, null, null), 0),
            Expected(Npcs.THIRD_AGE_MAGE, "Third Age Mage", listOf(null, "Attack", null, null, null), 0),
            Expected(Npcs.WATSON, "Watson", listOf("Talk-to", null, null, null, null), 1),
        )

    private val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())

    @AfterTest
    fun close() {
        library.close()
    }

    @Test
    fun `every imported Mimic npc decodes with its OSRS name, options, chatheads and a movement set`() {
        val definitions = DefinitionSet()
        definitions.load(library, NpcDef::class.java)
        assertEquals((14401..14406).toList(), expected.map { it.id })
        expected.forEach { e ->
            val def = definitions.get(NpcDef::class.java, e.id)
            assertEquals(e.name, def.name, "${e.id}")
            assertEquals(e.options, def.options.map { it?.takeIf { o -> o.isNotEmpty() } }, "${e.id} ${e.name} options")
            assertEquals(e.chatheads, def.chatheadModels?.size ?: 0, "${e.id} ${e.name} chatheads")
            assertTrue(def.basId >= 0, "${e.id} ${e.name} has a BAS")
        }
        assertEquals(definitions.get(NpcDef::class.java, Npcs.THE_MIMIC).basId, definitions.get(NpcDef::class.java, Npcs.THE_MIMIC_14402).basId)
    }

    @Test
    fun `the Mimic attack sequences and candy spotanims exist in the game cache`() {
        val definitions = DefinitionSet()
        definitions.load(library, AnimDef::class.java)
        definitions.load(library, SpotAnimDef::class.java)
        listOf(15404, 15405, 15406).forEach { assertNotNull(definitions.getNullable(AnimDef::class.java, it), "seq $it") }
        (3012..3019).forEach { assertNotNull(definitions.getNullable(SpotAnimDef::class.java, it), "spotanim $it") }
    }
}
