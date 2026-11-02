package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaveFee
import gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaves
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N522 (owner 2026-11-02: the Revenant Caves entrance fee as OSRS): 100,000 coins, carried coins first then the bank, kept until a
 * death inside the caves or a player kill in the Wilderness; a player killer gets the 100,000 coins as loot.
 */
class RevenantCaveFeeTests {
    private fun player(inventory: Int, bank: Int): Player {
        val coinDef = ItemDef(Items.COINS_995).apply { name = "Coins"; stacks = true }
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ItemDef::class.java, any()) } returns coinDef
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.STACK)
        every { player.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
        if (inventory > 0) player.inventory.add(Items.COINS_995, inventory)
        if (bank > 0) player.bank.add(Items.COINS_995, bank)
        return player
    }

    @Test
    fun `the fee comes from the inventory first, then the bank, and nothing is taken when both together are short`() {
        val p = player(inventory = 30_000, bank = 200_000)
        assertTrue(RevenantCaveFee.pay(p))
        assertEquals(0, p.inventory.getItemCount(Items.COINS_995))
        assertEquals(130_000, p.bank.getItemCount(Items.COINS_995))
        assertTrue(RevenantCaveFee.paid(p))

        val poor = player(inventory = 60_000, bank = 39_999)
        assertFalse(RevenantCaveFee.pay(poor))
        assertEquals(60_000, poor.inventory.getItemCount(Items.COINS_995))
        assertEquals(39_999, poor.bank.getItemCount(Items.COINS_995))
        assertFalse(RevenantCaveFee.paid(poor))
    }

    @Test
    fun `a death in the caves costs the fee, a player kill in the caves pays the killer 100,000 coins`() {
        val cave = RevenantCaves.ENTRANCES.getValue(RevenantCaves.CAVERN_17)
        assertTrue(RevenantCaveFee.inCaves(cave))

        val byMonster = player(0, 100_000).also { RevenantCaveFee.pay(it) }
        assertEquals(emptyList(), RevenantCaveFee.onDeath(byMonster, null, cave))
        assertFalse(RevenantCaveFee.paid(byMonster), "dying to a revenant inside the caves loses the fee")

        val byPlayer = player(0, 100_000).also { RevenantCaveFee.pay(it) }
        val loot = RevenantCaveFee.onDeath(byPlayer, player(0, 0), cave)
        assertEquals(listOf(Items.COINS_995 to 100_000), loot.map { it.id to it.amount })
        assertFalse(RevenantCaveFee.paid(byPlayer))
        assertEquals(emptyList(), RevenantCaveFee.onDeath(byPlayer, player(0, 0), cave), "a fee is lost (and paid out) only once")
    }

    @Test
    fun `outside the caves only a player kill in the Wilderness costs the fee - a monster death or a kill outside it does not`() {
        val wilderness = Tile(3100, 3700) // level 20
        val lumbridge = Tile(3222, 3218)
        assertFalse(RevenantCaveFee.inCaves(wilderness))
        assertFalse(RevenantCaveFee.inCaves(RevenantCaves.EXITS.getValue(RevenantCaves.STAIRS_SOUTH)), "the surface trapdoor is outside")

        val monster = player(0, 100_000).also { RevenantCaveFee.pay(it) }
        assertEquals(emptyList(), RevenantCaveFee.onDeath(monster, null, wilderness))
        assertTrue(RevenantCaveFee.paid(monster))

        val outside = player(0, 100_000).also { RevenantCaveFee.pay(it) }
        assertEquals(emptyList(), RevenantCaveFee.onDeath(outside, player(0, 0), lumbridge))
        assertTrue(RevenantCaveFee.paid(outside))

        val pked = player(0, 100_000).also { RevenantCaveFee.pay(it) }
        assertEquals(1, RevenantCaveFee.onDeath(pked, player(0, 0), wilderness).size)
        assertFalse(RevenantCaveFee.paid(pked))
    }

    @Test
    fun `no fee paid - nothing lost and nothing for the killer`() {
        val p = player(0, 0)
        assertEquals(emptyList(), RevenantCaveFee.onDeath(p, player(0, 0), RevenantCaves.ENTRANCES.getValue(RevenantCaves.CAVERN_40)))
    }
}
