package gg.rsmod.plugins.content.combat.specialattack

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: Abyssal Vine Whip's delayed vine hits must not touch stale actors. */
class AbyssalVineWhipDelayedTests {
    @Test
    fun `vine repeat rejects offline victim and attacker before delayed hit`() {
        val source =
            File(
                "src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/melee_specials.plugin.kts",
            ).readText()
        val vine = source.substringAfter("Items.ABYSSAL_VINE_WHIP").substringBefore("/* Ancient mace")

        assertTrue(
            "victim is Player && !victim.isOnline" in vine,
            "delayed vine damage must stop after victim logout",
        )
        assertTrue("!attacker.isOnline" in vine)
        assertTrue(vine.indexOf("isOnline") < vine.indexOf("victim.hit"))
    }
}
