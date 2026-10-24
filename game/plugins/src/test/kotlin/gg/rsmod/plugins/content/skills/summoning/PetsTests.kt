package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.store.StorePets
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 71 OSRS pets (owner 2026-09-30): the generated table against the roster and the cache, the variant cycle, OSRS's reward rules,
 * and the tier -> shop split with the boss-drop pets never for sale.
 */
class PetsTests {
    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()
        val TABLE = File(DIR, "pets.tsv")
        val TIERS = File(DIR, "pet_tiers.tsv")
        val ROSTER = File(DIR, "osrs_pets.tsv")

        @BeforeClass
        @JvmStatic
        fun load() {
            assertTrue(Pets.load(TABLE, TIERS) > 0, "data/cfg/pets/pets.tsv is missing - run OsrsPetImportTool table")
        }
    }

    private fun rosterRows() = ROSTER.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }

    @Test
    fun `every roster variant is in the table, one item and one follower npc each, all distinct`() {
        val roster = rosterRows()
        assertEquals(180, roster.size)
        assertEquals(71, roster.map { it[0] }.toSet().size)
        assertEquals(roster.map { it[0] to it[1] }, Pets.variants.map { it.pet to it.name })
        assertEquals(Pets.variants.size, Pets.variants.map { it.item }.toSet().size, "a pet item id is used twice")
        assertEquals(Pets.variants.size, Pets.variants.map { it.npc }.toSet().size, "a follower npc id is used twice")
    }

    @Test
    fun `every pet item drops and every follower can be picked up, in the production cache`() {
        val defs = SummoningTestCache.definitions
        val wrong = mutableListOf<String>()
        Pets.variants.forEach { v ->
            val item = defs.getNullable(ItemDef::class.java, v.item)
            val npc = defs.getNullable(NpcDef::class.java, v.npc)
            if (item == null) wrong += "${v.name}: item ${v.item} missing"
            if (npc == null) wrong += "${v.name}: npc ${v.npc} missing"
            // An unset op5 is "Drop" in the client (ObjTypeList default, 2011scape-client ObjTypeList.java:123) and takes the drop path in
            // OpHeld5Handler: Lil'viathan and Smol Heredit rely on that default in the OSRS cache too.
            val op5 = item?.inventoryMenu?.get(4)
            if (item != null && op5 != null && !op5.equals("Drop", ignoreCase = true)) wrong += "${v.name}: item ${v.item} op5 is $op5"
            if (npc != null && npc.options.none { it.equals("Pick-up", ignoreCase = true) }) wrong += "${v.name}: npc ${v.npc} has no Pick-up (${npc.options.toList()})"
            if (item != null && !item.name.equals(v.name, ignoreCase = true) && v.pet != "Rocky") wrong += "${v.name}: item ${v.item} is '${item.name}'"
            if (FamiliarCapabilityTable.forNpc(v.npc) != null) wrong += "${v.name}: npc ${v.npc} is a Summoning familiar"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `metamorphosis cycles through every variant of the pet and back`() {
        Pets.variants.groupBy { it.pet }.forEach { (pet, variants) ->
            var item = variants.first().item
            val seen = mutableListOf<Int>()
            repeat(variants.size) {
                seen += item
                item = Pets.nextVariant(item)!!.item
            }
            assertEquals(variants.first().item, item, "$pet does not come back to its first variant")
            assertEquals(variants.map { it.item }, seen, "$pet skips a variant")
        }
    }

    @Test
    fun `a reward follows OSRS - duplicate, follower, backpack, then the bank`() {
        assertEquals(Pets.Award.DUPLICATE, Pets.awardOutcome(alreadyOwned = true, followerOut = false, inventorySpace = true, bankSpace = true))
        assertEquals(Pets.Award.FOLLOWER, Pets.awardOutcome(alreadyOwned = false, followerOut = false, inventorySpace = false, bankSpace = false))
        assertEquals(Pets.Award.BACKPACK, Pets.awardOutcome(alreadyOwned = false, followerOut = true, inventorySpace = true, bankSpace = true))
        assertEquals(Pets.Award.BANK, Pets.awardOutcome(alreadyOwned = false, followerOut = true, inventorySpace = false, bankSpace = true))
        assertEquals(Pets.Award.LOST, Pets.awardOutcome(alreadyOwned = false, followerOut = true, inventorySpace = false, bankSpace = false))
    }

    @Test
    fun `every pet has a tier, and every boss-drop pet names real npcs and an OSRS rate`() {
        val pets = Pets.variants.map { it.pet }.toSet()
        assertEquals(pets, Pets.infos.keys)
        assertEquals(setOf(1, 2, 3), Pets.infos.values.map { it.tier }.toSet())
        val defs = SummoningTestCache.definitions
        val wrong = Pets.infos.values.filter { it.dropOnly }.flatMap { info ->
            info.dropNpcs.filter { defs.getNullable(NpcDef::class.java, it) == null }.map { "${info.pet}: npc $it" } +
                listOfNotNull(if (info.dropRate < 2) "${info.pet}: rate 1/${info.dropRate}" else null)
        }
        assertEquals(emptyList(), wrong)
        // A kill of the boss rolls the base (first) variant.
        Pets.infos.values.filter { it.dropOnly }.forEach { info -> info.dropNpcs.forEach { assertEquals(info.pet, Pets.dropFor(it)?.pet) } }
    }

    @Test
    fun `N101 N316 - the chest has the pets without a boss here, former shop filtering and existing boss drops stay independent`() {
        val stock = StorePets.stock(Pets.variants, Pets.infos.values.toList())
        val dropOnly = Pets.infos.values.filter { it.dropOnly }.map { it.pet }.toSet()
        assertTrue(stock.none { it.first in dropOnly })
        stock.forEach { (pet, shop, item) ->
            assertEquals(StorePets.SHOP_BY_TIER.getValue(Pets.infos.getValue(pet).tier), shop, pet)
            assertEquals(Pets.baseItem(pet), item, pet)
        }
        assertEquals(Pets.infos.size - dropOnly.size, stock.size)
        // Own boss drops stay.
        val expected = Pets.parseTiers(TIERS.readLines())
        val chest = gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot.pets()
        // N316 (owner 2026-10-24): the chest has exactly the pets without a boss here.
        assertEquals(expected.filterNot { it.dropOnly }.map { it.pet }.toSet(), chest.map { it.first }.toSet())
        expected.forEach { info -> info.dropNpcs.forEach { assertEquals(info, Pets.dropFor(it)) } }
        assertEquals(emptyList(), StorePets.entries(TABLE, TIERS))
    }
}
