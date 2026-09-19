package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Kind
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Shop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreCatalogueTests {
    @Test
    fun `the catalogue satisfies every owner rule`() {
        assertEquals(emptyList<String>(), StoreCatalogue.violations())
    }

    @Test
    fun `Elder chaos, Dagon'hai and Heavy ballista kits are Deadman-only`() {
        listOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, Items.DAGONHAI_ROBES_ORNAMENT_KIT, Items.HEAVY_BALLISTA_ORNAMENT_KIT).forEach { kit ->
            assertEquals(setOf(Shop.DEADMAN), StoreCatalogue.ENTRIES.filter { it.purchaseItem == kit }.map { it.shop }.toSet(), "kit $kit")
        }
    }

    @Test
    fun `the Donator shop sells kits only, never a complete ornamented item`() {
        val donator = StoreCatalogue.entries(Shop.DONATOR)
        assertTrue(donator.isNotEmpty())
        assertEquals(emptyList(), donator.filter { it.kind != Kind.KIT }.map { it.purchaseItem })
        val ornamented = OsrsOrnamentKits.ORNAMENTED_RESULTS
        assertEquals(emptyList(), donator.filter { it.purchaseItem in ornamented }.map { it.purchaseItem })
    }

    @Test
    fun `every kit entry previews exactly its attach routes from the shared ornament table`() {
        val offenders = mutableListOf<String>()
        StoreCatalogue.ENTRIES.filter { it.kind == Kind.KIT }.forEach { entry ->
            val expected =
                OsrsOrnamentKits.ALL.filter { it.kit == entry.purchaseItem }.map { it.ornamented to it.base } +
                    OsrsOrnamentKits.CONSUMED.filter { it.kit == entry.purchaseItem }.map { it.ornamented to it.base }
            val actual = entry.previewItems.zip(entry.requiredBaseItems)
            // Masori crafting kit and Ward upgrade kit have their own routes (osrs_max_capes / osrs_magegear), listed in StoreCatalogue.
            if (entry.purchaseItem !in setOf(Items.MASORI_CRAFTING_KIT, Items.WARD_UPGRADE_KIT) && actual != expected) {
                offenders += "${entry.purchaseItem}: $actual != $expected"
            }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `the multi-result kits the owner named get a carousel`() {
        fun previews(kit: Int) = StoreCatalogue.ENTRIES.single { it.purchaseItem == kit }.previewItems.size
        assertEquals(3, previews(Items.LIGHT_INFINITY_COLOUR_KIT))
        assertEquals(3, previews(Items.DARK_INFINITY_COLOUR_KIT))
        assertEquals(3, previews(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT))
        assertEquals(3, previews(Items.DAGONHAI_ROBES_ORNAMENT_KIT))
        assertEquals(3, previews(Items.TWISTED_ANCESTRAL_COLOUR_KIT))
        assertEquals(2, previews(Items.WARD_UPGRADE_KIT))
    }

    @Test
    fun `the 667 fury and dragon or and sp kits have attach and Split routes`() {
        listOf(
            Items.FURY_ORNAMENT_KIT, Items.DRAGON_FULL_HELM_ORNAMENT_KIT_OR, Items.DRAGON_PLATEBODY_ORNAMENT_KIT_OR,
            Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_OR, Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT_OR, Items.DRAGON_FULL_HELM_ORNAMENT_KIT_SP,
            Items.DRAGON_PLATEBODY_ORNAMENT_KIT_SP, Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_SP, Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT_SP,
        ).forEach { kit ->
            val routes = OsrsOrnamentKits.ALL.filter { it.kit == kit }
            assertTrue(routes.isNotEmpty(), "kit $kit has no route")
            assertTrue(routes.all { it.detachOption == "Split" }, "kit $kit must detach with the 667 Split option")
            routes.forEach { route ->
                assertTrue(
                    gg.rsmod.plugins.content.items.combine.CombinationData.values().any {
                        it.resultItem == route.ornamented && it.items.toSet() == setOf(route.kit, route.base)
                    },
                    "no CombinationData entry for ${route.ornamented}",
                )
            }
        }
    }
}
