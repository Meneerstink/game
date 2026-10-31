package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.hunter.ImplingData
import io.mockk.every
import kotlin.test.*

class N11LootTests {
    @Test fun `exactly two free slots allow loot jar and clue and retain unrelated object identities`() {
        N11Fixture().use { f ->
            setup(f, clue = true)
            repeat(27) { f.inventory[it] = Item(100000 + it) }
            val jar = Item(11256)
            f.inventory[0] = jar
            val neighbour = f.inventory[3]
            f.repo.executeItem(f.player, 11256, 3)
            assertSame(jar, f.inventory[0], "one free slot refuses before any consumption")
            f.inventory[26] = null
            f.repo.executeItem(f.player, 11256, 3)
            assertEquals(10, f.inventory.getItemCount(9193))
            assertEquals(1, f.inventory.getItemCount(Items.IMPLING_JAR))
            assertEquals(1, f.inventory.getItemCount(Items.CLUE_SCROLL_ELITE))
            assertTrue(f.inventory.isFull)
            assertSame(neighbour, f.inventory[3])
        }
    }
    private fun setup(f: N11Fixture, broken: Boolean = false, clue: Boolean = false, row: Int = 0) {
        every { f.player.varps.getState(1913) } returns 0
        every { f.world.random(any<IntRange>()) } answers {
            val range = firstArg<IntRange>()
            when (range) {
                0..9 -> if (broken) 0 else 1
                0..49 -> if (clue) 0 else 1
                ImplingData.dragonJar.common.indices -> row
                else -> range.first
            }
        }
        every { f.player.bank } returns f.container()
        setOf(9193, 1705, 1703, 11212, 9341, 5699, 11230, 1616, 11232, 11237, 535, 537, 5316, 5300, 7219).forEach { f.def(it).stacks = true }
        f.load("skills.hunter.Impling_jars_plugin")
    }

    @Test fun `dragon jar always returns main loot with or without jar break and may add an elite clue`() {
        listOf(false, true).forEach { broken ->
            N11Fixture().use { f ->
                setup(f, broken, clue = true)
                f.inventory[0] = Item(11256)
                assertTrue(f.repo.executeItem(f.player, 11256, 3))
                assertEquals(10, f.inventory.getItemCount(9193))
                assertEquals(if (broken) 0 else 1, f.inventory.getItemCount(Items.IMPLING_JAR))
                assertEquals(1, f.inventory.getItemCount(Items.CLUE_SCROLL_ELITE))
                f.repo.executeItem(f.player, 11256, 3)
                assertEquals(10, f.inventory.getItemCount(9193))
            }
        }
    }

    @Test fun `all eighteen ungated loot slots grant correct ids quantities and jar`() {
        ImplingData.dragonJar.common.forEachIndexed { index, (id, amounts) ->
            N11Fixture().use { f ->
                setup(f, row = index)
                f.inventory[0] = Item(11256)
                f.repo.executeItem(f.player, 11256, 3)
                assertEquals(amounts.first, f.inventory.getItemCount(id))
                assertEquals(1, f.inventory.getItemCount(Items.IMPLING_JAR))
            }
        }
    }

    @Test fun `nineteenth loot slot uses existing Monkey Madness II completion and imported javelin tips`() {
        N11Fixture().use { f ->
            setup(f)
            assertEquals(18, ImplingData.mainLoot(f.player, ImplingData.dragonJar).size)
            every { f.player.varps.getState(1913) } returns 9
            assertEquals(18, ImplingData.mainLoot(f.player, ImplingData.dragonJar).size)
            every { f.player.varps.getState(1913) } returns 10
            assertEquals(19, ImplingData.mainLoot(f.player, ImplingData.dragonJar).size)
            every { f.world.random(0..18) } returns 18
            f.def(Items.DRAGON_JAVELIN_TIPS).stacks = true
            f.inventory[0] = Item(11256)
            f.repo.executeItem(f.player, 11256, 3)
            assertEquals(25, f.inventory.getItemCount(22585))
        }
    }

    @Test fun `full inventory notes and stale slot refuse without consuming any other jar`() {
        N11Fixture().use { f ->
            setup(f)
            repeat(28) { f.inventory[it] = Item(100000 + it) }
            val jar = Item(11256)
            f.inventory[0] = jar
            f.repo.executeItem(f.player, 11256, 3)
            assertSame(jar, f.inventory[0])
            f.inventory[0] = Item(11257)
            f.inventory[1] = jar
            f.repo.executeItem(f.player, 11256, 3)
            assertSame(jar, f.inventory[1])
            assertFalse(f.repo.hasItemOption(11257, 3))
        }
    }

    @Test fun `elite clue is not duplicated from inventory or bank and old jar uses shared payout`() {
        N11Fixture().use { f ->
            setup(f, clue = true)
            f.player.bank[0] = Item(Items.CLUE_SCROLL_ELITE)
            f.inventory[0] = Item(11256)
            f.repo.executeItem(f.player, 11256, 3)
            assertEquals(0, f.inventory.getItemCount(Items.CLUE_SCROLL_ELITE))
        }
        N11Fixture().use { f ->
            val old = ImplingData.TIERS.first()
            f.menu(old.jarItem, "Loot")
            setup(f)
            f.inventory[0] = Item(old.jarItem)
            f.repo.executeItem(f.player, old.jarItem, 1)
            assertEquals(1, f.inventory.getItemCount(Items.IMPLING_JAR))
            assertEquals(1, f.inventory.getItemCount(old.rare.first().first))
        }
    }

    @Test fun `Loot retains a prebound option`() {
        N11Fixture().use { f ->
            f.repo.bindItem(11256, 3) {}
            setup(f)
        }
    }
}
