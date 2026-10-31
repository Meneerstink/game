package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.plugin.Plugin
import io.mockk.every
import java.io.File
import kotlin.test.*

class N11RemainingEmptyTests {
    @Test fun `legacy cocktail and Easter bucket use the shared late Empty route and preserve prebound options`() {
        N11Fixture().use { f ->
            val rows = File("../../data/cfg/item_empty.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { it.split('\t').let { c -> c[0].toInt() to c[1].toInt() } }
            assertEquals(2026, rows[9508])
            assertEquals(1925, rows[12651])
            f.menu(9508, "Drink", "Empty")
            f.menu(12651, "", "", "Empty")
            f.repo.bindItem(1929, 3) {}
            val script = f.load("items.Empty_containers_plugin")
            script.javaClass.getDeclaredField("EMPTIED").apply { isAccessible = true }.set(script, rows)
            every { f.world.executePlugin(any(), any()) } answers {
                secondArg<Plugin.() -> Unit>().invoke(Plugin(firstArg()))
            }
            f.repo.executeWorldInit(f.world)
            listOf(Triple(9508, 2, 2026), Triple(12651, 3, 1925)).forEach { (id, option, empty) ->
                repeat(28) { f.inventory[it] = Item(100000 + it) }
                f.inventory[0] = Item(id)
                f.select(0)
                f.repo.executeItem(f.player, id, option)
                assertEquals(empty, f.inventory[0]?.id)
                f.repo.executeItem(f.player, id, option)
                assertEquals(1, f.inventory.getItemCount(empty))
                f.inventory[1] = Item(id)
                f.repo.executeItem(f.player, id, option)
                assertEquals(id, f.inventory[1]?.id)
            }
        }
    }
}
