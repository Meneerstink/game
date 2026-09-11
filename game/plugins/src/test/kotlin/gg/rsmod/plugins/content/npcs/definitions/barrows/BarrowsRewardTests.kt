package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Q-040: coverage for [Barrows.reward] (the chest reward formula), which had no test at all.
 * Mocks [World.random] deterministically per call (the function's own inclusive-bound contract,
 * `random.nextInt(boundInclusive + 1)`) rather than the RNG itself, so every assertion pins an
 * exact roll to an exact outcome.
 */
class BarrowsRewardTests {
    @Test
    fun `no brothers killed and zero kill levels yields nothing`() {
        val player = newPlayer(world = mockk(relaxed = true))

        val items = Barrows.reward(player)

        assertTrue(items.isEmpty())
    }

    @Test
    fun `a killed brother rolls armour on a hit and the item belongs to that brother's set`() {
        val world = mockk<World>()
        // kills=1 -> armour bound is (450 - 58*1 - 1) = 391; roll 0 hits.
        every { world.random(391) } returns 0
        val player = newPlayer(world = world)
        player.attr[Barrows.KILLS] = 1
        player.attr[Barrows.Brother.AHRIM.killedAttr] = true

        val items = Barrows.reward(player)

        assertEquals(1, items.size)
        assertTrue(items[0].id in Barrows.REWARD_ARMOUR.getValue(Barrows.Brother.AHRIM))
    }

    @Test
    fun `a killed brother rolls no armour on a miss`() {
        val world = mockk<World>()
        every { world.random(391) } returns 1
        val player = newPlayer(world = world)
        player.attr[Barrows.KILLS] = 1
        player.attr[Barrows.Brother.AHRIM.killedAttr] = true

        val items = Barrows.reward(player)

        assertTrue(items.isEmpty())
    }

    @Test
    fun `kills is capped at 6 for the armour roll bound even if more were recorded`() {
        val world = mockk<World>()
        // Bound must use kills=6 (450 - 58*6 - 1 = 101), not the raw recorded value.
        every { world.random(101) } returns 0
        val player = newPlayer(world = world)
        player.attr[Barrows.KILLS] = 9
        player.attr[Barrows.Brother.VERAC.killedAttr] = true

        val items = Barrows.reward(player)

        // repeat(kills) with the capped kills=6 means up to 6 armour rolls attempted.
        assertEquals(6, items.size)
        items.forEach { assertTrue(it.id in Barrows.REWARD_ARMOUR.getValue(Barrows.Brother.VERAC)) }
    }

    @Test
    fun `kill levels is capped at 1012 for the rune roll bound`() {
        val world = mockk<World>()
        every { world.random(1011) } returns 0
        every { world.random(772) } returns 0 // coins amount roll
        val player = newPlayer(world = world)
        player.attr[Barrows.KILL_LEVELS] = 5000

        val items = Barrows.reward(player)

        assertEquals(1, items.size)
        assertEquals(Items.COINS_995, items[0].id)
    }

    @Test
    fun `rune roll brackets resolve to the documented items`() {
        val cases =
            listOf(
                0 to Items.COINS_995,
                379 to Items.COINS_995,
                380 to Items.MIND_RUNE,
                504 to Items.MIND_RUNE,
                505 to Items.CHAOS_RUNE,
                629 to Items.CHAOS_RUNE,
                630 to Items.DEATH_RUNE,
                754 to Items.DEATH_RUNE,
                755 to Items.BLOOD_RUNE,
                879 to Items.BLOOD_RUNE,
                880 to Items.BOLT_RACK,
                1004 to Items.BOLT_RACK,
                1005 to Items.LOOP_HALF_OF_A_KEY,
                1007 to Items.LOOP_HALF_OF_A_KEY,
                1008 to Items.TOOTH_HALF_OF_A_KEY,
                1010 to Items.TOOTH_HALF_OF_A_KEY,
                1011 to Items.DRAGON_MED_HELM,
            )
        for ((roll, expected) in cases) {
            val world = mockk<World>()
            every { world.random(1011) } returns roll
            // Each bracket's item amount is a second, independent roll - stub every possible
            // bound rather than branch on which one this roll lands in.
            every { world.random(772) } returns 0 // coins
            every { world.random(83) } returns 0 // mind rune
            every { world.random(27) } returns 0 // chaos rune
            every { world.random(13) } returns 0 // death rune
            every { world.random(6) } returns 0 // blood rune
            every { world.random(5) } returns 0 // bolt rack
            val player = newPlayer(world = world)
            player.attr[Barrows.KILL_LEVELS] = 1012

            val items = Barrows.reward(player)

            assertEquals(1, items.size, "roll $roll")
            assertEquals(expected, items[0].id, "roll $roll")
        }
    }

    private fun newPlayer(world: World): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        return player
    }
}
