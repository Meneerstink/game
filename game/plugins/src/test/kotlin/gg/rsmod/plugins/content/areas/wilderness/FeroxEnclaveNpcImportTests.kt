package gg.rsmod.plugins.content.areas.wilderness

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.cfg.Npcs
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-012 decision 3: the 24 OSRS Ferox Enclave npcs imported by `OsrsNpcImportTool` batch "ferox" (tx-20260914-103151). Every npc is
 * read back from the game cache and checked against the OSRS definition probed from the pinned source cache (name, options, chathead
 * count) plus a movement BAS.
 */
class FeroxEnclaveNpcImportTests {
    private data class Expected(val id: Int, val name: String, val options: List<String?>, val chatheads: Int)

    private val expected =
        listOf(
            Expected(Npcs.FEROX, "Ferox", listOf("Talk-to", null, null, null, null), 3),
            Expected(Npcs.SIGISMUND, "Sigismund", listOf("Talk-to", null, null, null, null), 2),
            Expected(Npcs.ZAMORAKIAN_ACOLYTE, "Zamorakian Acolyte", listOf("Talk-to", null, null, null, null), 1),
            Expected(Npcs.ZAMORAKIAN_ACOLYTE_14380, "Zamorakian Acolyte", listOf("Talk-to", null, null, null, null), 2),
            Expected(Npcs.ZAMORAKIAN_ACOLYTE_14381, "Zamorakian Acolyte", listOf("Talk-to", null, null, null, null), 2),
            Expected(Npcs.SKULLY, "Skully", listOf("Talk-to", null, "Value", "Settings", null), 3),
            Expected(Npcs.REFUGEE_14383, "Refugee", listOf(null, null, null, null, null), 2),
            Expected(Npcs.REFUGEE_14384, "Refugee", listOf(null, null, null, null, null), 1),
            Expected(Npcs.REFUGEE_14385, "Refugee", listOf(null, null, null, null, null), 1),
            Expected(Npcs.PHABELLE_BILE, "Phabelle Bile", listOf("Talk-to", null, null, null, null), 3),
            Expected(Npcs.DERSE_VENATOR, "Derse Venator", listOf("Talk-to", null, null, null, null), 2),
            Expected(Npcs.ANDROS_MAI, "Andros Mai", listOf("Talk-to", null, null, null, null), 1),
            Expected(Npcs.BANKER_FEROX_ENCLAVE, "Banker", listOf("Talk-to", null, "Bank", "Collect", null), 3),
            Expected(Npcs.MERCENARY_FEROX_ENCLAVE, "Mercenary", listOf(null, null, null, null, null), 2),
            Expected(Npcs.CAMARST, "Camarst", listOf("Talk-to", null, null, null, null), 3),
            Expected(Npcs.MARTEN, "Marten", listOf("Talk-to", null, "Store-axe", null, null), 2),
            Expected(Npcs.SISTER_SCAROPHIA, "Sister Scarophia", listOf("Talk-to", null, null, null, null), 1),
            Expected(Npcs.PERDU, "Perdu", listOf("Talk-to", null, "Trade", null, null), 2),
            Expected(Npcs.JUSTINE, "Justine", listOf("Talk-to", null, "Trade", null, null), 2),
            Expected(Npcs.LISA, "Lisa", listOf("Talk-to", null, null, null, null), 1),
            Expected(Npcs.LISA_14397, "Lisa", listOf("Talk-to", null, "Join", null, null), 1),
            Expected(Npcs.WIZARD_LMS_14398, "Wizard", listOf(null, null, null, null, null), 0),
            Expected(Npcs.WIZARD_LMS_14399, "Wizard", listOf(null, null, null, null, null), 0),
            Expected(Npcs.WIZARD_LMS_14400, "Wizard", listOf(null, null, null, null, null), 0),
        )

    private val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())

    @AfterTest
    fun close() {
        library.close()
    }

    @Test
    fun `every imported Ferox npc decodes from the game cache with its OSRS name, options, chatheads and a movement set`() {
        val definitions = DefinitionSet()
        definitions.load(library, NpcDef::class.java)
        assertEquals((14377..14400).toList(), expected.map { it.id })
        expected.forEach { e ->
            val def = definitions.get(NpcDef::class.java, e.id)
            assertEquals(e.name, def.name, "${e.id}")
            assertEquals(e.options, def.options.map { it?.takeIf { o -> o.isNotEmpty() } }, "${e.id} ${e.name} options")
            assertEquals(e.chatheads, def.chatheadModels?.size ?: 0, "${e.id} ${e.name} chatheads")
            assertTrue(def.basId >= 0, "${e.id} ${e.name} has a BAS")
        }
    }
}
