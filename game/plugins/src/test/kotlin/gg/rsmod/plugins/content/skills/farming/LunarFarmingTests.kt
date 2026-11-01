package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.plugins.content.skills.farming.LunarFarming.View
import gg.rsmod.plugins.content.skills.farming.constants.CompostState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N72 deel C: Cure Plant and Fertile Soil cast on a patch (OSRS Wiki rules), no longer "This spell is not available yet." */
class LunarFarmingTests {
    private fun view(
        planted: Boolean = true,
        weedy: Boolean = false,
        dead: Boolean = false,
        diseased: Boolean = false,
        grown: Boolean = false,
        compost: CompostState = CompostState.None,
    ) = View(planted, weedy, dead, diseased, grown, compost, "allotment")

    @Test
    fun `Cure Plant cures only a diseased crop`() {
        assertNull(LunarFarming.cureRefusal(view(diseased = true)))
        assertEquals("It's growing just fine.", LunarFarming.cureRefusal(view()))
        assertEquals("That's not diseased.", LunarFarming.cureRefusal(view(grown = true)))
        assertEquals(LunarFarming.CURE_DEAD, LunarFarming.cureRefusal(view(dead = true)))
        assertEquals("The weeds are healthy enough already.", LunarFarming.cureRefusal(view(planted = false, weedy = true)))
        assertEquals("There's nothing there to cure.", LunarFarming.cureRefusal(view(planted = false)))
        assertEquals(LunarFarming.CURE_NOT_A_PATCH, LunarFarming.cureRefusal(null))
        assertEquals(LunarFarming.CURE_BIN, LunarFarming.cureRefusal(null, bin = true))
    }

    @Test
    fun `Fertile Soil treats an empty or growing patch once, never a grown, weedy or dead one`() {
        assertNull(LunarFarming.fertileRefusal(view(planted = false)))
        assertNull(LunarFarming.fertileRefusal(view()))
        assertNull(LunarFarming.fertileRefusal(view(diseased = true)))
        assertEquals(LunarFarming.FERTILE_FULLY_GROWN, LunarFarming.fertileRefusal(view(grown = true)))
        assertEquals("This patch needs weeding first.", LunarFarming.fertileRefusal(view(planted = false, weedy = true)))
        assertEquals("This patch needs to be cleared first.", LunarFarming.fertileRefusal(view(dead = true)))
        assertEquals("This allotment has already been treated with supercompost.", LunarFarming.fertileRefusal(view(compost = CompostState.SuperCompost)))
        assertEquals("This allotment has already been treated with compost.", LunarFarming.fertileRefusal(view(compost = CompostState.Compost)))
        assertEquals(LunarFarming.FERTILE_NOT_A_PATCH, LunarFarming.fertileRefusal(null))
        assertEquals(LunarFarming.FERTILE_BIN, LunarFarming.fertileRefusal(null, bin = true))
    }

    @Test
    fun `both spells are bound on the patch and no longer answer not available`() {
        val src = File("src/main/kotlin/gg/rsmod/plugins/content/magic/lunar/lunar_spells.plugin.kts").readText()
        assertTrue("on_spell_on_obj(parent = 430, child = SpellbookData.CURE_PLANT.component)" in src)
        assertTrue("on_spell_on_obj(parent = 430, child = SpellbookData.FERTILE_SOIL.component)" in src)
        val stubs = Regex("listOf\\(([^)]*)\\)\\.forEach \\{ name ->\\s*on_magic_spell_button").find(src)!!.groupValues[1]
        assertTrue("Cure Plant" !in stubs && "Fertile Soil" !in stubs, stubs)
    }
}
