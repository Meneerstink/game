package gg.rsmod.plugins.api.ext

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Audit E-06: a charged item moved by [transfer] (bank deposit / withdraw) or [addPreservingAttr] (trade, death
 * recovery) keeps its id and its charges; only a newly created item goes through the creation redirect.
 */
class ContainerExtRedirectTests {
    private val definitions: DefinitionSet =
        mockk<DefinitionSet>(relaxed = true).also { defs ->
            every { defs.get(ItemDef::class.java, CHARGED) } returns ItemDef(CHARGED).apply { name = "Toxic blowpipe" }
            every { defs.get(ItemDef::class.java, EMPTY) } returns ItemDef(EMPTY).apply { name = "Toxic blowpipe (empty)" }
        }

    private var saved: ((Int) -> Int?)? = null

    @BeforeTest
    fun installRedirect() {
        saved = ItemContainer.creationRedirect
        ItemContainer.creationRedirect = { id -> if (id == CHARGED) EMPTY else null }
    }

    @AfterTest
    fun restoreRedirect() {
        ItemContainer.creationRedirect = saved
    }

    private fun charged() = Item(CHARGED).putAttr(ItemAttribute.CHARGES, 16_383)

    @Test
    fun `a bank round trip keeps the charged id and its charges`() {
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        val bank = ItemContainer(definitions, 800, ContainerStackType.STACK)
        inventory[0] = charged()

        inventory.transfer(bank, inventory[0]!!, fromSlot = 0)
        assertEquals(CHARGED, bank[0]!!.id)
        bank.transfer(inventory, bank[0]!!, fromSlot = 0)

        assertEquals(CHARGED, inventory[0]!!.id)
        assertEquals(16_383, inventory[0]!!.getAttr(ItemAttribute.CHARGES))
    }

    @Test
    fun `a traded item keeps the charged id and its charges`() {
        val receiver = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        receiver.addPreservingAttr(charged())
        assertEquals(CHARGED, receiver[0]!!.id)
        assertEquals(16_383, receiver[0]!!.getAttr(ItemAttribute.CHARGES))
    }

    @Test
    fun `a created charged item is still its uncharged item`() {
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        inventory.add(CHARGED)
        assertEquals(EMPTY, inventory[0]!!.id)
    }

    companion object {
        const val CHARGED = 12926
        const val EMPTY = 12924
    }
}
