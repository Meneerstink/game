package gg.rsmod.plugins.content.skills.summoning

/** Production combat ledger for the 78 revision-667 familiars.
 * Target pouch/base NPC data comes from the local revision-667 cache-backed roster.
 * Combat candidates were cross-checked against the preserved January-2011 revision-634 data.
 */
enum class FamiliarAttackStyle { NONE, MELEE, RANGED, MAGIC }
enum class FamiliarAssistMode { NONE, ASSIST, DEFENSIVE_ONLY }

data class SummoningCombatDefinition(
    val pouch: SummoningPouchData,
    val combatNpc: Int?,
    val hitpoints: Int,
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val ranged: Int,
    val magic: Int,
    val style: FamiliarAttackStyle,
    val assistMode: FamiliarAssistMode,
    val attackRange: Int,
    val attackSpeed: Int,
    val maxHit: Int,
    val attackAnimation: Int,
    val blockAnimation: Int,
    val deathAnimation: Int,
    val attackGraphic: Int,
    val projectile: Int,
) {
    val canFight: Boolean get() = style != FamiliarAttackStyle.NONE
    val isExecutable: Boolean get() = canFight && attackAnimation >= 0 && deathAnimation >= 0
}

object SummoningCombatDefinitions {
    private val definitions = listOf(
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_WOLF, 6830, 150, 10, 10,
            10, 10, 10, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 40,
            8292, 6557, 8295, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.DREADFOWL, 6826, 160, 15, 15,
            10, 10, 15, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 40,
            7810, 5388, 5389, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_SPIDER, 6842, 180, 15, 0,
            15, 0, 0, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 30,
            5327, 5328, 5329, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.THORNY_SNAIL, 6807, 280, 1, 1,
            15, 15, 1, FamiliarAttackStyle.RANGED,
            FamiliarAssistMode.ASSIST, 7, 4, 40,
            8143, 8145, 8143, 1379, 1380,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.GRANITE_CRAB, 6797, 160, 15, 0,
            15, 0, 0, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 40,
            8104, 8105, 8106, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_MOSQUITO, 7332, 430, 5, 1,
            45, 1, 1, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 42,
            8032, 8034, 8033, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.DESERT_WYRM, 6832, 470, 20, 20,
            20, 20, 20, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 42,
            7795, 7796, 7797, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_SCORPION, 6838, 670, 20, 20,
            20, 20, 20, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 65,
            6254, 6255, 6256, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_TZ_KIH, 7362, 630, 20, 20,
            20, 20, 20, FamiliarAttackStyle.MAGIC,
            FamiliarAssistMode.ASSIST, 7, 4, 50,
            8257, 8256, 8258, 1422, 1423,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.ALBINO_RAT, 6848, 680, 22, 22,
            22, 22, 22, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 50,
            -1, 14861, -1, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_KALPHITE, 6995, 770, 25, 25,
            25, 25, 25, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 50,
            8519, 8518, 8517, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.COMPOST_MOUND, 6872, 930, 25, 25,
            25, 25, 25, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 37,
            7769, 7771, 7770, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.GIANT_CHINCHOMPA, 7354, 970, 25, 25,
            25, 25, 25, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 38,
            7755, 7753, 7758, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.VAMPYRE_BAT, 6836, 1050, 30, 30,
            30, 30, 30, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 40,
            4915, 4916, 4917, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.HONEY_BADGER, 6846, 1100, 29, 30,
            26, 29, 29, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 41,
            7928, 7927, 7925, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.BEAVER, null, 0, 0, 0,
            0, 0, 0, FamiliarAttackStyle.NONE,
            FamiliarAssistMode.NONE, 0, 0, 0,
            -1, -1, -1, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.VOID_RAVAGER, 7371, 1210, 31, 30,
            28, 31, 31, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 44,
            8086, 8088, 8087, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.VOID_SHIFTER, 7368, 1210, 31, 30,
            28, 31, 31, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 44,
            8131, 8132, 8133, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.VOID_SPINNER, 7334, 590, 31, 30,
            28, 31, 31, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.DEFENSIVE_ONLY, 1, 4, 44,
            8172, 8173, 8176, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.VOID_TORCHER, 7352, 1210, 31, 30,
            28, 31, 31, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 44,
            8235, 8237, 8236, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.BRONZE_MINOTAUR, 6854, 1330, 33, 30,
            28, 33, 33, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 47,
            8024, 8023, 8025, 1498, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.BULL_ANT, 6868, 1540, 36, 30,
            32, 36, 36, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 52,
            7896, 7900, 7897, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.MACAW, null, 0, 0, 0,
            0, 0, 0, FamiliarAttackStyle.NONE,
            FamiliarAssistMode.NONE, 0, 0, 0,
            -1, -1, -1, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.EVIL_TURNIP, 6834, 1670, 38, 30,
            34, 28, 34, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 58,
            8248, 8249, 8250, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_COCKATRICE, 6876, 1730, 39, 35,
            35, 39, 39, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 56,
            7762, 7761, 7763, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_GUTHATRICE, 6878, 1730, 39, 35,
            35, 39, 39, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 56,
            7762, 7761, 7763, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_SARATRICE, 6880, 1730, 39, 35,
            35, 39, 39, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 56,
            7762, 7761, 7763, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_ZAMATRICE, 6882, 1730, 39, 35,
            35, 39, 39, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 56,
            7762, 7761, 7763, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_PENGATRICE, 6884, 1730, 39, 35,
            35, 39, 39, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 56,
            7762, 7761, 7763, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_CORAXATRICE, 6886, 1730, 39, 35,
            35, 39, 39, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 56,
            7762, 7761, 7763, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_VULATRICE, 6888, 1730, 39, 35,
            35, 39, 39, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 56,
            7762, 7761, 7763, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.IRON_MINOTAUR, 6856, 1930, 42, 35,
            37, 42, 42, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 60,
            8024, 8023, 8025, 1498, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.PYRELORD, 7378, 1930, 60, 40,
            30, 1, 1, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 60,
            8080, 8079, 8078, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.MAGPIE, null, 0, 0, 0,
            0, 0, 0, FamiliarAttackStyle.NONE,
            FamiliarAssistMode.NONE, 0, 0, 0,
            -1, -1, -1, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.BLOATED_LEECH, 6844, 2110, 45, 35,
            40, 45, 45, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 65,
            7657, 7655, 7656, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_TERRORBIRD, 6795, 2330, 47, 35,
            37, 47, 47, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 68,
            1010, 1011, 1012, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.ABYSSAL_PARASITE, 6819, 2340, 49, 35,
            44, 49, 49, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 70,
            8910, 7670, 7671, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_JELLY, 6993, 2550, 50, 35,
            44, 50, 50, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 72,
            8569, 8571, 8570, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.IBIS, null, 0, 0, 0,
            0, 0, 0, FamiliarAttackStyle.NONE,
            FamiliarAssistMode.NONE, 0, 0, 0,
            -1, -1, -1, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.STEEL_MINOTAUR, 6858, 2600, 51, 35,
            44, 51, 51, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 73,
            8024, 8023, 8025, 1498, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_GRAAHK, 7364, 2680, 52, 35,
            46, 52, 52, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 75,
            5229, 5227, 5230, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_KYATT, 7366, 2680, 52, 35,
            46, 52, 52, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 75,
            5229, 5227, 5230, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_LARUPIA, 7338, 2680, 52, 35,
            46, 52, 52, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 75,
            7018, 7017, 7016, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.KARAMTHULHU_OVERLORD, 6810, 2760, 53, 35,
            47, 53, 53, FamiliarAttackStyle.RANGED,
            FamiliarAssistMode.ASSIST, 7, 4, 76,
            7970, 7962, 7964, 1474, 1477,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SMOKE_DEVIL, 6866, 3000, 55, 35,
            49, 55, 55, FamiliarAttackStyle.MAGIC,
            FamiliarAssistMode.ASSIST, 7, 4, 150,
            7816, 7817, 7818, -1, 1376,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.ABYSSAL_LURKER, 6821, 3080, 56, 35,
            50, 56, 56, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 80,
            2019, 2020, 2021, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_COBRA, 6803, 3140, 57, 35,
            57, 57, 57, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 82,
            8152, 8154, 8153, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.STRANGER_PLANT, 6828, 3220, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 103,
            8208, -1, 8209, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.BARKER_TOAD, 6890, 3400, 60, 35,
            53, 60, 60, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 86,
            7260, 7257, 7256, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.MITHRIL_MINOTAUR, 6860, 3400, 60, 35,
            53, 60, 60, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 110,
            8024, 8023, 8025, 1498, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.WAR_TORTOISE, 6816, 3480, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 87,
            8286, 8287, 8285, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.BUNYIP, 6814, 400, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.DEFENSIVE_ONLY, 1, 4, 89,
            7741, 7739, 7740, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.FRUIT_BAT, null, 0, 0, 0,
            0, 0, 0, FamiliarAttackStyle.NONE,
            FamiliarAssistMode.NONE, 0, 0, 0,
            -1, -1, -1, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.RAVENOUS_LOCUST, 7373, 3700, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 90,
            7994, 7995, 7996, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.ARCTIC_BEAR, 6840, 3810, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 92,
            4925, 4928, 4929, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.PHOENIX, 8576, 1530, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MAGIC,
            FamiliarAssistMode.ASSIST, 7, 4, 160,
            11093, 11107, 11108, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.OBSIDIAN_GOLEM, 7346, 4060, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 120,
            8050, 8051, 8052, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.GRANITE_LOBSTER, 6850, 4180, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 96,
            8112, 8114, 8113, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.PRAYING_MANTIS, 6799, 4280, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 120,
            8069, 8066, 8065, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.FORGE_REGENT, 7336, 4410, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 100,
            7866, 7865, 7864, -1, 1330,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.ADAMANT_MINOTAUR, 6862, 4410, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 160,
            8024, 8023, 8025, 1498, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.TALON_BEAST, 7348, 4540, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 100,
            5989, 5988, 5990, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.GIANT_ENT, 6801, 4670, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 102,
            7853, 7852, 7854, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.FIRE_TITAN, 7356, 4760, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 152,
            7834, 7832, 7833, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.ICE_TITAN, 7360, 4760, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 152,
            8183, 8185, 8184, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.MOSS_TITAN, 7358, 4760, 72, 35,
            64, 72, 72, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 150,
            7844, 7842, 7843, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.HYDRA, 6812, 4900, 72, 35,
            64, 72, 72, FamiliarAttackStyle.RANGED,
            FamiliarAssistMode.ASSIST, 7, 4, 103,
            7935, 7936, 7937, -1, 1489,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SPIRIT_DAGANNOTH, 6805, 5280, 75, 35,
            67, 75, 75, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 108,
            7786, 7785, 7780, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.LAVA_TITAN, 7342, 5280, 75, 35,
            67, 75, 75, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 140,
            7980, 7981, 7692, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.SWAMP_TITAN, 7330, 5660, 77, 35,
            67, 77, 77, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 160,
            8222, 8224, 8226, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.RUNE_MINOTAUR, 6864, 5700, 80, 35,
            69, 80, 80, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 180,
            8024, 8023, 8025, 1498, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.UNICORN_STALLION, 6823, 1000, 80, 35,
            69, 80, 80, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.DEFENSIVE_ONLY, 1, 4, 115,
            6376, 6375, 6377, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.GEYSER_TITAN, 7340, 6100, 85, 35,
            72, 85, 85, FamiliarAttackStyle.RANGED,
            FamiliarAssistMode.ASSIST, 7, 4, 190,
            7883, 7878, 7880, 1375, 1374,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.WOLPERTINGER, 6870, 6510, 85, 35,
            72, 85, 85, FamiliarAttackStyle.MAGIC,
            FamiliarAssistMode.ASSIST, 7, 4, 224,
            8303, 8304, 8305, -1, 2733,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.ABYSSAL_TITAN, 7350, 6670, 100, 100,
            100, 100, 100, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 229,
            7693, 7691, 7979, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.IRON_TITAN, 7376, 6940, 120, 120,
            120, 120, 120, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.ASSIST, 1, 4, 244,
            7946, 7948, 7947, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.PACK_YAK, 6874, 7100, 87, 87,
            77, 87, 87, FamiliarAttackStyle.MELEE,
            FamiliarAssistMode.DEFENSIVE_ONLY, 1, 4, 125,
            5782, 5783, 852, -1, -1,
        ),
        SummoningCombatDefinition(
            SummoningPouchData.STEEL_TITAN, 7344, 7540, 130, 130,
            130, 130, 130, FamiliarAttackStyle.RANGED,
            FamiliarAssistMode.ASSIST, 7, 4, 244,
            8190, 8185, 8184, 1444, 1445,
        ),
    )
    private val byBaseNpc = definitions.associateBy { it.pouch.npc }
    private val byAnyNpc = buildMap {
        definitions.forEach { definition ->
            put(definition.pouch.npc, definition)
            definition.combatNpc?.let { put(it, definition) }
        }
    }

    val values: List<SummoningCombatDefinition> get() = definitions
    val combatValues: List<SummoningCombatDefinition> get() = definitions.filter { it.canFight }
    val executableCombatValues: List<SummoningCombatDefinition> get() = definitions.filter { it.isExecutable }
    val blockedCombatValues: List<SummoningCombatDefinition> get() = combatValues.filterNot { it.isExecutable }
    fun getByNpc(npc: Int): SummoningCombatDefinition? = byAnyNpc[npc]
    fun get(pouch: SummoningPouchData): SummoningCombatDefinition = byBaseNpc.getValue(pouch.npc)

    fun validate() {
        check(definitions.size == 78) { "Expected 78 familiar combat ledger rows, found ${definitions.size}." }
        check(byBaseNpc.size == 78) { "Duplicate base familiar NPC in combat ledger." }
        check(combatValues.size == 73) { "Expected 73 fighting familiars, found ${combatValues.size}." }
        check(executableCombatValues.size == 72) { "Expected 72 fully sourced executable combat rows, found ${executableCombatValues.size}." }
        check(blockedCombatValues.singleOrNull()?.pouch == SummoningPouchData.ALBINO_RAT) { "Only Albino Rat may remain animation-blocked." }
        executableCombatValues.forEach { definition ->
            check(definition.hitpoints > 0 && definition.maxHit > 0)
            check(definition.attackRange in 1..10 && definition.attackSpeed > 0)
            check(definition.attackAnimation >= 0 && definition.deathAnimation >= 0)
        }
    }
}
