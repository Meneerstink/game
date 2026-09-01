package gg.rsmod.plugins.content.skills.summoning

/**
 * Which skill the owner is credited in for damage their familiar deals. Sourced from the
 * revision-667-era official Summoning Knowledge Base familiars table, whose "Skill Focus" column
 * is defined by the same knowledge base as: "Every familiar that can fight will also note the
 * style. You will receive experience in that skill (or spread evenly in the case of 'Controlled')
 * as well as Constitution as if you had inflicted the damage yourself."
 *
 * This is deliberately not the same thing as [FamiliarAttackStyle], which describes how the
 * familiar fights (animation, projectile, accuracy formula). A familiar can swing in melee and
 * still credit Magic, so the two are recorded separately rather than derived from one another.
 */
enum class FamiliarSkillFocus { NONE, ATTACK, STRENGTH, DEFENCE, CONTROLLED, RANGED, MAGIC }

/**
 * The knowledge base's own broad familiar classifications, plus the two it describes in prose
 * rather than as a column ([LIGHT_SOURCE] "Light enhancer" and [REMOTE_VIEW]).
 */
enum class FamiliarCategory {
    COMBAT,
    BEAST_OF_BURDEN,
    FORAGER,
    HEALER,
    BOOSTER,
    TELEPORT,
    SKILLING_UTILITY,
    LIGHT_SOURCE,
    REMOTE_VIEW,
    RIGHT_CLICK_ABILITY,
}

enum class FamiliarInventoryKind { NONE, BEAST_OF_BURDEN, FORAGER }

/**
 * A familiar's item-carrying contract. Beast-of-burden capacities are the per-familiar numbers
 * printed in the knowledge base familiars table; the forager capacity is its general statement
 * that "A forager will find certain items from time to time, and can carry up to 30. You are only
 * able to 'Withdraw' items from these familiars."
 *
 * The three abyssal familiars are 7 essence slots each in this revision. Later sources give the
 * lurker 12 and the titan 20, but the wiki dates the titan's increase to the 15 September 2014
 * update, well after this revision, so the knowledge base value is the correct one here.
 */
data class FamiliarInventorySpec(
    val kind: FamiliarInventoryKind,
    val capacity: Int,
    val essenceOnly: Boolean = false,
)

/**
 * One familiar's sourced classification data: what it is, what it credits, what it carries and
 * what it can do beyond fighting.
 *
 * [abilities] holds the knowledge base's "Other Abilities" lines verbatim rather than a parsed
 * model of them. They are the evidence record for behaviour that later phases implement (passive
 * heals, invisible boosts, teleports, right-click moves), and keeping them unedited means an
 * implementation can be checked against the wording it came from.
 */
data class SummoningCatalogueEntry(
    val pouch: SummoningPouchData,
    val skillFocus: FamiliarSkillFocus,
    val combatLevel: Int?,
    val categories: Set<FamiliarCategory>,
    val inventory: FamiliarInventorySpec,
    val abilities: List<String>,
) {
    val canFight: Boolean get() = combatLevel != null

    fun isIn(category: FamiliarCategory): Boolean = category in categories
}

/**
 * The classification half of the 78-familiar ledger. The numeric half lives in
 * [SummoningPouchData] (ids, level, experience), [SummoningFamiliarDefinitions] (point cost and
 * duration), [SummoningCombatDefinitions] (combat stats and animations) and [SummoningScrollData]
 * (scrolls); [SummoningLedger] joins all of them and asserts they agree.
 *
 * Every row here comes from one revision-667-era knowledge base familiars-table row. The knowledge
 * base also lists Meerkats, which this revision's pouch table does not carry, so it has no row.
 * The seven -atrice familiars share a single knowledge base row and therefore share their data.
 */
object SummoningCatalogue {
    private val NO_INVENTORY = FamiliarInventorySpec(FamiliarInventoryKind.NONE, 0)
    private val FORAGER_INVENTORY = FamiliarInventorySpec(FamiliarInventoryKind.FORAGER, 30)

