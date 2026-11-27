package gg.rsmod.plugins.content.diaries

import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.diaries.Diaries.Area
import gg.rsmod.plugins.content.diaries.Diaries.Box
import gg.rsmod.plugins.content.diaries.Diaries.Diary
import gg.rsmod.plugins.content.diaries.Diaries.Task
import gg.rsmod.plugins.content.diaries.Diaries.Tier
import gg.rsmod.plugins.content.diaries.Diaries.Trigger
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.All
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Burn
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Enter
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.EquipCape
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Ev
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Gain
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Kill
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.PrayUp
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Steps
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Talk
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Tele
import gg.rsmod.plugins.content.diaries.Diaries.Trigger.Xp
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * The tasks of the Kandarin, Western Provinces and Wilderness diaries - OSRS Wiki <Area>_Diary (raw copies in
 * work\claude-scripts\diaries\wiki, fetched 2026-11-27), same order and text. A task whose content does not exist in our world is
 * REPLACED by an equal task in the same area at about the same level (owner choice 2 on 27-11); [Task.osrs] keeps the OSRS text so
 * it can come back when the content is imported. The list of replacements is in team\dossiers\DIARIES-OSRS.txt (VERVANGINGEN).
 */
object DiaryTasks {
    // ------------------------------------------------------------------------------------------------------------ areas
    val CATHERBY = Area("Catherby", Box(2775, 3405, 2870, 3470))
    val SEERS = Area("Seers' Village", Box(2685, 3430, 2770, 3510))
    val SEERS_BANK = Area("Seers' Village bank", Box(2721, 3487, 2730, 3494))
    val SEERS_CATHERBY = Area("Seers' Village and Catherby", *SEERS.boxes, *CATHERBY.boxes)
    val COURTHOUSE = Area("Seers' courthouse", Box(2728, 3463, 2741, 3474))
    val RANGING_GUILD = Area("Ranging Guild", Box(2652, 3418, 2681, 3446))
    val OTTO = Area("Otto's Grotto", Box(2494, 3480, 2512, 3500))
    val MCGRUBOR = Area("McGrubor's Wood", Box(2620, 3470, 2680, 3530))
    val FAIRY_MCGRUBOR = Area("McGrubor's Wood fairy ring", Box(2640, 3492, 2648, 3499))
    val COAL_TRUCKS = Area("Coal trucks", Box(2568, 3466, 2612, 3492))
    val HEMENSTER = Area("Hemenster", Box(2630, 3420, 2650, 3450))
    val CATHERBY_SHORE = Area("Catherby shore", Box(2838, 3422, 2856, 3436))
    val WORKSHOP = Area("Elemental Workshop", Box(2690, 9870, 2760, 9910))
    val WATERFALL = Area("Baxtorian Waterfall dungeon", Box(2550, 9860, 2600, 9920))
    val SHADOW = Area("Shadow dungeon", Box(2680, 5056, 2760, 5120))
    val CAMELOT_TELE = Area("Camelot", Box(2752, 3472, 2764, 3482), Box(2724, 3484, 2729, 3489))
    val CATHERBY_TELE = Area("Catherby", Box(2798, 3442, 2810, 3454))
    val GALAHAD = Area("Galahad's house", Box(2605, 3470, 2618, 3480))

    val PISCATORIS = Area("Piscatoris", Box(2300, 3560, 2390, 3720))
    val GNOME = Area("Gnome Stronghold", Box(2370, 3380, 2500, 3520))
    val GRAND_TREE_MINE = Area("Grand Tree mine", Box(2440, 9880, 2500, 9920))
    val APE_ATOLL = Area("Ape Atoll", Box(2690, 2690, 2820, 2810))
    val TIRANNWN = Area("Tirannwn", Box(2140, 3060, 2370, 3330))
    val LLETYA = Area("Lletya", Box(2310, 3140, 2360, 3195))
    val FELDIP = Area("Feldip Hills", Box(2480, 2880, 2640, 3010))
    val PEST_ISLAND = Area("Void Knights' Outpost", Box(2624, 2560, 2690, 2690))
    val ESSENCE_MINE = Area("Rune Essence mine", Box(2880, 4800, 2950, 4870))

    val WILDERNESS = Area("Wilderness", pred = { it.getWildernessLevel() > 0 })
    val KBD_LAIR = Area("King Black Dragon's lair", Box(2250, 4675, 2295, 4715))
    val CHAOS_RC = Area("Chaos Altar (runecrafting)", Box(2240, 4820, 2300, 4860))
    val FOUNTAIN = Area("Fountain of Rune", Box(3365, 3885, 3386, 3900))
    val LEVER = Area("Deserted Keep", Box(3150, 3918, 3160, 3928))
    val CHAOS_ALTAR_WEST = Area("Chaos Temple (western Wilderness)", Box(2945, 3816, 2952, 3824))
    val DEMONIC_RUINS = Area("Demonic Ruins", Box(3279, 3879, 3296, 3896))
    val ABYSS = Area("Abyss", Box(3008, 4800, 3071, 4863))
    val GHORROCK = Area("Ghorrock", Box(2968, 3868, 2985, 3880))
    val ROGUES_CASTLE = Area("Rogues' Castle", Box(3275, 3920, 3300, 3950))
    val RESOURCE_AREA = Area("Resource Area", Box(3174, 3924, 3196, 3944))
    val LAVA_MAZE = Area("Lava Maze", Box(3008, 3800, 3110, 3900))
    val REV_CAVES = Area("Revenant Caves", Box(3136, 10048, 3263, 10239)) // DIARIES-OSRS replacement for the Wilderness God Wars Dungeon

    // ------------------------------------------------------------------------------------------------------------ Varrock areas
    private const val KUDOS_VARP = 1010 // VarrockMuseum.KUDOS_VARP
    val VARROCK = Area("Varrock", Box(3170, 3370, 3290, 3515))
    val VARROCK_SE_MINE = Area("south-east Varrock mine", Box(3278, 3358, 3296, 3376))
    val LUMBER_YARD = Area("Lumber Yard", Box(3295, 3480, 3330, 3515))
    val SAWMILL = Area("Sawmill", Box(3295, 3484, 3310, 3500))
    val SOS_2 = Area("Stronghold of Security, level 2", Box(1984, 5184, 2047, 5247))
    val SOS_3 = Area("Stronghold of Security, level 3", Box(2112, 5248, 2175, 5311))
    val BARB_VILLAGE = Area("Barbarian Village", Box(3070, 3400, 3112, 3445))
    val EARTH_ALTAR = Area("Earth Altar", Box(2640, 4820, 2672, 4852))
    val CHAMPIONS_GUILD = Area("Champions' Guild", Box(3188, 3354, 3194, 3362))
    val CHAMPIONS_BUSH = Area("Champions' Guild bush patch", Box(3178, 3354, 3184, 3360))
    val GE_SPIRIT_TREE = Area("Grand Exchange spirit tree", Box(3182, 3505, 3191, 3514))
    val DIGSITE_TELE = Area("Digsite", Box(3335, 3438, 3348, 3450))
    val VARROCK_TELE = Area("Varrock Square", Box(3205, 3418, 3222, 3436))
    val GE_TELE = Area("Grand Exchange", Box(3150, 3468, 3180, 3500))
    val EDGEVILLE = Area("Edgeville", Box(3070, 3460, 3140, 3520))
    val PADDEWWA = Area("Paddewwa", Box(3088, 9872, 3106, 9892))
    val CHURCH_TOP = Area("top of the Varrock church", pred = { it.x in 3252..3261 && it.z in 3470..3489 && it.height >= 1 })
    val PALACE_GARDEN = Area("Varrock Palace tree patch", Box(3222, 3452, 3236, 3466))
    val PALACE_ALTAR = Area("Varrock Palace altar", Box(3203, 3489, 3213, 3499))
    val VARROCK_WEST_BANK = Area("Varrock west bank", Box(3180, 3433, 3194, 3447))
    val COOKS_GUILD = Area("Cooks' Guild", Box(3137, 3438, 3148, 3452))

