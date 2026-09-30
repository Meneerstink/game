package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.skills.farming.data.Seed
import gg.rsmod.plugins.content.skills.fishing.Fish
import gg.rsmod.plugins.content.skills.mining.RockType
import gg.rsmod.plugins.content.skills.runecrafting.Rune
import gg.rsmod.plugins.content.skills.thieving.pickpocketing.PickpocketTarget
import gg.rsmod.plugins.content.skills.thieving.stalls.StallTarget
import gg.rsmod.plugins.content.skills.woodcutting.TreeType

/**
 * OSRS skilling pets (OSRS Wiki "Pet", Skilling pets): "DropRate = 1 / (BaseChance - SkillLevel * 25)", the level is the base level and
 * stops counting at 99, and "players with 200 million experience in the corresponding skill will be fifteen times more likely".
 * Base chances per activity: each pet's wiki page, section "Drop rates" (copied 2026-09-30 to agent-handoffs/2026-09-30-pets-skilling-rates.txt).
 *
 * Activities this server has that the wiki does not list take the listed activity of the same kind (ADAPTED, marked below); activities
 * the wiki excludes (rune essence, salt, the advanced RS2 courses' extra laps are laps) follow the wiki.
 */
object SkillingPets {
    const val MAX_LEVEL = 99
    const val XP_200M = 200_000_000.0
    const val BONUS_200M = 15

    /** 1 / chance, as the wiki formula gives it for [base], [level] and [xp]. */
    fun oneIn(
        base: Int,
        level: Int,
        xp: Double,
    ): Int {
        val denominator = base - 25 * level.coerceAtMost(MAX_LEVEL)
        return (if (xp >= XP_200M) denominator / BONUS_200M else denominator).coerceAtLeast(1)
    }

    /** One pet roll of [skill] against [base]; [item] is the variant to award (default: the pet's base variant). */
    fun roll(
        player: Player,
        pet: String,
        skill: Int,
        base: Int?,
        item: Int? = null,
    ) {
        base ?: return
        val odds = oneIn(base, player.skills.getMaxLevel(skill), player.skills.getCurrentXp(skill))
        if (player.world.random(odds - 1) != 0) return
        if (item != null) Pets.award(player, pet, item) else Pets.award(player, pet)
    }

    /** [times] independent rolls (the rift guardian rolls per essence). */
    fun rollTimes(
        player: Player,
        pet: String,
        skill: Int,
        base: Int?,
        times: Int,
        item: Int? = null,
    ) {
        base ?: return
        val odds = oneIn(base, player.skills.getMaxLevel(skill), player.skills.getCurrentXp(skill))
        repeat(times) {
            if (player.world.random(odds - 1) == 0) {
                if (item != null) Pets.award(player, pet, item) else Pets.award(player, pet)
                return
            }
        }
    }

    // ---- Rock golem (Mining): "Rock golem#Drop rates"; rune essence, salt and dense essence give no roll.
    val MINING: Map<RockType, Int> =
        mapOf(
            RockType.CLAY to 741_600, RockType.COPPER to 741_600, RockType.TIN to 741_600, RockType.BLURITE to 741_600,
            RockType.IRON to 741_600, RockType.SILVER to 741_600, RockType.COAL to 290_640, RockType.GOLD to 296_640,
            RockType.MITHRIL to 148_320, RockType.ADAMANTITE to 59_328, RockType.RUNITE to 42_377, RockType.SANDSTONE to 741_600,
            RockType.GRANITE to 741_600,
            // ADAPTED: the RS2 concentrated deposits yield coal / gold, so they take the coal / gold rocks' chance.
            RockType.CONCENTRATED_COAL to 290_640, RockType.CONCENTRATED_GOLD to 296_640,
        )

    // ---- Beaver (Woodcutting): "Beaver#Drop rates", rolled "when successfully chopping a tree and receiving a log". Ivy gives no log.
    val WOODCUTTING: Map<TreeType, Int> =
        mapOf(
            TreeType.TREE to 317_647, TreeType.ACHEY to 317_647, TreeType.OAK to 361_146, TreeType.WILLOW to 289_286,
            TreeType.TEAK to 264_336, TreeType.MAPLE to 221_918, TreeType.HOLLOW to 214_367, TreeType.MAHOGANY to 220_623,
            TreeType.YEW to 145_013, TreeType.MAGIC to 72_321,
        )

