package gg.rsmod.plugins.content.combat.strategy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared special-hit path can reach the melee XP helper with a hybrid weapon's selected
 * ranged or magic XP mode. Those modes must delegate to the existing strategy formulas, never
 * fall through to a runtime TODO() exception.
 */
class MeleeCombatXpTests {
    @Test
    fun `hybrid xp modes delegate instead of throwing`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt").readText()
        assertFalse("-> TODO()" in source, "melee XP helper must not retain a reachable TODO crash")
        assertTrue("XpMode.RANGED_XP -> RangedCombatStrategy.addCombatXp(player, target, damage)" in source)
        assertTrue("XpMode.MAGIC_XP -> MagicCombatStrategy.addCombatXp(player, target, damage, baseXp = 0.0)" in source)
    }
}