    // ------------------------------------------------------------------------------------------------------------- Falador areas
    val FALADOR = Area("Falador", Box(2935, 3305, 3065, 3395))
    val FALADOR_WEST_WALL = Area("western Falador wall", Box(2932, 3353, 2938, 3357))
    val FALADOR_PUMP = Area("Falador west pump", Box(2944, 3378, 2954, 3388))
    val FALADOR_PARK = Area("Falador Park", Box(2980, 3366, 3026, 3392))
    val ENTRANA = Area("Entrana", Box(2800, 3325, 2870, 3395))
    val PORT_SARIM = Area("Port Sarim", Box(3005, 3170, 3060, 3265))
    val DWARVEN_MINE = Area("Dwarven Mine", Box(2990, 9750, 3070, 9850))
    val DORIC = Area("Doric's anvil", Box(2948, 3448, 2954, 3454))
    val CHEMIST = Area("Rimmington Chemist", Box(2928, 3206, 2937, 3215))
    val CHAOS_TEMPLE_ASGARNIA = Area("Chaos Temple", Box(2930, 3511, 2940, 3521))
    val FALADOR_FARM = Area("Falador farm", Box(3044, 3298, 3066, 3316))
    val MUDSKIPPER = Area("Mudskipper Point", Box(2978, 3105, 3012, 3132))
    val FALADOR_NORTH_WALL_TOP = Area("north Falador wall", Box(3004, 3392, 3007, 3395, 1))
    val FALADOR_NORTH_WALL_FOOT = Area("north Falador wall", Box(3004, 3390, 3007, 3396, 0))
    val TAVERLEY_GUTHIX_ALTAR = Area("Altar of Guthix", Box(2920, 3480, 2930, 3490))
    val CRAFTING_GUILD = Area("Crafting Guild", Box(2929, 3276, 2943, 3290))
    val MINING_GUILD = Area("Mining Guild", Box(3016, 9730, 3056, 9760))
    val TAVERLEY = Area("Taverley", Box(2875, 3400, 2945, 3460))
    val FALADOR_TELE = Area("Falador square", Box(2961, 3375, 2971, 3385))
    val BLACK_KNIGHTS_FORTRESS = Area("Black Knights' Fortress", Box(3008, 3500, 3035, 3530))
    val HEROES_GUILD_BASEMENT = Area("Heroes' Guild basement", Box(2880, 9880, 2945, 9920))
    val ROGUES_DEN = Area("Rogues' Den", Box(3040, 4960, 3070, 5000))
    val PORT_SARIM_CHURCH = Area("Port Sarim church", Box(3006, 3170, 3018, 3184))
    val WARRIORS_GUILD = Area("Warriors' Guild", Box(2836, 3533, 2876, 3558))
    val FALADOR_CASTLE = Area("Falador Castle", Box(2954, 3330, 2996, 3355))
    val FALADOR_CASTLE_TOP = Area("top of Falador Castle", Box(2954, 3330, 2996, 3355, 3), Box(2954, 3330, 2996, 3355, 2))
    val TAVERLEY_STRANGE_FLOOR = Area("Taverley dungeon strange floor", Box(2876, 9811, 2882, 9815))
    val FALADOR_EAST_BANK = Area("Falador east bank", Box(3009, 3353, 3019, 3358))

    // ------------------------------------------------------------------------------------------------------------ Ardougne areas
    val ARD_EAST = Area("East Ardougne", Box(2560, 3255, 2700, 3345))
    val ARD_WEST = Area("West Ardougne", Box(2430, 3270, 2559, 3345))
    val ARD_MARKET = Area("Ardougne market", Box(2650, 3295, 2675, 3320))
    val ARD_CHURCH = Area("East Ardougne church", Box(2611, 3302, 2623, 3312))
    val BRIMHAVEN_DOCK = Area("Brimhaven docks", Box(2755, 3220, 2790, 3240))
    val COMBAT_CAMP = Area("Combat Training Camp", Box(2513, 3355, 2534, 3378))
    val ARD_FARM = Area("Ardougne farming patch", Box(2660, 3370, 2675, 3380))
    val ARD_BUSH = Area("Ardougne bush patch", Box(2612, 3220, 2622, 3230))
    val GNOME_VILLAGE_PATCH = Area("Tree Gnome Village fruit tree patch", Box(2484, 3172, 2496, 3184))
    val YANILLE = Area("Yanille", Box(2532, 3070, 2620, 3110))
    val YANILLE_WITCHAVEN = Area("Yanille or Witchaven", Box(2532, 3070, 2620, 3110), Box(2700, 3265, 2740, 3300))
    val YANILLE_DUNGEON = Area("Yanille Agility dungeon", Box(2560, 9470, 2630, 9530))
    val MAGIC_GUILD = Area("Magic Guild", Box(2584, 3082, 2597, 3094))
    val WATCHTOWER_TELE = Area("Watchtower", Box(2540, 3105, 2560, 3120))
    val ARD_TELE = Area("Ardougne market", Box(2655, 3295, 2670, 3312))
    val CASTLE_WARS = Area("Castle Wars", Box(2365, 3070, 2450, 3140), Box(2365, 9470, 2435, 9535))
    val LEGENDS_GUILD = Area("Legends' Guild", Box(2723, 3373, 2735, 3383))
    val DEATH_ALTAR = Area("Death Altar", Box(2190, 4815, 2230, 4855))
    val NEAR_ARDOUGNE = Area("near Ardougne", Box(2430, 3140, 2745, 3400), Box(2532, 3070, 2620, 3110))

    private fun s(vararg n: String) = n.map { it.lowercase() }.toSet()

    private val ALL_TASKS = ArrayList<Task>()

    private fun t(diary: Diary, tier: Tier, nr: Int, text: String, trigger: Trigger, req: String = "", osrs: String? = null) {
        ALL_TASKS += Task("${diary.name.take(3)}_${tier.name.take(2)}$nr", diary, tier, text, trigger, req, osrs)
    }

