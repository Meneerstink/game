package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
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
        // 1 brother -> armour bound (450 - 58*1 - 1) = 391; roll 0 hits on both of the 1 + 1 rolls (Chest (Barrows)).
        every { world.random(391) } returns 0
        val player = newPlayer(world = world)
        player.attr[Barrows.Brother.AHRIM.killedAttr] = true

        val items = Barrows.reward(player)

        assertEquals(2, items.size)
        items.forEach { assertTrue(it.id in Barrows.REWARD_ARMOUR.getValue(Barrows.Brother.AHRIM)) }
        // N469: "once chosen it will not be chosen again on future rolls".
        assertEquals(2, items.map { it.id }.toSet().size, "two different pieces")
    }

    @Test
    fun `a killed brother rolls no armour on a miss`() {
        val world = mockk<World>()
        every { world.random(391) } returns 1
        // Reward potential = 0 levels + 2 for the brother -> table bound 1; roll 0 = coins.
        every { world.random(1) } returns 0
        every { world.random(772) } returns 0
        val player = newPlayer(world = world)
        player.attr[Barrows.Brother.AHRIM.killedAttr] = true

        val items = Barrows.reward(player)

        // N469: the two coin rolls are one reward of 2 + 2 coins, not two lines.
        assertEquals(listOf(Items.COINS_995 to 4), items.map { it.id to it.amount })
    }

    @Test
    fun `six brothers give seven rolls at the 1 in 102 armour chance`() {
        val world = mockk<World>()
        // 450 - 58*6 - 1 = 101.
        every { world.random(101) } returns 0
        val player = newPlayer(world = world)
        Barrows.Brother.values().forEach { player.attr[it.killedAttr] = true }
        player.attr[Barrows.KILLS] = 9

        val items = Barrows.reward(player)

        assertEquals(7, items.size)
        items.forEach { item -> assertTrue(Barrows.REWARD_ARMOUR.values.any { item.id in it }) }
        assertEquals(7, items.map { it.id }.toSet().size, "seven different pieces")
    }

    @Test
    fun `kill levels are capped at 1000 without brothers`() {
        val world = mockk<World>()
        every { world.random(999) } returns 0
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
            // Six brothers, armour missed: reward potential 1000 + 12 = 1012 -> bound 1011, seven rolls.
            every { world.random(101) } returns 1
            every { world.random(1011) } returns roll
            every { world.random(772) } returns 0 // coins
            every { world.random(83) } returns 0 // mind rune
            every { world.random(27) } returns 0 // chaos rune
            every { world.random(13) } returns 0 // death rune
            every { world.random(6) } returns 0 // blood rune
            every { world.random(5) } returns 0 // bolt rack
            val player = newPlayer(world = world)
            Barrows.Brother.values().forEach { player.attr[it.killedAttr] = true }
            player.attr[Barrows.KILL_LEVELS] = 1000

            val items = Barrows.reward(player)

            // N469: seven rolls on the same line are one reward.
            assertEquals(listOf(expected), items.map { it.id }, "roll $roll")
        }
    }

    @Test
    fun `the same table item rolled twice is one reward in the order first rolled`() {
        val combined =
            Barrows.combineSameItems(
                listOf(
                    Item(Items.DEATH_RUNE, 70),
                    Item(Items.MIND_RUNE, 253),
                    Item(Items.DEATH_RUNE, 83),
                    Item(Items.MIND_RUNE, 300),
                    Item(Items.COINS_995, 5),
                ),
            )
        assertEquals(listOf(Items.DEATH_RUNE to 153, Items.MIND_RUNE to 553, Items.COINS_995 to 5), combined.map { it.id to it.amount })
    }

    private fun newPlayer(world: World): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        return player
    }
}
