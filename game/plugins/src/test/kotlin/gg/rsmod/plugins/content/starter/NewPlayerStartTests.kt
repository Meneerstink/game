package gg.rsmod.plugins.content.starter

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NewPlayerStartTests {
    @Test
    fun `the Deadman starter pack is handed out once and opens into its contents`() {
        val config = NewPlayerConfig.DEFAULTS
        val player = newPlayer(isNew = true)

        NewPlayerStart.grantStarterPack(player)
        NewPlayerStart.grantStarterPack(player)
        assertEquals(1, player.inventory.getItemCount(config.starterChestItem))
        assertEquals(1, player.inventory.occupiedSlotCount)

        NewPlayerStart.openStarterPack(player)
        assertEquals(0, player.inventory.getItemCount(config.starterChestItem))
        config.starterChestContents.forEach { (item, amount) -> assertEquals(amount, player.inventory.getItemCount(item)) }
    }

    @Test
    fun `the Deadman starter pack opens into 1m coins, 100 house tablets and the skull, nothing in the bank`() {
        // Owner 2026-10-22 (N173/N179): no tuna or Dramen staff; the 1m starter cash comes out of the pack into the inventory.
        val player = newPlayer(isNew = true)

        NewPlayerStart.grantStarterPack(player)
        NewPlayerStart.openStarterPack(player)
        assertEquals(NewPlayerStart.STARTER_CASH, player.inventory.getItemCount(Items.COINS_995))
        assertEquals(100, player.inventory.getItemCount(Items.TELEPORT_TO_HOUSE))
        assertEquals(1, player.inventory.getItemCount(Items.DEADMANS_SKULL))
        assertEquals(3, player.inventory.occupiedSlotCount)
        assertEquals(0, player.bank.occupiedSlotCount)
    }

    @Test
    fun `the Deadman's skull has no Swap option`() {
        // Owner 2026-10-22 (N172/N175): no prayer book / spellbook swap on the skull.
        val options = DEFINITIONS.get(ItemDef::class.java, Items.DEADMANS_SKULL).inventoryMenu
        assertTrue(options.none { it?.lowercase() == "swap" }, "options=${options.toList()}")
        assertTrue(options.any { it?.lowercase() == "breach check" }, "options=${options.toList()}")
    }

    @Test
    fun `the Deadman starter pack has the Open option the handler binds`() {
        val options = DEFINITIONS.get(ItemDef::class.java, NewPlayerConfig.DEFAULTS.starterChestItem).inventoryMenu
        assertTrue(options.any { it?.lowercase() == "open" }, "options=${options.toList()}")
    }

    private fun newPlayer(isNew: Boolean): Player {
        val player = mockk<Player>(relaxed = true)
        val attributes = AttributeMap()
        if (isNew) {
            attributes[NEW_ACCOUNT_ATTR] = true
        }
        every { player.attr } returns attributes
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.world.definitions } returns DEFINITIONS
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
