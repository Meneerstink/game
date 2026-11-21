package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.plugins.content.skills.farming.data.Patch
import gg.rsmod.plugins.content.skills.farming.data.Seed
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** PROJECT48 I-A08: the seed box used on a farming patch (OSRS Wiki "Seed box", fetched 2026-11-21) plants the first seed that fits the patch. */
class SeedBoxPlantTests {
    @Test
    fun `a herb seed fits a herb patch and an allotment seed does not`() {
        val guam = assertNotNull(Seed.fromId(5291), "guam seed 5291")
        val potato = assertNotNull(Seed.fromId(5318), "potato seed 5318")
        assertTrue(guam.seedType in Patch.FaladorHerb.seedTypes)
        assertFalse(potato.seedType in Patch.FaladorHerb.seedTypes)
        assertTrue(potato.seedType in Patch.FaladorAllotmentNorth.seedTypes)
        assertTrue(guam.amountToPlant() >= 1)
        assertEquals(25474, Sacks.SEED_BOX)
        assertEquals(25475, Sacks.SEED_BOX_OPEN)
    }

    @Test
    fun `both seed box items are routed from the patch to the box planting`() {
        val farming = "src/main/kotlin/gg/rsmod/plugins/content/skills/farming/logic/FarmingManager.kt"
        val text = File(farming).readText().replace("\r\n", "\n")
        assertTrue("Sacks.SEED_BOX," in text && "Sacks.SEED_BOX_OPEN," in text && "plantFromSeedBox(patch)" in text)
        assertTrue("Sacks.takeSeedForPatch(player, patch)" in text)
    }
}
