package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.items.armor.DragonChainbodyMigration
import gg.rsmod.plugins.content.quests.tab.CollectionLog
import io.mockk.every
import java.io.File
import kotlin.test.*

class N11RewardIdentityTests {
    @Test fun `login converts all five containers preserving stacks attributes and neighbouring items`() {
        N11Fixture().use { f ->
            val bank = f.container()
            val grave = f.container()
            val recovery = f.container()
            every { f.player.bank } returns bank
            every { f.player.gravestone } returns grave
            every { f.player.deathRecovery } returns recovery
            val containers = listOf(f.inventory, f.equipment, bank, grave, recovery)
            containers.forEachIndexed { index, container ->
                container[0] = Item(2513, index + 1).apply { attr[ItemAttribute.CHARGES] = 23 }
                container[1] = Item(995, 100)
                container[2] = Item(3140)
            }
            f.attrs[CollectionLog.ATTR] = "2513:2:7;3140:1:9"
            f.load("items.armor.Dragon_chainbody_migration_plugin")
            f.repo.executeLogin(f.player)
            containers.forEachIndexed { index, container ->
                assertEquals(3140, container[0]?.id)
                assertEquals(index + 1, container[0]?.amount)
                assertEquals(23, container[0]?.attr?.get(ItemAttribute.CHARGES))
                assertEquals(100, container[1]?.amount)
            }
            assertEquals("3140:3:9", f.attrs[CollectionLog.ATTR])
            assertEquals(0, DragonChainbodyMigration.migrate(f.player), "idempotent login")
        }
    }

    @Test fun `KQ awards the standard wearable identity with unchanged rarity`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/other/kalphite_queen.plugin.kts").readText()
        assertTrue(source.contains("obj(Items.DRAGON_CHAINBODY_3140, slots = 78)"))
        assertFalse(source.contains("obj(Items.DRAGON_CHAINBODY,"))
        assertEquals("3140:1:2", DragonChainbodyMigration.migratedLog("2513:1:2"))
        assertEquals("3140:kalphite_queen:0", DragonChainbodyMigration.migratedRecent("2513:kalphite_queen:0"))
    }
}
