package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.skills.prayer.scattering.AshData
import io.mockk.verify
import kotlin.test.*

class N11ScatterTests {
    private fun load(f: N11Fixture) {
        AshData.values.forEach { f.menu(it.ash, "Scatter") }
        f.load("skills.prayer.scattering.Ash_scattering_plugin")
    }

    @Test fun `imported ashes award 110 once in full inventory and native ashes retain their XP`() {
        listOf(22312 to 110.0, AshData.INFERNAL_ASHES.ash to 62.5).forEach { (id, xp) ->
            N11Fixture().use { f ->
                load(f)
                repeat(28) { f.inventory[it] = Item(100000 + it) }
                f.inventory[0] = Item(id)
                f.repo.executeItem(f.player, id, 1)
                f.repo.executeItem(f.player, id, 1)
                repeat(6) { f.queues.cycle() }
                assertNull(f.inventory[0])
                verify(exactly = 1) { f.player.addXp(Skills.PRAYER, xp, any(), any()) }
            }
        }
    }

    @Test fun `pending scatter cannot consume a replacement or follow changed interacting slot`() {
        N11Fixture().use { f ->
            load(f)
            val original = Item(22312)
            f.inventory[0] = original
            f.repo.executeItem(f.player, 22312, 1)
            f.queues.cycle()
            f.inventory[0] = Item(22312)
            f.inventory[1] = original
            f.select(1)
            repeat(6) { f.queues.cycle() }
            assertEquals(22312, f.inventory[0]?.id)
            assertSame(original, f.inventory[1])
            verify(exactly = 0) { f.player.addXp(any(), any(), any(), any()) }
            assertFalse(f.repo.hasItemOption(22313, 1))
            assertFalse(f.repo.hasEquipmentOption(22312, 1))
        }
    }

    @Test fun `scatter retains an existing binding`() {
        N11Fixture().use { f ->
            f.repo.bindItem(22312, 1) {}
            load(f)
        }
    }
}