    private fun entry(
        pouch: SummoningPouchData,
        skillFocus: FamiliarSkillFocus,
        combatLevel: Int?,
        categories: Set<FamiliarCategory>,
        inventory: FamiliarInventorySpec = NO_INVENTORY,
        abilities: List<String> = emptyList(),
    ) = SummoningCatalogueEntry(pouch, skillFocus, combatLevel, categories, inventory, abilities)

    private val entries = listOf(
        entry(
            SummoningPouchData.SPIRIT_WOLF,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 26,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.DREADFOWL,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 26,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER),
            abilities = listOf("Farming boost (1)"),
        ),
        entry(
            SummoningPouchData.SPIRIT_SPIDER,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 25,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER),
            inventory = FORAGER_INVENTORY,
        ),
        entry(
            SummoningPouchData.THORNY_SNAIL,
            FamiliarSkillFocus.RANGED,
            combatLevel = 26,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 3, essenceOnly = false),
            abilities = listOf("Beast of burden (3)"),
        ),
        entry(
            SummoningPouchData.GRANITE_CRAB,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 26,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Fishing boost (1) - invisible"),
        ),
        entry(
            SummoningPouchData.SPIRIT_MOSQUITO,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 32,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.DESERT_WYRM,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 31,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Mining boost (1) - invisible"),
        ),
        entry(
            SummoningPouchData.SPIRIT_SCORPION,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 51,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.SPIRIT_TZ_KIH,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 36,
            categories = setOf(FamiliarCategory.COMBAT),
            abilities = listOf("Despair", "- when fighting other players, the Tz-Kih will drain your opponent's Prayer instead of inflicting damage"),
        ),
        entry(
            SummoningPouchData.ALBINO_RAT,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 37,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Forager - stores cheese after scroll use"),
        ),
        entry(
            SummoningPouchData.SPIRIT_KALPHITE,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 39,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 6, essenceOnly = false),
        ),
        entry(
            SummoningPouchData.COMPOST_MOUND,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 37,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Farming boost (1 + 2% of your level)", "Use a bucket on your compost mound to get compost - deals 20 damage to your familiar"),
        ),
        entry(
            SummoningPouchData.GIANT_CHINCHOMPA,
            FamiliarSkillFocus.RANGED,
            combatLevel = 42,
            categories = setOf(FamiliarCategory.COMBAT),
            abilities = listOf("Explode - chance of exploding in combat, damaging nearby targets"),
        ),
        entry(
            SummoningPouchData.VAMPYRE_BAT,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 44,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.LIGHT_SOURCE),
            abilities = listOf("Can heal itself slightly when it damages an enemy", "Light enhancer"),
        ),
        entry(
            SummoningPouchData.HONEY_BADGER,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 45,
            categories = setOf(FamiliarCategory.COMBAT),
            abilities = listOf("Ferocious - chance of attacking again without delay"),
        ),
        entry(
            SummoningPouchData.BEAVER,
            FamiliarSkillFocus.NONE,
            combatLevel = null,
            categories = setOf(FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER, FamiliarCategory.SKILLING_UTILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Woodcutting boost (2) - invisible", "Fletcher - counts as a knife for Fletching purposes"),
        ),
        entry(
            SummoningPouchData.VOID_RAVAGER,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 46,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Mining boost (1)", "Mining boost (1) - invisible"),
        ),
        entry(
            SummoningPouchData.VOID_SPINNER,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 40,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.HEALER),
        ),
        entry(
            SummoningPouchData.VOID_SHIFTER,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 46,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.TELEPORT),
            abilities = listOf("Teleporter - if you are in combat and have less than 10% of your life points, it will teleport you to the Void Knight Outpost"),
        ),
        entry(
            SummoningPouchData.VOID_TORCHER,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 46,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.RIGHT_CLICK_ABILITY),
            abilities = listOf("Right-click ", "Strike", "- inflicts 10 extra damage with this attack"),
        ),
        entry(
            SummoningPouchData.BRONZE_MINOTAUR,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 50,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.BULL_ANT,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 58,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 9, essenceOnly = false),
        ),
        entry(
            SummoningPouchData.MACAW,
            FamiliarSkillFocus.NONE,
            combatLevel = null,
            categories = setOf(FamiliarCategory.FORAGER, FamiliarCategory.REMOTE_VIEW),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Remote view", "Improved herb drops"),
        ),
        entry(
            SummoningPouchData.EVIL_TURNIP,
            FamiliarSkillFocus.RANGED,
            combatLevel = 62,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Forager - evil turnip slices", "Can heal itself slightly when using Ranged attacks"),
        ),
        entry(
            SummoningPouchData.SPIRIT_COCKATRICE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 64,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Right-click ", "Drain", "- inflicts damage and drains a combat stat (varies according to type)", "Forager - cockatrice eggs"),
        ),
        entry(
            SummoningPouchData.SPIRIT_GUTHATRICE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 64,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Right-click ", "Drain", "- inflicts damage and drains a combat stat (varies according to type)", "Forager - cockatrice eggs"),
        ),
        entry(
            SummoningPouchData.SPIRIT_SARATRICE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 64,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Right-click ", "Drain", "- inflicts damage and drains a combat stat (varies according to type)", "Forager - cockatrice eggs"),
        ),
        entry(
            SummoningPouchData.SPIRIT_ZAMATRICE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 64,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Right-click ", "Drain", "- inflicts damage and drains a combat stat (varies according to type)", "Forager - cockatrice eggs"),
        ),
        entry(
            SummoningPouchData.SPIRIT_PENGATRICE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 64,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Right-click ", "Drain", "- inflicts damage and drains a combat stat (varies according to type)", "Forager - cockatrice eggs"),
        ),
        entry(
            SummoningPouchData.SPIRIT_CORAXATRICE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 64,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Right-click ", "Drain", "- inflicts damage and drains a combat stat (varies according to type)", "Forager - cockatrice eggs"),
        ),
        entry(
            SummoningPouchData.SPIRIT_VULATRICE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 64,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Right-click ", "Drain", "- inflicts damage and drains a combat stat (varies according to type)", "Forager - cockatrice eggs"),
        ),
        entry(
            SummoningPouchData.IRON_MINOTAUR,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 70,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.PYRELORD,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 70,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER, FamiliarCategory.SKILLING_UTILITY),
            abilities = listOf("Firemaking boost (3) - invisible", "Counts as a tinderbox, with 10xp bonus"),
        ),
        entry(
            SummoningPouchData.MAGPIE,
            FamiliarSkillFocus.NONE,
            combatLevel = null,
            categories = setOf(FamiliarCategory.FORAGER),
            inventory = FORAGER_INVENTORY,
        ),
        entry(
            SummoningPouchData.BLOATED_LEECH,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 76,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.SPIRIT_TERRORBIRD,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 62,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 12, essenceOnly = false),
        ),
        entry(
            SummoningPouchData.ABYSSAL_PARASITE,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 86,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 7, essenceOnly = true),
            abilities = listOf("Slowed abyssal Prayer drain"),
        ),
        entry(
            SummoningPouchData.SPIRIT_JELLY,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 88,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.IBIS,
            FamiliarSkillFocus.NONE,
            combatLevel = null,
            categories = setOf(FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Fishing boost (3) - invisible"),
        ),
        entry(
            SummoningPouchData.STEEL_MINOTAUR,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 90,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.SPIRIT_GRAAHK,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 93,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER, FamiliarCategory.TELEPORT),
            abilities = listOf("Hunter boost (5) - invisible", "Trample - if the graahk has to move to get into combat, it will strike twice", "Can teleport you to the horned graahk area"),
        ),
        entry(
            SummoningPouchData.SPIRIT_KYATT,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 93,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER, FamiliarCategory.TELEPORT),
            abilities = listOf("Hunter boost (5) - invisible", "Pounce - if summoned or called directly into combat, its first attack can deal up to triple normal damage", "Can teleport you to the Piscatoris Hunter area"),
        ),
        entry(
            SummoningPouchData.SPIRIT_LARUPIA,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 93,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER, FamiliarCategory.TELEPORT),
            abilities = listOf("Hunter boost (5) - invisible", "Can teleport you to the Feldip Hunter area"),
        ),
        entry(
            SummoningPouchData.KARAMTHULHU_OVERLORD,
            FamiliarSkillFocus.RANGED,
            combatLevel = 95,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.RIGHT_CLICK_ABILITY),
            abilities = listOf("Right-click ", "Drown", "- hits opponent with a water spell"),
        ),
        entry(
            SummoningPouchData.SMOKE_DEVIL,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 101,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.RIGHT_CLICK_ABILITY),
            abilities = listOf("Right-click ", "Flames", "- hits opponent with a fire spell"),
        ),
        entry(
            SummoningPouchData.ABYSSAL_LURKER,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 93,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 7, essenceOnly = true),
        ),
        entry(
            SummoningPouchData.SPIRIT_COBRA,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 105,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.STRANGER_PLANT,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 107,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Farming boost (1 + 4% of your level)", "Forager - strange fruit"),
        ),
        entry(
            SummoningPouchData.BARKER_TOAD,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 112,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.RIGHT_CLICK_ABILITY),
            abilities = listOf("Right-click ", "Cannon", "- must be 'loaded' with a cannonball"),
        ),
        entry(
            SummoningPouchData.MITHRIL_MINOTAUR,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 112,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.WAR_TORTOISE,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 86,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 18, essenceOnly = false),
        ),
        entry(
            SummoningPouchData.BUNYIP,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 70,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.HEALER, FamiliarCategory.SKILLING_UTILITY),
            abilities = listOf("Use raw fish on the bunyip to turn them into water runes"),
        ),
        entry(
            SummoningPouchData.FRUIT_BAT,
            FamiliarSkillFocus.NONE,
            combatLevel = null,
            categories = setOf(FamiliarCategory.FORAGER, FamiliarCategory.LIGHT_SOURCE),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Light enhancer", "Fly - gathers fruit in Karamja"),
        ),
        entry(
            SummoningPouchData.RAVENOUS_LOCUST,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 120,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.REMOTE_VIEW),
            abilities = listOf("Can eat your opponent's food", "Remote view"),
        ),
        entry(
            SummoningPouchData.ARCTIC_BEAR,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 122,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER, FamiliarCategory.TELEPORT, FamiliarCategory.SKILLING_UTILITY),
            abilities = listOf("Hunter boost (7) - invisible", "Counts as two pieces of arctic camouflage", "Can teleport you to the Trollweiss and Rellekka Hunter area"),
        ),
        entry(
            SummoningPouchData.PHOENIX,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 124,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.RIGHT_CLICK_ABILITY),
            abilities = listOf("Right-click ", "Ash Blast", "- blinds opponents for a few seconds, reducing their chance to hit"),
        ),
        entry(
            SummoningPouchData.OBSIDIAN_GOLEM,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 126,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER),
            abilities = listOf("Mining boost (7) - invisible"),
        ),
        entry(
            SummoningPouchData.GRANITE_LOBSTER,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 129,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.BOOSTER),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Fishing boost (4) - invisible"),
        ),
        entry(
            SummoningPouchData.PRAYING_MANTIS,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 131,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.ADAMANT_MINOTAUR,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 133,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.FORGE_REGENT,
            FamiliarSkillFocus.RANGED,
            combatLevel = 133,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.RIGHT_CLICK_ABILITY, FamiliarCategory.SKILLING_UTILITY),
            abilities = listOf("Counts as a tinderbox, with 10xp bonus", "Right-click ", "Fireball", "- flaming attack that hits up to 6 enemies for up to 50 damage each"),
        ),
        entry(
            SummoningPouchData.TALON_BEAST,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 135,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.GIANT_ENT,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 137,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.FORAGER, FamiliarCategory.SKILLING_UTILITY),
            inventory = FORAGER_INVENTORY,
            abilities = listOf("Increased yield when harvesting Farming fruit trees, belladonna and cacti", "Forager - produces oak logs", "Nature link - can convert pure essence into nature or earth runes"),
        ),
        entry(
            SummoningPouchData.FIRE_TITAN,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 139,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.ICE_TITAN,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 139,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.MOSS_TITAN,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 139,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.HYDRA,
            FamiliarSkillFocus.RANGED,
            combatLevel = 141,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.SPIRIT_DAGANNOTH,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 148,
            categories = setOf(FamiliarCategory.COMBAT),
            abilities = listOf("Ferocious - chance of attacking again without delay"),
        ),
        entry(
            SummoningPouchData.LAVA_TITAN,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 148,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER, FamiliarCategory.TELEPORT),
            abilities = listOf("Chance of inflicting 50 extra damage with every attack", "Firemaking boost (10) - invisible", "Mining boost (10) - invisible", "Can teleport you to the Lava Maze"),
        ),
        entry(
            SummoningPouchData.SWAMP_TITAN,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 152,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.RUNE_MINOTAUR,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 154,
            categories = setOf(FamiliarCategory.COMBAT),
        ),
        entry(
            SummoningPouchData.UNICORN_STALLION,
            FamiliarSkillFocus.CONTROLLED,
            combatLevel = 70,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.HEALER, FamiliarCategory.RIGHT_CLICK_ABILITY),
            abilities = listOf("Right-click ", "Cure", "- cures poison and disease"),
        ),
        entry(
            SummoningPouchData.GEYSER_TITAN,
            FamiliarSkillFocus.RANGED,
            combatLevel = 200,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER, FamiliarCategory.SKILLING_UTILITY),
            abilities = listOf("Ranged Boost (1 + 3% of your level)", "Use bowls on the geyser titan to get bowls of hot water", "Use amulets of glory on the geyser titan to recharge them"),
        ),
        entry(
            SummoningPouchData.WOLPERTINGER,
            FamiliarSkillFocus.MAGIC,
            combatLevel = 210,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER),
            abilities = listOf("Hunter boost (5) - invisible", "Grants you a 5% Defence bonus against Magic", "Double experience and yield when harvesting berries"),
        ),
        entry(
            SummoningPouchData.ABYSSAL_TITAN,
            FamiliarSkillFocus.ATTACK,
            combatLevel = 215,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 7, essenceOnly = true),
        ),
        entry(
            SummoningPouchData.IRON_TITAN,
            FamiliarSkillFocus.DEFENCE,
            combatLevel = 220,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER),
            abilities = listOf("Defence boost (10% bonus to your stab, slash and crush Defence) - invisible"),
        ),
        entry(
            SummoningPouchData.PACK_YAK,
            FamiliarSkillFocus.STRENGTH,
            combatLevel = 175,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BEAST_OF_BURDEN),
            inventory = FamiliarInventorySpec(FamiliarInventoryKind.BEAST_OF_BURDEN, 30, essenceOnly = false),
        ),
        entry(
            SummoningPouchData.STEEL_TITAN,
            FamiliarSkillFocus.RANGED,
            combatLevel = 230,
            categories = setOf(FamiliarCategory.COMBAT, FamiliarCategory.BOOSTER),
            abilities = listOf("Defence boost (15% bonus to your stab, slash and crush Defence) - invisible"),
        ),
    )

    val byPouch: Map<SummoningPouchData, SummoningCatalogueEntry> = entries.associateBy { it.pouch }

    operator fun get(pouch: SummoningPouchData): SummoningCatalogueEntry = byPouch.getValue(pouch)

    fun getByNpc(npcId: Int): SummoningCatalogueEntry? =
        byPouch.values.firstOrNull { it.pouch.npc == npcId }

    fun inCategory(category: FamiliarCategory): List<SummoningCatalogueEntry> =
        entries.filter { category in it.categories }
}
