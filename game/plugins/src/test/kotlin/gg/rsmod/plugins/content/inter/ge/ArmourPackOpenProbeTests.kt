package gg.rsmod.plugins.content.inter.ge

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.items.packs.ArmourHarness
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCV-010: every 667 cache item with an "Unpack" op is an [ArmourHarness] with real, correctly named parts. */
class ArmourPackOpenProbeTests {
    @Test
    fun `every cache item with Unpack is a harness with valid parts`() {
        val count = DEFINITIONS.getCount(ItemDef::class.java)
        val unpackable =
            (0 until count).mapNotNull { DEFINITIONS.getNullable(ItemDef::class.java, it) }
                .filter { def -> !def.noted && def.inventoryMenu.any { it.equals("Unpack", true) } }
                .map { it.id }.toSet()
        val table = ArmourHarness.values().map { it.id }.toSet()
        assertEquals(unpackable, table, "cache Unpack items vs harness table")
        val bad = mutableListOf<String>()
        ArmourHarness.values().forEach { harness ->
            val name = DEFINITIONS.get(ItemDef::class.java, harness.id).name
            val prefix = if (name.startsWith("Pros'yte")) "Proselyte" else name.substringBefore(' ')
            harness.components.forEach { id ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, id)
                if (def == null || def.noted || !def.name.startsWith(prefix)) bad += "${harness.name}: part $id '${def?.name}'"
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString())
    }

    @Test
    fun `unpacking needs space and conserves items for every harness`() {
        ArmourHarness.values().forEach { harness ->
            val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
            val player = mockk<Player>(relaxed = true)
            every { player.inventory } returns inventory
            // Fill with unstackable filler so exactly (parts - 2) slots are free: one short.
            inventory.add(harness.id, 1)
            val filler = DEFINITIONS.get(ItemDef::class.java, 1351) // Bronze hatchet, unstackable
            repeat(inventory.capacity - 1 - (harness.components.size - 2)) { inventory.add(filler.id, 1) }
            assertFalse(ArmourHarness.unpack(player, 0), "${harness.name} unpacked without space")
            assertEquals(1, inventory.getItemCount(harness.id))
            inventory.remove(filler.id, 1)
            assertTrue(ArmourHarness.unpack(player, 0), "${harness.name} did not unpack with space")
            assertEquals(0, inventory.getItemCount(harness.id))
            harness.components.forEach { assertEquals(1, inventory.getItemCount(it), "${harness.name} part $it") }
        }
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
