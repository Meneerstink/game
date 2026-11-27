package gg.rsmod.plugins.content.diaries

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
        t(K, Tier.EASY, 7, "Defeat one of each elemental in the workshop.", All(listOf(Kill(s("Earth elemental"), WORKSHOP), Kill(s("Water elemental"), WORKSHOP), Kill(s("Air elemental"), WORKSHOP), Kill(s("Fire elemental"), WORKSHOP))))
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
        t(K, Tier.MEDIUM, 11, "Complete a wave of Barbarian Assault.", Ev("barbarian_assault:wave"))
        t(K, Tier.MEDIUM, 12, "Steal from the chest in Hemenster.", Xp(Skills.THIEVING, HEMENSTER), "Thieving 47")
        t(K, Tier.MEDIUM, 13, "Travel to McGrubor's Wood by Fairy Ring.", Tele(FAIRY_MCGRUBOR))
        t(K, Tier.MEDIUM, 14, "Mine some coal near the coal trucks.", Gain(s("Coal"), COAL_TRUCKS), "Mining 30")
        t(K, Tier.HARD, 1, "Catch a Leaping Sturgeon.", Gain(s("Leaping sturgeon"), OTTO), "Fishing 70, Agility 45, Strength 45")
        t(K, Tier.HARD, 2, "Complete a lap of the Seers' Village agility course.", Ev("lap:seers"), "Agility 60")
        t(K, Tier.HARD, 3, "Create a Yew Longbow from scratch around Seers' Village.", Steps(listOf(Gain(s("Yew logs"), SEERS), Gain(s("Yew longbow (u)"), SEERS), Gain(s("Yew longbow"), SEERS))), "Woodcutting 60, Fletching 70")
        t(K, Tier.HARD, 4, "Enter the Seers' Village courthouse with piety turned on.", Enter(COURTHOUSE) { Prayers.isActive(it, Prayer.PIETY) }, "Prayer 70, Defence 70")
        t(K, Tier.HARD, 5, "Charge a Water Orb.", Gain(s("Water orb")), "Magic 56")
        t(K, Tier.HARD, 6, "Burn some Maple logs with a bow in Seers' Village.", Ev("bowfire:maple logs", SEERS), "Firemaking 65")
        t(K, Tier.HARD, 7, "Kill a Shadow Hound in the Shadow dungeon.", Kill(s("Shadow hound"), SHADOW))
        t(K, Tier.HARD, 8, "Kill a Mithril Dragon.", Kill(s("Mithril dragon")))
        t(K, Tier.HARD, 9, "Purchase and equip a granite body from Barbarian Assault.", Ev("barbarian_assault:granite body"))
        t(K, Tier.HARD, 10, "Have the Seers' estate agent decorate your house with Fancy Stone.", Ev("estate:fancy stone", SEERS), "Construction 50")
        t(K, Tier.HARD, 11, "Smith an Adamant spear at Otto's Grotto.", Gain(s("Adamant spear"), OTTO), "Smithing 75")
        t(K, Tier.ELITE, 1, "Read the Blackboard at Barbarian Assault after reaching level 5 in every role.", Ev("barbarian_assault:blackboard"))
        t(K, Tier.ELITE, 2, "Pick some Dwarf weed from the herb patch at Catherby.", Gain(s("Grimy dwarf weed"), CATHERBY), "Farming 79")
        t(K, Tier.ELITE, 3, "Fish and Cook 5 Sharks in Catherby using the Cooking gauntlets.", Steps(listOf(Gain(s("Raw shark"), CATHERBY, count = 5), Gain(s("Shark"), CATHERBY, count = 5, cond = { equipped(it, "cooking gauntlets") })), reset = false), "Fishing 76, Cooking 80")
        t(K, Tier.ELITE, 4, "Mix a Stamina Mix on top of the Seers' Village bank.", Gain(s("Stamina mix(2)", "Stamina mix(1)"), SEERS_BANK), "Herblore 86, Agility 60")
        t(K, Tier.ELITE, 5, "Smith a Rune Hasta at Otto's Grotto.", Gain(s("Rune hasta"), OTTO), "Smithing 90")
        t(K, Tier.ELITE, 6, "Construct a Pyre ship from Magic Logs.", Ev("pyre:magic"), "Firemaking 85, Crafting 85")
        t(K, Tier.ELITE, 7, "Teleport to Catherby.", Tele(CATHERBY_TELE), "Magic 87, Lunar spellbook")

        val W = Diary.WESTERN
        t(W, Tier.EASY, 1, "Catch a Copper Longtail.", Ev("hunt:copper longtail", PISCATORIS), "Hunter 9")
        t(W, Tier.EASY, 2, "Complete a novice game of Pest Control.", Ev("pest_control:novice"), "Combat 40")
        t(W, Tier.EASY, 3, "Mine some Iron Ore near Piscatoris.", Gain(s("Iron ore"), PISCATORIS), "Mining 15")
        t(W, Tier.EASY, 4, "Complete a lap of the Gnome agility course.", Ev("lap:35609"))
        t(W, Tier.EASY, 5, "Score a goal in a Gnomeball match.", Ev("gnomeball:goal"))
        t(W, Tier.EASY, 6, "Claim any Chompy bird hat from Rantz.", Ev("chompy:hat:1"), "30 chompy kills")
        t(W, Tier.EASY, 7, "Travel to the Void Knights' Outpost.", Tele(PEST_ISLAND))
        t(W, Tier.EASY, 8, "Collect a swamp toad at the Gnome Stronghold.", Gain(s("Swamp toad"), GNOME))
        t(W, Tier.EASY, 9, "Have Brimstail teleport you to the Essence Mine.", Tele(ESSENCE_MINE, from = GNOME))
        t(W, Tier.EASY, 10, "Fletch an Oak Shortbow in the Gnome Stronghold.", Gain(s("Oak shortbow (u)", "Oak shortbow"), GNOME), "Fletching 20")
        t(W, Tier.EASY, 11, "Kill a Terrorbird in the Terrorbird enclosure.", Kill(s("Terrorbird"), GNOME))
        t(W, Tier.MEDIUM, 1, "Take the agility shortcut from the Grand Tree to Otto's Grotto.", Xp(Skills.AGILITY, OTTO), "Agility 37")
        t(W, Tier.MEDIUM, 2, "Travel to the Gnome Stronghold by Spirit Tree.", Tele(GNOME))
        t(W, Tier.MEDIUM, 3, "Trap a Spined Larupia.", Gain(s("Larupia fur"), FELDIP), "Hunter 31")
        t(W, Tier.MEDIUM, 4, "Fish some Bass on Ape Atoll.", Gain(s("Raw bass"), APE_ATOLL), "Fishing 46")
        t(W, Tier.MEDIUM, 5, "Chop and burn some teak logs on Ape Atoll.", Steps(listOf(Gain(s("Teak logs"), APE_ATOLL), Burn(s("Teak logs"), APE_ATOLL))), "Woodcutting 35, Firemaking 35")
        t(W, Tier.MEDIUM, 6, "Complete an intermediate game of Pest Control.", Ev("pest_control:intermediate"), "Combat 70")
        t(W, Tier.MEDIUM, 7, "Travel to the Feldip Hills by Gnome Glider.", Tele(FELDIP))
        t(W, Tier.MEDIUM, 8, "Claim a Chompy bird hat from Rantz after registering at least 125 kills.", Ev("chompy:hat:125"))
        t(W, Tier.MEDIUM, 9, "Travel from Eagles' Peak to the Feldip Hills by Eagle.", Ev("eagle:feldip"))
        t(W, Tier.MEDIUM, 10, "Make a Chocolate Bomb at the Grand Tree.", Gain(s("Chocolate bomb"), GNOME), "Cooking 42")
        t(W, Tier.MEDIUM, 11, "Complete a delivery for the Gnome Restaurant.", Ev("gnome_restaurant:delivery"))
        t(W, Tier.MEDIUM, 12, "Turn your small crystal seed into a Crystal saw.", Gain(s("Crystal saw")))
        t(W, Tier.MEDIUM, 13, "Mine some Gold ore underneath the Grand Tree.", Gain(s("Gold ore"), GRAND_TREE_MINE), "Mining 40")
        t(W, Tier.HARD, 1, "Kill an Elf with a Crystal bow.", Kill(s("Elf warrior", "Elf archer", "Guard", "Iorwerth warrior", "Iorwerth archer", "Elf"), TIRANNWN) { equipped(it, "crystal bow") }, "Ranged 70")
        t(W, Tier.HARD, 2, "Catch and cook a Monkfish in Piscatoris.", Steps(listOf(Gain(s("Raw monkfish"), PISCATORIS), Gain(s("Monkfish"), PISCATORIS))), "Fishing 62, Cooking 62")
        t(W, Tier.HARD, 3, "Complete a Veteran game of Pest Control.", Ev("pest_control:veteran"), "Combat 100")
        t(W, Tier.HARD, 4, "Catch a Dashing Kebbit.", Gain(s("Dashing kebbit fur"), PISCATORIS), "Hunter 69")
        t(W, Tier.HARD, 5, "Complete a lap of the Ape Atoll agility course.", Ev("lap:ape atoll"), "Agility 48")
        t(W, Tier.HARD, 6, "Chop and burn some Mahogany logs on Ape Atoll.", Steps(listOf(Gain(s("Mahogany logs"), APE_ATOLL), Burn(s("Mahogany logs"), APE_ATOLL))), "Woodcutting 50, Firemaking 50")
        t(W, Tier.HARD, 7, "Mine some Adamantite ore in Tirannwn.", Gain(s("Adamantite ore"), TIRANNWN), "Mining 70")
        t(W, Tier.HARD, 8, "Check the health of your Palm tree in Lletya.", Xp(Skills.FARMING, LLETYA), "Farming 68")
        t(W, Tier.HARD, 9, "Claim a Chompy bird hat from Rantz after registering at least 300 kills.", Ev("chompy:hat:300"))
        t(W, Tier.HARD, 10, "Build an Isafdar painting in your POH Quest hall.", Ev("poh_build:isafdar (painting)"), "Construction 65")
        t(W, Tier.HARD, 11, "Kill Zulrah.", Kill(s("Zulrah")))
        t(W, Tier.HARD, 12, "Teleport to Ape Atoll.", Tele(APE_ATOLL), "Magic 64")
        t(W, Tier.HARD, 13, "Pickpocket a Gnome.", Ev("pickpocket:gnome"), "Thieving 75")
        t(W, Tier.ELITE, 1, "Fletch a Magic Longbow in Tirannwn.", Gain(s("Magic longbow (u)", "Magic longbow"), TIRANNWN), "Fletching 85")
        t(W, Tier.ELITE, 2, "Kill the Thermonuclear Smoke Devil.", Kill(s("Thermonuclear smoke devil")), "Slayer 93")
        t(W, Tier.ELITE, 3, "Have Prissy Scilla protect your Magic tree.", Ev("farming_protect:magic tree"), "Farming 75")
        t(W, Tier.ELITE, 4, "Use the Elven overpass advanced cliffside shortcut.", Ev("shortcut:elven overpass advanced"), "Agility 85")
        t(W, Tier.ELITE, 5, "Equip any complete void set.", Enter(null) { voidSet(it) })
        t(W, Tier.ELITE, 6, "Claim a Chompy bird hat from Rantz after registering at least 1,000 kills.", Ev("chompy:hat:1000"))
        t(W, Tier.ELITE, 7, "Pickpocket an Elf.", Ev("pickpocket:elf"), "Thieving 85")

        val D = Diary.WILDERNESS
        t(D, Tier.EASY, 1, "Cast Low Alchemy at the Fountain of Rune.", Ev("spell:low level alchemy", FOUNTAIN), "Magic 21")
        t(D, Tier.EASY, 2, "Enter the Wilderness from the Ardougne or Edgeville lever.", Tele(LEVER))
        t(D, Tier.EASY, 3, "Pray at the Chaos Altar in the Western Wilderness.", PrayUp(CHAOS_ALTAR_WEST))
        t(D, Tier.EASY, 4, "Enter the Chaos Runecrafting temple.", Enter(CHAOS_RC))
        t(D, Tier.EASY, 5, "Kill a Mammoth in the Wilderness.", Kill(s("Mammoth"), WILDERNESS))
        t(D, Tier.EASY, 6, "Kill an Earth Warrior in the Wilderness beneath Edgeville.", Kill(s("Earth warrior"), WILDERNESS))
        t(D, Tier.EASY, 7, "Restore some prayer points at the demonic ruins.", PrayUp(DEMONIC_RUINS))
        t(D, Tier.EASY, 8, "Enter the King Black Dragon's lair.", Enter(KBD_LAIR))
        t(D, Tier.EASY, 9, "Collect 5 Red spiders' eggs from the Wilderness.", Gain(s("Red spiders' eggs"), WILDERNESS, count = 5))
        t(D, Tier.EASY, 10, "Mine some Iron ore in the Wilderness.", Gain(s("Iron ore"), WILDERNESS), "Mining 15")
        t(D, Tier.EASY, 11, "Have the Mage of Zamorak teleport you to the Abyss.", Tele(ABYSS))
        t(D, Tier.EASY, 12, "Equip any team cape in the Wilderness.", EquipCape("team-", WILDERNESS))
        t(D, Tier.MEDIUM, 1, "Mine some Mithril ore in the wilderness.", Gain(s("Mithril ore"), WILDERNESS), "Mining 55")
        t(D, Tier.MEDIUM, 2, "Chop some yew logs from a fallen Ent.", Gain(s("Yew logs"), WILDERNESS), "Woodcutting 61")
        t(D, Tier.MEDIUM, 3, "Enter the Wilderness Godwars Dungeon.", Ev("wildy_gwd:enter"))
        t(D, Tier.MEDIUM, 4, "Complete a lap of the Wilderness Agility course.", Ev("lap:34666"), "Agility 52")
        t(D, Tier.MEDIUM, 5, "Kill a Green Dragon.", Kill(s("Green dragon"), WILDERNESS))
        t(D, Tier.MEDIUM, 6, "Kill an Ankou in the Wilderness.", Kill(s("Ankou"), WILDERNESS))
        t(D, Tier.MEDIUM, 7, "Charge an Earth Orb.", Gain(s("Earth orb")), "Magic 60")
        t(D, Tier.MEDIUM, 8, "Kill a Bloodveld in the Wilderness Godwars Dungeon.", Ev("wildy_gwd:bloodveld"), "Slayer 50")
        t(D, Tier.MEDIUM, 9, "Talk to the Emblem Trader in Edgeville about emblems.", Talk(s("Emblem Trader")))
        t(D, Tier.MEDIUM, 10, "Smith a Golden helmet in the Resource Area.", Gain(s("Gold helmet"), RESOURCE_AREA), "Smithing 50")
        t(D, Tier.MEDIUM, 11, "Open the Muddy Chest in the lava maze.", Ev("chest:muddy"))
        t(D, Tier.HARD, 1, "Cast any of the 3 God spells against another player in the Wilderness.", Ev("god_spell:player", WILDERNESS), "Magic 60")
        t(D, Tier.HARD, 2, "Charge an Air Orb.", Gain(s("Air orb")), "Magic 66")
        t(D, Tier.HARD, 3, "Catch a Black Salamander in the Wilderness.", Gain(s("Black salamander"), WILDERNESS), "Hunter 67")
        t(D, Tier.HARD, 4, "Smith an Adamant scimitar in the Resource Area.", Gain(s("Adamant scimitar"), RESOURCE_AREA), "Smithing 75")
        t(D, Tier.HARD, 5, "Kill a Lava Dragon.", Kill(s("Lava dragon"), WILDERNESS))
        t(D, Tier.HARD, 6, "Kill the Chaos Elemental.", Kill(s("Chaos Elemental")))
        t(D, Tier.HARD, 7, "Kill the Crazy Archaeologist, Chaos Fanatic & Scorpia.", All(listOf(Kill(s("Crazy archaeologist")), Kill(s("Chaos Fanatic")), Kill(s("Scorpia")))))
        t(D, Tier.HARD, 8, "Take the agility shortcut from Trollheim into the Wilderness.", Ev("shortcut:trollheim wilderness"), "Agility 64")
        t(D, Tier.HARD, 9, "Kill a Spiritual warrior in the Wilderness Godwars Dungeon.", Ev("wildy_gwd:spiritual warrior"), "Slayer 68")
        t(D, Tier.HARD, 10, "Fish some Raw Lava Eel in the Wilderness.", Gain(s("Raw lava eel"), WILDERNESS), "Fishing 53")
        t(D, Tier.ELITE, 1, "Kill Callisto, Venenatis & Vet'ion.", All(listOf(Kill(s("Callisto", "Artio")), Kill(s("Venenatis", "Spindel")), Kill(s("Vet'ion", "Calvar'ion")))))
        t(D, Tier.ELITE, 2, "Teleport to Ghorrock.", Tele(GHORROCK), "Magic 96")
        t(D, Tier.ELITE, 3, "Fish and Cook a Dark Crab in the Resource Area.", Steps(listOf(Gain(s("Raw dark crab"), RESOURCE_AREA), Gain(s("Dark crab"), RESOURCE_AREA))), "Fishing 85, Cooking 90")
        t(D, Tier.ELITE, 4, "Smith a rune scimitar from scratch in the Resource Area.", Steps(listOf(Gain(s("Runite ore"), RESOURCE_AREA), Gain(s("Runite bar"), RESOURCE_AREA), Gain(s("Rune scimitar"), RESOURCE_AREA))), "Mining 85, Smithing 90")
        t(D, Tier.ELITE, 5, "Steal from the Rogues' chest.", Xp(Skills.THIEVING, ROGUES_CASTLE), "Thieving 84")
        t(D, Tier.ELITE, 6, "Slay a spiritual mage inside the wilderness Godwars Dungeon.", Ev("wildy_gwd:spiritual mage"), "Slayer 83")
        t(D, Tier.ELITE, 7, "Cut and burn some magic logs in the Resource Area.", Steps(listOf(Gain(s("Magic logs"), RESOURCE_AREA), Burn(s("Magic logs"), RESOURCE_AREA))), "Woodcutting 75, Firemaking 75")
    }

    val ALL: List<Task> get() = ALL_TASKS
}
