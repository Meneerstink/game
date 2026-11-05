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
    fun `N559 a lost untradeable is not sold back while the player has it again - grave, bank or worn - and only one per item`() {
        val player = newPlayer()
        PerduReclaim.record(player, Item(Items.NEITIZNOT_FACEGUARD), 50_000)
        PerduReclaim.record(player, Item(Items.NEITIZNOT_FACEGUARD), 50_000)
        assertEquals(1, PerduReclaim.offers(player).count { it.itemId == Items.NEITIZNOT_FACEGUARD }, "two records, one offer")
        player.gravestone[0] = Item(Items.NEITIZNOT_FACEGUARD)
        assertTrue(PerduReclaim.offers(player).none { it.itemId == Items.NEITIZNOT_FACEGUARD }, "in the gravestone: not offered (the dupe)")
        val offer = PerduReclaim.Offer(Items.NEITIZNOT_FACEGUARD, 1, 50_000, record = PerduReclaim.records(player).first())
        player.inventory.add(Items.COINS_995, 100_000)
        assertEquals("You don't need that back - you still have it.", PerduReclaim.claim(player, offer), "an old window cannot buy it either")
        player.gravestone[0] = null
        player.equipment[0] = Item(Items.NEITIZNOT_FACEGUARD)
        assertTrue(PerduReclaim.offers(player).none { it.itemId == Items.NEITIZNOT_FACEGUARD }, "worn: not offered")
        player.equipment[0] = null
        assertEquals(null, PerduReclaim.claim(player, offer), "really lost: bought back")
        assertTrue(PerduReclaim.offers(player).none { it.itemId == Items.NEITIZNOT_FACEGUARD }, "bought: the second record stays hidden")
        assertEquals(1, PerduReclaim.records(player).count { it.itemId == Items.NEITIZNOT_FACEGUARD }, "the second record is kept")
    }

    @Test
    fun `N559 a locked defender's broken form counts as owning the defender`() {
        val player = newPlayer()
        PerduReclaim.record(player, Item(Items.DRAGON_DEFENDER_L), 1_000)
        player.bank.add(Items.DRAGON_DEFENDER_L_BROKEN, 1)
        assertTrue(PerduReclaim.offers(player).none { it.itemId == Items.DRAGON_DEFENDER_L })
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

    @Test
    fun `K01 deel 3 an item in a familiar's pack is not lost either`() {
        val player = newPlayer()
        player.inventory.add(Items.COLOSSAL_BLADE, 1)
        PerduLostProperty.observe(player)
        player.inventory.remove(Items.COLOSSAL_BLADE, 1)
        val pack = ItemContainer(DEFINITIONS, gg.rsmod.plugins.content.skills.summoning.BeastOfBurden.PACK_YAK_KEY)
        player.containers[gg.rsmod.plugins.content.skills.summoning.BeastOfBurden.PACK_YAK_KEY] = pack
        pack[3] = Item(Items.COLOSSAL_BLADE)
        assertEquals("your familiar's pack", PerduLostProperty.stillOwned(player).single().second)
        pack[3] = null
        assertTrue(PerduLostProperty.available(player).any { it.itemId == Items.COLOSSAL_BLADE }, "really lost: offered")
    }

    @Test
    fun `N414 a pet the player had once and has nowhere is given back for free, one of each`() {
        val pets = gg.rsmod.plugins.content.skills.summoning.Pets
        val saved = pets.variants
        try {
            pets.install(listOf(gg.rsmod.plugins.content.skills.summoning.Pets.Variant("Test pet", "Test pet", PET, 1), gg.rsmod.plugins.content.skills.summoning.Pets.Variant("Test pet", "Test pet morph", PET_MORPH, 2)), emptyList())
            val player = newPlayer()
            player.bank.add(PET_MORPH, 1)
            PerduLostProperty.observe(player)
            assertTrue(PerduLostProperty.available(player).none { it.itemId == PET }, "a morph in the bank: owned")
            player.bank.remove(PET_MORPH, 1)
            val back = PerduLostProperty.available(player).single { it.itemId == PET }
            assertEquals(0L, back.price, "free")
            assertEquals("free", PerduReclaim.priceText(back.price))
            player.inventory.add(PET, 1)
            assertTrue(PerduLostProperty.available(player).none { it.itemId == PET }, "given back: owned again, never a second one")
        } finally {
            pets.install(saved, pets.infos.values.toList())
        }
    }

    @Test
    fun `N382 buying a lost item pays the carried coins first, then the bank, and the row is gone`() {
        val player = newPlayer()
        PerduReclaim.record(player, Item(Items.GLOVES_7462), 3_000)
        player.inventory.add(Items.COINS_995, 1_000)
        player.bank.add(Items.COINS_995, 5_000)
        val offer = PerduReclaim.paidOffers(player).single { it.itemId == Items.GLOVES_7462 }
        assertEquals(null, PerduReclaim.claim(player, offer))
        assertEquals(0, player.inventory.getItemCount(Items.COINS_995))
        assertEquals(3_000, player.bank.getItemCount(Items.COINS_995))
        assertEquals(1, player.inventory.getItemCount(Items.GLOVES_7462))
        assertTrue(PerduReclaim.paidOffers(player).none { it.itemId == Items.GLOVES_7462 }, "bought: no longer listed")
        assertEquals("I don't have that any more.", PerduReclaim.claim(player, offer), "a second click buys nothing")
    }

    @Test
    fun `N382 not enough coins or no space buys nothing and keeps the row`() {
        val player = newPlayer()
        PerduReclaim.record(player, Item(Items.GLOVES_7462), 3_000)
        player.bank.add(Items.COINS_995, 2_999)
        val offer = PerduReclaim.paidOffers(player).single()
        assertEquals("That will be 3,000 coins, which you don't have.", PerduReclaim.claim(player, offer))
        assertEquals(2_999, player.bank.getItemCount(Items.COINS_995))
        player.bank.add(Items.COINS_995, 1)
        repeat(28) { player.inventory.add(Items.LOBSTER, 1) }
        assertEquals("You'll need a free inventory space for that.", PerduReclaim.claim(player, offer))
        assertEquals(3_000, player.bank.getItemCount(Items.COINS_995), "nothing paid")
        assertEquals(1, PerduReclaim.paidOffers(player).size)
    }
    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.gravestone } returns ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.GRAVESTONE_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY)
        val containers = hashMapOf(
            INVENTORY_KEY to player.inventory, EQUIPMENT_KEY to player.equipment, BANK_KEY to player.bank,
            gg.rsmod.game.model.container.key.GRAVESTONE_KEY to player.gravestone, gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY to player.deathRecovery,
        )
        every { player.containers } returns containers
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private const val PET = Items.ABYSSAL_WHIP
        private const val PET_MORPH = Items.DRAGON_DAGGER

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            assertTrue(DEFINITIONS.getCount(ItemDef::class.java) > 0)
        }
    }
}
