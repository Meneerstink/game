package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cache-backed checks for the authoritative pouch -> familiar mapping.
 * A pouch must spawn the non-combat familiar definition: the definition must
 * expose Interact, while quest/event NPCs and combat variants must not be used.
 */
class FamiliarDefinitionTests {
    @Test
    fun `every pouch maps to an interactable familiar definition`() {
        val definitions = DefinitionSet()
        definitions.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))

        val failures = SummoningPouchData.values.mapNotNull { data ->
            val npc = definitions.getNullable(NpcDef::class.java, data.npc)
            when {
                npc == null -> "${data.name}: missing NPC ${data.npc}"
                npc.options.none { it.equals("Interact", ignoreCase = true) } ->
                    "${data.name}: NPC ${data.npc} options=${npc.options.contentToString()}"
                else -> null
            }
        }

        assertEquals(0, failures.size, failures.joinToString(separator = "; "))
        assertTrue(SummoningPouchData.values.size >= 78)
    }
}
