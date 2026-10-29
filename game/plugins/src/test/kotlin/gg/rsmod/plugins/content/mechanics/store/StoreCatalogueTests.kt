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
    fun `the Donator shop sells kits, gear and looks, never a complete ornamented item`() {
        val donator = StoreCatalogue.entries(Shop.DONATOR)
        assertTrue(donator.isNotEmpty())
        // Owner 2026-09-29 (layout page): kits, gear, 3rd Age and look unlocks; the kit stays the product, never its ornamented result.
        assertTrue(donator.any { it.kind == Kind.KIT })
        assertTrue(donator.any { it.kind == Kind.ITEM && it.purchaseItem == Items.PRIMORDIAL_BOOTS })
        val ornamented = OsrsOrnamentKits.ORNAMENTED_RESULTS
        assertEquals(emptyList(), donator.filter { it.purchaseItem in ornamented }.map { it.purchaseItem })
        // Charged weapons are sold empty/uncharged only (no free charges); the loaded forms are never listed.
        assertEquals(emptyList(), donator.filter { it.purchaseItem == Items.TOXIC_BLOWPIPE || it.purchaseItem == Items.TRIDENT_OF_THE_SWAMP }.map { it.purchaseItem })
        assertTrue(donator.any { it.purchaseItem == Items.TOXIC_BLOWPIPE_EMPTY })
    }

    @Test
    fun `every shop puts the highest price first and the cheapest last`() {
        Shop.values().forEach { shop ->
            val ordered = StoreCatalogue.entries(shop)
            assertEquals(StoreCatalogue.ENTRIES.filter { it.shop == shop }.toSet(), ordered.toSet(), "$shop lists every entry once")
            ordered.zipWithNext().forEach { (a, b) ->
                assertTrue(a.price >= b.price, "$shop: ${a.purchaseItem} (${a.price}) must be before ${b.purchaseItem} (${b.price})")
            }
        }
        StoreCatalogue.Shop.values().forEach { shop ->
            val all = StoreCatalogue.entries(shop, StoreCatalogue.Section.ALL)
            assertEquals(
                all.map { StoreCatalogue.section(it).ordinal }.sorted(),
                all.map { StoreCatalogue.section(it).ordinal },
                "$shop All tab category order",
            )
            StoreCatalogue.Section.values().filter { it != StoreCatalogue.Section.ALL }.forEach { section ->
                StoreCatalogue.entries(shop, section).zipWithNext().forEach { (a, b) ->
                    assertTrue(a.price >= b.price, "$shop $section: ${a.purchaseItem} must precede ${b.purchaseItem}")
                }
            }
        }
        assertEquals(StoreCatalogue.Section.GEAR, StoreCatalogue.Section.values()[1])
        assertEquals(500, StoreCatalogue.entries(Shop.LOYALTY).last().price)
    }

    @Test
    fun `imbued rings are upgrades that need the base ring, never sold outright`() {
        val imbues = StoreCatalogue.ENTRIES.filter { it.category == "Imbue services" }
        assertEquals(9, imbues.size)
        imbues.forEach { assertEquals(Kind.UPGRADE, it.kind, "entry ${it.purchaseItem}") }
        assertEquals(listOf(Items.RING_OF_SUFFERING_R), imbues.single { it.purchaseItem == Items.RING_OF_SUFFERING_RI }.requiredBaseItems)
        assertEquals(emptyList(), StoreCatalogue.ENTRIES.filter { it.purchaseItem == Items.DIVINE_RUNE_POUCH }.map { it.shop })
    }

    @Test
    fun `every max cape variant look is unlockable for Loyalty and Donator points and requires its real component`() {
        gg.rsmod.plugins.content.items.osrs.MaxCapes.VARIANTS.forEach { variant ->
            val unlocks = StoreCatalogue.ENTRIES.filter { it.kind == Kind.UNLOCK && it.purchaseItem == variant.cape }
            assertEquals(setOf(Shop.LOYALTY, Shop.DONATOR), unlocks.map { it.shop }.toSet(), "look ${variant.cape}")
            unlocks.forEach { assertEquals(listOf(variant.component), it.requiredBaseItems) }
        }
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

    /** Equipment slot per item id from data/cfg/items.yml (what ItemMetadataService puts on ItemDef.equipSlot at boot). */
    private val slots: Map<Int, Int> by lazy {
        val file = listOf(java.io.File("../../data/cfg/items.yml"), java.io.File("../data/cfg/items.yml")).first { it.exists() }
        val map = HashMap<Int, Int>()
        var id = -1
        file.forEachLine { line ->
            if (line.startsWith("- id: ")) id = line.substring(6).trim().toInt()
            else if (line.trimStart().startsWith("equip_slot: ") && id >= 0) map[id] = line.substringAfter("equip_slot: ").trim().toInt()
        }
        map
    }

    private fun kits(entry: StoreCatalogue.Entry) = entry.kitsPerPurchase { slots[it] ?: -1 }

    @Test
    fun `a set kit purchase grants one kit for every piece of the set`() {
        // N323: hat + top + bottoms (3 slots) -> 3 kits for the price of one.
        listOf(Items.LIGHT_INFINITY_COLOUR_KIT, Items.DARK_INFINITY_COLOUR_KIT, Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT,
            Items.DAGONHAI_ROBES_ORNAMENT_KIT, Items.TWISTED_ANCESTRAL_COLOUR_KIT).forEach { kit ->
            val entry = StoreCatalogue.ENTRIES.first { it.purchaseItem == kit }
            assertEquals(3, kits(entry), "kit $kit")
            assertEquals("3 kits - full set", StoreUi.kitSetDescription(kits(entry)), "visible purchase description for $kit")
        }
        // A kit that fits one of several items worn in the same slot is one kit: platelegs OR plateskirt, a steam staff OR the mystic one.
        StoreCatalogue.ENTRIES.filter { it.purchaseItem == Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_SP || it.purchaseItem == Items.STEAM_STAFF_UPGRADE_KIT }
            .forEach { assertEquals(1, kits(it), "kit ${it.purchaseItem}") }
        // Every kit sold: never more kits than routes, never a set count for routes that share one slot.
        StoreCatalogue.ENTRIES.filter { it.kind == Kind.KIT && it.requiredBaseItems.size == 1 }.forEach { assertEquals(1, kits(it), "kit ${it.purchaseItem}") }
        StoreCatalogue.ENTRIES.filter { it.kind == Kind.KIT && it.requiredBaseItems.size > 1 }.forEach { entry ->
            assertTrue(entry.requiredBaseItems.all { slots.containsKey(it) }, "kit ${entry.purchaseItem}: every base item has an equipment slot")
            assertEquals(entry.requiredBaseItems.map { slots.getValue(it) }.distinct().size, kits(entry), "kit ${entry.purchaseItem}")
        }
    }

    @Test
    fun `set kit purchases fit only whole sets in the remaining inventory slots`() {
        assertEquals(0, StoreUi.fittingPurchases(kits = 3, wanted = 1, freeSlots = 2, stackable = false), "two slots cannot fit a set of three")
        assertEquals(1, StoreUi.fittingPurchases(kits = 3, wanted = 1, freeSlots = 3, stackable = false))
        assertEquals(2, StoreUi.fittingPurchases(kits = 3, wanted = 2, freeSlots = 7, stackable = false))
        assertEquals(6, StoreUi.itemCountForPurchases(kits = 3, purchases = 2))
        assertEquals(3, StoreUi.fittingPurchases(kits = 3, wanted = 3, freeSlots = 0, stackable = true), "stackable kits need no additional slot")
    }

    @Test
    fun `a kit's preview shows the kit itself first, then the kit on each item`() {
        StoreCatalogue.ENTRIES.filter { it.kind == Kind.KIT }.forEach { entry ->
            assertEquals(listOf(entry.purchaseItem) + entry.previewItems, StoreUi.showcase(entry), "kit ${entry.purchaseItem}")
            assertEquals(0, StoreUi.resultIndex(entry, 0))
            assertEquals(entry.previewItems.size - 1, StoreUi.resultIndex(entry, entry.previewItems.size))
        }
        StoreCatalogue.ENTRIES.filter { it.kind != Kind.KIT }.forEach { assertEquals(it.previewItems, StoreUi.showcase(it)) }
    }

    @Test
    fun `kit list icons show the kit being bought and other entries show their result`() {
        StoreCatalogue.ENTRIES.forEach { entry ->
            val expected = if (entry.kind == Kind.KIT) entry.purchaseItem else entry.previewItems.first()
            assertEquals(expected, StoreUi.slotIcon(entry), "${entry.shop} item ${entry.purchaseItem}")
        }
    }

    @Test
    fun `only kits returned on dismantle show the permanent kit promise`() {
        val returnable = StoreCatalogue.ENTRIES.filter { it.kind == Kind.KIT && it.dismantlePolicy == StoreCatalogue.DismantlePolicy.RETURNS_KIT }
        val consumed = StoreCatalogue.ENTRIES.filter { it.kind == Kind.KIT && it.dismantlePolicy == StoreCatalogue.DismantlePolicy.KIT_CONSUMED }
        assertTrue(returnable.isNotEmpty())
        assertTrue(consumed.isNotEmpty())
        returnable.forEach {
            val lines = StoreUi.kitPermanenceLines(it)
            assertEquals("You never lose the kit - it's permanent.", lines?.toList()?.joinToString(" "), "${it.purchaseItem}")
        }
        consumed.forEach { assertEquals(null, StoreUi.kitPermanenceLines(it), "${it.purchaseItem} consumes its kit") }
    }

    @Test
    fun `Snowman top hat is not sold in the Loyalty shop`() {
        assertTrue(StoreCatalogue.ENTRIES.none { it.shop == Shop.LOYALTY && it.purchaseItem == Items.SNOWMAN_TOP_HAT })
    }

    /**
     * Owner 2026-09-20: "also remove the crown of helious out of the shop". The Crown of Helios is the staff-only
     * "yellow partyhat" (`CrownOfHelios`), so it must not be purchasable from any of the three shops.
     */
    @Test
    fun `the Crown of Helios is not sold in any shop`() {
        assertEquals(
            emptyList(),
            StoreCatalogue.ENTRIES.filter { Items.CROWN_OF_HELIOS in it.previewItems || it.purchaseItem == Items.CROWN_OF_HELIOS }
                .map { "${it.shop} ${it.purchaseItem}" },
        )
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

    /** Owner 2026-10-10 "per categorie": every entry sits in exactly one category button, and All shows each entry once. */
    @Test
    fun `every shop splits into its category buttons without losing or doubling an entry`() {
        StoreCatalogue.Shop.values().forEach { shop ->
            val all = StoreCatalogue.entries(shop)
            val sections = StoreCatalogue.sections(shop)
            assertEquals(StoreCatalogue.Section.ALL, sections.first())
            assertTrue(sections.size <= gg.rsmod.game.tools.importer.StoreInterfaceImportTool.SECTION_COUNT, "$shop: ${sections.size} buttons")
            val split = sections.drop(1).flatMap { StoreCatalogue.entries(shop, it) }
            assertEquals(all.size, split.size, "$shop")
            assertEquals(all.toSet(), split.toSet(), "$shop")
            assertEquals(all.toSet(), StoreCatalogue.entries(shop, StoreCatalogue.Section.ALL).toSet())
            assertEquals(all.size, StoreCatalogue.entries(shop, StoreCatalogue.Section.ALL).size)
            sections.drop(1).forEach { assertTrue(StoreCatalogue.entries(shop, it).isNotEmpty(), "$shop shows an empty $it button") }
        }
    }

    @Test
    fun `kits, pets, capes and cosmetics land in their own category`() {
        val section = { item: Int -> StoreCatalogue.section(StoreCatalogue.ENTRIES.first { it.purchaseItem == item && it.kind != StoreCatalogue.Kind.UNLOCK }) }
        assertEquals(StoreCatalogue.Section.ORNAMENTS, section(Items.ARMADYL_GODSWORD_ORNAMENT_KIT))
        assertEquals(StoreCatalogue.Section.ORNAMENTS, section(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT))
        // N326: the Loyalty upgrade kits (orbs, Ward upgrade kit, Masori crafting kit) are gear, not a look.
        listOf(Items.WARD_UPGRADE_KIT, Items.MASORI_CRAFTING_KIT, Items.HARMONISED_ORB).forEach { assertEquals(StoreCatalogue.Section.GEAR, section(it), "$it") }
        assertEquals(StoreCatalogue.Section.GEAR, section(Items.ANCESTRAL_HAT))
        assertEquals(StoreCatalogue.Section.GEAR, section(Items.RING_OF_THE_GODS_I))
        assertEquals(StoreCatalogue.Section.PETS, section(Items.HELLKITTEN))
        assertEquals(StoreCatalogue.Section.CAPES, section(Items.IMBUED_ZAMORAK_CAPE))
        assertEquals(StoreCatalogue.Section.COSMETICS, section(Items.SANTA_HAT))
        StoreCatalogue.ENTRIES.filter { it.kind == StoreCatalogue.Kind.UNLOCK }.forEach { assertEquals(StoreCatalogue.Section.CAPES, StoreCatalogue.section(it)) }
    }
}
