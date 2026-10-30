package gg.rsmod.plugins.content.mechanics.removed

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.DeathRules
import gg.rsmod.plugins.content.mechanics.pvp.LootingBag
import io.mockk.every
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N424 (owner 2026-10-29: "looting bag volledig weg"): no route offers, uses or keeps the bag; an old save's bag contents go to the
 * bank (else Death's Office) at login, then the purge takes the bag item - nothing the player stored is lost.
 */
class LootingBagRemovedTests {
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    @Test
    fun `both bag ids and their noted forms are unobtainable`() {
        LootingBag.IDS.forEach { id ->
            val d = DEFINITIONS.get(ItemDef::class.java, id)
            assertTrue(RemovedItems.isUnobtainable(d), "$id must be unobtainable")
            if (d.noteLinkId > 0) assertTrue(RemovedItems.isUnobtainable(DEFINITIONS.get(ItemDef::class.java, d.noteLinkId)))
        }
        assertFalse(RemovedItems.isUnobtainable(DEFINITIONS.get(ItemDef::class.java, Items.ABYSSAL_WHIP)), "a normal item stays")
    }

    @Test
    fun `login moves the bag's contents to the bank with their attributes and the purge takes the bag`() {
        val bag = ItemContainer(DEFINITIONS, LootingBag.KEY)
        val player = player(mapOf(LootingBag.KEY to bag))
        bag[0] = Item(Items.ABYSSAL_WHIP).apply { putAttr(gg.rsmod.game.model.item.ItemAttribute.CHARGES, 77) }
        bag[4] = Item(Items.COINS_995, 12_345)
        player.inventory[0] = Item(Items.LOOTING_BAG)
        player.bank[0] = Item(Items.LOOTING_BAG_OPEN)

        assertEquals(2, LootingBag.retire(player))
        assertEquals(0, bag.occupiedSlotCount, "the bag's container is empty")
        assertEquals(1, player.bank.getItemCount(Items.ABYSSAL_WHIP))
        assertEquals(12_345, player.bank.getItemCount(Items.COINS_995))
        val whip = player.bank.rawItems.filterNotNull().first { it.id == Items.ABYSSAL_WHIP }
        assertEquals(77, whip.getAttr(gg.rsmod.game.model.item.ItemAttribute.CHARGES), "the whip keeps its charges")

        RemovedItems.purge(player)
        assertEquals(0, player.inventory.getItemCount(Items.LOOTING_BAG), "the bag item is gone")
        assertEquals(0, player.bank.getItemCount(Items.LOOTING_BAG_OPEN), "the banked open bag is gone")
        assertEquals(1, player.bank.getItemCount(Items.ABYSSAL_WHIP), "the stored items stay")
        assertEquals(0, LootingBag.retire(player), "a second login moves nothing")
    }

    @Test
    fun `a full bank sends the contents to Death's Office for free and nothing is ever dropped`() {
        val bag = ItemContainer(DEFINITIONS, LootingBag.KEY)
        val player = player(mapOf(LootingBag.KEY to bag))
        // Fill every bank slot with a distinct unstackable item so nothing new fits.
        var id = Items.BRONZE_DAGGER
        for (slot in 0 until player.bank.capacity) {
            while (DEFINITIONS.getNullable(ItemDef::class.java, id)?.let { it.noted || it.isPlaceholder || it.name.isBlank() } != false) id++
            player.bank[slot] = Item(id++)
        }
        bag[0] = Item(Items.ABYSSAL_WHIP)
        assertEquals(1, LootingBag.retire(player))
        assertNull(bag[0])
        assertEquals(1, player.deathRecovery.getItemCount(Items.ABYSSAL_WHIP), "Death's Office holds it")
    }

    @Test
    fun `no route offers, uses or treats a looting bag any more`() {
        assertFalse(File(content, "mechanics/pvp/looting_bag.plugin.kts").exists(), "the bag's item options are gone")
        assertFalse("LOOTING_BAG" in File(content, "mechanics/store/store_npcs.plugin.kts").readText(), "the Emblem Trader hands out no bag")
        LootingBag.IDS.forEach { assertFalse(DeathRules.alwaysLost(it), "deaths have no bag rule left") }
        val mentions =
            content.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                .filter { it.name != "LootingBag.kt" && it.name != "RemovedItems.kt" && it.name != "removed_items.plugin.kts" }
                .filter { f -> f.readText().let { "LootingBag." in it || "LOOTING_BAG" in it } }
                .map { it.path }.toList()
        assertTrue(mentions.isEmpty(), "only the retirement code may name the bag: $mentions")
    }

    private fun player(extra: Map<ContainerKey, ItemContainer>): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        val bank = ItemContainer(DEFINITIONS, BANK_KEY)
        val gravestone = ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        val death = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.inventory } returns inventory
        every { player.equipment } returns equipment
        every { player.bank } returns bank
        every { player.gravestone } returns gravestone
        every { player.deathRecovery } returns death
        every { player.containers } returns
            HashMap<ContainerKey, ItemContainer>().apply {
                put(INVENTORY_KEY, inventory)
                put(EQUIPMENT_KEY, equipment)
                put(BANK_KEY, bank)
                put(GRAVESTONE_KEY, gravestone)
                put(DEATH_RECOVERY_KEY, death)
                putAll(extra)
            }
        every { player.attr } returns AttributeMap()
        // Bank.deposit reads the selected bank tab (a varc).
        every { player.varcs } returns MutableList(2000) { 0 }
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
