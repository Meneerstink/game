package gg.rsmod.plugins.content.mechanics.prayer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N98 (owner VRAGEN 29 = C + 2026-10-22 drop rates): Rigour / Augury need 70 Defence and their prayer scroll, read once. */
class PrayerScrollsTests {
    @Test
    fun `the scrolls are in the cache with their Read option`() {
        listOf(Items.DEXTEROUS_PRAYER_SCROLL to "Dexterous prayer scroll", Items.ARCANE_PRAYER_SCROLL to "Arcane prayer scroll").forEach { (id, name) ->
            val def = DEFINITIONS.get(ItemDef::class.java, id)
            assertEquals(name, def.name)
            assertTrue(def.inventoryMenu.any { it?.lowercase() == "read" })
        }
    }

    @Test
    fun `Rigour needs 70 Defence and the read Dexterous scroll, Augury the Arcane one, other prayers nothing`() {
        val p = player(defence = 1)
        assertNotNull(PrayerScrolls.refusal(p, Prayer.RIGOUR), "1 Defence")
        assertNull(PrayerScrolls.refusal(p, Prayer.PIETY))
        val maxed = player(defence = 70)
        assertTrue(PrayerScrolls.refusal(maxed, Prayer.RIGOUR)!!.contains("Dexterous prayer scroll"))
        maxed.inventory.add(Items.DEXTEROUS_PRAYER_SCROLL, 1)
        assertTrue(PrayerScrolls.read(maxed, Items.DEXTEROUS_PRAYER_SCROLL))
        assertEquals(0, maxed.inventory.getItemCount(Items.DEXTEROUS_PRAYER_SCROLL), "the scroll is used up")
        assertNull(PrayerScrolls.refusal(maxed, Prayer.RIGOUR))
        assertNotNull(PrayerScrolls.refusal(maxed, Prayer.AUGURY), "Augury still needs its own scroll")
        maxed.inventory.add(Items.DEXTEROUS_PRAYER_SCROLL, 1)
        assertFalse(PrayerScrolls.read(maxed, Items.DEXTEROUS_PRAYER_SCROLL), "already learned: the scroll stays")
        assertEquals(1, maxed.inventory.getItemCount(Items.DEXTEROUS_PRAYER_SCROLL))
    }

    @Test
    fun `drop rates - 1 in 200 per breach kill, 1 in 10 per chest`() {
        val random = Random(7)
        val breach = (1..200_000).count { PrayerScrolls.roll(random, PrayerScrolls.BREACH_CHANCE) != null }
        assertTrue(breach in 850..1150, "about 1000 in 200,000: $breach")
        val chest = (1..10_000).count { PrayerScrolls.roll(random, PrayerScrolls.CHEST_CHANCE) != null }
        assertTrue(chest in 900..1100, "about 1000 in 10,000: $chest")
    }

    private fun player(defence: Int): Player {
        val p = mockk<Player>(relaxed = true)
        val skills = SkillSet(maxSkills = 25)
        skills.setBaseLevel(Skills.DEFENCE, defence)
        every { p.skills } returns skills
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        return p
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}

class PrayerScrollsWiringTests {
    @Test
    fun `both the queued toggle and the instant toggle check the scroll`() {
        val s = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/prayer/Prayers.kt").readText()
        val now = s.substringAfter("fun toggleNow(").substringBefore("flip(p, prayer)")
        assertTrue("PrayerScrolls.refusal(p, prayer) != null" in now, "the instant path (the one a click takes) skipped it")
        val queued = s.substringAfter("private suspend fun checkRequirements(").substringBefore("return true")
        assertTrue("PrayerScrolls.refusal(p, prayer)" in queued)
    }
}
