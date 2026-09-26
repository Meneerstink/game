package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.trouver.TrouverLockable
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import org.junit.After
import kotlin.test.assertEquals
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [UntradeableDeathProtection] (owner 2026-09-26, OSRS level-20 rule): at or below level 20 untradeables break and stay with
 * the victim (killer gets the repair price), above level 20 unlocked ones are destroyed and locked ones mangled (500,000).
 */
class UntradeableDeathProtectionTests {
    @After
    fun clearRegistry() = TrouverRegistry.clear()

    @Test
    fun `an untradeable item such as Ferocious gloves is handled, a tradeable one is not`() {
        assertFalse(DEFINITIONS.get(ItemDef::class.java, Items.FEROCIOUS_GLOVES).tradeable, "sanity: must actually be untradeable")
        assertTrue(UntradeableDeathProtection.handles(DEFINITIONS, Items.FEROCIOUS_GLOVES))
        assertTrue(DEFINITIONS.get(ItemDef::class.java, Items.ABYSSAL_WHIP).tradeable, "sanity: the whip is tradeable")
        assertFalse(UntradeableDeathProtection.handles(DEFINITIONS, Items.ABYSSAL_WHIP))
    }

    @Test
    fun `loot keys, conversions and emblems keep their own rules`() {
        assertFalse(UntradeableDeathProtection.handles(DEFINITIONS, LootKeys.KEY_IDS.first()), "loot keys must stay always-lost (RCV-012 3b)")
        assertFalse(UntradeableDeathProtection.handles(DEFINITIONS, Items.TOXIC_BLOWPIPE), "the blowpipe keeps its OSRS conversion")
    }

