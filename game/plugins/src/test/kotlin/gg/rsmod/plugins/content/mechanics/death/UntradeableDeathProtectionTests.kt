package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [UntradeableDeathProtection]: the fallback for untradeable items with no item-specific PvP-death
 * rule elsewhere in this codebase - see its class doc for the sourced general "Items Kept on Death"
 * rule this approximates (kept, rather than dropped fully working to the killer).
 */
class UntradeableDeathProtectionTests {
    @Test
    fun `an untradeable item with no specific rule, such as Ferocious gloves, is protected`() {
        assertFalse(DEFINITIONS.get(ItemDef::class.java, Items.FEROCIOUS_GLOVES).tradeable, "sanity: must actually be untradeable")
        assertTrue(UntradeableDeathProtection.shouldProtect(DEFINITIONS, Items.FEROCIOUS_GLOVES))
    }

    @Test
    fun `a tradeable item is never protected by this fallback`() {
        assertTrue(DEFINITIONS.get(ItemDef::class.java, Items.ABYSSAL_WHIP).tradeable, "sanity: the whip is tradeable")
        assertFalse(UntradeableDeathProtection.shouldProtect(DEFINITIONS, Items.ABYSSAL_WHIP))
    }

    @Test
    fun `an untradeable item already covered by an item-specific rule defers to that rule instead`() {
        assertFalse(DEFINITIONS.get(ItemDef::class.java, Items.AVERNIC_DEFENDER).tradeable, "sanity: must actually be untradeable")
        assertFalse(
            UntradeableDeathProtection.shouldProtect(DEFINITIONS, Items.AVERNIC_DEFENDER),
            "PvpDeathBreakables already owns this item's death rule",
        )
    }

    @Test
    fun `a loot key is never protected by this fallback, even if untradeable`() {
        val key = LootKeys.KEY_IDS.first()
        assertFalse(UntradeableDeathProtection.shouldProtect(DEFINITIONS, key), "loot keys must stay always-lost (RCV-012 3b)")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            assertNotEquals(0, DEFINITIONS.getCount(ItemDef::class.java))
            applyRealTradeableFlags()
        }

        /**
         * [ItemDef.tradeable] is filled at world boot by `ItemMetadataService` from `data/cfg/items.yml`, not by
         * the cache decode, so a bare [DefinitionSet] reports every item as untradeable (same helper as
         * `DuelArenaInterfacesTests`).
         */
        private fun applyRealTradeableFlags() {
            var id = -1
            java.io.File(Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
                when {
                    line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                    line.startsWith("  tradeable: ") && id >= 0 ->
                        DEFINITIONS.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
                }
            }
        }
    }
}
