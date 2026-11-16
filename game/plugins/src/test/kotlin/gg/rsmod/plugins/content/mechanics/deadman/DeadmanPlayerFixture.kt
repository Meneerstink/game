package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk

/** In-memory actors only: no saves or running server. Test ids are fixtures, never production bindings. */
class DeadmanPlayerFixture {
    val definitions = mockk<DefinitionSet>()
    val defs = mutableMapOf<Int, ItemDef>()
    val world = mockk<World>(relaxed = true)
    val inventory = ItemContainer(definitions, INVENTORY_KEY)
    val bank = ItemContainer(definitions, BANK_KEY)
    val equipment = ItemContainer(definitions, EQUIPMENT_KEY)
    val containers = hashMapOf(INVENTORY_KEY to inventory, BANK_KEY to bank, EQUIPMENT_KEY to equipment)
    val attr = AttributeMap()
    val player = mockk<Player>(relaxed = true).also { p ->
        every { p.world } returns world
        every { p.inventory } returns inventory
        every { p.bank } returns bank
        every { p.equipment } returns equipment
        every { p.containers } returns containers
        every { p.attr } returns attr
        every { p.timers } returns TimerMap()
        every { p.isDead() } returns false
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { defs.getValue(secondArg()) }
    }
    fun define(id: Int, stackable: Boolean = false): ItemDef = ItemDef(id).also {
        it.stacks = stackable; defs[id] = it
    }
    fun count(id: Int): Long = containers.values.sumOf { c -> c.filterNotNull().filter { it.id == id }.sumOf { it.amount.toLong() } }
}
