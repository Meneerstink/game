package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.sync.block.UpdateBlockBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirement G9, across all 78 familiars: a combat familiar shows its authentic 2011 combat
 * level, a non-combat familiar shows none, and nothing shows level 0 or a placeholder.
 *
 * The owner's rule for this one is explicit - "Do not treat ledger data alone as verification.
 * Prove the actual client-facing NPC combat level is correct" - so these tests are written against
 * the two things that actually decide what a client draws:
 *
 *  1. the **production cache**, which is where the client gets its default from, and
 *  2. the **`COMBAT_LEVEL` extended-info block**, which is the only way a server can override it.
 *
 * What they cannot do is watch a pixel. The step that needs a human is confirming the number is
 * on screen; everything up to the packet is pinned here.
 */
class SummoningCombatLevelTests {
    @Test
    fun `the production cache gives every familiar no combat level of its own`() {
        /*
         * This is the fault, stated as a test. If a future cache import ever fills these in, this
         * fails and the override below becomes redundant rather than silently double-counting -
         * the client would take the cache value only when no block is sent, so the two must not
         * be allowed to drift apart unnoticed.
         */
        SummoningPouchData.values().forEach { pouch ->
            val def = definitions.get(NpcDef::class.java, pouch.npc)
            assertEquals(
                "${pouch.name} (npc ${pouch.npc}) now carries a cache combat level; " +
                    "SummoningCombatLevels was written on the basis that none of them do",
                0,
                def.combatLevel,
            )
        }
    }

    @Test
    fun `exactly the five foragers show no combat level, and every other familiar shows one`() {
        /*
         * The 78/78 statement of requirement G9. The Knowledge Base gives a level for 67 of the
         * 78; the six cockatrice variants derive theirs from their combat-identical twin; the
         * five pure foragers have none and must have none, because the article gives them no
         * level and they do not fight.
         *
         * Written as an exact set rather than as "some are missing" so that a familiar silently
         * losing its level - or gaining one it has no source for - fails here by name.
         */
        val uncovered = SummoningPouchData.values().filter { SummoningCombatLevels.levels[it] == null }.map { it.name }.toSet()
        assertEquals(
            "the set of familiars showing no combat level has changed",
            setOf("BEAVER", "FRUIT_BAT", "IBIS", "MACAW", "MAGPIE"),
            uncovered,
        )
        assertEquals(73, SummoningCombatLevels.levels.size)
    }

    @Test
    fun `no familiar with a combat level is one the sources say cannot fight`() {
        /*
         * The five familiars without a level are also the five with no sourced combat record at
         * all, so the two ways of asking the question have to agree. This is the guard against
         * the reverse fault - a level appearing on a familiar that has no combat data behind it.
         */
        val spurious =
            SummoningCombatLevels.levels.keys
                .filter { SummoningCombatDefinitions.getByNpc(it.npc)?.canFight != true }
                .map { it.name }
        assertTrue(
            "these familiars would advertise a combat level with no sourced combat data: $spurious",
            spurious.isEmpty(),
        )
    }

    @Test
    fun `the six cockatrice variants derive Spirit cockatrice's level from identical combat data`() {
        /*
         * The article does not cover the alternative hatchlings. Their levels are derived, not
         * invented, and the derivation is only legitimate while their sourced combat records are
         * genuinely identical to Spirit cockatrice's - so that identity is asserted here rather
         * than assumed. If any of them is ever given combat data of its own, this fails and the
         * variant needs a real source instead of an inherited number.
         */
        val base = SummoningCombatDefinitions.getByNpc(SummoningPouchData.SPIRIT_COCKATRICE.npc)
        assertNotNull("Spirit cockatrice has no sourced combat definition", base)
        val baseLevel = SummoningCombatLevels.levels[SummoningPouchData.SPIRIT_COCKATRICE]
        assertEquals("Spirit cockatrice's own level is not the article's", 64, baseLevel)

        val variants =
            listOf(
                SummoningPouchData.SPIRIT_SARATRICE,
                SummoningPouchData.SPIRIT_GUTHATRICE,
                SummoningPouchData.SPIRIT_ZAMATRICE,
                SummoningPouchData.SPIRIT_PENGATRICE,
                SummoningPouchData.SPIRIT_VULATRICE,
                SummoningPouchData.SPIRIT_CORAXATRICE,
            )
        variants.forEach { variant ->
            val definition = SummoningCombatDefinitions.getByNpc(variant.npc)
            assertNotNull("${variant.name} has no sourced combat definition", definition)
            listOf<Pair<String, (SummoningCombatDefinition) -> Any>>(
                "hitpoints" to { it.hitpoints },
                "attack" to { it.attack },
                "strength" to { it.strength },
                "defence" to { it.defence },
                "ranged" to { it.ranged },
                "magic" to { it.magic },
                "style" to { it.style },
                "assistMode" to { it.assistMode },
                "attackRange" to { it.attackRange },
                "attackSpeed" to { it.attackSpeed },
                "maxHit" to { it.maxHit },
                "attackAnimation" to { it.attackAnimation },
                "blockAnimation" to { it.blockAnimation },
                "deathAnimation" to { it.deathAnimation },
            ).forEach { (field, read) ->
                assertEquals(
                    "${variant.name} differs from Spirit cockatrice in $field, so it may not inherit its level",
                    read(base!!),
                    read(definition!!),
                )
            }
            assertEquals(
                "${variant.name} does not inherit Spirit cockatrice's combat level",
                baseLevel,
                SummoningCombatLevels.levels[variant],
            )
        }
    }

