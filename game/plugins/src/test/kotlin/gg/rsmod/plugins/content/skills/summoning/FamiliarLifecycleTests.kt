package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.BeforeClass
import java.lang.ref.WeakReference
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lifecycle rules that are not covered by the pure-data ledger tests: where a familiar is
 * actually placed, and what happens to a beast of burden's cargo when its owner dies.
 *
 * Uses the same cache-backed [DefinitionSet] plus mocked [World] harness as [BeastOfBurdenTests],
 * because familiar placement reads the real [NpcDef] footprint out of the cache.
 */
class FamiliarLifecycleTests {
    @Test
    fun `calling a familiar places it beside the owner, not on top of them`() {
        assertPlacedBeside(Npcs.SPIRIT_WOLF)
    }

    @Test
    fun `a large familiar is placed on tiles its whole footprint fits on`() {
        assertPlacedBeside(Npcs.PACK_YAK)
        assertPlacedBeside(Npcs.STEEL_TITAN)
    }

    /**
     * Placement is expressed as the familiar's south-west corner, so a multi-tile familiar's
     * corner is legitimately more than one tile away from its owner. What must hold for every
     * size is that the footprint clears the owner's tile entirely and still touches it.
     */
    private fun assertPlacedBeside(npcId: Int) {
        val owner = Tile(3222, 3218, 0)
        val player = newPlayer(npcId, owner)
        val familiar = Familiar.current(player)!!
        val size = DEFINITIONS.get(NpcDef::class.java, npcId).size.coerceAtLeast(1)

        assertTrue(Familiar.call(player))

        val footprint =
            (0 until size).flatMap { x -> (0 until size).map { z -> familiar.tile.transform(x, z) } }
        assertEquals(owner.height, familiar.tile.height)
        assertTrue(footprint.none { it == owner }, "footprint $footprint overlaps the owner tile $owner")
        assertTrue(
            footprint.any { it.isWithinRadius(owner, 1) },
            "footprint $footprint is not adjacent to the owner tile $owner",
        )
    }

    /*
     * CUSTOM_SERVER_OVERRIDE - deliberately not authentic 2011 behaviour. These three tests used
     * to assert the two period rules (dismiss/death drops the cargo on the floor, owner death
     * destroys it). The owner's standing design decision replaces both with a single safe one:
     * the cargo is never floored and never lost, it moves into Death's Domain recovery storage.
     * See BeastOfBurden.release.
     */
    @Test
    fun `dismissing moves the beast of burden cargo to deaths domain`() {
        val player = newPlayer(Npcs.PACK_YAK, Tile(3222, 3218, 0))
        val world = player.world
        val key = BeastOfBurden.activeKey(player)!!
        assertEquals(5, BeastOfBurden.grant(player, Item(Items.COINS_995, 5)))

        Familiar.dismiss(player)

        verify(exactly = 0) { world.spawn(any<GroundItem>()) }
        assertEquals(0, player.containers.getValue(key).getItemCount(Items.COINS_995))
        assertEquals(5, player.deathRecovery.getItemCount(Items.COINS_995))
        assertEquals(1, BeastOfBurden.deathsDomainCount(player))
        assertNull(Familiar.current(player))
    }

    @Test
    fun `owner death moves the beast of burden cargo to deaths domain`() {
        val player = newPlayer(Npcs.PACK_YAK, Tile(3222, 3218, 0))
        val world = player.world
        val key = BeastOfBurden.activeKey(player)!!
        assertEquals(5, BeastOfBurden.grant(player, Item(Items.COINS_995, 5)))

        Familiar.ownerDeath(player)

        verify(exactly = 0) { world.spawn(any<GroundItem>()) }
        assertEquals(0, player.containers.getValue(key).getItemCount(Items.COINS_995))
        assertEquals(5, player.deathRecovery.getItemCount(Items.COINS_995))
        assertNull(Familiar.current(player))
    }

    @Test
    fun `familiar death moves bob cargo to deaths domain and clears state`() {
        val player = newPlayer(Npcs.PACK_YAK, Tile(3222, 3218, 0))
        val world = player.world
        val familiar = Familiar.current(player)!!
        familiar.attr[DAMAGE_CREDIT_ATTR] = WeakReference(player)
        val key = BeastOfBurden.activeKey(player)!!
        assertEquals(5, BeastOfBurden.grant(player, Item(Items.COINS_995, 5)))

        Familiar.onDeath(familiar)

        verify(exactly = 0) { world.spawn(any<GroundItem>()) }
        assertEquals(0, player.containers.getValue(key).getItemCount(Items.COINS_995))
        assertEquals(5, player.deathRecovery.getItemCount(Items.COINS_995))
        assertNull(Familiar.current(player))
    }

    /** Nothing may be duplicated or destroyed when Death's Domain has no room left. */
    @Test
    fun `a full deaths domain leaves the cargo where it is instead of destroying it`() {
        val player = newPlayer(Npcs.PACK_YAK, Tile(3222, 3218, 0))
        val key = BeastOfBurden.activeKey(player)!!
        for (slot in 0 until player.deathRecovery.capacity) {
            player.deathRecovery[slot] = Item(Items.BRONZE_ARROW, 1)
        }
        assertEquals(1, BeastOfBurden.grant(player, Item(Items.RUNE_PLATEBODY, 1)))

        Familiar.dismiss(player)

        assertEquals(1, player.containers.getValue(key).getItemCount(Items.RUNE_PLATEBODY))
        assertEquals(0, player.deathRecovery.getItemCount(Items.RUNE_PLATEBODY))
    }
    private fun newPlayer(
        familiarNpcId: Int,
        tile: Tile,
    ): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs

        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        every { player.tile } returns tile
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.containers } returns HashMap()
        // A real container, not a relaxed mock: the Death's Domain rescue is the thing under test.
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)

        val npc = Npc(familiarNpcId, tile, world)
        npcs.add(npc)
        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
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
