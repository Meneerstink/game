package gg.rsmod.plugins.content.areas.watson

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner answer Q10: the Strange casket dialogue follows the OSRS Wiki transcript and persists the Mimic opt-in. */
class StrangeCasketTests {
    @Test
    fun `every transcript line is present and the opt-in is persistent`() {
        assertEquals(62743, StrangeCasket.LOC)
        assertEquals("mimic_challenges", StrangeCasket.MIMIC_CHALLENGES.persistenceKey)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/areas/watson/strange_casket.plugin.kts").readText()
        listOf(
            "Do you... seek a challenge? If you do, the Mimic will test your mettle.",
            "The Mimic is... a casket. When you find treasure, perhaps you will realise your casket is the Mimic.",
            "If so, the Mimic will reveal itself, and summon you to its arena, to fight.",
            "If you are victorious, the Mimic will reward you with... things.",
            "So, would you like the chance to face the Mimic? The rewards are... special.",
            "Yes, I'd like to get Mimic challenges.", "The Mimic is... pleased.",
            "You may find me here if you change your mind. The Mimic will wait.",
            "As you see, I am... a casket. Some of us are not mere vessels of wealth.",
            "Some of us speak. Some can walk. And some, like the Mimic... can fight.",
            "The Mimic is... disappointed.", "Do you... tire of the challenge?",
            "Very well. You shall not be challenged by the Mimic again. Return to me if you change your mind.",
            "No, I want to keep getting mimic challenges.",
            "on_obj_option(obj = StrangeCasket.LOC, option = \"search\")",
            "it.player.attr[StrangeCasket.MIMIC_CHALLENGES] = true", "it.player.attr.remove(StrangeCasket.MIMIC_CHALLENGES)",
            "on_item_option(item = Items.MIMIC, option = \"open\")", "player.message(StrangeCasket.OPEN_BEFORE_FIGHT)",
        ).forEach { assertTrue(it in plugin, it) }
        assertEquals(23729, gg.rsmod.plugins.api.cfg.Items.MIMIC)
        assertEquals("Visit the Strange Casket, upstairs in Watson's house in Hosidius, to attempt the Mimic's challenge.", StrangeCasket.OPEN_BEFORE_FIGHT)
        // Line-ending agnostic: the checkout may carry CRLF (git autocrlf), the entry is what is asserted.
        val yml = File("../../data/cfg/items.yml").readText().replace("\r\n", "\n")
        assertTrue("- id: 23729\n  name: \"Mimic\"\n  examine: \"Oh great, it's a casket that's come to life.\"\n  tradeable: false" in yml)
    }
}
