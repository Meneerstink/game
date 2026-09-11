package gg.rsmod.plugins.content.inter.emotes

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Q-025 (normal player controls: emotes) regression coverage.
 *
 * Every entry in [Emote] must resolve to a real, non-negative animation id, and - when it declares
 * a graphic at all (`gfx != -1` is the class's own "no graphic" sentinel) - a real, non-negative
 * graphic id. This is the enumerable-whole-set check the plan's R8 requires: a single bad id
 * anywhere in the 40+ emote roster fails this test and names the exact emote, instead of only one
 * exemplar being checked by hand.
 */
class EmoteDefinitionTests {
    companion object {
        /**
         * Emotes whose `anim == Anims.RESET` (-1) is a real, investigated gap rather than an
         * untouched default, each with a specific, named reason it is not a quick fix:
         *
         * - [Emote.SKILLCAPE]: Novite `EmotesManager.java` id==39 proves a real per-equipped-cape
         *   switch table (dozens of skillcapes, each its own anim+gfx pair) - sourceable, but a
         *   full table port, not a one-line id fix.
         * - [Emote.SEAL_OF_APPROVAL]: Novite id==52 proves a real 3-step sequence (anim 15104/gfx
         *   1287, then anim 15106 while the player's appearance is swapped to one of NPCs
         *   SEAL/SEAL_13256/SEAL_13257 - ids confirmed present in this cache's own `Npcs.kt`).
         *   Blocked on a real, missing engine capability: this codebase has no player-appearance-
         *   as-NPC transform (only [gg.rsmod.plugins.api.ext.PlayerExt.transformObject] for world
         *   objects) - building that is new engine work, not an audit-sized fix.
         * - [Emote.GIVE_THANKS]: searched Novite's `EmotesManager.java` in full - no "Give Thanks"
         *   emote exists in that rev-667 donor at all (its id 46 is "Turkey", a different emote),
         *   so there is no sourced anim/gfx to port without guessing (R6).
         *
         * Any other emote falling back to RESET is a real regression this test should catch.
         */
        val KNOWN_RESET_ONLY_EMOTES = setOf(Emote.SKILLCAPE, Emote.SEAL_OF_APPROVAL, Emote.GIVE_THANKS)
    }

    @Test
    fun `every emote has a real animation id, except the specific known-gap emotes`() {
        val bad = Emote.values.filter { it.anim < 0 && it !in KNOWN_RESET_ONLY_EMOTES }
        assertTrue(bad.isEmpty(), "Emotes with an unexplained invalid (negative) animation id: ${bad.map { it.name }}")
    }

    @Test
    fun `every emote that declares a graphic has a real graphic id`() {
        val bad = Emote.values.filter { it.gfx != -1 && it.gfx < 0 }
        assertTrue(bad.isEmpty(), "Emotes with an invalid (negative, non-sentinel) graphic id: ${bad.map { it.name }}")
    }

    @Test
    fun `every emote has a unique interface component`() {
        val duplicates =
            Emote.values
                .groupBy { it.component }
                .filterValues { it.size > 1 }
                .mapValues { (_, emotes) -> emotes.map { it.name } }
        assertTrue(duplicates.isEmpty(), "Emote component ids reused across multiple emotes: $duplicates")
    }
}