    @Test
    fun `no level is zero, negative, or outside what the update block can carry`() {
        SummoningCombatLevels.levels.forEach { (pouch, level) ->
            assertTrue("${pouch.name} has a non-positive combat level ($level)", level > 0)
            /*
             * The block is a single unsigned short and the client reads 65535 as "use the cache
             * value", so that exact value would silently mean the opposite of what was intended.
             */
            assertTrue(
                "${pouch.name}'s level ($level) does not fit the COMBAT_LEVEL update block",
                level < UpdateBlockBuffer.CACHE_COMBAT_LEVEL,
            )
        }
    }

    @Test
    fun `the three familiars the Knowledge Base names differently are still covered`() {
        // The article says "Mosquito", "Phoenix essence" and "Vampire bat"; the roster says
        // SPIRIT_MOSQUITO, PHOENIX and VAMPYRE_BAT. A spelling-based import would have dropped
        // all three silently, so they are asserted by hand against the article's own numbers.
        assertEquals(32, SummoningCombatLevels.levels[SummoningPouchData.SPIRIT_MOSQUITO])
        assertEquals(124, SummoningCombatLevels.levels[SummoningPouchData.PHOENIX])
        assertEquals(44, SummoningCombatLevels.levels[SummoningPouchData.VAMPYRE_BAT])
    }

    @Test
    fun `the regression examples carry the levels the Knowledge Base states`() {
        assertEquals(230, SummoningCombatLevels.levels[SummoningPouchData.STEEL_TITAN])
        assertEquals(26, SummoningCombatLevels.levels[SummoningPouchData.SPIRIT_WOLF])
        assertEquals(26, SummoningCombatLevels.levels[SummoningPouchData.DREADFOWL])
        /*
         * Pack yak and Unicorn stallion do have levels, and this is the correction to an
         * assumption worth recording: the article says they "will only fight to defend
         * themselves", which reads like "not a combat familiar" but is not the same thing. It
         * still states a level for each, and RuneScape still displayed it, so they show one.
         */
        assertEquals(175, SummoningCombatLevels.levels[SummoningPouchData.PACK_YAK])
        assertEquals(70, SummoningCombatLevels.levels[SummoningPouchData.UNICORN_STALLION])
        // The five pure foragers genuinely have none.
        assertNull(SummoningCombatLevels.levels[SummoningPouchData.BEAVER])
        assertNull(SummoningCombatLevels.levels[SummoningPouchData.MACAW])
    }

    @Test
    fun `lookup by npc id agrees with lookup by pouch for all 78`() {
        SummoningPouchData.values().forEach { pouch ->
            assertEquals(
                "SummoningCombatLevels.forNpc disagrees with the table for ${pouch.name}",
                SummoningCombatLevels.levels[pouch],
                SummoningCombatLevels.forNpc(pouch.npc),
            )
        }
    }

    @Test
    fun `every level is quoted verbatim from the 2011 Knowledge Base article`() {
        /*
         * The whole table is re-extracted from the archived article on every run, so a value can
         * never be edited in the source without the article agreeing. The article states the level
         * inline in each familiar's abilities cell, in two shapes: "Fights (Level 230)" and
         * "Fights (Level 25 - Controlled)" where it also names the attack style. Matching only the
         * first shape is exactly the mistake that made this table look 17 entries shorter than it
         * is, so the closing bracket is deliberately not part of the pattern.
         */
        val article = java.io.File("src/test/resources/2011RS_SUMMONING_FAMILIARS.md")
        assertTrue("the Knowledge Base article is missing: ${article.absolutePath}", article.exists())

        val text = article.readText()
        val quoted = LinkedHashMap<String, Int>()
        var pending: String? = null
        Regex("""familiars/([a-z_0-9]+)\.gif|Fights \(Level (\d+)""").findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val level = match.groupValues[2]
            if (name.isNotEmpty()) {
                pending = name
            } else if (pending != null) {
                quoted[pending!!] = level.toInt()
                pending = null
            }
        }
        assertEquals("the article no longer yields the expected number of combat levels", 67, quoted.size)

        // Every level the article states is carried, unchanged.
        quoted.forEach { (articleName, level) ->
            val pouch = pouchFor(articleName)
            assertNotNull("the article names a familiar the roster does not have: $articleName", pouch)
            assertEquals(
                "${pouch!!.name} does not carry the level the article states",
                level,
                SummoningCombatLevels.levels[pouch],
            )
        }

        // ...and nothing carries a level the article does not state, except the six cockatrice
        // variants, which are derived from a combat-identical twin by the test above.
        val derived = SummoningCombatLevels.levels.keys.filter { pouchFor(it) !in quoted.keys }.map { it.name }.toSet()
        assertEquals(
            "a familiar carries a combat level that is neither quoted nor derived",
            setOf(
                "SPIRIT_SARATRICE",
                "SPIRIT_GUTHATRICE",
                "SPIRIT_ZAMATRICE",
                "SPIRIT_PENGATRICE",
                "SPIRIT_VULATRICE",
                "SPIRIT_CORAXATRICE",
            ),
            derived,
        )
    }

    /** The article's own name for [pouch]. */
    private fun pouchFor(pouch: SummoningPouchData): String = ARTICLE_NAMES[pouch] ?: pouch.name.lowercase()

    /** The roster entry the article's [articleName] refers to. */
    private fun pouchFor(articleName: String): SummoningPouchData? =
        SummoningPouchData.values().firstOrNull { pouchFor(it) == articleName }

    private val definitions: gg.rsmod.game.fs.DefinitionSet get() = SummoningTestCache.definitions

    companion object {
        /** The three familiars whose Knowledge Base name is not their roster name, lowercased. */
        private val ARTICLE_NAMES =
            mapOf(
                SummoningPouchData.SPIRIT_MOSQUITO to "mosquito",
                SummoningPouchData.PHOENIX to "phoenix_essence",
                SummoningPouchData.VAMPYRE_BAT to "vampire_bat",
            )
    }
}