    // ---- Heron (Fishing): "Heron#Drop rate".
    val FISHING: Map<Fish, Int> =
        mapOf(
            Fish.SHRIMP to 870_330, Fish.ANCHOVIES to 870_330, Fish.KARAMBWANJI to 443_697, Fish.SARDINE to 1_056_000,
            Fish.HERRING to 1_056_000, Fish.TROUT to 923_616, Fish.SALMON to 923_616, Fish.PIKE to 305_792,
            Fish.RAINBOW_FISH to 137_739, Fish.TUNA to 257_770, Fish.SWORDFISH to 257_770, Fish.LOBSTER to 116_129,
            Fish.CAVE_EEL to 257_770, Fish.MONKFISH to 138_583, Fish.RAW_KARAMBWAN to 170_874, Fish.LEAPING_TROUT to 1_280_862,
            Fish.LEAPING_SALMON to 1_280_862, Fish.LEAPING_STURGEON to 1_280_862, Fish.SHARK to 82_243, Fish.CAVEFISH to 300_792,
            // "Big net fishing": every catch of the big net, junk included.
            Fish.MACKEREL to 1_147_827, Fish.COD to 1_147_827, Fish.BASS to 1_147_827, Fish.CASKET to 1_147_827,
            Fish.OYSTER to 1_147_827, Fish.SEAWEED to 1_147_827, Fish.LEATHER_GLOVES to 1_147_827, Fish.LEATHER_BOOTS to 1_147_827,
            // ADAPTED: crayfish (cage, small fish) as shrimps; slimy / lava eels as cave eels.
            Fish.CRAYFISH to 870_330, Fish.SLIMY_EEL to 257_770, Fish.LAVA_EEL to 257_770,
        )

    // ---- Rocky (Thieving): "Rocky#Drop rates".
    val PICKPOCKET: Map<PickpocketTarget, Int> =
        mapOf(
            PickpocketTarget.ManWoman to 257_211, PickpocketTarget.Farmer to 257_211, PickpocketTarget.FemaleHamMember to 257_211,
            PickpocketTarget.MaleHamMember to 257_211, PickpocketTarget.Warrior to 257_211, PickpocketTarget.Rogue to 257_211,
            PickpocketTarget.CaveGoblin to 257_211, PickpocketTarget.MasterFarmer to 257_211, PickpocketTarget.Guard to 257_211,
            PickpocketTarget.FremennikCitizen to 257_211, PickpocketTarget.DesertBandit to 257_211,
            PickpocketTarget.KnightOfArdougne to 257_211, PickpocketTarget.MenaphiteThug to 257_211,
            PickpocketTarget.YanilleWatchman to 134_625, PickpocketTarget.Paladin to 127_056, PickpocketTarget.Gnome to 108_718,
            PickpocketTarget.Hero to 99_175,
            // ADAPTED: the H.A.M. guard, monkey knife fighter and dwarf trader as the ordinary pickpockets (257,211).
            PickpocketTarget.HamGuard to 257_211, PickpocketTarget.MonkeyKnifeFighter to 257_211, PickpocketTarget.DwarfTrader to 257_211,
        )

    val STALLS: Map<StallTarget, Int> =
        mapOf(
            StallTarget.VegetableStall to 206_777, StallTarget.BakerStall to 124_066, StallTarget.CraftingStall to 47_718,
            StallTarget.MonkeyFoodStall to 47_718, StallTarget.TeaStall to 68_926, StallTarget.SilkStall to 68_926,
            StallTarget.WineStall to 36_490, StallTarget.SeedStall to 36_490, StallTarget.FurStall to 36_490, StallTarget.FishStall to 36_490,
            StallTarget.CrossbowStall to 36_490, StallTarget.SilverStall to 36_490, StallTarget.SpiceStall to 36_490,
            StallTarget.MagicStall to 36_490, StallTarget.ScimitarStall to 36_490, StallTarget.GemStall to 36_490,
            // ADAPTED: Ape Atoll's general stall as its food / crafting stalls.
            StallTarget.MonkeyGeneralStall to 47_718,
        )

    // ---- Giant squirrel (Agility): "Giant squirrel#Drop rates", rolled "after completing an Agility course".
    const val GNOME_COURSE = 35_609
    const val BARBARIAN_COURSE = 44_376
    const val WILDERNESS_COURSE = 34_666

