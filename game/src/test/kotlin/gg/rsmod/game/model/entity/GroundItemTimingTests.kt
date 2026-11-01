package gg.rsmod.game.model.entity

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N503 (owner 2026-11-01 D5/D7/D8): a ground item may carry its own public/despawn time (PvP death loot); every other item
 * keeps the world's 1 + 1 minute, and two stacks only merge when they would disappear together.
 */
class GroundItemTimingTests {
    private val world: World by lazy {
        val context = mockk<GameContext>(relaxed = true)
        every { context.gItemPublicDelay } returns 100
        every { context.gItemDespawnDelay } returns 200
        val world = World(context, mockk<DevContext>(relaxed = true))
        world.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        world.definitions.loadAll(world.filestore)
        // Tradeability comes from ItemMetadataService at boot; a tradeable item is what turns public.
        world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, WHIP).tradeable = true
        world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, COINS).tradeable = true
        world
    }

    private val owner = mockk<Player>(relaxed = true).also { every { it.uid } returns gg.rsmod.game.model.PlayerUID(42) }

    private fun cycles(n: Int) = repeat(n) { world.cycle() }

    @Test
    fun `an ordinary drop keeps the world's 1 + 1 minute`() {
        val drop = GroundItem(WHIP, 1, Tile(3200, 3200), owner)
        world.spawn(drop)
        cycles(99)
        assertFalse(drop.isPublic())
        cycles(1)
        assertTrue(drop.isPublic(), "public after the world's 100 cycles")
        cycles(99)
        assertTrue(world.isSpawned(drop))
        cycles(1)
        assertFalse(world.isSpawned(drop), "gone after the world's 200 cycles")
    }

    @Test
    fun `an item with its own times turns public and despawns on them`() {
        val loot = GroundItem(WHIP, 1, Tile(3201, 3200), owner).also { it.publicCycles = 100; it.despawnCycles = 6100 }
        world.spawn(loot)
        cycles(100)
        assertTrue(loot.isPublic(), "1 minute for the killer")
        cycles(5000)
        assertTrue(world.isSpawned(loot), "still there long after an ordinary drop would be gone")
        cycles(1000)
        assertFalse(world.isSpawned(loot), "gone after 1 + 60 minutes")
    }

    @Test
    fun `public at once when nobody owns it`() {
        val loot = GroundItem(WHIP, 1, Tile(3202, 3200), null).also { it.publicCycles = 0; it.despawnCycles = 400 }
        world.spawn(loot)
        assertTrue(loot.isPublic())
        cycles(399)
        assertTrue(world.isSpawned(loot))
        cycles(1)
        assertFalse(world.isSpawned(loot))
    }

    @Test
    fun `stacks with different times never merge`() {
        val tile = Tile(3203, 3200)
        val dropped = GroundItem(COINS, 10, tile, null)
        world.spawn(dropped)
        val loot = GroundItem(COINS, 500, tile, null).also { it.publicCycles = 0; it.despawnCycles = 6000 }
        world.spawn(loot)
        assertEquals(10, dropped.amount, "the PvP coins did not join the ordinary stack (they would vanish with it)")
        assertTrue(world.isSpawned(loot))
        val more = GroundItem(COINS, 5, tile, null)
        world.spawn(more)
        assertEquals(15, dropped.amount, "two ordinary stacks still merge")
    }

    companion object {
        private const val WHIP = 4151
        private const val COINS = 995
    }
}