    @Test
    fun `below level 20 an untradeable breaks and the killer gets the repair price`() {
        val fate = UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.FEROCIOUS_GLOVES), deepWilderness = false)
        assertEquals(UntradeableFate.BROKEN, fate.fate)
        assertEquals(RepairPrices.repairPrice(DEFINITIONS, Items.FEROCIOUS_GLOVES), fate.killerCoins)
        assertTrue(fate.killerCoins >= RepairPrices.MIN_REPAIR)
    }

    @Test
    fun `an untradeable without combat use is kept unchanged and pays nothing (OSRS)`() {
        val ammoMould = 4 // untradeable, not equipable (items.yml)
        assertFalse(DEFINITIONS.get(ItemDef::class.java, ammoMould).tradeable)
        for (deep in listOf(false, true)) {
            val fate = UntradeableDeathProtection.fateOf(DEFINITIONS, Item(ammoMould), deep)
            assertEquals(UntradeableFate.UNCHANGED, fate.fate)
            assertEquals(0L, fate.killerCoins)
        }
    }

    @Test
    fun `above level 20 an unlocked untradeable is destroyed for coins`() {
        val fate = UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.AVERNIC_DEFENDER), deepWilderness = true)
        assertEquals(UntradeableFate.DESTROYED, fate.fate)
        assertEquals(600_000L, fate.killerCoins, "Avernic defender: its OSRS repair price")
    }

    @Test
    fun `a locked item breaks below 20 and is mangled above 20 for 500k`() {
        TrouverRegistry.register(TrouverLockable(Items.ANCIENT_SCEPTRE, Items.ANCIENT_SCEPTRE_L, Items.ANCIENT_SCEPTRE_L_BROKEN, Items.ANCIENT_SCEPTRE_L_MANGLED))
        val low = UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.ANCIENT_SCEPTRE_L), deepWilderness = false)
        val deep = UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.ANCIENT_SCEPTRE_L), deepWilderness = true)
        assertEquals(UntradeableFate.BROKEN, low.fate)
        assertEquals(UntradeableFate.MANGLED, deep.fate)
        assertEquals(RepairPrices.MANGLED_REPAIR, deep.killerCoins)
        val mangled = Item(Items.ANCIENT_SCEPTRE_L_MANGLED)
        assertEquals(RepairPrices.MANGLED_REPAIR, UntradeableDeathProtection.repairCost(DEFINITIONS, mangled))
        assertEquals(Items.ANCIENT_SCEPTRE_L, UntradeableDeathProtection.repaired(mangled).id, "the lock survives the repair")
    }

    @Test
    fun `an already damaged item is never paid out twice`() {
        val broken = Item(Items.FEROCIOUS_GLOVES).also { it.attr[ItemAttribute.BROKEN] = UntradeableDeathProtection.STATE_BROKEN }
        val fate = UntradeableDeathProtection.fateOf(DEFINITIONS, broken, deepWilderness = true)
        assertEquals(UntradeableFate.UNCHANGED, fate.fate)
        assertEquals(0L, fate.killerCoins)
        assertEquals(UntradeableFate.UNCHANGED, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.AVERNIC_DEFENDER_BROKEN), false).fate)
        assertFalse(UntradeableDeathProtection.isBroken(UntradeableDeathProtection.repaired(broken)))
    }

    @Test
    fun `rune pouch keeps the empty pouch below 20 and is destroyed above 20 when unlocked`() {
        assertEquals(UntradeableFate.POUCH_EMPTIED, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.RUNE_POUCH), false).fate)
        assertEquals(UntradeableFate.DESTROYED, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.RUNE_POUCH), true).fate)
        TrouverRegistry.register(TrouverLockable(Items.RUNE_POUCH, Items.RUNE_POUCH_L))
        assertEquals(UntradeableFate.POUCH_EMPTIED, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.RUNE_POUCH_L), true).fate, "locked: keep the empty pouch")
        val pouch = gg.rsmod.plugins.content.magic.RunePouch.withContents(Item(Items.RUNE_POUCH_L), listOf(Item(Items.DEATH_RUNE, 100)))
        val loot = UntradeableDeathProtection.killerLoot(listOf(UntradeableOutcome(DeathSlotItem(DeathContainerSource.INVENTORY, 0, pouch), UntradeableFate.POUCH_EMPTIED, 0L)))
        assertEquals(listOf(Items.DEATH_RUNE to 100), loot.map { it.id to it.amount }, "the runes always go to the killer")
    }

    @Test
    fun `repair price is the OSRS amount, else 25 percent of the store price with a 10k floor`() {
        assertEquals(150_000L, RepairPrices.repairPrice(DEFINITIONS, Items.FIRE_CAPE))
        assertEquals(225_000L, RepairPrices.repairPrice(DEFINITIONS, Items.INFERNAL_CAPE))
        val cost = DEFINITIONS.get(ItemDef::class.java, Items.FEROCIOUS_GLOVES).cost.toLong()
        assertEquals(maxOf(10_000L, cost / 4), RepairPrices.repairPrice(DEFINITIONS, Items.FEROCIOUS_GLOVES))
    }
    @Test
    fun `a broken quiver never keeps the ammo that went to the killer`() {
        val victim = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        io.mockk.every { victim.inventory } returns gg.rsmod.game.model.container.ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.INVENTORY_KEY)
        io.mockk.every { victim.equipment } returns gg.rsmod.game.model.container.ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.EQUIPMENT_KEY)
        val quiver =
            Item(Items.DIZANAS_QUIVER).also {
                it.attr[ItemAttribute.ATTACHED_ITEM_ID] = Items.RUNE_ARROW
                it.attr[ItemAttribute.ATTACHED_ITEM_COUNT] = 500
            }
        victim.inventory[0] = quiver
        val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, quiver))
        val resolved = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, null, DeathItemRiskResult(0, emptyList(), lost))
        val (stripped, ammo) = QuiverDeathRules.stripLost(resolved)
        assertEquals(listOf(Items.RUNE_ARROW to 500), ammo.map { it.id to it.amount })
        val (_, outcomes) = UntradeableDeathProtection.splitPvp(DEFINITIONS, stripped, deepWilderness = false)
        assertEquals(UntradeableFate.BROKEN, outcomes.single().fate)
        UntradeableDeathProtection.execute(victim, outcomes)
        val broken = victim.inventory[0]!!
        assertTrue(UntradeableDeathProtection.isBroken(broken))
        assertEquals(null, broken.attr[ItemAttribute.ATTACHED_ITEM_ID], "the ammo went to the killer only")
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
                    line.startsWith("    equip_slot: ") && id >= 0 ->
                        DEFINITIONS.getNullable(ItemDef::class.java, id)?.equipSlot = line.removePrefix("    equip_slot: ").trim().toInt()
                }
            }
        }
    }
}
