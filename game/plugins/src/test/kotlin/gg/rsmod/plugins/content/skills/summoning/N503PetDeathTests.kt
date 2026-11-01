package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskCalculator
import gg.rsmod.plugins.content.mechanics.death.DeathRules
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider
import gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
import gg.rsmod.plugins.api.ext.addPreservingAttr
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N503 deel C (owner 2026-11-01): D13 pets, D14 burden trinkets and ornament kits go to the gravestone on every death; D17 an upgraded
 * pet keeps its items in its own pack when it leaves; D19 the login reminder.
 */
class N503PetDeathTests {
    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()
        private const val PACK_YAK_TRINKET = 24236

        @BeforeClass
        @JvmStatic
        fun load() {
            Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv"), File(DIR, "pets_667.tsv"))
        }
    }

    @Test
    fun `D13 D14 - pets, trinkets and kits never take a kept place, even as the most valuable items`() {
        val pet = Pets.baseItem("Beef")!!
        val kit = gg.rsmod.plugins.content.items.osrs.OrnamentKitRules.KITS.first()
        listOf(pet, PACK_YAK_TRINKET, kit).forEach { assertTrue(DeathRules.toGrave(it), "item $it goes to the gravestone") }
        val inventory = arrayOfNulls<Item?>(28).also {
            it[0] = Item(pet)
            it[1] = Item(PACK_YAK_TRINKET)
            it[2] = Item(kit)
            it[3] = Item(4151)
        }
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = arrayOfNulls(14),
                keepCount = 3,
                valueProvider = ItemRiskValueProvider { if (it == 4151) 1L else 1_000_000_000L },
                alwaysLost = DeathRules::alwaysLost,
            )
        assertEquals(listOf(4151), result.protected.map { it.item.id })
        assertEquals(setOf(pet, PACK_YAK_TRINKET, kit), result.lost.map { it.item.id }.toSet())
    }

    @Test
    fun `D13 D14 - on a PvP death a pet or trinket is nothing for the killer and goes to the victim's gravestone as itself`() {
        val pet = Item(Pets.baseItem("Beef")!!)
        listOf(pet, Item(PACK_YAK_TRINKET)).forEach { item ->
            assertTrue(PvpDeathBreakables.converts(item.id))
            assertEquals(emptyList(), PvpDeathBreakables.killerItems(item))
            assertEquals(listOf(item.id to 1), PvpDeathBreakables.victimGraveItems(item).map { it.id to it.amount })
        }
    }

    @Test
    fun `D17 - an upgraded pet that leaves keeps its items in its own pack and takes them back when it comes out`() {
        val (player, containers) = newPlayer()
        val beef = Pets.baseItem("Beef")!!
        player.inventory.add(PACK_YAK_TRINKET, 1)
        player.attr[BurdenTrinkets.BOUND_ATTR] = BurdenTrinkets.encode(mapOf("Beef" to PACK_YAK_TRINKET))
        player.attr[Pets.PET_ITEM_ATTR] = beef
        val tier = BurdenTrinkets.tier(PACK_YAK_TRINKET)!!
        val follower = containers.getOrPut(BeastOfBurden.PET_KEYS.getValue(tier)) { ItemContainer(player.world.definitions, BeastOfBurden.PET_KEYS.getValue(tier)) }
        val charged = Item(11283, 1).also { it.attr[ItemAttribute.CHARGES] = 37 }
        follower.add(Item(995, 100))
        follower.addPreservingAttr(charged)

        assertEquals(2, BeastOfBurden.stowPetCargo(player))
        assertTrue(follower.rawItems.none { it != null }, "the follower pack is empty")
        val own = containers.getValue(BeastOfBurden.petPackKey(beef))
        assertEquals(100, own.getItemCount(995))
        assertEquals(37, own.rawItems.filterNotNull().single { it.id == 11283 }.attr[ItemAttribute.CHARGES], "charges kept")
        assertEquals(2, BeastOfBurden.petPackCount(player, "Beef"))
        // D19: the login reminder.
        assertEquals(1, BeastOfBurden.petsCarrying(player))
        assertEquals("1 of your pets still carries items.", BeastOfBurden.petsCarryingMessage(player))

        assertEquals(0, BeastOfBurden.unstowPetCargo(player))
        assertEquals(100, follower.getItemCount(995))
        assertEquals(0, BeastOfBurden.petPackCount(player, "Beef"))
        assertNull(BeastOfBurden.petsCarryingMessage(player))
    }

    @Test
    fun `D17 - every pet's own pack is saved and counted as owned`() {
        val key = BeastOfBurden.petPackKey(Pets.baseItem("Beef")!!)
        assertEquals(BeastOfBurden.PET_PACK_CAPACITY, key.capacity)
        assertEquals("your pet's pack", gg.rsmod.plugins.content.mechanics.ownership.Possessions.label(key))
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/pets.plugin.kts").readText()
        assertTrue("register_container_key(BeastOfBurden.petPackKey(it))" in plugin, "registered, so saved with the account")
    }

    private fun newPlayer(): Pair<Player, HashMap<ContainerKey, ItemContainer>> {
        val defs = SummoningTestCache.definitions
        val player = mockk<Player>(relaxed = true)
        val inventory = ItemContainer(defs, INVENTORY_KEY)
        val equipment = ItemContainer(defs, EQUIPMENT_KEY)
        val bank = ItemContainer(defs, BANK_KEY)
        val containers = hashMapOf<ContainerKey, ItemContainer>(INVENTORY_KEY to inventory, EQUIPMENT_KEY to equipment, BANK_KEY to bank)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.equipment } returns equipment
        every { player.bank } returns bank
        every { player.containers } returns containers
        every { player.world.definitions } returns defs
        return player to containers
    }
}
