package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PROJECT48 DMM-ITEMS group C: Tome of earth, Twinflame staff and Soulflame horn against the OSRS Wiki pages (fetched 2026-11-21). */
class DmmCItemsTests {
    @Test
    fun `the tome of earth is a third tome with soiled pages and earth runes`() {
        val tome = Tomes.Tome.EARTH
        assertEquals(25495, tome.charged)
        assertEquals(25496, tome.empty)
        assertEquals(25631, tome.page)
        assertEquals(Items.EARTH_RUNE, tome.rune)
        assertEquals(5, Tomes.EARTH_SPELLS.size)
        assertTrue(Tomes.usesCharge(tome, CombatSpell.EARTH_BLAST))
        assertFalse(Tomes.usesCharge(tome, CombatSpell.FIRE_BLAST))
        assertEquals(tome, Tomes.tomeFor(25495))
        val charged = Tomes.withCharges(Item(25496), 2 * Tomes.CHARGES_PER_PAGE)
        assertEquals(25495, charged.id)
        assertEquals(40, Tomes.charges(charged))
        assertEquals(25496, Tomes.withCharges(charged, 0).id)
    }

    @Test
    fun `the twinflame staff second spell is 40 percent of the first rounded down on the shown hit`() {
        assertEquals(0, TwinflameStaff.secondDamage(10)) // shown 1 -> 0
        assertEquals(20, TwinflameStaff.secondDamage(70)) // shown 7 -> 2
        assertEquals(40, TwinflameStaff.secondDamage(100)) // shown 10 -> 4
        assertEquals(120, TwinflameStaff.secondDamage(300)) // shown 30 -> 12
        // bolts, blasts and waves of the four elements; never strikes or surges
        assertEquals(12, TwinflameStaff.DOUBLE_HIT_SPELLS.size)
        assertFalse(CombatSpell.FIRE_STRIKE in TwinflameStaff.DOUBLE_HIT_SPELLS)
        assertFalse(CombatSpell.EARTH_SURGE in TwinflameStaff.DOUBLE_HIT_SPELLS)
        assertTrue(CombatSpell.WIND_WAVE in TwinflameStaff.DOUBLE_HIT_SPELLS)
    }

    @Test
    fun `the group C items are wired into the formulas and the item data`() {
        val main = "src/main/kotlin/gg/rsmod/plugins/content/"
        assertTrue("TwinflameStaff.boost(pawn, spell)" in File(main + "combat/formula/MagicCombatFormula.kt").readText())
        assertTrue("TwinflameStaff.suppliesRune(p, rune)" in File(main + "magic/MagicSpells.kt").readText())
        assertTrue("TwinflameStaff.doubleHit(pawn, spell)" in File(main + "combat/strategy/MagicCombatStrategy.kt").readText())
        assertTrue("SoulflameHorn.consume(pawn)" in File(main + "combat/formula/MeleeCombatFormula.kt").readText())
        assertTrue("SoulflameHorn.use(player)" in File(main + "combat/specialattack/weapons/soulflame_horn.plugin.kts").readText())
        assertEquals(10, SoulflameHorn.DURATION_TICKS)
        assertEquals(25, SoulflameHorn.COST_PER_ALLY)
        val items = File("../../data/cfg/items.yml").readText()
        assertTrue("- id: 25631\n  name: \"Soiled page\"" in items.replace("\r\n", "\n"), "the soiled page is imported")
    }
}