    // ---- Rift guardian (Runecraft): "rolled per essence crafted"; "Everything else" 1,795,758 (Arceuus blood / soul / Ourania do not exist).
    const val RUNECRAFT = 1_795_758

    // ---- Baby chinchompa (Hunter): grey 131,395, red 98,373 (black chinchompas are not huntable here); the pet's colour is the catch's.
    const val GREY_CHINCHOMPA = 131_395
    const val RED_CHINCHOMPA = 98_373
    const val BABY_CHIN_GREY = 13324
    const val BABY_CHIN_RED = 13323

    // ---- Tangleroot (Farming): rolled at check-health, or at the final harvest when the patch has no health check.
    val FARMING: Map<Seed, Int> =
        mapOf(
            Seed.Guam to 98_364, Seed.Marrentill to 98_364, Seed.Tarromin to 98_364, Seed.Harralander to 98_364, Seed.Ranarr to 98_364,
            Seed.Toadflax to 98_364, Seed.Irit to 98_364, Seed.Avantoe to 98_364, Seed.Kwuarm to 98_364, Seed.Snapdragon to 98_364,
            Seed.Cadantine to 98_364, Seed.Lantadyme to 98_364, Seed.DwarfWeed to 98_364, Seed.Torstol to 98_364,
            Seed.Marigold to 281_040, Seed.Rosemary to 281_040, Seed.Nasturtium to 281_040, Seed.WoadSeed to 281_040, Seed.Limpwurt to 281_040,
            Seed.Potato to 281_040, Seed.Onion to 281_040, Seed.Cabbage to 281_040, Seed.Tomato to 281_040, Seed.Sweetcorn to 224_832,
            Seed.Strawberry to 187_360, Seed.Watermelon to 160_594,
            Seed.Barley to 112_416, Seed.Hammerstone to 112_416, Seed.Asgarnian to 89_933, Seed.Jute to 89_933, Seed.Yanillian to 74_944,
            Seed.Krandorian to 64_238, Seed.WildBlood to 56_208,
            Seed.Redberry to 44_966, Seed.Cadavaberry to 37_472, Seed.Dwellberry to 32_119, Seed.Jangerberry to 28_104,
            Seed.Whiteberry to 28_104, Seed.PoisonIvy to 28_104,
            Seed.Apple to 9_000, Seed.Banana to 9_000, Seed.Orange to 9_000, Seed.Curry to 9_000, Seed.Pineapple to 9_000,
            Seed.Papaya to 9_000, Seed.Palm to 9_000,
            Seed.Oak to 22_483, Seed.Willow to 16_059, Seed.Maple to 14_052, Seed.Yew to 11_242, Seed.Magic to 9_368, Seed.Calquat to 6_000,
            // ADAPTED: spirit weed (an RS2 herb) as the herbs.
            Seed.SpiritWeed to 98_364,
        )

    fun mining(player: Player, rock: RockType) = roll(player, "Rock golem", Skills.MINING, MINING[rock])

    fun woodcutting(player: Player, tree: TreeType) = roll(player, "Beaver", Skills.WOODCUTTING, WOODCUTTING[tree])

    fun fishing(player: Player, fish: Fish) = roll(player, "Heron", Skills.FISHING, FISHING[fish])

    fun pickpocket(player: Player, target: PickpocketTarget) = roll(player, "Rocky", Skills.THIEVING, PICKPOCKET[target])

    fun stall(player: Player, target: StallTarget) = roll(player, "Rocky", Skills.THIEVING, STALLS[target])

    fun agilityLap(player: Player, base: Int) = roll(player, "Giant Squirrel", Skills.AGILITY, base)

    fun runecraft(player: Player, rune: Rune?, essence: Int) =
        rollTimes(player, PetMorphs.RIFT_GUARDIAN, Skills.RUNECRAFTING, RUNECRAFT, essence, rune?.let { PetMorphs.riftGuardianFor(it) })

    fun chinchompa(player: Player, red: Boolean) {
        val item = Pets.variantsOf("Baby chinchompa").firstOrNull { it.osrsItem == if (red) BABY_CHIN_RED else BABY_CHIN_GREY }?.item
        roll(player, "Baby chinchompa", Skills.HUNTER, if (red) RED_CHINCHOMPA else GREY_CHINCHOMPA, item)
    }

    fun farming(player: Player, seed: Seed) = roll(player, "Tangleroot", Skills.FARMING, FARMING[seed])
}
