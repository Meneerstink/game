package gg.rsmod.plugins.content.areas.burthorpe

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarriorsGuildDefendersTests {
    @Test
    fun `N374 the cyclops gives the next defender in order, never a random one`() {
        val p = newPlayer()
        assertEquals(Items.BRONZE_DEFENDER, WarriorsGuildDefenders.next(p, 4291), "nothing yet: bronze")
        p.equipment[5] = gg.rsmod.game.model.item.Item(Items.MITHRIL_DEFENDER)
        assertEquals(Items.ADAMANT_DEFENDER, WarriorsGuildDefenders.next(p, 4291))
        p.equipment[5] = null
        WarriorsGuildDefenders.earn(p, Items.ADAMANT_DEFENDER)
        assertEquals(Items.RUNE_DEFENDER, WarriorsGuildDefenders.next(p, 4291), "an earned defender still counts once banked or lost")
        WarriorsGuildDefenders.earn(p, Items.RUNE_DEFENDER)
        assertNull(WarriorsGuildDefenders.next(p, 4291), "the 56s give no dragon defender")
        assertEquals(Items.DRAGON_DEFENDER, WarriorsGuildDefenders.next(p, 4292))
        assertEquals(50, WarriorsGuildDefenders.chance(Items.RUNE_DEFENDER))
        assertEquals(100, WarriorsGuildDefenders.chance(Items.DRAGON_DEFENDER))
    }

    @Test
    fun `N374 the bulk tables no longer drop every defender at random`() {
        val text = File("../../data/cfg/npcs/drop-tables.json").readLines()
        WarriorsGuildDefenders.CYCLOPES.forEach { id ->
            val row = text.firstOrNull { it.startsWith("{\"id\":$id,") } ?: return@forEach
            WarriorsGuildDefenders.ORDER.forEach { d -> assertTrue("\"item\":$d," !in row, "cyclops $id still drops $d") }
        }
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun load() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
