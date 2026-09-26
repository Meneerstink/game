package gg.rsmod.plugins.content.quests.foundation

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.tools.importer.AfkBasementMapTool
import gg.rsmod.game.tools.importer.QuestListCacheTool
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.items.osrs.OsrsQuestRequirements
import gg.rsmod.plugins.content.items.osrs.QuestStubs
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
import gg.rsmod.plugins.content.newplayer.AfkArea
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import gg.rsmod.plugins.content.newplayer.SummoningKit
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The new-player foundation (owner 2026-09-26): short quest progress and saving, the permanent quest choices, no way
 * round the unlocks, the quest list, the AFK area cap and the one-time Summoning start. Varps, varbits (real cache
 * definitions), skills and containers are real; the rest of the player is a relaxed mock.
 */
class NewPlayerFoundationTests {
    private class Account(isNew: Boolean, varps: VarpSet = VarpSet((0..MAX_VARP).toSet())) {
        val attributes = AttributeMap()
        val skills = SkillSet(25)
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val bank = ItemContainer(DEFINITIONS, BANK_KEY)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        var xpCalls = 0

        init {
            if (isNew) attributes[NEW_ACCOUNT_ATTR] = true
            every { world.definitions } returns DEFINITIONS
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.varps } returns varps
            every { player.skills } returns skills
            every { player.inventory } returns inventory
            every { player.bank } returns bank
            every { player.equipment } returns equipment
            every { player.addXp(any(), any(), any(), any()) } answers {
                xpCalls++
                val skill = firstArg<Int>()
                skills.setBaseXp(skill, skills.getCurrentXp(skill) + secondArg<Double>())
            }
        }
    }

    // ------------------------------------------------------------------------------------ short quests

    @Test
    fun `every short quest stores its progress in its own quest varp and it survives a reload`() {
        FoundationQuests.SHORT.forEach { quest ->
            val account = Account(isNew = true)
            assertEquals(0, quest.stage(account.player), quest.name)
            assertTrue(quest.steps.size in 2..4, "${quest.name}: 3-5 steps including the start")
            for (stage in 1..quest.steps.size) {
                quest.setStage(account.player, stage)
                val journal = quest.getObjective(account.player, stage).objectives
                assertTrue(journal.containsAll(quest.steps[stage - 1].journal), "${quest.name} stage $stage shows its step")
                // "Logout": the serializer keeps every non-zero varp; a new session starts from exactly those.
                val saved = VarpSet((0..MAX_VARP).toSet()).also { copy -> account.player.varps.getAll().filter { it.state != 0 }.forEach { copy.setState(it.id, it.state) } }
                val reloaded = Account(isNew = false, varps = saved)
                assertEquals(stage, quest.stage(reloaded.player), "${quest.name} stage $stage after reload")
                assertFalse(quest.isFinished(reloaded.player))
            }
            assertTrue(FoundationRewards.complete(account.player, quest))
            assertTrue(quest.isFinished(account.player), quest.name)
            assertEquals(quest.completedValue, quest.stage(account.player))
            assertTrue(quest.getObjective(account.player, quest.completedValue).objectives.last().contains("QUEST COMPLETE"))
        }
    }

    @Test
    fun `the four 667 quests use the cache's own complete values`() {
        assertEquals(15, DesertTreasure.completedValue)
        assertEquals(190, LunarDiplomacy.completedValue)
        assertEquals(90, KingsRansom.completedValue)
        assertEquals(90, TempleAtSenntisten.completedValue)
        FoundationQuests.SHORT.forEach { assertTrue(it.completedValue > it.steps.size, "${it.name} complete value is above its steps") }
    }

    @Test
    fun `completing a quest pays its reward exactly once`() {
        val account = Account(isNew = true)
        assertTrue(FoundationRewards.complete(account.player, DragonSlayerII))
        val qp = account.player.getVarp(Varps.QUEST_POINTS)
        val calls = account.xpCalls
        assertEquals(5, qp)
        assertEquals(80_000.0, account.skills.getCurrentXp(Skills.SMITHING))
        assertTrue(account.attributes[UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED] == true)
        assertFalse(FoundationRewards.complete(account.player, DragonSlayerII))
        assertEquals(qp, account.player.getVarp(Varps.QUEST_POINTS))
        assertEquals(calls, account.xpCalls)
        assertEquals(80_000.0, account.skills.getCurrentXp(Skills.SMITHING))
    }

    @Test
    fun `quest lamps wait on the account and respect their level`() {
        val account = Account(isNew = true)
        FoundationRewards.complete(account.player, DesertTreasureII)
        val lamps = FoundationRewards.pendingLamps(account.player)
        assertEquals(3, lamps.size)
        lamps.forEach { assertEquals(100_000, it.xp); assertEquals(60, it.minLevel) }
        assertEquals(1, account.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED))
        FoundationRewards.setPendingLamps(account.player, lamps.drop(1))
        assertEquals(2, FoundationRewards.pendingLamps(account.player).size)
    }

    // ----------------------------------------------------------------------------------- choice groups

    @Test
    fun `a choice completes that quest only and can never be made twice`() {
        val account = Account(isNew = true)
        assertEquals(QuestChoices.CHOICE, QuestChoices.cohort(account.player))
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.MAGIC, LunarDiplomacy))
        assertTrue(LunarDiplomacy.isFinished(account.player))
        assertFalse(DesertTreasure.isFinished(account.player))
        assertFalse(DesertTreasureII.isFinished(account.player))
        assertEquals(LunarDiplomacy, QuestChoices.chosen(account.player, ChoiceGroup.MAGIC))
        val xp = account.skills.getCurrentXp(Skills.MAGIC)
        assertEquals(QuestChoices.Result.ALREADY_CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.MAGIC, DesertTreasure))
        assertEquals(QuestChoices.Result.ALREADY_CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.MAGIC, LunarDiplomacy))
        assertFalse(DesertTreasure.isFinished(account.player))
        assertEquals(xp, account.skills.getCurrentXp(Skills.MAGIC))
        assertTrue(QuestChoices.available(account.player, ChoiceGroup.MAGIC).isEmpty())
        // Another group is independent; a quest from another group is refused.
        assertEquals(QuestChoices.Result.NOT_IN_GROUP, QuestChoices.choose(account.player, ChoiceGroup.PRAYER, DragonSlayerII))
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.PRAYER, KingsRansom))
        assertEquals(QuestChoices.Result.ALREADY_CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.PRAYER, TempleAtSenntisten))
    }

    @Test
    fun `the choice is written before the reward so a replayed click cannot pay twice`() {
        val account = Account(isNew = true)
        QuestChoices.choose(account.player, ChoiceGroup.GEAR, MonkeyMadnessII)
        val slayer = account.skills.getCurrentXp(Skills.SLAYER)
        repeat(3) { QuestChoices.choose(account.player, ChoiceGroup.GEAR, MonkeyMadnessII) }
        assertEquals(slayer, account.skills.getCurrentXp(Skills.SLAYER))
        assertEquals(4, account.player.getVarp(Varps.QUEST_POINTS))
    }

    @Test
    fun `a quest finished by playing cannot also be the choice`() {
        val account = Account(isNew = true)
        FoundationRewards.complete(account.player, SongOfTheElves)
        assertEquals(QuestChoices.Result.ALREADY_COMPLETE, QuestChoices.choose(account.player, ChoiceGroup.GEAR, SongOfTheElves))
        assertFalse(SongOfTheElves in QuestChoices.available(account.player, ChoiceGroup.GEAR))
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.GEAR, DragonSlayerII))
    }

    @Test
    fun `existing accounts keep every quest completed and get no choices`() {
        val legacy = Account(isNew = false)
        assertEquals(QuestChoices.LEGACY, QuestChoices.cohort(legacy.player))
        QuestChoices.applyLegacy(legacy.player)
        FoundationQuests.SHORT.forEach { assertTrue(it.isFinished(legacy.player), it.name); assertTrue(QuestChoices.unlockHeld(legacy.player, it), it.name) }
        ChoiceGroup.values().forEach { assertTrue(QuestChoices.available(legacy.player, it).isEmpty()) }
        assertEquals(QuestChoices.Result.LEGACY_ACCOUNT, QuestChoices.choose(legacy.player, ChoiceGroup.MAGIC, DesertTreasure))
        assertEquals(0.0, legacy.skills.getCurrentXp(Skills.MAGIC), "legacy completion pays no experience again")
        // The cohort is stored: a later login of the same account stays legacy even though nothing else changes.
        legacy.attributes[NEW_ACCOUNT_ATTR] = true
        assertEquals(QuestChoices.LEGACY, QuestChoices.cohort(legacy.player))
    }

    @Test
    fun `new accounts start with all eight choice quests open`() {
        val account = Account(isNew = true)
        FoundationQuests.SHORT.forEach { assertFalse(it.isFinished(account.player), it.name); assertFalse(QuestChoices.unlockHeld(account.player, it), it.name) }
        assertEquals(8, ChoiceGroup.values().sumOf { it.quests.size })
        assertEquals(FoundationQuests.SHORT.toSet(), ChoiceGroup.values().flatMap { it.quests }.toSet())
    }

    // ------------------------------------------------------------------------------------ no bypasses

    @Test
    fun `no unlock is available without the quest`() {
        val account = Account(isNew = true)
        account.skills.setBaseLevel(Skills.MAGIC, 99)
        account.skills.setBaseLevel(Skills.PRAYER, 99)
        account.skills.setBaseLevel(Skills.DEFENCE, 99)
        assertFalse(Spellbooks.select(account.player, gg.rsmod.plugins.api.Spellbook.ANCIENT))
        assertFalse(Spellbooks.select(account.player, gg.rsmod.plugins.api.Spellbook.LUNAR))
        AncientCurses.unlock(account.player)
        assertNotEquals(true, account.attributes[AncientCurses.UNLOCKED_ATTR], "the old coin ritual is gone")
        assertTrue(Prayers.knightWavesRefusal(account.player, Prayer.PIETY) != null)
        assertTrue(Prayers.knightWavesRefusal(account.player, Prayer.CHIVALRY) != null)
        assertFalse(QuestStubs.songOfTheElvesCompleted(account.player))
        assertTrue(OsrsQuestRequirements.wearRefusal(account.player, Items.MAGUS_RING) != null)
        assertTrue(OsrsQuestRequirements.wearRefusal(account.player, Items.HEAVY_BALLISTA) != null)
        assertFalse(OsrsQuestRequirements.canUpgradeSceptre(account.player))

        QuestChoices.choose(account.player, ChoiceGroup.PRAYER, KingsRansom)
        assertNull(Prayers.knightWavesRefusal(account.player, Prayer.PIETY))
        account.skills.setBaseLevel(Skills.DEFENCE, 69)
        assertTrue(Prayers.knightWavesRefusal(account.player, Prayer.PIETY)!!.contains("Defence level of 70"))
        assertNull(Prayers.knightWavesRefusal(account.player, Prayer.CHIVALRY))
    }

    @Test
    fun `no main source outside the foundation writes a choice quest or its unlock`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins")
        val allowed = setOf("FoundationQuests.kt", "QuestChoices.kt", "FoundationRewards.kt", "ShortQuest.kt")
        val forbidden =
            listOf(
                "ANCIENT_MAGIC_UNLOCKED] = true", "LUNAR_MAGIC_UNLOCKED] = true", "UNLOCKED_ATTR] = true",
                "AVAS_ASSEMBLER_UNLOCKED] = true", "ANCIENT_RINGS_UNLOCKED] = true", "DESERT_TREASURE_II_UNLOCKED] = true",
                "DESERT_TREASURE_PROGRESS,", "LUNAR_DIPLOMACY_PROGRESS,", "KINGS_RANSOM_PROGRESS,", "THE_TEMPLE_AT_SENNTISTEN_PROGRESS,",
                "KNIGHT_WAVES_VARBIT,", "setVarbit(3909",
            )
        val offenders =
            root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.name.endsWith(".kts")) && it.name !in allowed }
                .flatMap { file ->
                    file.readLines().filter { line -> !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") && forbidden.any { it in line } && ("set" in line || "] = true" in line) }
                        .map { "${file.name}: ${it.trim()}" }
                }.toList()
        assertTrue(offenders.isEmpty(), "unlock written outside the foundation:\n" + offenders.joinToString("\n"))
        val rewards = File("src/main/kotlin/gg/rsmod/plugins/content/unlocks/UnlockNpcRewards.kt").readText()
        listOf("fun unlockAncientMagic", "fun unlockLunarMagic", "fun giveAncientHymnal", "fun unlockAncientCurses", "fun completeDragonSlayerII", "fun completeDesertTreasureII", "fun unlockSummoning")
            .forEach { assertFalse(it in rewards, "$it must stay removed") }
        listOf("prayeraltar/prayer_altar.plugin.kts", "poh/player_house.plugin.kts").forEach {
            val file = root.walkTopDown().first { f -> f.path.replace('\\', '/').endsWith(it) }
            assertFalse("Unlock Ancient Curses" in file.readText(), "$it still sells the curses")
        }
    }

    // ------------------------------------------------------------------------------------ quest list

    @Test
    fun `the quest list has the ten OSRS quests and puts unfinished quests on top`() {
        assertTrue(QuestListCacheTool.plan(CACHE_PATH).isEmpty(), "QuestListCacheTool must be applied to the cache")
        assertEquals(mapOf(0 to 1, 1 to 0, 2 to 2), QuestListCacheTool.PROGRESS_ORDER, "In progress, Not started, Complete")
        val slots = (FoundationQuests.SHORT.filter { !it.usesVarbits } + FoundationQuests.COMPLETED).map { it.slot }.toSet()
        assertEquals(QuestListCacheTool.QUESTS.map { it.slot }.toSet(), slots)
        QuestListCacheTool.QUESTS.forEach { entry ->
            val quest = (FoundationQuests.SHORT + FoundationQuests.COMPLETED).first { it.slot == entry.slot }
            assertEquals(entry.varp, quest.questId, entry.name)
            assertEquals(entry.complete, quest.stages, entry.name)
        }
        val account = Account(isNew = true)
        FoundationQuests.applyQuestListOrder(account.player)
        assertEquals(FoundationQuests.GROUP_BY_PROGRESS, account.player.getVarbit(FoundationQuests.QUEST_LIST_GROUPING_VARBIT))
        assertEquals(0, account.player.getVarbit(FoundationQuests.QUEST_LIST_DIRECTION_VARBIT))
        FoundationQuests.completeListedQuests(account.player)
        FoundationQuests.COMPLETED.forEach { assertEquals(1, account.player.getVarp(it.questId), it.name) }
    }

    // --------------------------------------------------------------------------------------- AFK area

    @Test
    fun `AFK training stops exactly on level 55 and a level 55 skill cannot start`() {
        val config = NewPlayerConfig.DEFAULTS
        val account = Account(isNew = true)
        var ticks = 0
        while (AfkArea.train(account.player, Skills.MINING, config)) ticks++
        assertEquals(SkillSet.getXpForLevel(55), account.skills.getCurrentXp(Skills.MINING))
        assertEquals(55, account.skills.getMaxLevel(Skills.MINING))
        assertTrue(AfkArea.atCap(account.player, Skills.MINING, config))
        assertEquals(0.0, AfkArea.tickXp(account.player, Skills.MINING, config))
        assertFalse(AfkArea.train(account.player, Skills.MINING, config))
        assertEquals(SkillSet.getXpForLevel(55), account.skills.getCurrentXp(Skills.MINING))
        assertTrue(ticks > 1000, "training takes real time at the default rate")
        account.skills.setBaseLevel(Skills.COOKING, 70)
        assertTrue(AfkArea.atCap(account.player, Skills.COOKING, config))
        assertEquals(0.0, AfkArea.tickXp(account.player, Skills.COOKING, config))
    }

    @Test
    fun `the AFK area has one station per non-combat skill and matches the map tool`() {
        val expected = setOf(
            Skills.COOKING, Skills.WOODCUTTING, Skills.FLETCHING, Skills.FISHING, Skills.FIREMAKING, Skills.CRAFTING, Skills.SMITHING,
            Skills.MINING, Skills.HERBLORE, Skills.AGILITY, Skills.THIEVING, Skills.FARMING, Skills.RUNECRAFTING, Skills.HUNTER, Skills.CONSTRUCTION,
        )
        assertEquals(expected, AfkArea.TRAINABLE)
        assertEquals(expected, AfkArea.STATIONS.map { it.skill }.toSet())
        assertEquals(AfkArea.STATIONS.size, AfkArea.STATIONS.map { it.skill }.distinct().size)
        assertEquals(AfkBasementMapTool.STATIONS.size, AfkArea.STATIONS.size)
        AfkBasementMapTool.STATIONS.forEachIndexed { index, station ->
            assertEquals(AfkBasementMapTool.FIRST_STATION_LOC + index, AfkArea.STATIONS[index].loc)
            assertEquals(SKILL_BY_NAME.getValue(station.skill), AfkArea.STATIONS[index].skill, station.skill)
        }
        assertEquals(AfkBasementMapTool.REGION_ID, AfkArea.REGION_ID)
        assertEquals(AfkBasementMapTool.BASE_X + AfkBasementMapTool.ROOM_X.first, AfkArea.MIN_X)
        assertEquals(AfkBasementMapTool.BASE_X + AfkBasementMapTool.ROOM_X.last, AfkArea.MAX_X)
        assertEquals(AfkBasementMapTool.BASE_Z + AfkBasementMapTool.ROOM_Z.first, AfkArea.MIN_Z)
        assertEquals(AfkBasementMapTool.BASE_Z + AfkBasementMapTool.ROOM_Z.last, AfkArea.MAX_Z)
        assertEquals(AfkBasementMapTool.BASE_X + AfkBasementMapTool.STAIRS_LX, AfkArea.BASEMENT_STAIRS.x)
        assertEquals(AfkBasementMapTool.BASE_Z + AfkBasementMapTool.STAIRS_LZ, AfkArea.BASEMENT_STAIRS.z)
        assertTrue(AfkArea.contains(AfkArea.BASEMENT_ARRIVAL))
        assertTrue(GuardedZones.contains(AfkArea.BASEMENT_ARRIVAL), "the basement is a safe zone")
        val decor = File("../../data/cfg/home_decor.txt").readText()
        assertTrue("obj ${AfkArea.HALL_STAIRS_DOWN} ${AfkArea.HALL_STAIRS.x} ${AfkArea.HALL_STAIRS.z} 0 10 0" in decor, "the hall's way down is placed")
        AfkArea.STATIONS.forEach { assertTrue(DEFINITIONS.getNullable(gg.rsmod.game.fs.def.ObjectDef::class.java, it.loc) != null, "station loc ${it.loc} in cache") }
    }

    // --------------------------------------------------------------------------------------- Summoning

    @Test
    fun `the Summoning start is handed out once and reaches level 55`() {
        val config = NewPlayerConfig.DEFAULTS
        val kit = SummoningKit.compute(config, SummoningKit.WOLF_WHISTLE_XP, SummoningKit.WOLF_WHISTLE_GOLD_CHARMS)
        // Infusing exactly the kit's pouches from 276 XP ends at level 55 or above.
        var xp = SummoningKit.WOLF_WHISTLE_XP
        kit.pouches.forEach { (pouch, count) -> repeat(count) { xp += pouch.creationExperience * SummoningKit.levelCurve(SkillSet.getLevelForXp(xp)) } }
        assertTrue(SkillSet.getLevelForXp(xp) >= 55, "kit reaches level ${SkillSet.getLevelForXp(xp)}")
        kit.pouches.forEach { (pouch, count) ->
            val shardsNeeded = kit.pouches.entries.sumOf { it.key.shards * it.value }
            assertEquals(shardsNeeded, kit.items.first { it.first == Items.SPIRIT_SHARDS }.second)
            assertTrue(count > 0, pouch.name)
        }
        val account = Account(isNew = true)
        repeat(27) { account.inventory.add(Items.BRONZE_DAGGER) }
        assertTrue(SummoningKit.claim(account.player, config))
        assertTrue(account.attributes[UnlockNpcRewards.SUMMONING_REWARDED] == true)
        assertEquals(SummoningKit.WOLF_WHISTLE_XP, account.skills.getCurrentXp(Skills.SUMMONING))
        val shards = account.inventory.getItemCount(Items.SPIRIT_SHARDS) + account.bank.getItemCount(Items.SPIRIT_SHARDS)
        assertTrue(shards > 0)
        assertTrue(account.bank.getItemCount(Items.SPIRIT_SHARDS) > 0, "a full inventory sends the rest to the bank")
        assertFalse(SummoningKit.claim(account.player, config))
        assertEquals(shards, account.inventory.getItemCount(Items.SPIRIT_SHARDS) + account.bank.getItemCount(Items.SPIRIT_SHARDS))
    }

    @Test
    fun `every quest npc can be talked to and the basement stairs climb both ways`() {
        val npcs =
            FoundationQuests.SHORT.flatMap { quest -> listOf(quest.startNpc) + quest.steps.map { it.npc } }.toSet() + NewPlayerConfig.DEFAULTS.choiceNpc
        npcs.forEach { id ->
            val options = DEFINITIONS.get(gg.rsmod.game.fs.def.NpcDef::class.java, id).options.filterNotNull().map { it.lowercase() }
            assertTrue("talk-to" in options, "npc $id has no Talk-to: $options")
        }
        val down = DEFINITIONS.get(gg.rsmod.game.fs.def.ObjectDef::class.java, AfkArea.HALL_STAIRS_DOWN).options.filterNotNull().map { it.lowercase() }
        val up = DEFINITIONS.get(gg.rsmod.game.fs.def.ObjectDef::class.java, AfkArea.BASEMENT_STAIRS_UP).options.filterNotNull().map { it.lowercase() }
        assertTrue("climb-down" in down, "$down")
        assertTrue("climb-up" in up, "$up")
        AfkArea.STATIONS.forEach {
            val options = DEFINITIONS.get(gg.rsmod.game.fs.def.ObjectDef::class.java, it.loc).options.filterNotNull().map { o -> o.lowercase() }
            assertTrue("train" in options, "station ${it.loc}: $options")
        }
    }

    // ------------------------------------------------------------------------------------------ config

    @Test
    fun `new_player yml ships the documented defaults`() {
        assertEquals(NewPlayerConfig.DEFAULTS, NewPlayerConfig.load(File("../../data/cfg/new_player.yml")))
    }

    companion object {
        private const val MAX_VARP = 8000
        private val SKILL_BY_NAME =
            mapOf(
                "Cooking" to Skills.COOKING, "Firemaking" to Skills.FIREMAKING, "Fishing" to Skills.FISHING, "Herblore" to Skills.HERBLORE,
                "Woodcutting" to Skills.WOODCUTTING, "Fletching" to Skills.FLETCHING, "Crafting" to Skills.CRAFTING, "Smithing" to Skills.SMITHING,
                "Mining" to Skills.MINING, "Construction" to Skills.CONSTRUCTION, "Runecrafting" to Skills.RUNECRAFTING, "Thieving" to Skills.THIEVING,
                "Agility" to Skills.AGILITY, "Farming" to Skills.FARMING, "Hunter" to Skills.HUNTER,
            )
        private val CACHE_PATH = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(CACHE_PATH))
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
