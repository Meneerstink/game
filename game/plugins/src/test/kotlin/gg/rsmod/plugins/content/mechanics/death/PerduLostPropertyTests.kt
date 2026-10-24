package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-04 ("a lot of items need to go to Perdu", "exactly OSRS"): Perdu's OSRS Lost Property. OSRS Wiki "Colossal blade":
 * "only has to be unlocked once; if lost in any situation, it can be re-obtained from Perdu for 300,000 as long as you do not have
 * another copy"; "Lost Property shop": "none of the item on them, in their bank or in the POH", exceptions "can be purchased
 * multiples of".
 */
class PerduLostPropertyTests {
    @Test
    fun `an earned item is offered once it is gone, never while the player owns one`() {
        val player = newPlayer()
        player.equipment[3] = Item(Items.COLOSSAL_BLADE)
        assertTrue(PerduLostProperty.available(player).isEmpty(), "never seen: not offered")
        PerduLostProperty.observe(player)
        assertTrue(PerduLostProperty.available(player).none { it.itemId == Items.COLOSSAL_BLADE }, "still owned: not offered")
        player.equipment[3] = null
        val blade = PerduLostProperty.available(player).single { it.itemId == Items.COLOSSAL_BLADE }
        assertEquals(300_000L, blade.price)
        player.bank.add(Items.COLOSSAL_BLADE, 1)
        assertTrue(PerduLostProperty.available(player).none { it.itemId == Items.COLOSSAL_BLADE }, "a copy in the bank counts")
    }

    @Test
    fun `N250 an item waiting in the gravestone or Death's Office is not lost - Perdu says where it is`() {
        val player = newPlayer()
        player.inventory.add(Items.COLOSSAL_BLADE, 1)
        PerduLostProperty.observe(player)
        player.inventory.remove(Items.COLOSSAL_BLADE, 1)
        player.gravestone[0] = Item(Items.COLOSSAL_BLADE)
        assertTrue(PerduLostProperty.available(player).none { it.itemId == Items.COLOSSAL_BLADE })
        assertEquals("your gravestone", PerduLostProperty.stillOwned(player).single().second)
        player.gravestone[0] = null
        player.deathRecovery[0] = Item(Items.COLOSSAL_BLADE)
        assertEquals("Death's Office", PerduLostProperty.stillOwned(player).single().second)
        player.deathRecovery[0] = null
        assertTrue(PerduLostProperty.available(player).any { it.itemId == Items.COLOSSAL_BLADE }, "really lost: offered")
    }

    @Test
    fun `the wiki's exceptions can be bought while the player owns one`() {
        val player = newPlayer()
        player.inventory[0] = Item(Items.AVAS_ACCUMULATOR)
        PerduLostProperty.observe(player)
        assertEquals(7_000L, PerduLostProperty.available(player).single { it.itemId == Items.AVAS_ACCUMULATOR }.price)
        assertEquals(Items.VORKATHS_HEAD, PerduLostProperty.ENTRIES.single { it.itemId == Items.AVAS_ASSEMBLER }.consumes)
    }

    @Test
    fun `achievement diary items cost 200 each, every tier, and can be bought while the player owns one`() {
        // Owner Q14=A (2026-10-06): Perdu StoreLine "Karamja gloves 200 | Ardougne cloak 200 | ...", exceptions "Achievement diary
        // reward items" "can be purchased multiples of". Every tier 1-4 of the six diary items this cache has.
        assertEquals(24, PerduLostProperty.DIARY_ITEMS.toSet().size)
        for (id in PerduLostProperty.DIARY_ITEMS) {
            val entry = PerduLostProperty.ENTRIES.single { it.itemId == id }
            assertEquals(200L, entry.price, "$id costs 200 coins")
            assertTrue(entry.multiple, "$id: multiples allowed")
            assertTrue(DEFINITIONS.getNullable(ItemDef::class.java, id) != null, "$id exists in the cache")
        }
        val player = newPlayer()
        player.equipment[9] = Item(Items.KARAMJA_GLOVES_4)
        assertTrue(PerduLostProperty.available(player).none { it.itemId == Items.KARAMJA_GLOVES_4 }, "never seen: not offered")
        PerduLostProperty.observe(player)
        assertEquals(200L, PerduLostProperty.available(player).single { it.itemId == Items.KARAMJA_GLOVES_4 }.price, "offered while worn")
        assertTrue(PerduLostProperty.available(player).none { it.itemId == Items.KARAMJA_GLOVES_3 }, "a tier never seen is not offered")
    }

    @Test
    fun `a broken or locked imbued cape counts as owning one`() {
        val player = newPlayer()
        player.inventory[0] = Item(Items.IMBUED_ZAMORAK_CAPE)
        PerduLostProperty.observe(player)
        player.inventory[0] = Item(Items.IMBUED_ZAMORAK_CAPE_BROKEN)
        assertTrue(PerduLostProperty.available(player).none { it.itemId == Items.IMBUED_ZAMORAK_CAPE })
        player.inventory[0] = null
        assertEquals(250_000L, PerduLostProperty.available(player).single { it.itemId == Items.IMBUED_ZAMORAK_CAPE }.price)
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.gravestone } returns ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.GRAVESTONE_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            assertTrue(DEFINITIONS.getCount(ItemDef::class.java) > 0)
        }
    }
}
