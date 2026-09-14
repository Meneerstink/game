package gg.rsmod.plugins.content.combat.scripts.impl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Q-042: Glacor rules from the pre-EoC RuneScape Wiki revisions (Glacor 2012-09-23, glacyte pages 2012). Glacors are not in OSRS. */
class GlacorPreEocRulesTests {
    @Test
    fun `enduring last cuts damage by 60 percent, freeze needs no magic protection, enduring glacyte is passive`() {
        assertEquals(0.4, GlacorCombatScript.ENDURING_LAST_DAMAGE_MULTIPLIER)
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/GlacorCombatScript.kt").readText()
        assertTrue("it.damage * ENDURING_LAST_DAMAGE_MULTIPLIER" in script)
        assertFalse("it.damage * 0.6)" in script)
        assertTrue("damage > 0 && !target.isProtectedFrom(CombatClass.MAGIC) && world.random(5) == 0" in script)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/other/glacor.plugin.kts").readText()
        assertTrue("radius = if (id == Npcs.ENDURING_GLACYTE) 0 else 8" in plugin)
        assertTrue("hitpoints = 5000" in plugin, "glacor 5,000 LP (x10) = 500")
        assertTrue("hitpoints = 1000" in plugin, "glacytes 1,000 LP (x10) = 100")
    }
}
