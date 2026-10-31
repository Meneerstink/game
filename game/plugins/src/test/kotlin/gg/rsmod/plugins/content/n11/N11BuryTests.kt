package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.skills.prayer.burying.BoneData
import io.mockk.verify
import kotlin.test.*

class N11BuryTests {
    @Test fun `both rare bones give precisely 15 point 1 base XP through the compiled bury route`() {
        listOf(10976, 10977).forEach { id ->
            N11Fixture().use { f ->
                BoneData.values.forEach { f.menu(it.bone, "Bury") }
                f.load("skills.prayer.burying.Bone_burying_plugin")
                f.inventory[0] = Item(id)
                assertTrue(f.repo.executeItem(f.player, id, 1))
                f.repo.executeItem(f.player, id, 1)
                repeat(5) { f.queues.cycle() }
                assertNull(f.inventory[0])
                verify(exactly = 1) { f.player.addXp(Skills.PRAYER, 15.1, any(), any()) }
            }
        }
    }

    @Test fun `changing selected slot cannot redirect a pending bury or award XP twice`() {
        N11Fixture().use { f ->
            BoneData.values.forEach { f.menu(it.bone, "Bury") }
            f.load("skills.prayer.burying.Bone_burying_plugin")
            f.inventory[0] = Item(10976)
            f.inventory[1] = Item(10976)
            f.repo.executeItem(f.player, 10976, 1)
            f.queues.cycle()
            f.select(1)
            repeat(5) { f.queues.cycle() }
            assertNull(f.inventory[0])
            assertEquals(10976, f.inventory[1]?.id)
            f.select(0)
            f.repo.executeItem(f.player, 10976, 1)
            repeat(5) { f.queues.cycle() }
            verify(exactly = 1) { f.player.addXp(Skills.PRAYER, 15.1, any(), any()) }
        }
    }
}
