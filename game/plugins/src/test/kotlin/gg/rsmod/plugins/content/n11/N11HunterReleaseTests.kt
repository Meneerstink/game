package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.hunter.HunterRelease
import io.mockk.every
import io.mockk.verify
import kotlin.test.*

class N11HunterReleaseTests {
    private fun load(f: N11Fixture) = f.load("skills.hunter.Hunter_release_plugin")
    private fun answer(f: N11Fixture, yes: Boolean) {
        f.queues.cycle()
        f.queues.submitReturnValue(ResumePauseButtonMessage(interfaceId = 228, component = if (yes) 2 else 3, button = if (yes) 2 else 3))
        f.queues.cycle()
    }

    @Test fun `compiled black warlock Release returns jar in full inventory and boosts Strength at any Hunter level`() {
        N11Fixture().use { f ->
            load(f)
            every { f.skills.getMaxLevel(Skills.HUNTER) } returns 1
            every { f.skills.getMaxLevel(Skills.STRENGTH) } returns 99
            (0 until 28).forEach { f.inventory[it] = Item(100000 + it) }
            f.inventory[0] = Item(Items.BLACK_WARLOCK)
            assertTrue(f.repo.executeItem(f.player, Items.BLACK_WARLOCK, 4))
            assertEquals(Items.BUTTERFLY_JAR, f.inventory[0]?.id)
            verify(exactly = 1) { f.skills.alterCurrentLevel(Skills.STRENGTH, 18, 18) }
            f.repo.executeItem(f.player, Items.BLACK_WARLOCK, 4)
            verify(exactly = 1) { f.skills.alterCurrentLevel(any(), any(), any()) }
            assertEquals(100001, f.inventory[1]?.id)
        }
    }

    @Test fun `repeated butterfly boosts cap and do not lower a stronger existing boost`() {
        N11Fixture().use { f ->
            val skills = gg.rsmod.game.model.skill.SkillSet(25)
            skills.setXp(Skills.STRENGTH, 13034431.0)
            skills.setCurrentLevel(Skills.STRENGTH, 99)
            every { f.player.skills } returns skills
            repeat(2) {
                val held = Item(Items.BLACK_WARLOCK)
                f.inventory[0] = held
                assertTrue(HunterRelease.release(f.player, 0, held, 1))
                assertEquals(117, skills.getCurrentLevel(Skills.STRENGTH))
            }
            skills.setCurrentLevel(Skills.STRENGTH, 120)
            val held = Item(Items.BLACK_WARLOCK)
            f.inventory[0] = held
            assertTrue(HunterRelease.release(f.player, 0, held, 1))
            assertEquals(120, skills.getCurrentLevel(Skills.STRENGTH))
        }
    }

    @Test fun `single red chin releases immediately and stack asks confirmation with cancel`() {
        N11Fixture().use { f ->
            load(f)
            f.def(Items.RED_CHINCHOMPA_10034).stacks = true
            f.inventory[0] = Item(Items.RED_CHINCHOMPA_10034)
            assertTrue(f.repo.executeItem(f.player, Items.RED_CHINCHOMPA_10034, 5))
            assertNull(f.inventory[0])
            f.inventory[0] = Item(Items.RED_CHINCHOMPA_10034, 10)
            f.repo.executeItem(f.player, Items.RED_CHINCHOMPA_10034, 5)
            assertEquals(10, f.inventory[0]?.amount)
            answer(f, false)
            assertEquals(10, f.inventory[0]?.amount)
            f.repo.executeItem(f.player, Items.RED_CHINCHOMPA_10034, 5)
            answer(f, true)
            assertNull(f.inventory[0])
        }
    }

    @Test fun `confirmation never releases a replacement or changed stack or a noted butterfly`() {
        N11Fixture().use { f ->
            load(f)
            f.inventory[0] = Item(Items.RED_CHINCHOMPA_10034, 10)
            f.repo.executeItem(f.player, Items.RED_CHINCHOMPA_10034, 5)
            f.inventory[0] = Item(Items.RED_CHINCHOMPA_10034, 10)
            answer(f, true)
            assertEquals(10, f.inventory[0]?.amount)
            val held = f.inventory[0]!!
            held.amount = 11
            assertFalse(HunterRelease.release(f.player, 0, held, 10))
            f.inventory[0] = Item(Items.BLACK_WARLOCK_NOTED)
            f.repo.executeItem(f.player, Items.BLACK_WARLOCK, 4)
            assertEquals(Items.BLACK_WARLOCK_NOTED, f.inventory[0]?.id)
            assertFalse(f.repo.hasItemOption(Items.BLACK_WARLOCK_NOTED, 4))
        }
    }

    @Test fun `Hunter release keeps a preexisting binding and does not implement rabbit Lay`() {
        N11Fixture().use { f ->
            var calls = 0
            f.repo.bindItem(Items.BLACK_WARLOCK, 4) { calls++ }
            load(f)
            f.repo.executeItem(f.player, Items.BLACK_WARLOCK, 4)
            assertEquals(1, calls)
            assertFalse(f.repo.hasItemOption(Items.RABBIT_SNARE, 1))
        }
    }
}
