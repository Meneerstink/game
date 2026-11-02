package gg.rsmod.plugins.content.magic.lunar

import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.skills.slayer.data.SlayerMaster
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/** N72 Astral Contact / NPC Contact: the Void donor's interface 429 layout on our npcs. */
class NpcContactTests {
    @Test
    fun `every contact has its own head and name component, none hidden, none the random or close button`() {
        val components = NpcContact.CONTACTS.flatMap { listOf(it.head, it.label) }
        assertEquals(components.size, components.toSet().size)
        assertTrue(components.none { it in NpcContact.HIDDEN || it == NpcContact.RANDOM_HEAD || it == NpcContact.RANDOM_LABEL || it == NpcContact.CLOSE })
        NpcContact.CONTACTS.forEach { c ->
            assertEquals(c, NpcContact.byComponent(c.head))
            assertEquals(c, NpcContact.byComponent(c.label))
        }
        assertNull(NpcContact.byComponent(NpcContact.CLOSE))
    }

    @Test
    fun `the slayer master contacts answer as our slayer masters and the dark mage as the abyss dark mage`() {
        val masters = SlayerMaster.values().map { it.id }.toSet() + 7779
        NpcContact.CONTACTS.filter { it.name != "Dark mage" }.forEach { c -> assertTrue(c.npcIds.any { it in masters }, c.name) }
        assertEquals(listOf(Npcs.DARK_MAGE_2262), NpcContact.CONTACTS.single { it.name == "Dark mage" }.npcIds)
        // Void: 63 xp (spells.tables 630 / 10), anim 4413, gfx 728 height 130, sound 3618.
        assertEquals(63.0, NpcContact.XP)
        assertEquals(listOf(4413, 728, 130, 3618), listOf(NpcContact.ANIMATION, NpcContact.GRAPHIC, NpcContact.GRAPHIC_HEIGHT, NpcContact.SOUND))
    }
}
