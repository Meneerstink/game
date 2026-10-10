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
            assertTrue(quest.steps.size in 4..7, "${quest.name}: 5-8 steps including the start (10-20 minutes, owner 2026-09-26)")
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
        // Quest Points are the completed quests of the list (QuestStatus): everything but the eight choice quests, plus this one.
        assertEquals(gg.rsmod.plugins.content.quests.QuestStatus.MAX_POINTS - 26 + 5, qp)
        assertEquals(80_000.0, account.skills.getCurrentXp(Skills.SMITHING))
        assertTrue(account.attributes[UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED] == true)
        assertEquals(4, FoundationRewards.pendingLamps(account.player).count { it.xp == 25_000 }, "Ellen: four 25,000 XP rewards")
        assertFalse(FoundationRewards.complete(account.player, DragonSlayerII))
        assertEquals(4, FoundationRewards.pendingLamps(account.player).size, "no second set of lamps")
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
        lamps.forEach { assertEquals(100_000, it.xp); assertEquals(1, it.minLevel) } // owner 2026-09-26: no lamp levels
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
        assertEquals(gg.rsmod.plugins.content.quests.QuestStatus.MAX_POINTS - 26 + 4, account.player.getVarp(Varps.QUEST_POINTS))
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
        // QuestsSuite.kt (conform quests) resets its own never-saved headless bots to a fresh account before playing; the intro's
        // pk scene (IntroGuardScene.kt) arms its two never-saved actor bots with the curses and Ancient Magicks they fight with.
        val allowed = setOf("FoundationQuests.kt", "QuestChoices.kt", "FoundationRewards.kt", "ShortQuest.kt", "QuestsSuite.kt", "IntroGuardScene.kt")
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
        assertEquals(mapOf(0 to 0, 1 to 1), QuestListCacheTool.PROGRESS_ORDER, "Not completed, Completed (quest list part 2)")
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
        // Only once per account: a player who picks another grouping keeps it on the next login.
        account.player.setVarbit(FoundationQuests.QUEST_LIST_GROUPING_VARBIT, 2)
        FoundationQuests.applyQuestListOrder(account.player)
        assertEquals(2, account.player.getVarbit(FoundationQuests.QUEST_LIST_GROUPING_VARBIT))
        gg.rsmod.plugins.content.quests.QuestStatus.sync(account.player)
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
    }

    @Test
    fun `every quest npc post and arrival tile is walkable floor on the real map`() {
        val library = CacheLibrary(CACHE_PATH)
        val keys = gg.rsmod.game.tools.importer.ModernRegionProbeTool.loadKeys(File("../../data/xteas/xteas.json"))
        val maps = library.index(5)
        val tiles =
            FoundationQuests.NPC_POSTS.map { "npc ${it.npc}" to it.tile } +
                FoundationQuests.SHORT.flatMap { q -> q.steps.mapNotNull { s -> s.location?.let { "${q.name} travel to ${s.place}" to it } } } +
                listOf("basement arrival" to AfkArea.BASEMENT_ARRIVAL)
        fun blockReason(tile: gg.rsmod.game.model.Tile): String? {
            val sx = tile.x shr 6
            val sz = tile.z shr 6
            val lx = tile.x and 63
            val lz = tile.z and 63
            val mapGroup = maps.archive("m${sx}_$sz") ?: return "no map square"
            val map = gg.rsmod.game.tools.importer.Rev667TileCodec.decode(library.data(5, mapGroup.id, 0)!!)
            val bridged = map.tiles[1][lx][lz].flags and 2 != 0
            val plane = if (bridged && tile.height < 3) tile.height + 1 else tile.height
            if (map.tiles[plane][lx][lz].flags and 1 != 0) return "blocked tile flag"
            val locGroup = maps.archive("l${sx}_$sz") ?: return null
            val key = keys[(sx shl 8) or sz]?.takeIf { k -> k.any { it != 0 } }
            val locs = gg.rsmod.game.tools.importer.Rev667LocCodec.decode(library.data(5, locGroup.id, 0, key)!!)
            locs.filter { it.plane == tile.height && it.type in 10..11 }.forEach { loc ->
                val def = DEFINITIONS.getNullable(gg.rsmod.game.fs.def.ObjectDef::class.java, loc.id) ?: return@forEach
                if (!def.solid) return@forEach
                val w = if (loc.rotation and 1 == 1) def.length else def.width
                val d = if (loc.rotation and 1 == 1) def.width else def.length
                if (lx in loc.localX until loc.localX + w && lz in loc.localZ until loc.localZ + d) return "covered by loc ${loc.id} '${def.name}'"
            }
            return null
        }
        val problems = mutableListOf<String>()
        tiles.forEach { (label, tile) ->
            val reason = blockReason(tile) ?: return@forEach
            val free =
                (1..4).asSequence().flatMap { r -> (-r..r).asSequence().flatMap { dx -> (-r..r).asSequence().map { dz -> gg.rsmod.game.model.Tile(tile.x + dx, tile.z + dz, tile.height) } } }
                    .firstOrNull { blockReason(it) == null }
            problems += "$label $tile: $reason (nearest free: $free)"
        }
        library.close()
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /** Every region spawn (spawns_*.plugin.kts) as file name, npc id and tile. */
    private fun regionSpawns(): List<Triple<String, Int, gg.rsmod.game.model.Tile>> {
        val constant = Regex("""Npcs\.(\w+)""")
        fun id(value: String): Int? =
            value.toIntOrNull() ?: constant.matchEntire(value)?.let { m ->
                runCatching { gg.rsmod.plugins.api.cfg.Npcs::class.java.getField(m.groupValues[1]).getInt(null) }.getOrNull()
            }
        val spawn = Regex("""spawn_npc\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
        fun field(body: String, key: String) = Regex("""$key\s*=\s*([\w.]+)""").find(body)?.groupValues?.get(1)
        return File("src/main/kotlin/gg/rsmod/plugins/content/areas/spawns").listFiles { f -> f.name.endsWith(".plugin.kts") }!!.flatMap { file ->
            spawn.findAll(file.readText()).mapNotNull { m ->
                val body = m.groupValues[1]
                val npc = field(body, "npc")?.let(::id) ?: return@mapNotNull null
                val x = field(body, "x")?.toIntOrNull() ?: return@mapNotNull null
                val z = field(body, "z")?.toIntOrNull() ?: return@mapNotNull null
                Triple(file.name, npc, gg.rsmod.game.model.Tile(x, z, field(body, "height")?.toIntOrNull() ?: 0))
            }.toList()
        }
    }

    /**
     * Quests run 2026-10-10: Daero's glider set the player down on APE_ATOLL - Garkor's own post, so they stood inside each
     * other. Quest travels land beside the npc (FoundationQuests.landing); a boat or glider trip that moves the player straight
     * to a place (`player.moveTo(PLACE)` in FoundationQuests.kt) must not use a tile an npc stands on.
     */
    @Test
    fun `no boat or glider trip lands on an npc`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/FoundationQuests.kt").readText()
        val tiles = Regex("""private val (\w+) = Tile\((\d+), (\d+)(?:, (\d+))?\)""").findAll(source)
            .associate { it.groupValues[1] to gg.rsmod.game.model.Tile(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].ifEmpty { "0" }.toInt()) }
        val moves = Regex("""player\.moveTo\((\w+)\)""").findAll(source).map { it.groupValues[1] }.toSet()
        assertTrue(moves.isNotEmpty(), "no direct quest moves found - the test reads the wrong file")
        val taken = regionSpawns().map { (file, npc, at) -> at to "npc $npc ($file)" } + FoundationQuests.NPC_POSTS.map { it.tile to "quest post ${it.npc}" }
        val problems = moves.flatMap { name -> val tile = tiles[name] ?: return@flatMap listOf("$name: no tile"); taken.filter { it.first == tile }.map { "$name $tile: ${it.second}" } }
        assertTrue(problems.isEmpty(), problems.joinToString("; "))
    }

    /**
     * Quests run 2026-10-10: Lunar Diplomacy landed the player on a wild suqah's own spawn tile; after the quest suqah the wild
     * one kept him in combat and "Take me to the Grand Exchange hall" failed. No aggressive world npc spawns within its hunt
     * range (5, data/cfg/npcs/hunt-modes.json) of a quest landing or fight tile.
     */
    @Test
    fun `no aggressive world npc spawns on a quest landing`() {
        val aggressive = com.google.gson.JsonParser().parse(File("../../data/cfg/npcs/combat-defs.json").readText()).asJsonArray
            .map { it.asJsonObject }.filter { it.get("aggressive")?.asBoolean == true }.map { it.get("id").asInt }.toSet()
        val tiles =
            FoundationQuests.SHORT.flatMap { q ->
                q.steps.flatMap { s -> listOfNotNull(s.location?.let { "${q.name} travel to ${s.place}" to it }, s.fight?.let { "${q.name} fight ${it.name}" to it.tile }) }
            }.distinctBy { it.second }
        val problems = mutableListOf<String>()
        regionSpawns().forEach { (file, npc, at) ->
            if (npc !in aggressive) return@forEach
            tiles.forEach { (label, tile) ->
                if (tile.height == at.height && Math.max(Math.abs(tile.x - at.x), Math.abs(tile.z - at.z)) <= 5) problems += "$label $tile: aggressive npc $npc spawns at $at ($file)"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /**
     * Quests run 2026-10-10: the world's own Eblis (1922, a multinpc showing Eblis), Lokar (413) and Elena (335) stood a tile
     * from the quests' posted ones - two of each, one wandering (and Edmond in East Ardougne). Every npc a quest sends the
     * player to is the only npc of its name within 20 tiles of that place: one region spawn (spawns_*.plugin.kts, multinpc
     * forms included) or one quest post.
     */
    @Test
    fun `no world spawn doubles a quest npc post`() {
        val npcDef = { id: Int -> DEFINITIONS.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, id) }
        fun names(id: Int): Set<String> {
            val def = npcDef(id) ?: return emptySet()
            val forms = def.transforms?.filter { it >= 0 }?.mapNotNull { npcDef(it)?.name } ?: emptyList()
            return (forms + def.name).filter { it.isNotBlank() && it != "null" }.toSet()
        }
        // Every npc a quest sends the player to, where it sends him: the posts, the start npcs and every step npc at its place
        // (Song of the Elves: Edmond 714 in East Ardougne and a 3213 multinpc showing Edmond in his garden).
        val wanted =
            FoundationQuests.NPC_POSTS.map { it.npc to it.tile } +
                FoundationQuests.SHORT.flatMap { q ->
                    listOfNotNull(q.startLocation?.let { q.startNpc to it }) + q.steps.mapNotNull { s -> s.location?.takeIf { s.fight == null }?.let { s.npc to it } }
                }
        val spawns = regionSpawns()
        val problems = mutableListOf<String>()
        wanted.distinct().forEach { (questNpc, place) ->
            val name = npcDef(questNpc)?.name ?: return@forEach
            val near = spawns.filter { (_, npc, at) -> at.height == place.height && Math.abs(place.x - at.x) <= 20 && Math.abs(place.z - at.z) <= 20 && name in names(npc) }
            val posted = FoundationQuests.NPC_POSTS.count { it.npc == questNpc && it.tile.height == place.height && Math.abs(place.x - it.tile.x) <= 20 && Math.abs(place.z - it.tile.z) <= 20 }
            if (near.size + posted > 1) problems += "'$name' near $place: ${near.map { (f, n, t) -> "$n at ${t.x},${t.z} ($f)" } + List(posted) { "quest post" }}"
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /**
     * Owner 2026-10-10: every short quest shows a recommended combat level - its strongest foe's level, at most 126 - in the
     * journal, the Quest Helper side panel and overlay; the Quest Tutor warns exactly the players below it.
     */
    @Test
    fun `recommended combat level is the strongest foe capped at 126`() {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        FoundationQuests.SHORT.forEach { quest ->
            val strongest = quest.strongestFoe(player)
            assertTrue(strongest != null, "${quest.name} has a fight")
            val recommended = quest.recommendedCombat(player)!!
            assertEquals(minOf(strongest!!.second, 126), recommended, quest.name)
            assertEquals("Recommended: combat level $recommended+", quest.recommendedLine(player))
            every { player.combatLevel } returns recommended - 1
            assertTrue(quest.combatWarning(player) != null, "${quest.name}: combat ${recommended - 1} is warned")
            every { player.combatLevel } returns recommended
            assertNull(quest.combatWarning(player), "${quest.name}: combat $recommended is not warned")
        }
    }

    /**
     * Quests run 2026-10-10: a new account's quest list did not show Desert Treasure, Lunar Diplomacy, King's Ransom or The
     * Temple at Senntisten at all - clientscript 2162 hides a not-started quest whose requirements (2149: struct params
     * 859-898) are not met, and the server lets anyone start them. Every choice quest's struct carries no start requirement
     * (QuestStartRequirementTool) and every choice slot is one of the tool's slots.
     */
    @Test
    fun `every choice quest is listed for a new player`() {
        val choiceSlots = FoundationQuests.SHORT.map { it.slot }.toSet()
        assertEquals(choiceSlots, gg.rsmod.game.tools.importer.QuestStartRequirementTool.CHOICE_SLOTS.toSet())
        assertTrue(gg.rsmod.game.tools.importer.QuestStartRequirementTool.plan(CACHE_PATH).isEmpty(), "QuestStartRequirementTool must be applied to the cache")
    }

    /**
     * Quests run 2026-10-10: the Quest Helper overlay (interface 1166, QuestHelperPanelImportTool) cut 7 of the 15 tips after
     * their second line ("... drink an antifire potion: Elvarg" - "breathes fire." was lost). Every tip fits the 2 tip lines and
     * every step instruction, with the longest floor hint, the 6 instruction lines - word-wrapped at the boxes' 192 px in the
     * cache's own p11 glyph widths (font metrics, index 13), as the client wraps them.
     */
    @Test
    fun `every quest helper tip and instruction fits the overlay`() {
        val library = CacheLibrary(CACHE_PATH)
        val font = library.index(8).archive("p11_full")!!.id
        val data = library.data(13, font, 0)!!
        library.close()
        val glyphs = IntArray(256) { data[2 + it].toInt() and 0xFF }
        val box = gg.rsmod.game.tools.importer.QuestHelperPanelImportTool.WIDTH - 12
        fun lines(text: String): Int {
            var count = 1
            var line = 0
            for (word in text.replace(Regex("<[^>]+>"), "").split(' ')) {
                val w = word.sumOf { glyphs[it.code and 0xFF] }
                val space = if (line == 0) 0 else glyphs[' '.code]
                if (line > 0 && line + space + w > box) {
                    count++
                    line = w
                } else {
                    line += space + w
                }
            }
            return count
        }
        val problems = mutableListOf<String>()
        FoundationQuests.SHORT.forEach { quest ->
            quest.steps.forEachIndexed { i, step ->
                step.tip?.let { if (lines("Tip: $it") > 2) problems += "${quest.name} step ${i + 1} tip: ${lines("Tip: $it")} lines" }
                val text = step.journal.joinToString(" ") + " Ask ${FoundationQuests.NPC_NAMES[step.npc] ?: "Sir Lancelot"} (first floor) to take you there."
                if (lines(text) > 6) problems += "${quest.name} step ${i + 1} instruction: ${lines(text)} lines"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("; "))
    }

    /**
     * Quests run 2026-10-10: Damis's OSRS table drops the Diamond of Shadow while the step hands it out too - two diamonds,
     * one on the ground. A fight step's own quest items come from the step only, never from its foes' drop tables.
     */
    @Test
    fun `no quest foe drops the item its step hands out`() {
        val tables = com.google.gson.JsonParser().parse(File("../../data/cfg/npcs/drop-tables.json").readText()).asJsonArray
            .map { it.asJsonObject }.associateBy { it.get("id").asInt }
        val problems = mutableListOf<String>()
        FoundationQuests.SHORT.forEach { quest ->
            quest.steps.forEach { step ->
                val fight = step.fight ?: return@forEach
                val handed = step.gives.map { it.first }.toSet()
                (listOf(fight.npc) + fight.chain.map { it.first }).forEach { foe ->
                    val table = tables[foe] ?: return@forEach
                    listOf("guaranteed", "main", "tertiary").forEach { part ->
                        table.getAsJsonArray(part)?.forEach { e ->
                            val item = e.asJsonObject.get("item")?.asInt
                            if (item != null && item in handed) problems += "${quest.name}: foe $foe drops item $item ($part) that the step hands out"
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `every short quest has fights against real npcs with their real combat levels`() {
        val defs = com.google.gson.JsonParser().parse(File("../../data/cfg/npcs/combat-defs.json").readText()).asJsonArray
            .map { it.asJsonObject }.associateBy { it.get("id").asInt }
        FoundationQuests.SHORT.forEach { quest ->
            val fights = quest.steps.mapNotNull { it.fight }
            // Desert Treasure: Dessous, Kamil, Damis and Fareed; the others one or two fights.
            assertEquals(if (quest == DesertTreasure) 4 else fights.size.coerceIn(1, 2), fights.size, "${quest.name} fights")
            fights.flatMap { it.chain }.forEach { (npc, _) -> assertTrue(defs[npc] != null, "${quest.name}: chained foe $npc has a combat definition") }
            fights.forEach { fight ->
                val def = defs[fight.npc]
                assertTrue(def != null, "${quest.name}: npc ${fight.npc} has a combat definition")
                assertEquals(fight.level, def!!.get("combat_level").asInt, "${quest.name}: journal level matches the npc")
                fight.secondForm?.let { assertTrue(defs[it] != null, "${quest.name}: second form $it has a combat definition") }
                val step = quest.steps.first { it.fight == fight }
                assertEquals(fight.tile, step.location, "${quest.name}: the fight tile is the step's travel tile (walkability-checked)")
                assertTrue(step.journal.joinToString(" ").contains("level ${fight.level}"), "${quest.name}: journal shows the level")
            }
        }
    }

    @Test
    fun `a fight kill completes its step with the step's items`() {
        val account = Account(isNew = true)
        // Desert Treasure: each of the four warriors' kills hands out his diamond.
        DesertTreasure.steps.forEachIndexed { index, step ->
            if (step.fight == null) return@forEachIndexed
            val fightStage = index + 1
            DesertTreasure.setStage(account.player, fightStage)
            FoundationQuests.completeStep(account.player, DesertTreasure, fightStage)
            assertEquals(fightStage + 1, DesertTreasure.stage(account.player))
        }
        listOf(Items.BLOOD_DIAMOND, Items.ICE_DIAMOND, Items.SMOKE_DIAMOND, Items.SHADOW_DIAMOND).forEach { assertEquals(1, account.inventory.getItemCount(it)) }
        // Owner 2026-10-09: after every kill the quest offers the trip to the next step (the way home from the boss), so every
        // fight step is followed by a step with a place and a location (no quest ends on a fight).
        FoundationQuests.SHORT.forEach { quest ->
            quest.steps.forEachIndexed { index, step ->
                if (step.fight == null) return@forEachIndexed
                val next = quest.steps.getOrNull(index + 1)
                assertTrue(next?.location != null && next.place != null, "${quest.name} step ${index + 1}: no trip on after the kill")
            }
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
