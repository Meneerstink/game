package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices
import gg.rsmod.plugins.content.skills.summoning.EnchantedHeadgear
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-18: Commune must open the exact OSRS GUI on every Ava's-type device (Zenyte OSRS dialogue text, see
 * [AvasAssembler.Commune]).
 */
class AvasCommuneTests {
    @Test
    fun `every item with a Commune option in the cache is routed`() {
        // Full slayer helmet (charged): no source for its scroll capacity/level (RS Wiki table lacks it) - recorded SOURCE_GAP.
        val headgear = EnchantedHeadgear.allItems + gg.rsmod.plugins.api.cfg.Items.FULL_SLAYER_HELMET_CHARGED
        val offenders =
            DEFINITIONS.getAllKeys(ItemDef::class.java).mapNotNull { id ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, id) ?: return@mapNotNull null
                val commune = (def.inventoryMenu + def.equipmentMenu).any { it?.equals("Commune", ignoreCase = true) == true }
                if (commune && id !in AvasDevices.COMMUNE_DEVICES && id !in headgear) "$id ${def.name}" else null
            }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `commune texts follow the OSRS dialogue and fit the 667 message box`() {
        val stop = AvasAssembler.Commune.forState(gatheringStopped = false)
        val start = AvasAssembler.Commune.forState(gatheringStopped = true)
        assertEquals("Ask it to stop gathering junk?", stop.question)
        assertEquals("Ask it to start gathering junk?", start.question)
        assertEquals(
            "The undead chicken can protect some of your ammunition while you're ranging, and will also gather random metal items for you.",
            stop.intro.joinToString(" "),
        )
        assertEquals(
            "You somehow communicate your message to the undead chicken. Henceforth it will no longer gather up random metal items " +
                "while you've got it equipped.",
            stop.confirm.joinToString(" "),
        )
        assertEquals(
            "The undead chicken understands that you currently don't want it to accumulate random metal items while you've got it equipped.",
            start.intro.joinToString(" "),
        )
        assertEquals(
            "You somehow communicate your message to the undead chicken. Henceforth it will gather up random metal items while you've " +
                "got it equipped.",
            start.confirm.joinToString(" "),
        )
        listOf(stop, start).flatMap { (it.intro + it.confirm).toList() }.forEach { assertTrue(it.length <= 66, it) }
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var STORE: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun load() {
            STORE = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(STORE)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
