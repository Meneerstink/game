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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NewPlayerStartTests {
    @Test
    fun `new account receives exactly fifty thousand coins once`() {
        val player = newPlayer(isNew = true)

        assertTrue(NewPlayerStart.grantStarterCash(player))
        assertEquals(50_000, player.bank.getItemCount(Items.COINS_995))
        assertFalse(NewPlayerStart.grantStarterCash(player))
        assertEquals(50_000, player.bank.getItemCount(Items.COINS_995))
    }
 @Test
 fun `legacy starter coins are included in exact fifty thousand total`() {
 val player = newPlayer(isNew = true)
 player.bank.add(Items.COINS_995, 25)

 assertTrue(NewPlayerStart.grantStarterCash(player))
 assertEquals(50_000, player.bank.getItemCount(Items.COINS_995))
 }

 @Test
 fun `existing account cannot trigger starter cash`() {
        val player = newPlayer(isNew = false)

        assertFalse(NewPlayerStart.grantStarterCash(player))
        assertEquals(0, player.bank.getItemCount(Items.COINS_995))
    }

    @Test
    fun `reconnected account without transient new-account flag receives nothing`() {
        val firstSession = newPlayer(isNew = true)
        assertTrue(NewPlayerStart.grantStarterCash(firstSession))

        val reconnected = newPlayer(isNew = false)
        assertFalse(NewPlayerStart.grantStarterCash(reconnected))
        assertEquals(0, reconnected.bank.getItemCount(Items.COINS_995))
    }

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
