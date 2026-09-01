package gg.rsmod.plugins.content.skills.summoning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the whole 78-familiar roster to the revision-era "Summoning - Familiars" knowledge-base
 * table (summon level, then familiar duration in minutes). Two rows were wrong before this table
 * existed - spirit graahk was carrying 57 minutes and abyssal titan 93, in both cases the
 * familiar's own summon level copied into the duration column by mistake.
 */
class SummoningLedgerTests {
    private val sourcedLevelAndDuration: Map<SummoningPouchData, Pair<Int, Int>> =
        mapOf(
            SummoningPouchData.SPIRIT_WOLF to (1 to 6),
            SummoningPouchData.DREADFOWL to (4 to 4),
            SummoningPouchData.SPIRIT_SPIDER to (10 to 15),
            SummoningPouchData.THORNY_SNAIL to (13 to 16),
            SummoningPouchData.GRANITE_CRAB to (16 to 18),
            SummoningPouchData.SPIRIT_MOSQUITO to (17 to 12),
            SummoningPouchData.DESERT_WYRM to (18 to 19),
            SummoningPouchData.SPIRIT_SCORPION to (19 to 17),
            SummoningPouchData.SPIRIT_TZ_KIH to (22 to 18),
            SummoningPouchData.ALBINO_RAT to (23 to 22),
            SummoningPouchData.SPIRIT_KALPHITE to (25 to 22),
            SummoningPouchData.COMPOST_MOUND to (28 to 24),
            SummoningPouchData.GIANT_CHINCHOMPA to (29 to 31),
            SummoningPouchData.VAMPYRE_BAT to (31 to 33),
            SummoningPouchData.HONEY_BADGER to (32 to 25),
            SummoningPouchData.BEAVER to (33 to 27),
            SummoningPouchData.VOID_RAVAGER to (34 to 27),
            SummoningPouchData.VOID_SHIFTER to (34 to 94),
            SummoningPouchData.VOID_SPINNER to (34 to 27),
            SummoningPouchData.VOID_TORCHER to (34 to 94),
            SummoningPouchData.BRONZE_MINOTAUR to (36 to 30),
            SummoningPouchData.BULL_ANT to (40 to 30),
            SummoningPouchData.MACAW to (41 to 31),
            SummoningPouchData.EVIL_TURNIP to (42 to 30),
            SummoningPouchData.SPIRIT_COCKATRICE to (43 to 36),
            SummoningPouchData.SPIRIT_GUTHATRICE to (43 to 36),
            SummoningPouchData.SPIRIT_SARATRICE to (43 to 36),
            SummoningPouchData.SPIRIT_ZAMATRICE to (43 to 36),
            SummoningPouchData.SPIRIT_PENGATRICE to (43 to 36),
            SummoningPouchData.SPIRIT_CORAXATRICE to (43 to 36),
            SummoningPouchData.SPIRIT_VULATRICE to (43 to 36),
            SummoningPouchData.IRON_MINOTAUR to (46 to 37),
            SummoningPouchData.PYRELORD to (46 to 32),
            SummoningPouchData.MAGPIE to (47 to 34),
            SummoningPouchData.BLOATED_LEECH to (49 to 34),
            SummoningPouchData.SPIRIT_TERRORBIRD to (52 to 36),
            SummoningPouchData.ABYSSAL_PARASITE to (54 to 30),
            SummoningPouchData.SPIRIT_JELLY to (55 to 43),
            SummoningPouchData.IBIS to (56 to 38),
            SummoningPouchData.STEEL_MINOTAUR to (56 to 46),
            SummoningPouchData.SPIRIT_GRAAHK to (57 to 49),
            SummoningPouchData.SPIRIT_KYATT to (57 to 49),
            SummoningPouchData.SPIRIT_LARUPIA to (57 to 49),
            SummoningPouchData.KARAMTHULHU_OVERLORD to (58 to 44),
            SummoningPouchData.SMOKE_DEVIL to (61 to 48),
            SummoningPouchData.ABYSSAL_LURKER to (62 to 41),
            SummoningPouchData.SPIRIT_COBRA to (63 to 56),
            SummoningPouchData.STRANGER_PLANT to (64 to 49),
            SummoningPouchData.BARKER_TOAD to (66 to 8),
            SummoningPouchData.MITHRIL_MINOTAUR to (66 to 55),
            SummoningPouchData.WAR_TORTOISE to (67 to 43),
            SummoningPouchData.BUNYIP to (68 to 44),
            SummoningPouchData.FRUIT_BAT to (69 to 45),
            SummoningPouchData.RAVENOUS_LOCUST to (70 to 24),
            SummoningPouchData.ARCTIC_BEAR to (71 to 28),
            SummoningPouchData.PHOENIX to (72 to 30),
            SummoningPouchData.OBSIDIAN_GOLEM to (73 to 55),
            SummoningPouchData.GRANITE_LOBSTER to (74 to 47),
            SummoningPouchData.PRAYING_MANTIS to (75 to 69),
            SummoningPouchData.FORGE_REGENT to (76 to 45),
            SummoningPouchData.ADAMANT_MINOTAUR to (76 to 66),
            SummoningPouchData.TALON_BEAST to (77 to 49),
            SummoningPouchData.GIANT_ENT to (78 to 49),
            SummoningPouchData.FIRE_TITAN to (79 to 62),
            SummoningPouchData.ICE_TITAN to (79 to 64),
            SummoningPouchData.MOSS_TITAN to (79 to 58),
            SummoningPouchData.HYDRA to (80 to 49),
            SummoningPouchData.SPIRIT_DAGANNOTH to (83 to 57),
            SummoningPouchData.LAVA_TITAN to (83 to 61),
            SummoningPouchData.SWAMP_TITAN to (85 to 56),
            SummoningPouchData.RUNE_MINOTAUR to (86 to 151),
            SummoningPouchData.UNICORN_STALLION to (88 to 54),
            SummoningPouchData.GEYSER_TITAN to (89 to 69),
            SummoningPouchData.WOLPERTINGER to (92 to 62),
            SummoningPouchData.ABYSSAL_TITAN to (93 to 32),
            SummoningPouchData.IRON_TITAN to (95 to 60),
            SummoningPouchData.PACK_YAK to (96 to 58),
            SummoningPouchData.STEEL_TITAN to (99 to 64),
        )

