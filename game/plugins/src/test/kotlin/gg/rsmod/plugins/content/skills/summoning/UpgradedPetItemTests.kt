package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N436 (owner 2026-10-30: an upgraded pet "moet dat ook zo laten zien in de inventory"): every pet item has an "Upgraded" copy
 * (data/cfg/pets/pet_item_upgrades.tsv, cache items 24237-24616); a pet item in the backpack or bank is the copy while a burden
 * trinket is bound to its pet and the plain item otherwise, and the copy is the same pet everywhere (ownership, variant, death).
 */
class UpgradedPetItemTests {
    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()

        @BeforeClass
        @JvmStatic
        fun load() {
            Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv"), File(DIR, "pets_667.tsv"))
        }

        private const val PET_ZILYANA = 24006
    }

    @Test
    fun `every pet item has one Upgraded copy in the cache, named after it`() {
        val rows = File(DIR, "pet_item_upgrades.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        assertEquals(380, rows.size)
        assertEquals(380, Pets.upgradedItems.size)
        val defs = SummoningTestCache.definitions
        rows.forEach { (base, up, name) ->
            val copy = defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, up.toInt())
            val plain = defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, base.toInt())
            assertEquals(name, copy.name)
            assertEquals("Upgraded ${plain.name}", copy.name)
            assertEquals(plain.inventoryMenu.toList(), copy.inventoryMenu.toList(), "$name keeps the pet's options")
            assertEquals(Pets.variantOfItem(base.toInt()), Pets.variantOfItem(up.toInt()), "$name is the same pet")
            assertTrue(Pets.isPetItem(up.toInt()))
        }
    }

    @Test
    fun `a bound trinket turns the pet item into its copy and unbinding turns it back, in the backpack and the bank`() {
        val player = newPlayer()
        val variant = Pets.variantOfItem(PET_ZILYANA)!!
        val copy = Pets.upgradedItems.first { Pets.baseItemOf(it) == PET_ZILYANA }
        player.inventory.add(PET_ZILYANA, 1)
        player.bank.add(variant.item, 1)
        player.inventory.add(BurdenTrinkets.Tier.PACK_YAK.item, 1)
        assertEquals(PET_ZILYANA, Pets.shownItem(player, variant))

        player.attr[BurdenTrinkets.BOUND_ATTR] = BurdenTrinkets.encode(mapOf(variant.pet to BurdenTrinkets.Tier.PACK_YAK.item))
        Pets.reconcileItems(player)
        assertEquals(copy, Pets.shownItem(player, variant))
        assertEquals(1, player.inventory.getItemCount(copy))
        assertEquals(1, player.bank.getItemCount(copy))
        assertEquals(0, player.inventory.getItemCount(PET_ZILYANA))
        assertTrue(Pets.owns(player, variant.pet), "the copy is the pet for the one-of-each rule")

        player.attr.remove(BurdenTrinkets.BOUND_ATTR)
        Pets.reconcileItems(player)
        assertEquals(1, player.inventory.getItemCount(PET_ZILYANA))
        assertEquals(1, player.bank.getItemCount(PET_ZILYANA))
        assertEquals(0, player.inventory.getItemCount(copy))
    }

    private fun newPlayer(): Player {
        val defs = SummoningTestCache.definitions
        val player = mockk<Player>(relaxed = true)
        val inventory = ItemContainer(defs, INVENTORY_KEY)
        val equipment = ItemContainer(defs, EQUIPMENT_KEY)
        val bank = ItemContainer(defs, BANK_KEY)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.equipment } returns equipment
        every { player.bank } returns bank
        every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, EQUIPMENT_KEY to equipment, BANK_KEY to bank)
        every { player.world.definitions } returns defs
        return player
    }
}
