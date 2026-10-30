package gg.rsmod.game.message.handler

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.impl.IfButtonMessage
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/** N446: use the real 667 inventory packet path; Feel must retain the combat target/queue just as eating does. */
class IfButtonLocatorOrbTests {
    @Test
    fun `Feel keeps combat like food, and unrelated Feel actions retain their interruption`() {
        for ((name, option, preserve) in listOf(
            Triple("Locator orb", "Feel", true),
            Triple("Another device", "Feel", false),
            Triple("Shark", "Eat", true),
            Triple("Prayer potion(4)", "Drink", true),
            Triple("Spade", "Dig", false),
        )) {
            val client = mockk<Client>(relaxed = true)
            val world = mockk<World>(relaxed = true)
            val definitions = mockk<DefinitionSet>()
            val plugins = mockk<PluginRepository>(relaxed = true)
            every { world.plugins } returns plugins
            val def = ItemDef(24618).apply { this.name = name; inventoryMenu[0] = option }
            every { definitions.get(ItemDef::class.java, def.id) } returns def
            every { world.definitions } returns definitions
            every { client.world } returns world
            every { client.attr } returns AttributeMap()
            every { client.lock } returns LockState.NONE
            every { client.interfaces.isVisible(any()) } returns true
            every { client.inventory } returns ItemContainer(definitions, INVENTORY_KEY).also { it[0] = Item(def.id) }
            every { plugins.executeItem(client, def.id, 1) } returns true
            IfButton1Handler().handle(client, world, IfButtonMessage(679 shl 16, 0, 0, def.id, 61))
            verify(exactly = 1) { client.fullInterruption(false, true, false, true, preserve) }
            verify(exactly = 1) { plugins.executeItem(client, def.id, 1) }
        }
    }
}
