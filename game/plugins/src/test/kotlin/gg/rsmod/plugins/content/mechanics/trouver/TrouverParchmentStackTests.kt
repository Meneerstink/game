package gg.rsmod.plugins.content.mechanics.trouver

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** N60 (owner VRAGEN 55, 2026-10-22): Trouver parchments do not stack (max 28 carried); an old carried stack moves to the bank. */
class TrouverParchmentStackTests {
    @Test
    fun `the parchment does not stack`() {
        assertFalse(DEFINITIONS.get(ItemDef::class.java, Items.TROUVER_PARCHMENT).stackable)
    }

    @Test
    fun `a carried stack keeps one and banks the rest`() {
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val bank = ItemContainer(DEFINITIONS, 800, ContainerStackType.STACK)
        inventory[3] = Item(Items.TROUVER_PARCHMENT, 500)
        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns inventory
        every { player.bank } returns bank
        every { player.attr } returns AttributeMap()
        assertEquals(499, Trouver.splitCarriedStack(player))
        assertEquals(1, inventory[3]!!.amount)
        assertEquals(499, bank.getItemCount(Items.TROUVER_PARCHMENT))
        assertEquals(0, Trouver.splitCarriedStack(player), "nothing more to move")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
