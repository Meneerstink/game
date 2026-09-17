package gg.rsmod.plugins.content.combat.specialattack

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: Enhanced Excalibur's delayed healing must not touch a logged-out player. */
class EnhancedExcaliburDelayedTests {
    @Test
    fun `delayed sanctuary healing rejects offline player`() {
        val source =
            File(
                "src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/instant_specials.plugin.kts",
            ).readText()
        val sanctuary = source.substringAfter("Items.ENHANCED_EXCALIBUR").substringBefore("/* Dragon battleaxe")

        assertTrue("p.isDead() || !p.isOnline" in sanctuary)
        assertTrue(sanctuary.indexOf("isOnline") < sanctuary.indexOf("p.heal"))
    }
}
