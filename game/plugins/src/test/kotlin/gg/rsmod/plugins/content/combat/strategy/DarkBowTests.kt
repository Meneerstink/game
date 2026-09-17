package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS import run 2026-09-17: Dark bow double arrow and Descent of Darkness/Dragons (OSRS Wiki "Dark bow", wiki DPS calculator). */
class DarkBowTests {
    @Test
    fun `normal attacks fire a second independently rolled arrow when two arrows are equipped`() {
        assertEquals(setOf(Items.DARK_BOW, Items.DARK_BOW_15701, Items.DARK_BOW_15702, Items.DARK_BOW_15703, Items.DARK_BOW_15704, Items.DARK_BOW_GREEN, Items.DARK_BOW_BLUE, Items.DARK_BOW_YELLOW, Items.DARK_BOW_WHITE), Bows.DARK_BOWS)
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("darkBowArrows >= 2" in strategy, "second arrow only with two arrows")
        assertTrue("spendArrow(pawn, target, secondFired, secondArrow.id)" in strategy, "the second arrow is used/dropped/saved independently")
        assertTrue("landHit = formula.getAccuracy(pawn, target) >= world.randomDouble()" in strategy, "own accuracy roll")
    }

    @Test
    fun `descent special uses regular accuracy, minimum on a miss and the 48 cap`() {
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/ranged_specials.plugin.kts").readText()
        assertTrue("fired.item.amount < 2" in specials)
        assertTrue("rolled.coerceIn(minimum.toInt(), 48)" in specials)
        assertTrue("specialAttackMultiplier = 1.0) >= world.randomDouble()" in specials)
        assertTrue("accuracy = 1.15" !in specials.substringAfter("Descent of Darkness").substringBefore("Seercull"))
    }

    /**
     * OSRS-IMPORT audit round 2026-09-17b: OSRS Wiki "Dark bow" infobox `attackrange = 10` and "It has the maximum
     * possible attack range of 10, so the longrange attack style will not increase its attack range." The kits2 fix
     * (2026-09-17) corrected the recolours from the 667 default of 7 to 9, one tile short of the sourced value for
     * every dark bow variant (base included). Guards the corrected 10-tile range and that dark bows are no longer
     * grouped with the wooden/magic longbow family's 9-tile range.
     */
    @Test
    fun `every dark bow variant has the sourced 10-tile range, not 9`() {
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("in Bows.CRYSTAL_BOWS, in Bows.DARK_BOWS -> 10" in strategy)
        val bowsFile = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/ranged/weapon/Bows.kt").readText()
        assertTrue(Bows.DARK_BOWS.none { it in Bows.LONG_BOWS }, "dark bows must not also fall into the 9-tile long-bow lookup")
        assertTrue("DARK_BOWS.toTypedArray()" !in bowsFile, "dark bows no longer spliced into LONG_BOWS")
    }
}
