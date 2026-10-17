package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.drops.DropTableFactory
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/** Drop Viewer (owner 2026-09-30): every chance is read from the table the kill rolls, the same way the roll reads it. */
class DropViewerTests {
    @Test
    fun `chances follow the roll - guaranteed, declared total, nested table`() {
        val id = 999_001
        DropTableFactory.register(
            DropTableFactory.build {
                guaranteed { obj(536) }
                main {
                    total(128)
                    obj(1149, slots = 1)
                    obj(995, quantityRange = 100..200, slots = 32)
                    table(DropTableFactory.build { main { total(2); obj(1305, slots = 1); nothing(1) } }, slots = 4)
                }
            },
            id,
        )
        val rows = DropViewer.rows(mockk<Player>(relaxed = true), id, rate = 1.0) // the 1x table; the Annihilation rates: AnnihilationRatesTests
        fun chance(item: Int) = rows.single { it.item == item }.chance
        assertEquals(1.0, chance(536))
        assertEquals(1.0 / 128, chance(1149), 1e-12)
        assertEquals(32.0 / 128, chance(995), 1e-12)
        assertEquals("100-200", rows.single { it.item == 995 }.quantity)
        assertEquals(4.0 / 128 * 0.5, chance(1305), 1e-12)
        assertEquals(listOf(536, 995, 1305, 1149), rows.map { it.item }, "Always first, then most common")
        assertEquals("Always", DropViewer.rarity(1.0))
        assertEquals("1 / 128", DropViewer.rarity(1.0 / 128))
        assertEquals("1 / 4", DropViewer.rarity(0.25))
        assertEquals("1 / 64", DropViewer.rarity(4.0 / 128 * 0.5))
    }
}
