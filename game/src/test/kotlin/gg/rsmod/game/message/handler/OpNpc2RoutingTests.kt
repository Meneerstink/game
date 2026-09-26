package gg.rsmod.game.message.handler

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.tools.importer.QuestGuideNpcTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-09-26: right-clicking the Quest Tutor and picking "Quest choices" said "You can't attack this npc". The
 * client sends op 2 for whatever an npc's second option is, and the op-2 handler always attacked. Only "Attack" (or no
 * option) may attack; any other second option is an interaction that reaches the plugin bound to option 2.
 */
class OpNpc2RoutingTests {
    @Test
    fun `only an Attack second option attacks`() {
        assertTrue(OpNpc2Handler.routesToAttack("Attack"))
        assertTrue(OpNpc2Handler.routesToAttack("attack"))
        assertTrue(OpNpc2Handler.routesToAttack(null))
        assertTrue(OpNpc2Handler.routesToAttack(""))
        assertFalse(OpNpc2Handler.routesToAttack("Quest choices"))
        assertFalse(OpNpc2Handler.routesToAttack("Trade"))
    }

    @Test
    fun `the Quest Tutor's Quest choices is its second option and is not an attack`() {
        val cache = CacheLibrary(File("../data/cache").path)
        try {
            val definitions = DefinitionSet()
            definitions.load(cache, NpcDef::class.java, emptyList())
            val options = definitions.get(NpcDef::class.java, QuestGuideNpcTool.NPC_ID).options
            assertEquals("Quest choices", options[1])
            assertFalse(OpNpc2Handler.routesToAttack(options[1]))
        } finally {
            cache.close()
        }
    }
}