    private fun equipped(p: gg.rsmod.game.model.entity.Player, vararg names: String): Boolean =
        p.equipment.rawItems.any { it != null && p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, it.id).name.lowercase().let { n -> names.any { x -> n.contains(x) } } }

    private fun voidSet(p: gg.rsmod.game.model.entity.Player): Boolean {
        val n = p.equipment.rawItems.filterNotNull().map { p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, it.id).name.lowercase() }
        return n.any { it.contains("void") && (it.contains("top") || it.contains("body")) } && n.any { it.contains("void") && it.contains("robe") } &&
            n.any { it.contains("void knight gloves") } && n.any { it.contains("void") && (it.contains("helm") || it.contains("hood")) }
    }

    init {
        val K = Diary.KANDARIN
        t(K, Tier.EASY, 1, "Catch a Mackerel at Catherby.", Gain(s("Raw mackerel"), CATHERBY), "Fishing 16")
        t(K, Tier.EASY, 2, "Buy a candle from the Chandler in Catherby.", Gain(s("Candle"), CATHERBY))
        t(K, Tier.EASY, 3, "Collect five Flax from the Seers' flax fields.", Gain(s("Flax"), SEERS, count = 5))
        t(K, Tier.EASY, 4, "Play the Organ in Seers' church.", Ev("organ:seers"))
        t(K, Tier.EASY, 5, "Plant some Jute seeds in the patch north of McGrubor's Wood.", Ev("plant:jute seed", MCGRUBOR), "Farming 13")
        t(K, Tier.EASY, 6, "Have Galahad make you a cup of tea.", Gain(s("Cup of tea"), GALAHAD))
        t(K, Tier.EASY, 7, "Pickpocket a Man in Seers' Village.", Ev("pickpocket:man", SEERS), osrs = "Defeat one of each elemental in the workshop.")
        t(K, Tier.EASY, 8, "Get a pet fish from Harry in Catherby.", Gain(emptySet(), CATHERBY, ids = setOf(6670, 6671, 6672)))
        t(K, Tier.EASY, 9, "Buy a Stew from the Seers' pub.", Gain(s("Stew"), SEERS))
        t(K, Tier.EASY, 10, "Speak to Sherlock.", Talk(s("Sherlock")))
        t(K, Tier.EASY, 11, "Cross the Coal truck log shortcut.", Xp(Skills.AGILITY, COAL_TRUCKS), "Agility 20")
        t(K, Tier.MEDIUM, 1, "Complete a lap of the Barbarian agility course.", Ev("lap:44376"), "Agility 35")
        t(K, Tier.MEDIUM, 2, "Create a Super Antipoison potion from scratch in the Seers/Catherby Area.", Steps(listOf(Gain(s("Clean irit", "Irit leaf"), SEERS_CATHERBY), Gain(s("Irit potion (unf)"), SEERS_CATHERBY), Gain(s("Super antipoison (3)", "Superantipoison(3)", "Super antipoison(3)"), SEERS_CATHERBY))), "Herblore 48")
        t(K, Tier.MEDIUM, 3, "Enter the Ranging guild.", Enter(RANGING_GUILD), "Ranged 40")
        t(K, Tier.MEDIUM, 4, "Use the grapple shortcut to get from the water obelisk to Catherby shore.", Xp(Skills.AGILITY, CATHERBY_SHORE), "Agility 36, Strength 22, Ranged 39")
        t(K, Tier.MEDIUM, 5, "Catch and cook a Bass in Catherby.", Steps(listOf(Gain(s("Raw bass"), CATHERBY), Gain(s("Bass"), CATHERBY))), "Fishing 46, Cooking 43")
        t(K, Tier.MEDIUM, 6, "Teleport to Camelot.", Tele(CAMELOT_TELE), "Magic 45")
        t(K, Tier.MEDIUM, 7, "String a Maple shortbow in Seers' Village bank.", Gain(s("Maple shortbow"), SEERS_BANK), "Fletching 50")
        t(K, Tier.MEDIUM, 8, "Pick some Limpwurt root from the farming patch in Catherby.", Gain(s("Limpwurt root"), CATHERBY), "Farming 26")
        t(K, Tier.MEDIUM, 9, "Create a Mind helmet.", Gain(s("Mind helmet")))
        t(K, Tier.MEDIUM, 10, "Kill a Fire Giant inside Baxtorian Waterfall.", Kill(s("Fire giant"), WATERFALL))
        t(K, Tier.MEDIUM, 11, "Catch a Leaping trout at Otto's Grotto.", Gain(s("Leaping trout"), OTTO), "Fishing 48, Agility 15, Strength 15", osrs = "Complete a wave of Barbarian Assault.")
        t(K, Tier.MEDIUM, 12, "Steal from the chest in Hemenster.", Xp(Skills.THIEVING, HEMENSTER), "Thieving 47")
        t(K, Tier.MEDIUM, 13, "Travel to McGrubor's Wood by Fairy Ring.", Tele(FAIRY_MCGRUBOR))
        t(K, Tier.MEDIUM, 14, "Mine some coal near the coal trucks.", Gain(s("Coal"), COAL_TRUCKS), "Mining 30")
        t(K, Tier.HARD, 1, "Catch a Leaping Sturgeon.", Gain(s("Leaping sturgeon"), OTTO), "Fishing 70, Agility 45, Strength 45")
        t(K, Tier.HARD, 2, "Complete a lap of the Seers' Village agility course.", Ev("lap:seers"), "Agility 60")
        t(K, Tier.HARD, 3, "Create a Yew Longbow from scratch around Seers' Village.", Steps(listOf(Gain(s("Yew logs"), SEERS), Gain(s("Yew longbow (u)"), SEERS), Gain(s("Yew longbow"), SEERS))), "Woodcutting 60, Fletching 70")
        t(K, Tier.HARD, 4, "Enter the Seers' Village courthouse with piety turned on.", Enter(COURTHOUSE) { Prayers.isActive(it, Prayer.PIETY) }, "Prayer 70, Defence 70")
        t(K, Tier.HARD, 5, "Charge a Water Orb.", Gain(s("Water orb")), "Magic 56")
        t(K, Tier.HARD, 6, "Burn some Maple logs in Seers' Village.", Burn(s("Maple logs"), SEERS), "Firemaking 45", osrs = "Burn some Maple logs with a bow in Seers' Village.")
        t(K, Tier.HARD, 7, "Kill a Shadow Hound in the Shadow dungeon.", Kill(s("Shadow hound"), SHADOW), "Thieving 53")
        t(K, Tier.HARD, 8, "Kill a Mithril Dragon.", Kill(s("Mithril dragon")))
        t(K, Tier.HARD, 9, "Catch a Leaping salmon at Otto's Grotto.", Gain(s("Leaping salmon"), OTTO), "Fishing 58, Agility 30, Strength 30", osrs = "Purchase and equip a granite body from Barbarian Assault.")
        t(K, Tier.HARD, 10, "Have the Seers' estate agent decorate your house with Fancy Stone.", Ev("estate:fancy stone", SEERS), "Construction 50")
        t(K, Tier.HARD, 11, "Smith an Adamant spear at Otto's Grotto.", Gain(s("Adamant spear"), OTTO), "Smithing 75")
        t(K, Tier.ELITE, 1, "String a Magic longbow in Seers' Village.", Gain(s("Magic longbow"), SEERS), "Fletching 85", osrs = "Read the Blackboard at Barbarian Assault after reaching level 5 in every role.")
        t(K, Tier.ELITE, 2, "Pick some Dwarf weed from the herb patch at Catherby.", Gain(s("Grimy dwarf weed"), CATHERBY), "Farming 79")
        t(K, Tier.ELITE, 3, "Fish and Cook 5 Sharks in Catherby using the Cooking gauntlets.", Steps(listOf(Gain(s("Raw shark"), CATHERBY, count = 5), Gain(s("Shark"), CATHERBY, count = 5, cond = { equipped(it, "cooking gauntlets") })), reset = false), "Fishing 76, Cooking 80")
        t(K, Tier.ELITE, 4, "Mix a Stamina Mix on top of the Seers' Village bank.", Gain(s("Stamina mix(2)", "Stamina mix(1)"), SEERS_BANK), "Herblore 86, Agility 60")
        t(K, Tier.ELITE, 5, "Smith a Rune Hasta at Otto's Grotto.", Gain(s("Rune hasta"), OTTO), "Smithing 90")
        t(K, Tier.ELITE, 6, "Burn some Magic logs in Seers' Village.", Burn(s("Magic logs"), SEERS), "Firemaking 75", osrs = "Construct a Pyre ship from Magic Logs.")
        t(K, Tier.ELITE, 7, "Teleport to Catherby.", Tele(CATHERBY_TELE), "Magic 87, Lunar spellbook")

        val W = Diary.WESTERN
        t(W, Tier.EASY, 1, "Catch a Copper Longtail.", Ev("hunt:copper longtail", PISCATORIS), "Hunter 9")
        t(W, Tier.EASY, 2, "Complete a novice game of Pest Control.", Ev("pest_control:novice"), "Combat 40")
        t(W, Tier.EASY, 3, "Mine some Iron Ore near Piscatoris.", Gain(s("Iron ore"), PISCATORIS), "Mining 15")
        t(W, Tier.EASY, 4, "Complete a lap of the Gnome agility course.", Ev("lap:35609"))
        t(W, Tier.EASY, 5, "Chop some logs in the Gnome Stronghold.", Gain(s("Logs"), GNOME), osrs = "Score a goal in a Gnomeball match.")
        t(W, Tier.EASY, 6, "Catch a Crimson swift.", Ev("hunt:crimson swift"), "Hunter 9", osrs = "Claim any Chompy bird hat from Rantz.")
        t(W, Tier.EASY, 7, "Travel to the Void Knights' Outpost.", Tele(PEST_ISLAND))
        t(W, Tier.EASY, 8, "Collect a swamp toad at the Gnome Stronghold.", Gain(s("Swamp toad"), GNOME))
        t(W, Tier.EASY, 9, "Have Brimstail teleport you to the Essence Mine.", Tele(ESSENCE_MINE, from = GNOME))
        t(W, Tier.EASY, 10, "Fletch an Oak Shortbow in the Gnome Stronghold.", Gain(s("Oak shortbow (u)", "Oak shortbow"), GNOME), "Fletching 20")
        t(W, Tier.EASY, 11, "Kill a Terrorbird in the Terrorbird enclosure.", Kill(s("Terrorbird"), GNOME))
        t(W, Tier.MEDIUM, 1, "Take the agility shortcut from the Grand Tree to Otto's Grotto.", Xp(Skills.AGILITY, OTTO), "Agility 37")
        t(W, Tier.MEDIUM, 2, "Travel to the Gnome Stronghold by Spirit Tree.", Tele(GNOME))
        t(W, Tier.MEDIUM, 3, "Catch a Grey chinchompa.", Ev("hunt:grey chinchompa"), "Hunter 53", osrs = "Trap a Spined Larupia.")
        t(W, Tier.MEDIUM, 4, "Fish some Bass on Ape Atoll.", Gain(s("Raw bass"), APE_ATOLL), "Fishing 46")
        t(W, Tier.MEDIUM, 5, "Chop and burn some teak logs on Ape Atoll.", Steps(listOf(Gain(s("Teak logs"), APE_ATOLL), Burn(s("Teak logs"), APE_ATOLL))), "Woodcutting 35, Firemaking 35")
        t(W, Tier.MEDIUM, 6, "Complete an intermediate game of Pest Control.", Ev("pest_control:intermediate"), "Combat 70")
        t(W, Tier.MEDIUM, 7, "Travel to the Feldip Hills by Gnome Glider.", Tele(FELDIP))
        t(W, Tier.MEDIUM, 8, "Catch 125 chinchompas.", Gain(s("Chinchompa", "Red chinchompa"), count = 125), "Hunter 53", osrs = "Claim a Chompy bird hat from Rantz after registering at least 125 kills.")
        t(W, Tier.MEDIUM, 9, "Climb the rocks shortcut at Eagles' Peak.", Ev("shortcut:eagles peak"), "Agility 25", osrs = "Travel from Eagles' Peak to the Feldip Hills by Eagle.")
        t(W, Tier.MEDIUM, 10, "Make a Chocolate Bomb at the Grand Tree.", Gain(s("Chocolate bomb"), GNOME), "Cooking 42")
        t(W, Tier.MEDIUM, 11, "Fletch a Willow longbow in the Gnome Stronghold.", Gain(s("Willow longbow (u)"), GNOME), "Fletching 40", osrs = "Complete a delivery for the Gnome Restaurant.")
        t(W, Tier.MEDIUM, 12, "Turn your small crystal seed into a Crystal saw.", Gain(s("Crystal saw")))
        t(W, Tier.MEDIUM, 13, "Mine some Gold ore underneath the Grand Tree.", Gain(s("Gold ore"), GRAND_TREE_MINE), "Mining 40")
        t(W, Tier.HARD, 1, "Kill an Elf with a Crystal bow.", Kill(s("Elf warrior", "Elf archer", "Guard", "Iorwerth warrior", "Iorwerth archer", "Elf"), TIRANNWN) { equipped(it, "crystal bow") }, "Ranged 70")
        t(W, Tier.HARD, 2, "Catch and cook a Monkfish in Piscatoris.", Steps(listOf(Gain(s("Raw monkfish"), PISCATORIS), Gain(s("Monkfish"), PISCATORIS))), "Fishing 62, Cooking 62")
        t(W, Tier.HARD, 3, "Complete a Veteran game of Pest Control.", Ev("pest_control:veteran"), "Combat 100")
        t(W, Tier.HARD, 4, "Catch a Red chinchompa.", Ev("hunt:red chinchompa"), "Hunter 63", osrs = "Catch a Dashing Kebbit.")
        t(W, Tier.HARD, 5, "Complete a lap of the Ape Atoll agility course.", Ev("lap:ape atoll"), "Agility 48")
        t(W, Tier.HARD, 6, "Chop and burn some Mahogany logs on Ape Atoll.", Steps(listOf(Gain(s("Mahogany logs"), APE_ATOLL), Burn(s("Mahogany logs"), APE_ATOLL))), "Woodcutting 50, Firemaking 50")
        t(W, Tier.HARD, 7, "Mine some Adamantite ore in Tirannwn.", Gain(s("Adamantite ore"), TIRANNWN), "Mining 70")
        t(W, Tier.HARD, 8, "Check the health of your Palm tree in Lletya.", Xp(Skills.FARMING, LLETYA), "Farming 68")
        t(W, Tier.HARD, 9, "Catch 300 chinchompas.", Gain(s("Chinchompa", "Red chinchompa"), count = 300), "Hunter 53", osrs = "Claim a Chompy bird hat from Rantz after registering at least 300 kills.")
        t(W, Tier.HARD, 10, "Build an Isafdar painting in your POH Quest hall.", Ev("poh_build:isafdar (painting)"), "Construction 65")
        t(W, Tier.HARD, 11, "Kill a Tormented demon.", Kill(s("Tormented demon")), osrs = "Kill Zulrah.")
        t(W, Tier.HARD, 12, "Teleport to Ape Atoll.", Tele(APE_ATOLL), "Magic 64")
        t(W, Tier.HARD, 13, "Pickpocket a Gnome.", Ev("pickpocket:gnome"), "Thieving 75")
        t(W, Tier.ELITE, 1, "Fletch a Magic Longbow in Tirannwn.", Gain(s("Magic longbow (u)", "Magic longbow"), TIRANNWN), "Fletching 85")
        t(W, Tier.ELITE, 2, "Kill the Corporeal Beast.", Kill(s("Corporeal beast")), osrs = "Kill the Thermonuclear Smoke Devil.")
        t(W, Tier.ELITE, 3, "Have Prissy Scilla protect your Magic tree.", Ev("farming_protect:magic", GNOME), "Farming 75")
        t(W, Tier.ELITE, 4, "Use the Elven overpass advanced cliffside shortcut.", Ev("shortcut:elven overpass advanced"), "Agility 85")
        t(W, Tier.ELITE, 5, "Equip any complete void set.", Enter(null) { voidSet(it) }, "Combat 40, Attack 42, Strength 42, Defence 42, Hitpoints 42, Ranged 42, Magic 42, Prayer 22")
        t(W, Tier.ELITE, 6, "Catch 1,000 chinchompas.", Gain(s("Chinchompa", "Red chinchompa"), count = 1000), "Hunter 53", osrs = "Claim a Chompy bird hat from Rantz after registering at least 1,000 kills.")
        t(W, Tier.ELITE, 7, "Pickpocket an Elf.", Ev("pickpocket:elf"), "Thieving 85")

        val D = Diary.WILDERNESS
        t(D, Tier.EASY, 1, "Cast Low Alchemy at the Fountain of Rune.", Ev("spell:low level alchemy", FOUNTAIN), "Magic 21")
        t(D, Tier.EASY, 2, "Enter the Wilderness from the Ardougne or Edgeville lever.", Tele(LEVER))
        t(D, Tier.EASY, 3, "Pray at the Chaos Altar in the Western Wilderness.", PrayUp(CHAOS_ALTAR_WEST))
        t(D, Tier.EASY, 4, "Enter the Chaos Runecrafting temple.", Enter(CHAOS_RC))
        t(D, Tier.EASY, 5, "Kill a Mammoth in the Wilderness.", Kill(s("Mammoth"), WILDERNESS))
        t(D, Tier.EASY, 6, "Kill an Earth Warrior in the Wilderness beneath Edgeville.", Kill(s("Earth warrior"), WILDERNESS), "Agility 15")
        t(D, Tier.EASY, 7, "Restore some prayer points at the demonic ruins.", PrayUp(DEMONIC_RUINS))
        t(D, Tier.EASY, 8, "Enter the King Black Dragon's lair.", Enter(KBD_LAIR))
        t(D, Tier.EASY, 9, "Collect 5 Red spiders' eggs from the Wilderness.", Gain(s("Red spiders' eggs"), WILDERNESS, count = 5))
        t(D, Tier.EASY, 10, "Mine some Iron ore in the Wilderness.", Gain(s("Iron ore"), WILDERNESS), "Mining 15")
        t(D, Tier.EASY, 11, "Have the Mage of Zamorak teleport you to the Abyss.", Tele(ABYSS))
        t(D, Tier.EASY, 12, "Equip any team cape in the Wilderness.", EquipCape("team-", WILDERNESS))
        t(D, Tier.MEDIUM, 1, "Mine some Mithril ore in the wilderness.", Gain(s("Mithril ore"), WILDERNESS), "Mining 55")
        t(D, Tier.MEDIUM, 2, "Chop some yew logs from a fallen Ent.", Gain(s("Yew logs"), WILDERNESS), "Woodcutting 61")
        t(D, Tier.MEDIUM, 3, "Enter the Revenant Caves.", Enter(REV_CAVES), osrs = "Enter the Wilderness Godwars Dungeon.")
        t(D, Tier.MEDIUM, 4, "Complete a lap of the Wilderness Agility course.", Ev("lap:34666"), "Agility 52")
        t(D, Tier.MEDIUM, 5, "Kill a Green Dragon.", Kill(s("Green dragon"), WILDERNESS))
        t(D, Tier.MEDIUM, 6, "Kill an Ankou in the Wilderness.", Kill(s("Ankou"), WILDERNESS))
        t(D, Tier.MEDIUM, 7, "Charge an Earth Orb.", Gain(s("Earth orb")), "Magic 60")
        t(D, Tier.MEDIUM, 8, "Kill a Revenant pyrefiend in the Revenant Caves.", Kill(s("Revenant pyrefiend"), REV_CAVES), osrs = "Kill a Bloodveld in the Wilderness Godwars Dungeon.")
        t(D, Tier.MEDIUM, 9, "Talk to the Emblem Trader in Edgeville about emblems.", Talk(s("Emblem Trader", "Deadman Store")))
        t(D, Tier.MEDIUM, 10, "Smith a Gold helmet.", Gain(s("Gold helmet", "Golden helmet")), "Smithing 50", osrs = "Smith a Golden helmet in the Resource Area.")
        t(D, Tier.MEDIUM, 11, "Open the Muddy Chest in the lava maze.", Ev("chest:muddy"))
        t(D, Tier.HARD, 1, "Cast any of the 3 God spells against another player in the Wilderness.", Ev("god_spell:player", WILDERNESS), "Magic 60")
        t(D, Tier.HARD, 2, "Charge an Air Orb.", Gain(s("Air orb")), "Magic 66")
        t(D, Tier.HARD, 3, "Catch a Black warlock.", Ev("hunt:black warlock"), "Hunter 54", osrs = "Catch a Black Salamander in the Wilderness.")
        t(D, Tier.HARD, 4, "Mine some Adamantite ore in the Wilderness.", Gain(s("Adamantite ore"), WILDERNESS), "Mining 70", osrs = "Smith an Adamant scimitar in the Resource Area.")
        t(D, Tier.HARD, 5, "Kill a Lava Dragon.", Kill(s("Lava dragon"), WILDERNESS))
        t(D, Tier.HARD, 6, "Kill the Chaos Elemental.", Kill(s("Chaos Elemental")))
        t(D, Tier.HARD, 7, "Kill the Crazy Archaeologist, Chaos Fanatic & Scorpia.", All(listOf(Kill(s("Crazy archaeologist")), Kill(s("Chaos Fanatic")), Kill(s("Scorpia")))))
        t(D, Tier.HARD, 8, "Take the agility shortcut from Trollheim into the Wilderness.", Ev("shortcut:trollheim wilderness"), "Agility 64")
        t(D, Tier.HARD, 9, "Kill a Revenant dark beast in the Revenant Caves.", Kill(s("Revenant dark beast"), REV_CAVES), osrs = "Kill a Spiritual warrior in the Wilderness Godwars Dungeon.")
        t(D, Tier.HARD, 10, "Fish some Raw Lava Eel in the Wilderness.", Gain(s("Raw lava eel"), WILDERNESS), "Fishing 53, Herblore 25")
        t(D, Tier.ELITE, 1, "Kill Callisto, Venenatis & Vet'ion.", All(listOf(Kill(s("Callisto", "Artio")), Kill(s("Venenatis", "Spindel")), Kill(s("Vet'ion", "Calvar'ion")))))
        t(D, Tier.ELITE, 2, "Teleport to Ghorrock.", Tele(GHORROCK), "Magic 96")
        t(D, Tier.ELITE, 3, "Cook a Shark in the Wilderness.", Gain(s("Shark"), WILDERNESS), "Cooking 80", osrs = "Fish and Cook a Dark Crab in the Resource Area.")
        t(D, Tier.ELITE, 4, "Mine some Runite ore in the Lava Maze.", Gain(s("Runite ore"), LAVA_MAZE), "Mining 85", osrs = "Smith a rune scimitar from scratch in the Resource Area.")
        t(D, Tier.ELITE, 5, "Steal from the Rogues' chest.", Ev("chest:rogues", ROGUES_CASTLE), "Thieving 84")
        t(D, Tier.ELITE, 6, "Kill a Revenant knight in the Revenant Caves.", Kill(s("Revenant knight"), REV_CAVES), osrs = "Slay a spiritual mage inside the wilderness Godwars Dungeon.")
        t(D, Tier.ELITE, 7, "Burn some Magic logs in the Wilderness.", Burn(s("Magic logs"), WILDERNESS), "Firemaking 75", osrs = "Cut and burn some magic logs in the Resource Area.")

        // ---------------------------------------------------------------------------------------------------- VARROCK (D5, 27-11)
        val V = Diary.VARROCK
        val sk = Skills
        t(V, Tier.EASY, 1, "Browse Thessalia's store.", Talk(s("Thessalia"), VARROCK))
        t(V, Tier.EASY, 2, "Have Aubury teleport you to the Essence mine.", Tele(ESSENCE_MINE, from = VARROCK))
        t(V, Tier.EASY, 3, "Mine some Iron in the south-east mining patch near Varrock.", Gain(s("Iron ore"), VARROCK_SE_MINE), "Mining 15")
        t(V, Tier.EASY, 4, "Make a normal plank at the Sawmill.", Gain(s("Plank"), SAWMILL))
        t(V, Tier.EASY, 5, "Enter the second level of the Stronghold of Security.", Enter(SOS_2))
        t(V, Tier.EASY, 6, "Squeeze under the Lumber Yard fence.", Ev("shortcut:lumber_yard_fence"), osrs = "Jump over the fence south of Varrock.")
        t(V, Tier.EASY, 7, "Chop down a dying tree in the Lumber Yard.", Gain(s("Logs"), LUMBER_YARD, skill = sk.WOODCUTTING))
        t(V, Tier.EASY, 8, "Buy a newspaper.", Gain(s("Newspaper"), VARROCK))
        t(V, Tier.EASY, 9, "Give a dog a bone!", Ev("dog_bone"))
        t(V, Tier.EASY, 10, "Spin a bowl on the pottery wheel and fire it in the oven in Barb Village.",
            Steps(listOf(Gain(s("Unfired bowl"), BARB_VILLAGE, skill = sk.CRAFTING), Gain(s("Bowl"), BARB_VILLAGE, skill = sk.CRAFTING))), "Crafting 8")
        t(V, Tier.EASY, 11, "Speak to Haig Halen after obtaining at least 50 Kudos.", Talk(s("Haig Halen"), cond = { it.getVarp(KUDOS_VARP) >= 50 }))
        t(V, Tier.EASY, 12, "Craft some Earth runes from Essence.", Gain(s("Earth rune"), EARTH_ALTAR, skill = sk.RUNECRAFTING), "Runecrafting 9")
        t(V, Tier.EASY, 13, "Catch some trout in the River Lum at Barbarian Village.", Gain(s("Raw trout"), BARB_VILLAGE), "Fishing 20")
        t(V, Tier.EASY, 14, "Steal from the Tea stall in Varrock.", Gain(s("Cup of tea"), VARROCK, skill = sk.THIEVING), "Thieving 5")
        t(V, Tier.MEDIUM, 1, "Make a Strength potion in Varrock.", Gain(s("Strength potion (3)", "Strength potion (4)", "Strength potion(3)", "Strength potion(4)"), VARROCK, skill = sk.HERBLORE),
            "Herblore 12", osrs = "Have the Apothecary in Varrock make you a strength potion.")
        t(V, Tier.MEDIUM, 2, "Enter the Champions' Guild.", Enter(CHAMPIONS_GUILD), "Quest points 32")
        t(V, Tier.MEDIUM, 3, "Pickpocket a Guard in Varrock.", Ev("pickpocket:guard", VARROCK), "Thieving 40", osrs = "Select a colour for your kitten.")
        t(V, Tier.MEDIUM, 4, "Use the spirit tree north of Varrock.", Ev("spirit_tree", GE_SPIRIT_TREE))
        t(V, Tier.MEDIUM, 5, "Perform the 4 emotes from the Stronghold of Security.",
            All(listOf(Ev("emote:flap"), Ev("emote:slap_head"), Ev("emote:idea"), Ev("emote:stamp"))))
        t(V, Tier.MEDIUM, 6, "Enter the third level of the Stronghold of Security.", Enter(SOS_3), osrs = "Enter the Tolna dungeon after completing A Soul's Bane.")
        t(V, Tier.MEDIUM, 7, "Teleport to the digsite using a Digsite pendant.", Tele(DIGSITE_TELE))
        t(V, Tier.MEDIUM, 8, "Cast the teleport to Varrock spell.", Tele(VARROCK_TELE), "Magic 25")
        t(V, Tier.MEDIUM, 9, "Get a Slayer task from Vannaka.", Ev("slayer_task:vannaka"), "Combat 40")
        t(V, Tier.MEDIUM, 10, "Make 20 Mahogany Planks in one go.", Ev("sawmill:mahogany:20"))
        t(V, Tier.MEDIUM, 11, "Pick some berries from the bush patch by the Champions' Guild.",
            Gain(s("Redberries", "Cadava berries", "Dwellberries", "Jangerberries", "Whiteberries", "Poison ivy berries"), CHAMPIONS_BUSH, skill = sk.FARMING),
            "Farming 10", osrs = "Pick a White tree fruit.")
        t(V, Tier.MEDIUM, 12, "Teleport to the Grand Exchange.", Tele(GE_TELE), osrs = "Use the balloon to travel from Varrock.")
        t(V, Tier.MEDIUM, 13, "Squeeze through the underwall tunnel to the Grand Exchange.", Ev("shortcut:underwall_tunnel"), "Agility 21",
            osrs = "Complete a lap of the Varrock Agility course.")
        t(V, Tier.HARD, 1, "Cook a Shark in the Cooks' Guild.", Gain(s("Shark"), COOKS_GUILD), "Cooking 80",
            osrs = "Trade furs with the Fancy Dress Seller for a spottier cape and equip it.")
        t(V, Tier.HARD, 2, "Smith an Adamant scimitar in Varrock.", Gain(s("Adamant scimitar"), VARROCK, skill = sk.SMITHING), "Smithing 75",
            osrs = "Speak to Orlando Smith when you have achieved 153 Kudos.")
        t(V, Tier.HARD, 3, "Make a Waka canoe near Edgeville.", Ev("canoe:waka", EDGEVILLE), "Woodcutting 57")
        t(V, Tier.HARD, 4, "Teleport to Paddewwa.", Tele(PADDEWWA), "Magic 54")
        t(V, Tier.HARD, 5, "Teleport to Barbarian Village with a skull sceptre.", Ev("tele:skull_sceptre"))
        t(V, Tier.HARD, 6, "Chop some yew logs in Varrock and burn them at the top of the Varrock church.",
            Steps(listOf(Gain(s("Yew logs"), VARROCK), Burn(s("Yew logs"), CHURCH_TOP))), "Woodcutting 60, Firemaking 60")
        t(V, Tier.HARD, 7, "Have the Varrock estate agent decorate your house with Fancy Stone.", Ev("estate:fancy stone", VARROCK), "Construction 50")
        t(V, Tier.HARD, 8, "Collect at least 2 yew roots from the Tree patch in Varrock Palace.", Gain(s("Yew roots"), PALACE_GARDEN, count = 2), "Farming 68")
        t(V, Tier.HARD, 9, "Pray at the altar in Varrock palace with Smite active.", PrayUp(PALACE_ALTAR) { Prayers.isActive(it, Prayer.SMITE) }, "Prayer 52")
        t(V, Tier.HARD, 10, "Squeeze through the obstacle pipe in Edgeville dungeon.", Ev("shortcut:edgeville_pipe"), "Agility 51")
        t(V, Tier.ELITE, 1, "Create a super combat potion in Varrock west bank.", Gain(s("Super combat potion(4)", "Super combat potion(3)"), VARROCK_WEST_BANK, skill = sk.HERBLORE), "Herblore 90")
        t(V, Tier.ELITE, 2, "Use Lunar magic to make 20 mahogany planks at the Lumberyard.", Gain(s("Mahogany plank"), LUMBER_YARD, count = 20, skill = sk.MAGIC), "Magic 86, Lunar spellbook")
        t(V, Tier.ELITE, 3, "Bake a summer pie in the Cooking Guild.", Gain(s("Summer pie"), COOKS_GUILD, skill = sk.COOKING), "Cooking 95")
        t(V, Tier.ELITE, 4, "Smith and fletch ten rune darts within Varrock.",
            Steps(listOf(Gain(s("Rune dart tip"), VARROCK, count = 10, skill = sk.SMITHING), Gain(s("Rune dart"), VARROCK, count = 10, skill = sk.FLETCHING))), "Smithing 89, Fletching 81")
        t(V, Tier.ELITE, 5, "Craft 100 or more earth runes simultaneously from Essence.", Gain(s("Earth rune"), EARTH_ALTAR, count = 100, skill = sk.RUNECRAFTING, once = true), "Runecrafting 78")

        // ---------------------------------------------------------------------------------------------------- ARDOUGNE (D5, 27-11)
        val A = Diary.ARDOUGNE
        t(A, Tier.EASY, 1, "Have Wizard Cromperty teleport you to the Rune Essence mine.", Tele(ESSENCE_MINE, from = ARD_EAST))
        t(A, Tier.EASY, 2, "Steal a cake from the Ardougne market stalls.", Gain(s("Cake"), ARD_MARKET, skill = sk.THIEVING), "Thieving 5")
        t(A, Tier.EASY, 3, "Talk to the Silk trader in Ardougne market.", Talk(s("Silk trader", "Silk merchant"), ARD_MARKET),
            osrs = "Sell silk to the Silk trader in Ardougne for 60 coins each.")
        t(A, Tier.EASY, 4, "Use the altar in East Ardougne's church.", PrayUp(ARD_CHURCH))
        t(A, Tier.EASY, 5, "Sail from Ardougne to Brimhaven with Captain Barnaby.", Tele(BRIMHAVEN_DOCK, from = ARD_EAST),
            osrs = "Go out fishing on the Fishing Trawler.")
        t(A, Tier.EASY, 6, "Enter the Combat Training Camp north of W. Ardougne.", Enter(COMBAT_CAMP))
        t(A, Tier.EASY, 7, "Have Tindel Marchant identify a Rusted Sword for you.", Ev("tindel:identify"))
        t(A, Tier.EASY, 8, "Use the Ardougne Lever to teleport to the Wilderness.", Tele(LEVER, from = ARD_EAST))
        t(A, Tier.EASY, 9, "View Aleck's Hunter Emporium.", Talk(s("Aleck")))
        t(A, Tier.EASY, 10, "Steal some silk from the Ardougne market.", Gain(s("Silk"), ARD_MARKET, skill = sk.THIEVING), "Thieving 20",
            osrs = "Check what pets you have insured with Probita in Ardougne.")
        t(A, Tier.MEDIUM, 1, "Pickpocket a Knight of Ardougne.", Ev("pickpocket:knight of ardougne"), "Thieving 55",
            osrs = "Enter the Unicorn pen in Ardougne zoo using Fairy rings.")
        t(A, Tier.MEDIUM, 2, "Grapple over Yanille's south wall.", Ev("shortcut:yanille_grapple"), "Agility 39, Strength 38, Ranged 21")
        t(A, Tier.MEDIUM, 3, "Harvest some strawberries from the Ardougne farming patch.", Gain(s("Strawberry"), ARD_FARM, skill = sk.FARMING), "Farming 31")
        t(A, Tier.MEDIUM, 4, "Cast the Ardougne Teleport spell.", Tele(ARD_TELE), "Magic 51")
        t(A, Tier.MEDIUM, 5, "Teleport to Castle Wars.", Tele(CASTLE_WARS), osrs = "Travel to Castlewars by Hot Air Balloon.")
        t(A, Tier.MEDIUM, 6, "Steal some fur from the Ardougne market.", Gain(s("Fur", "Grey wolf fur"), ARD_MARKET, skill = sk.THIEVING), "Thieving 35",
            osrs = "Claim buckets of sand from Bert in Yanille.")
        t(A, Tier.MEDIUM, 7, "Steal from the Ardougne spice stall.", Gain(s("Spice"), ARD_MARKET, skill = sk.THIEVING), "Thieving 65",
            osrs = "Catch any fish on the Fishing Platform.")
        t(A, Tier.MEDIUM, 8, "Pickpocket the master farmer north of Ardougne.", Ev("pickpocket:master farmer"), "Thieving 38")
        t(A, Tier.MEDIUM, 9, "Pick the lock of the door north of West Ardougne.", Ev("picklock:2565,3356"), "Thieving 46",
            osrs = "Collect some Nightshade from the Skavid Caves.")
        t(A, Tier.MEDIUM, 10, "Pickpocket a Guard in East Ardougne.", Ev("pickpocket:guard", ARD_EAST), "Thieving 40",
            osrs = "Kill a swordchick in the Tower of Life.")
        t(A, Tier.MEDIUM, 11, "Smith a Mithril plateskirt in Yanille.", Gain(s("Mithril plateskirt"), YANILLE, skill = sk.SMITHING), "Smithing 56",
            osrs = "Equip Iban's upgraded staff or upgrade an Iban staff.")
        t(A, Tier.MEDIUM, 12, "Enter the Yanille Agility dungeon.", Enter(YANILLE_DUNGEON), osrs = "Visit the Island East of the Necromancer's Tower.")
        t(A, Tier.HARD, 1, "Recharge some Jewellery at the Totem in the Legends Guild.", Ev("totem:recharge"))
        t(A, Tier.HARD, 2, "Enter the Magic Guild.", Enter(MAGIC_GUILD), "Magic 66")
        t(A, Tier.HARD, 3, "Pickpocket a Paladin in Ardougne.", Ev("pickpocket:paladin"), "Thieving 70", osrs = "Steal from a chest in Ardougne Castle.")
        t(A, Tier.HARD, 4, "Steal from the gem stall in East Ardougne.",
            Gain(s("Uncut sapphire", "Uncut emerald", "Uncut ruby", "Uncut diamond", "Sapphire", "Emerald", "Ruby", "Diamond"), ARD_MARKET, skill = sk.THIEVING),
            "Thieving 75", osrs = "Have a zookeeper put you in Ardougne Zoo's monkey cage.")
        t(A, Tier.HARD, 5, "Teleport to the Watchtower.", Tele(WATCHTOWER_TELE), "Magic 58")
        t(A, Tier.HARD, 6, "Enter the Legends' Guild.", Enter(LEGENDS_GUILD), osrs = "Catch a Red Salamander.")
        t(A, Tier.HARD, 7, "Check the health of a Palm tree near tree gnome village.", Trigger.Xp(Skills.FARMING, GNOME_VILLAGE_PATCH), "Farming 68")
        t(A, Tier.HARD, 8, "Pick some Poison Ivy berries from the patch south of Ardougne.", Gain(s("Poison ivy berries"), ARD_BUSH, skill = sk.FARMING), "Farming 70")
        t(A, Tier.HARD, 9, "Smith a Mithril platebody near Ardougne.", Gain(s("Mithril platebody"), NEAR_ARDOUGNE, skill = sk.SMITHING), "Smithing 68")
        t(A, Tier.HARD, 10, "Build a Yanille portal in your portal chamber.", Ev("poh_build:yanille_portal"), "Construction 50",
            osrs = "Enter your POH from Yanille.")
        t(A, Tier.HARD, 11, "Smith a Dragon sq shield in West Ardougne.", Gain(s("Dragon sq shield"), ARD_WEST, skill = sk.SMITHING), "Smithing 60")
        t(A, Tier.HARD, 12, "Craft some Death runes from Essence.", Gain(s("Death rune"), DEATH_ALTAR, skill = sk.RUNECRAFTING), "Runecrafting 65")
        t(A, Tier.ELITE, 1, "Cook a Manta ray in Ardougne.", Gain(s("Manta ray"), NEAR_ARDOUGNE, skill = sk.COOKING), "Cooking 91",
            osrs = "Catch a Manta ray in the Fishing Trawler and cook it in Port Khazard.")
        t(A, Tier.ELITE, 2, "Picklock the door to the basement of Yanille Agility Dungeon.", Ev("picklock:2601,9482"), "Thieving 82")
        t(A, Tier.ELITE, 3, "Pickpocket a Hero.", Ev("pickpocket:hero"), "Thieving 80")
        t(A, Tier.ELITE, 4, "Make a rune crossbow yourself within Witchaven or Yanille.",
            Steps(listOf(Gain(s("Runite limbs"), YANILLE, skill = sk.SMITHING), Gain(s("Rune crossbow"), YANILLE_WITCHAVEN, skill = sk.FLETCHING))),
            "Smithing 91, Fletching 69", osrs = "Make a rune crossbow yourself from scratch within Witchaven or Yanille.")
        t(A, Tier.ELITE, 5, "Burn some Magic logs in Yanille.", Burn(s("Magic logs"), YANILLE), "Firemaking 75",
            osrs = "Imbue a Salve amulet at Nightmare Zone, or equip a Salve amulet that was imbued there.")
        t(A, Tier.ELITE, 6, "Pick some Torstol from the patch north of Ardougne.", Gain(s("Grimy torstol"), ARD_FARM), "Farming 85")
        t(A, Tier.ELITE, 7, "Fletch a Magic longbow in Ardougne.", Gain(s("Magic longbow (u)", "Magic longbow"), NEAR_ARDOUGNE, skill = sk.FLETCHING), "Fletching 85",
            osrs = "Complete a lap of Ardougne's rooftop agility course.")
        t(A, Tier.ELITE, 8, "Cast Ice Barrage on another player within Castle Wars.", Ev("spell:ice barrage player", CASTLE_WARS), "Magic 94")

        // ---------------------------------------------------------------------------------------------------- FALADOR (D5, 27-11)
        val F = Diary.FALADOR
        val I = gg.rsmod.plugins.api.cfg.Items
        val initiate = { p: gg.rsmod.game.model.entity.Player ->
            p.equipment.contains(I.INITIATE_SALLET) && p.equipment.contains(I.INITIATE_HAUBERK) && p.equipment.contains(I.INITIATE_CUISSE) }
        val proselyte = { p: gg.rsmod.game.model.entity.Player ->
            p.equipment.contains(I.PROSELYTE_SALLET) && p.equipment.contains(I.PROSELYTE_HAUBERK) &&
                (p.equipment.contains(I.PROSELYTE_CUISSE) || p.equipment.contains(I.PROSELYTE_TASSET)) }
        t(F, Tier.EASY, 1, "Find out what your family crest is from Sir Renitee.", Ev("crest:asked"), "Construction 16")
        t(F, Tier.EASY, 2, "Climb over the western Falador wall.", Trigger.Xp(Skills.AGILITY, FALADOR_WEST_WALL), "Agility 5")
        t(F, Tier.EASY, 3, "Browse Sarah's farm shop.", Talk(s("Sarah")))
        t(F, Tier.EASY, 4, "Get a Haircut or a Shave from the Falador Hairdresser.", Talk(s("Hairdresser"), FALADOR))
        t(F, Tier.EASY, 5, "Fill a bucket from the pump north of Falador west bank.", Gain(s("Bucket of water"), FALADOR_PUMP))
        t(F, Tier.EASY, 6, "Kill a duck in Falador Park.", Kill(s("Duck", "Drake"), FALADOR_PARK))
        t(F, Tier.EASY, 7, "Make a mind tiara.", Gain(s("Mind tiara"), skill = sk.RUNECRAFTING))
        t(F, Tier.EASY, 8, "Take the boat to Entrana.", Tele(ENTRANA, from = PORT_SARIM))
        t(F, Tier.EASY, 9, "Mine some Iron ore in the Dwarven Mine.", Gain(s("Iron ore"), DWARVEN_MINE, skill = sk.MINING), "Mining 15",
            osrs = "Repair a broken strut in the Motherlode Mine.")
        t(F, Tier.EASY, 10, "Talk to the Security guard at Port Sarim jail.", Talk(s("Security guard")),
            osrs = "Claim a security book from the Security guard at Port Sarim jail.")
        t(F, Tier.EASY, 11, "Smith some Blurite Limbs on Doric's anvil.", Gain(s("Blurite limbs"), DORIC, skill = sk.SMITHING), "Mining 10, Smithing 13")
        t(F, Tier.MEDIUM, 1, "Light a Bullseye lantern at the Chemist's in Rimmington.", Ev("light:bullseye lantern", CHEMIST), "Firemaking 49")
        t(F, Tier.MEDIUM, 2, "Telegrab some Wine of Zamorak at the Chaos Temple by the Wilderness.",
            Gain(s("Wine of zamorak"), CHAOS_TEMPLE_ASGARNIA, skill = sk.MAGIC), "Magic 33")
        t(F, Tier.MEDIUM, 3, "Unlock the Crystal chest in Taverley.", Ev("chest:crystal"))
        t(F, Tier.MEDIUM, 4, "Place a Scarecrow in the Falador farm flower patch.", Ev("plant:scarecrow", FALADOR_FARM), "Farming 23")
        t(F, Tier.MEDIUM, 5, "Kill a Mogre at Mudskipper Point.", Kill(s("Mogre"), MUDSKIPPER), "Slayer 32")
        t(F, Tier.MEDIUM, 6, "Kill a rat in Port Sarim.", Kill(s("Rat", "Giant rat"), PORT_SARIM), osrs = "Visit the Port Sarim Rat Pits.")
        t(F, Tier.MEDIUM, 7, "Grapple up and then jump off the north Falador wall.",
            Steps(listOf(Trigger.Xp(Skills.AGILITY, FALADOR_NORTH_WALL_TOP), Trigger.Xp(Skills.AGILITY, FALADOR_NORTH_WALL_FOOT))), "Agility 11, Strength 37, Ranged 19")
        t(F, Tier.MEDIUM, 8, "Pickpocket a Falador guard.", Ev("pickpocket:guard", FALADOR), "Thieving 40")
        t(F, Tier.MEDIUM, 9, "Pray at the Altar of Guthix in Taverley whilst wearing full Initiate.", PrayUp(TAVERLEY_GUTHIX_ALTAR, initiate), "Prayer 10, Defence 20")
        t(F, Tier.MEDIUM, 10, "Mine some Gold ore at the Crafting Guild.", Gain(s("Gold ore"), CRAFTING_GUILD, skill = sk.MINING), "Crafting 40, Mining 40")
        t(F, Tier.MEDIUM, 11, "Mine some Mithril ore in the Mining Guild.", Gain(s("Mithril ore"), MINING_GUILD, skill = sk.MINING), "Mining 60",
            osrs = "Squeeze through the crevice in the Dwarven mines.")
        t(F, Tier.MEDIUM, 12, "Chop and burn some Willow logs in Taverley.",
            Steps(listOf(Gain(s("Willow logs"), TAVERLEY, skill = sk.WOODCUTTING), Burn(s("Willow logs"), TAVERLEY))), "Woodcutting 30, Firemaking 30")
        t(F, Tier.MEDIUM, 13, "Craft a fruit basket on the Falador Farm loom.", Gain(s("Basket"), FALADOR_FARM, skill = sk.CRAFTING), "Crafting 36")
        t(F, Tier.MEDIUM, 14, "Teleport to Falador.", Tele(FALADOR_TELE), "Magic 37")
        t(F, Tier.HARD, 1, "Craft 140 Mind runes simultaneously from Essence.", Gain(s("Mind rune"), count = 140, skill = sk.RUNECRAFTING, once = true), "Runecrafting 56")
        t(F, Tier.HARD, 2, "Change your family crest to the Saradomin symbol.", Ev("crest:saradomin"), "Prayer 70, Construction 16")
        t(F, Tier.HARD, 3, "Kill the Giant Mole beneath Falador Park.", Kill(s("Giant mole")))
        t(F, Tier.HARD, 4, "Kill a Skeletal Wyvern in the Asgarnia Ice Dungeon.", Kill(s("Skeletal wyvern")), "Slayer 72")
        t(F, Tier.HARD, 5, "Kill a Black Knight in the Black Knights' Fortress.", Kill(s("Black knight"), BLACK_KNIGHTS_FORTRESS),
            osrs = "Complete a lap of the Falador rooftop agility course.")
        t(F, Tier.HARD, 6, "Mine some Runite ore in the Mining Guild.", Gain(s("Runite ore"), MINING_GUILD, skill = sk.MINING), "Mining 85",
            osrs = "Enter the mining guild while wearing a Prospector helmet.")
        t(F, Tier.HARD, 7, "Kill the Blue Dragon under the Heroes' Guild.", Kill(s("Blue dragon"), HEROES_GUILD_BASEMENT))
        t(F, Tier.HARD, 8, "Crack a wall safe within Rogues' Den.", Trigger.Xp(Skills.THIEVING, ROGUES_DEN), "Thieving 50")
        t(F, Tier.HARD, 9, "Recharge your prayer in the Port Sarim church while wearing full Proselyte.", PrayUp(PORT_SARIM_CHURCH, proselyte), "Defence 30, Prayer 20")
        t(F, Tier.HARD, 10, "Enter the Warriors' Guild.", Enter(WARRIORS_GUILD))
        t(F, Tier.HARD, 11, "Kill a Dwarf in the Dwarven Mine.", Kill(s("Dwarf", "Chaos dwarf"), DWARVEN_MINE), "Defence 50",
            osrs = "Equip a dwarven helmet within the dwarven mines.")
        t(F, Tier.ELITE, 1, "Craft 252 Air Runes simultaneously from Essence.", Gain(s("Air rune"), count = 252, skill = sk.RUNECRAFTING, once = true), "Runecrafting 88")
        t(F, Tier.ELITE, 2, "Purchase a White 2h Sword from Sir Vyvin.", Gain(s("White 2h sword"), FALADOR_CASTLE))
        t(F, Tier.ELITE, 3, "Find at least 3 magic roots at once when digging up your magic tree in Falador.",
            Gain(s("Magic roots"), FALADOR_PARK, count = 3, once = true), "Farming 91, Woodcutting 75")
        t(F, Tier.ELITE, 4, "Perform a skillcape or quest cape emote at the top of Falador Castle.", Ev("emote:skillcape", FALADOR_CASTLE_TOP))
        t(F, Tier.ELITE, 5, "Jump over the strange floor in Taverley dungeon.", Trigger.Xp(Skills.AGILITY, TAVERLEY_STRANGE_FLOOR), "Agility 80")
        t(F, Tier.ELITE, 6, "Mix a Saradomin brew in Falador east bank.", Gain(s("Saradomin brew(3)", "Saradomin brew (3)"), FALADOR_EAST_BANK, skill = sk.HERBLORE), "Herblore 81")
    }

    val ALL: List<Task> get() = ALL_TASKS
}
