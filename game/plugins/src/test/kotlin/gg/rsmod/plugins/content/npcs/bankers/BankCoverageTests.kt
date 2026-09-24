package gg.rsmod.plugins.content.npcs.bankers

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-24: "controleer alle bankers op dialog alle banks in onze rsps! pest control bank, burg de roth bank volledig werkend
 * maken, nardah bankers geen dialog". Whole-class guard over the cache: every bank object is caught by the bank fallback, and every
 * bank npc reaches the full banker conversation.
 */
class BankCoverageTests {
    private val bankNames = setOf("bank booth", "bank chest", "bank counter")

    @Test
    fun `every object that offers Use-quickly is a bank the fallback opens`() {
        val missed =
            DEFINITIONS.getAllKeys(ObjectDef::class.java).mapNotNull { id -> DEFINITIONS.get(ObjectDef::class.java, id) }
                .filter { def -> def.options.any { it?.lowercase() == "use-quickly" } }
                .filter { def -> def.name.lowercase() !in bankNames && def.name.lowercase() != "counter" }
                // A gate with a quick-pass option is not a bank.
                .filter { def -> def.name.lowercase() != "gate" }
                .map { "${it.id} ${it.name}" }
        assertEquals(emptyList(), missed)
        val booths = File("src/main/kotlin/gg/rsmod/plugins/content/objs/bank_locs/bank_booths.plugin.kts").readText()
        assertTrue("world.plugins.bindObjectFallback" in booths && "setOf(\"bank booth\", \"bank chest\", \"bank counter\")" in booths)
    }

    @Test
    fun `every bank npc gets the banker conversation`() {
        val fallback = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/talk_to_fallback.plugin.kts").readText()
        assertTrue("def.name.lowercase().contains(\"banker\") || def.options.any { it?.lowercase() == \"bank\" }" in fallback)
        assertTrue("BankerDialogue.chat(this)" in fallback)
        val bankers = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/bankers/bankers.plugin.kts").readText()
        assertTrue("BankerDialogue.chat(this)" in bankers && "Npcs.CORNELIUS_3569" in bankers)
        val nardah = listOf(3046, 5258, 5260).map { DEFINITIONS.get(NpcDef::class.java, it) }
        assertTrue(nardah.all { def -> def.options.any { it?.lowercase() == "bank" } }, "the Nardah bankers carry Bank, so the fallback treats them as bankers")
    }

    @Test
    fun `banker dialogue opens the real PIN settings and collection box`() {
        val text = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/bankers/BankerDialogue.kt").readText()
        assertTrue("BankPin.manage(it.player)" in text && "GrandExchangeInterface.openCollectionBox(it.player)" in text)
        assertTrue("chatNpc(\"Sorry, it is not implemented yet.\"" !in text)
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
