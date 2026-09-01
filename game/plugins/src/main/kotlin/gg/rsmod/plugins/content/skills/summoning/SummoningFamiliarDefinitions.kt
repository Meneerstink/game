package gg.rsmod.plugins.content.skills.summoning

/**
 * Revision-667 familiar constants cross-checked against the 2011 Knowledge Base roster.
 * Duration is expressed in 600 ms server cycles. Summoning points are the one-time
 * pouch cost; their lifecycle is independent of a familiar timer.
 */
data class SummoningFamiliarDefinition(val summonPoints: Int, val durationMinutes: Int)

object SummoningFamiliarDefinitions {
    private val byPouch = mapOf(
        SummoningPouchData.SPIRIT_WOLF to SummoningFamiliarDefinition(1, 6),
        SummoningPouchData.DREADFOWL to SummoningFamiliarDefinition(1, 4),
        SummoningPouchData.SPIRIT_SPIDER to SummoningFamiliarDefinition(2, 15),
        SummoningPouchData.THORNY_SNAIL to SummoningFamiliarDefinition(2, 16),
        SummoningPouchData.GRANITE_CRAB to SummoningFamiliarDefinition(2, 18),
        SummoningPouchData.SPIRIT_MOSQUITO to SummoningFamiliarDefinition(2, 12),
        SummoningPouchData.DESERT_WYRM to SummoningFamiliarDefinition(1, 19),
        SummoningPouchData.SPIRIT_SCORPION to SummoningFamiliarDefinition(2, 17),
        SummoningPouchData.SPIRIT_TZ_KIH to SummoningFamiliarDefinition(3, 18),
        SummoningPouchData.ALBINO_RAT to SummoningFamiliarDefinition(3, 22),
        SummoningPouchData.SPIRIT_KALPHITE to SummoningFamiliarDefinition(3, 22),
        SummoningPouchData.COMPOST_MOUND to SummoningFamiliarDefinition(6, 24),
        SummoningPouchData.GIANT_CHINCHOMPA to SummoningFamiliarDefinition(1, 31),
        SummoningPouchData.VAMPYRE_BAT to SummoningFamiliarDefinition(4, 33),
        SummoningPouchData.HONEY_BADGER to SummoningFamiliarDefinition(4, 25),
        SummoningPouchData.BEAVER to SummoningFamiliarDefinition(4, 27),
        SummoningPouchData.VOID_RAVAGER to SummoningFamiliarDefinition(4, 27),
        SummoningPouchData.VOID_SHIFTER to SummoningFamiliarDefinition(4, 94),
        SummoningPouchData.VOID_SPINNER to SummoningFamiliarDefinition(4, 27),
        SummoningPouchData.VOID_TORCHER to SummoningFamiliarDefinition(4, 94),
        SummoningPouchData.BRONZE_MINOTAUR to SummoningFamiliarDefinition(9, 30),
        SummoningPouchData.BULL_ANT to SummoningFamiliarDefinition(5, 30),
        SummoningPouchData.MACAW to SummoningFamiliarDefinition(5, 31),
        SummoningPouchData.EVIL_TURNIP to SummoningFamiliarDefinition(5, 30),
        SummoningPouchData.SPIRIT_COCKATRICE to SummoningFamiliarDefinition(5, 36),
        SummoningPouchData.SPIRIT_GUTHATRICE to SummoningFamiliarDefinition(5, 36),
        SummoningPouchData.SPIRIT_SARATRICE to SummoningFamiliarDefinition(5, 36),
        SummoningPouchData.SPIRIT_ZAMATRICE to SummoningFamiliarDefinition(5, 36),
        SummoningPouchData.SPIRIT_PENGATRICE to SummoningFamiliarDefinition(5, 36),
        SummoningPouchData.SPIRIT_CORAXATRICE to SummoningFamiliarDefinition(5, 36),
        SummoningPouchData.SPIRIT_VULATRICE to SummoningFamiliarDefinition(5, 36),
        SummoningPouchData.IRON_MINOTAUR to SummoningFamiliarDefinition(9, 37),
        SummoningPouchData.PYRELORD to SummoningFamiliarDefinition(5, 32),
        SummoningPouchData.MAGPIE to SummoningFamiliarDefinition(5, 34),
        SummoningPouchData.BLOATED_LEECH to SummoningFamiliarDefinition(5, 34),
        SummoningPouchData.SPIRIT_TERRORBIRD to SummoningFamiliarDefinition(6, 36),
        SummoningPouchData.ABYSSAL_PARASITE to SummoningFamiliarDefinition(6, 30),
        SummoningPouchData.SPIRIT_JELLY to SummoningFamiliarDefinition(6, 43),
        SummoningPouchData.IBIS to SummoningFamiliarDefinition(6, 38),
        SummoningPouchData.STEEL_MINOTAUR to SummoningFamiliarDefinition(9, 46),
        SummoningPouchData.SPIRIT_GRAAHK to SummoningFamiliarDefinition(6, 57),
        SummoningPouchData.SPIRIT_KYATT to SummoningFamiliarDefinition(6, 49),
        SummoningPouchData.SPIRIT_LARUPIA to SummoningFamiliarDefinition(6, 49),
        SummoningPouchData.KARAMTHULHU_OVERLORD to SummoningFamiliarDefinition(6, 44),
        SummoningPouchData.SMOKE_DEVIL to SummoningFamiliarDefinition(7, 48),
        SummoningPouchData.ABYSSAL_LURKER to SummoningFamiliarDefinition(7, 41),
        SummoningPouchData.SPIRIT_COBRA to SummoningFamiliarDefinition(7, 56),
        SummoningPouchData.STRANGER_PLANT to SummoningFamiliarDefinition(7, 49),
        SummoningPouchData.BARKER_TOAD to SummoningFamiliarDefinition(7, 8),
        SummoningPouchData.MITHRIL_MINOTAUR to SummoningFamiliarDefinition(9, 55),
        SummoningPouchData.WAR_TORTOISE to SummoningFamiliarDefinition(7, 43),
        SummoningPouchData.BUNYIP to SummoningFamiliarDefinition(7, 44),
        SummoningPouchData.FRUIT_BAT to SummoningFamiliarDefinition(7, 45),
        SummoningPouchData.RAVENOUS_LOCUST to SummoningFamiliarDefinition(4, 24),
        SummoningPouchData.ARCTIC_BEAR to SummoningFamiliarDefinition(8, 28),
        SummoningPouchData.PHOENIX to SummoningFamiliarDefinition(8, 30),
        SummoningPouchData.OBSIDIAN_GOLEM to SummoningFamiliarDefinition(8, 55),
        SummoningPouchData.GRANITE_LOBSTER to SummoningFamiliarDefinition(8, 47),
        SummoningPouchData.PRAYING_MANTIS to SummoningFamiliarDefinition(8, 69),
        SummoningPouchData.FORGE_REGENT to SummoningFamiliarDefinition(9, 45),
        SummoningPouchData.ADAMANT_MINOTAUR to SummoningFamiliarDefinition(9, 66),
        SummoningPouchData.TALON_BEAST to SummoningFamiliarDefinition(9, 49),
        SummoningPouchData.GIANT_ENT to SummoningFamiliarDefinition(8, 49),
        SummoningPouchData.FIRE_TITAN to SummoningFamiliarDefinition(9, 62),
        SummoningPouchData.ICE_TITAN to SummoningFamiliarDefinition(9, 64),
        SummoningPouchData.MOSS_TITAN to SummoningFamiliarDefinition(9, 58),
        SummoningPouchData.HYDRA to SummoningFamiliarDefinition(8, 49),
        SummoningPouchData.SPIRIT_DAGANNOTH to SummoningFamiliarDefinition(9, 57),
        SummoningPouchData.LAVA_TITAN to SummoningFamiliarDefinition(9, 61),
        SummoningPouchData.SWAMP_TITAN to SummoningFamiliarDefinition(9, 56),
        SummoningPouchData.RUNE_MINOTAUR to SummoningFamiliarDefinition(9, 151),
        SummoningPouchData.UNICORN_STALLION to SummoningFamiliarDefinition(9, 54),
        SummoningPouchData.GEYSER_TITAN to SummoningFamiliarDefinition(10, 69),
        SummoningPouchData.WOLPERTINGER to SummoningFamiliarDefinition(10, 62),
        SummoningPouchData.ABYSSAL_TITAN to SummoningFamiliarDefinition(10, 93),
        SummoningPouchData.IRON_TITAN to SummoningFamiliarDefinition(10, 60),
        SummoningPouchData.PACK_YAK to SummoningFamiliarDefinition(10, 58),
        SummoningPouchData.STEEL_TITAN to SummoningFamiliarDefinition(10, 64),
    )

    fun get(data: SummoningPouchData): SummoningFamiliarDefinition =
        requireNotNull(byPouch[data]) { "Missing Summoning definition for ${data.name}" }

    fun validate(): Unit {
        check(byPouch.keys == SummoningPouchData.values.toSet()) { "Summoning familiar ledger must cover every pouch exactly once." }
        check(byPouch.values.all { it.summonPoints > 0 && it.durationMinutes > 0 }) { "Summoning ledger contains an invalid cost or duration." }
    }
}
