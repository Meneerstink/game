package gg.rsmod.plugins.content.skills.summoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the sourced classification half of the 78-familiar ledger.
 *
 * [SummoningLedger.validate] already proves the catalogue agrees with the combat and container
 * tables, and it runs on world init so a broken relationship fails the server rather than a test.
 * What it cannot prove is that the catalogue still says what the source said, so the counts and
 * membership pinned here are read straight off the revision-667-era knowledge base familiars
 * table.
 */
class SummoningCatalogueTests {
    @Test
    fun `the ledger relationships hold`() {
        SummoningLedger.validate()
    }

    @Test
    fun `every pouch has exactly one catalogue row`() {
        assertEquals(78, SummoningPouchData.values.size)
        assertEquals(78, SummoningCatalogue.byPouch.size)
        SummoningPouchData.values.forEach { pouch ->
            assertEquals(pouch, SummoningCatalogue[pouch].pouch)
        }
    }

    /**
     * Beaver, macaw, magpie, ibis and fruit bat are the only familiars the knowledge base gives no
     * "Fights" line, so they are the only ones that credit no combat skill.
     */
    @Test
    fun `only the five pure foragers cannot fight`() {
        val peaceful = SummoningCatalogue.byPouch.values.filterNot { it.canFight }.map { it.pouch }.toSet()
        assertEquals(
            setOf(
                SummoningPouchData.BEAVER,
                SummoningPouchData.MACAW,
                SummoningPouchData.MAGPIE,
                SummoningPouchData.IBIS,
                SummoningPouchData.FRUIT_BAT,
            ),
            peaceful,
        )
        peaceful.forEach { pouch ->
            assertEquals(FamiliarSkillFocus.NONE, SummoningCatalogue[pouch].skillFocus)
        }
    }

    /**
     * The nine beasts of burden and their exact sourced capacities. The three abyssal familiars
     * carry 7 essence each in this revision - the larger modern numbers date from 2014.
     */
    @Test
    fun `beasts of burden carry their sourced capacities`() {
        val expected =
            mapOf(
                SummoningPouchData.THORNY_SNAIL to (3 to false),
                SummoningPouchData.SPIRIT_KALPHITE to (6 to false),
                SummoningPouchData.BULL_ANT to (9 to false),
                SummoningPouchData.SPIRIT_TERRORBIRD to (12 to false),
                SummoningPouchData.ABYSSAL_PARASITE to (7 to true),
                SummoningPouchData.ABYSSAL_LURKER to (7 to true),
                SummoningPouchData.WAR_TORTOISE to (18 to false),
                SummoningPouchData.ABYSSAL_TITAN to (7 to true),
                SummoningPouchData.PACK_YAK to (30 to false),
            )
        val actual =
            SummoningCatalogue.inCategory(FamiliarCategory.BEAST_OF_BURDEN).associate {
                it.pouch to (it.inventory.capacity to it.inventory.essenceOnly)
            }
        assertEquals(expected, actual)
    }

    /**
     * Foragers are sourced as 30-slot withdraw-only stores. The seven -atrice familiars share one
     * knowledge base row, which is why the count is higher than the number of table rows.
     */
    @Test
    fun `every forager is a thirty slot store`() {
        val foragers = SummoningCatalogue.inCategory(FamiliarCategory.FORAGER)
        assertEquals(22, foragers.size)
        foragers.forEach { entry ->
            assertEquals("${entry.pouch.name} forager capacity", 30, entry.inventory.capacity)
            assertEquals(FamiliarInventoryKind.FORAGER, entry.inventory.kind)
        }
    }

    /**
     * The skill focus is what the owner is credited in, and it is not derivable from the attack
     * style: these six fight in melee animation but credit Ranged or Magic experience.
     */
    @Test
    fun `skill focus is independent of attack style`() {
        listOf(
            SummoningPouchData.DREADFOWL to FamiliarSkillFocus.MAGIC,
            SummoningPouchData.GIANT_CHINCHOMPA to FamiliarSkillFocus.RANGED,
            SummoningPouchData.VOID_TORCHER to FamiliarSkillFocus.MAGIC,
            SummoningPouchData.EVIL_TURNIP to FamiliarSkillFocus.RANGED,
            SummoningPouchData.FORGE_REGENT to FamiliarSkillFocus.RANGED,
            SummoningPouchData.FIRE_TITAN to FamiliarSkillFocus.MAGIC,
        ).forEach { (pouch, focus) ->
            assertEquals(pouch.name, focus, SummoningCatalogue[pouch].skillFocus)
            assertEquals(
                "${pouch.name} attack style",
                FamiliarAttackStyle.MELEE,
                SummoningCombatDefinitions.get(pouch).style,
            )
        }
    }

    @Test
    fun `the four defensive only familiars are healers or carriers`() {
        setOf(
            SummoningPouchData.PACK_YAK,
            SummoningPouchData.UNICORN_STALLION,
            SummoningPouchData.BUNYIP,
            SummoningPouchData.VOID_SPINNER,
        ).forEach { pouch ->
            val entry = SummoningCatalogue[pouch]
            assertTrue(
                "${pouch.name} should be a healer or a beast of burden",
                entry.isIn(FamiliarCategory.HEALER) || entry.isIn(FamiliarCategory.BEAST_OF_BURDEN),
            )
            assertEquals(
                FamiliarAssistMode.DEFENSIVE_ONLY,
                SummoningCombatDefinitions.get(pouch).assistMode,
            )
        }
    }
}