    @Test
    fun `every familiar keeps its sourced summon level and duration`() {
        assertEquals(78, SummoningPouchData.values.size)
        assertEquals(78, sourcedLevelAndDuration.size)
        SummoningPouchData.values.forEach { pouch ->
            val (level, duration) = sourcedLevelAndDuration.getValue(pouch)
            assertEquals(level, pouch.level, "${pouch.name} summon level")
            assertEquals(duration, SummoningFamiliarDefinitions.get(pouch).durationMinutes, "${pouch.name} duration")
        }
    }

    @Test
    fun `pouch, scroll and combat transform relationships hold`() {
        SummoningLedger.validate()
    }

    @Test
    fun `only the four sourced familiars fight purely defensively`() {
        val defensive =
            SummoningPouchData.values
                .filter { SummoningCombatDefinitions.get(it).assistMode == FamiliarAssistMode.DEFENSIVE_ONLY }
                .toSet()
        assertEquals(
            setOf(
                SummoningPouchData.PACK_YAK,
                SummoningPouchData.UNICORN_STALLION,
                SummoningPouchData.BUNYIP,
                SummoningPouchData.VOID_SPINNER,
            ),
            defensive,
        )
    }

    @Test
    fun `every fighting familiar transforms into its own base npc plus one`() {
        val fighting = SummoningPouchData.values.filter { SummoningCombatDefinitions.get(it).canFight }
        assertEquals(73, fighting.size)
        assertTrue(fighting.all { SummoningCombatDefinitions.get(it).combatNpc == it.npc + 1 })
    }
}
