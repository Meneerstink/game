package gg.rsmod.plugins.content.combat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One rule for "which style entry does the wielded weapon use" (2026-10-01): [CombatConfigs.getCombatClass] (melee / ranged /
 * magic strategy) and [CombatConfigs.getCombatStyle] (the formula's stab / slash / crush / ranged / magic) must resolve the
 * style varp the same way. When the class still used the raw lookup, a style varp the weapon has no entry for gave
 * class MELEE (null -> else) while the style fell back to the equip rule (e.g. a salamander's Blaze = MAGIC), and
 * MeleeCombatFormula threw "Invalid combat style" mid-swing (crash log 2026-09-29 / 2026-10-01).
 */
class CombatStyleRouteTests {
    private val source = File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText()

    private fun body(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue(start >= 0, "$signature not found")
        var depth = 0
        var i = source.indexOf('{', start)
        val from = i
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(from, i + 1)
            }
            i++
        }
        error("unbalanced $signature")
    }

    @Test
    fun `combat class and combat style resolve the weapon style through the same route`() {
        val combatClass = body("fun getCombatClass(pawn: Pawn)")
        val combatStyle = body("fun getCombatStyle(pawn: Pawn)")
        val xpMode = body("fun getXpMode(player: Player)")
        listOf("getCombatClass" to combatClass, "getCombatStyle" to combatStyle, "getXpMode" to xpMode).forEach { (name, text) ->
            assertTrue("styleData(" in text, "$name must use styleData (the equip fallback for a style the weapon lacks)")
            assertFalse("WeaponCombatData.getAttackStyleType" in text, "$name must not look the style up on its own")
        }
    }
}
