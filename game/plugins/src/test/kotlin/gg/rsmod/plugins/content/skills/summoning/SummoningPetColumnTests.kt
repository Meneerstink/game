package gg.rsmod.plugins.content.skills.summoning

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N258 TERUG (owner 2026-10-24 12:39, bug-20261024-123909: "omdat een vorige agent de NA NA weg heeft gehaald ziet het er nu raar
 * uit, ik wil het terug"): Follower Details shows the pet column's two rows (662:44-48, growth and hunger, "NA" for an OSRS pet)
 * again - nothing hides them for a pet.
 */
class SummoningPetColumnTests {
    @Test
    fun `the pet column's growth and hunger rows show again for a pet - nothing hides 662 44 to 48`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningUi.kt").readText()
        assertTrue("PANEL_PET_COLUMN" !in source, "the N258 hide is back")
        // The rows read "NA" through the 2011 varbits, as before N258.
        assertTrue("PET_VALUE_NOT_APPLICABLE" in source)
    }
}
