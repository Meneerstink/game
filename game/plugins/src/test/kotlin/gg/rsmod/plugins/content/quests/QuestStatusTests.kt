package gg.rsmod.plugins.content.quests

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
import gg.rsmod.game.tools.importer.ProductionTabClientScriptPatchTool
import gg.rsmod.game.tools.importer.QuestListCacheTool
import gg.rsmod.game.tools.importer.QuestListPart2CacheTool
import gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.items.osrs.OsrsQuestRequirements
import gg.rsmod.plugins.content.items.osrs.QuestStubs
import gg.rsmod.plugins.content.quests.QuestStatus.State
import gg.rsmod.plugins.content.quests.foundation.BeneathCursedSands
import gg.rsmod.plugins.content.quests.foundation.ChoiceGroup
import gg.rsmod.plugins.content.quests.foundation.DesertTreasure
import gg.rsmod.plugins.content.quests.foundation.DragonSlayerII
import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.KingsRansom
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.foundation.SongOfTheElves
import gg.rsmod.plugins.content.quests.impl.CooksAssistant
import gg.rsmod.plugins.content.quests.impl.DoricsQuest
import gg.rsmod.plugins.content.quests.impl.DruidicRitual
import gg.rsmod.plugins.content.quests.impl.ImpCatcher
import gg.rsmod.plugins.content.quests.impl.LostCity
import gg.rsmod.plugins.content.quests.impl.PiratesTreasure
import gg.rsmod.plugins.content.quests.impl.PriestInPeril
import gg.rsmod.plugins.content.quests.impl.PrinceAliRescue
import gg.rsmod.plugins.content.quests.impl.RuneMysteries
import gg.rsmod.plugins.content.quests.impl.TheKnightsSword
import gg.rsmod.plugins.content.quests.impl.TheRestlessGhost
import gg.rsmod.plugins.content.quests.impl.VampyreSlayer
import gg.rsmod.plugins.content.quests.tab.DeadmanGroupsView
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Quest list part 2 (owner 2026-09-27): the one quest status, the varp/varbit values the client reads from it, Quest
 * Points, the list order in the cache and the quest list without filters. Varps, varbits (real cache definitions),
 * skills and containers are real; the rest of the player is a relaxed mock.
 */
class QuestStatusTests {
    private class Account(isNew: Boolean) {
        val attributes = AttributeMap()
        val varps = VarpSet((0..MAX_VARP).toSet())
        val player = mockk<Player>(relaxed = true)

        init {
            if (isNew) attributes[NEW_ACCOUNT_ATTR] = true
            val world = mockk<World>(relaxed = true)
            every { world.definitions } returns DEFINITIONS
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.varps } returns varps
            every { player.skills } returns SkillSet(25)
            every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
            every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
            every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        }
    }

    private val implQuests: List<Quest> =
        listOf(
            CooksAssistant, DoricsQuest, DruidicRitual, ImpCatcher, LostCity, PiratesTreasure, PriestInPeril, PrinceAliRescue,
            RuneMysteries, TheKnightsSword, TheRestlessGhost, VampyreSlayer,
        )

    private val choiceSlots get() = QuestStatus.CHOICE_QUESTS.keys

    // ------------------------------------------------------------------------------------------------ the table

    @Test
    fun `the table has every row of the cache quest list and nothing else`() {
        val library = CacheLibrary(CACHE_PATH)
        val slots =
            try {
                QuestListCacheTool.EnumCodec.decode(library.data(QuestListCacheTool.ENUM_INDEX, QuestListCacheTool.QUEST_ENUM ushr 8, QuestListCacheTool.QUEST_ENUM and 0xFF)!!).values.keys
            } finally {
                library.close()
            }
        assertEquals(188, slots.size)
        assertEquals(slots.toSortedSet(), QuestList.ENTRIES.map { it.slot }.toSortedSet())
        assertEquals(QuestList.ENTRIES.size, QuestList.ENTRIES.map { it.slot }.distinct().size)
    }

    /**
     * Every simple case of clientscript 2193 (push var, push complete value, gosub 2157) is checked against the table by
     * behaviour: the var at 0 reads not started, one below the complete value in progress, at it complete.
     */
    @Test
    fun `the table reads each quest exactly like clientscript 2193`() {
        val library = CacheLibrary(CACHE_PATH)
        val script =
            try {
                library.data(QuestListCacheTool.CLIENTSCRIPT_INDEX, QuestListCacheTool.PROGRESS_SCRIPT, 0)!!
            } finally {
                library.close()
            }
        val ins = ProductionTabClientScriptPatchTool.decode(script)
        val footer = ((script[script.size - 2].toInt() and 0xFF) shl 8) or (script[script.size - 1].toInt() and 0xFF)
        var p = script.size - 2 - footer + 1
        val count = ((script[p].toInt() and 0xFF) shl 8) or (script[p + 1].toInt() and 0xFF)
        p += 2
        val switchAt = ins.indexOfFirst { it.opcode == 51 }
        var simple = 0
        repeat(count) {
            val slot = java.nio.ByteBuffer.wrap(script, p, 4).int
            val offset = java.nio.ByteBuffer.wrap(script, p + 4, 4).int
            p += 8
            val first = switchAt + offset + 1
            val push = ins[first]
            val limit = ins[first + 1]
            val gosub = ins[first + 2]
            if ((push.opcode == 1 || push.opcode == 25) && limit.opcode == 0 && gosub.opcode == 40 && gosub.intOperand == 2157) {
                simple++
                val entry = QuestList.entry(slot)!!
                val complete = limit.intOperand!!
                fun readAt(value: Int): Int {
                    val account = Account(isNew = true)
                    if (push.opcode == 1) account.player.setVarp(push.intOperand!!, value) else account.player.setVarbit(push.intOperand!!, value)
                    return entry.progress.read(account.player)
                }
                assertEquals(0, readAt(0), "slot $slot")
                if (complete > 1) assertEquals(1, readAt(complete - 1), "slot $slot")
                assertEquals(2, readAt(complete), "slot $slot")
            }
        }
        assertEquals(170, simple, "simple 2193 cases")
    }

    @Test
    fun `quest points come from the cache and the wiki and the maximum is 349`() {
        assertEquals(349, QuestStatus.MAX_POINTS)
        assertEquals(26, QuestList.ENTRIES.filter { it.slot >= 185 }.sumOf { it.points }, "the ten OSRS quests")
        assertEquals(0, QuestList.entry(193)!!.points, "Mage Arena II is a miniquest")
        assertEquals(10, QuestList.entry(87)!!.points, "Recipe for Disaster with its nine subquests")
        assertEquals(mapOf(17 to 5, 159 to 3, 152 to 3, 156 to 1), listOf(17, 159, 152, 156).associateWith { QuestList.entry(it)!!.points })
        // The quest objects the server registers give the same points as the list.
        (implQuests + FoundationQuests.SHORT + FoundationQuests.COMPLETED).forEach { quest ->
            assertEquals(QuestList.entry(quest.slot)!!.points, quest.pointReward, quest.name)
        }
    }

    // ------------------------------------------------------------------------------------------------ the status

    @Test
    fun `a new account has everything completed except the eight choice quests`() {
        val account = Account(isNew = true)
        QuestStatus.login(account.player)
        QuestList.ENTRIES.forEach { entry ->
            if (entry.slot in choiceSlots) {
                assertEquals(State.NOT_STARTED, QuestStatus.state(account.player, entry.slot), entry.name)
                assertEquals(0, entry.progress.read(account.player), entry.name)
            } else {
                assertEquals(State.COMPLETED, QuestStatus.state(account.player, entry.slot), entry.name)
                assertEquals(QuestStatus.COMPLETE, entry.progress.read(account.player), "${entry.name}: the client must show it completed")
            }
        }
        assertEquals(349 - 26, QuestStatus.points(account.player))
        assertEquals(349 - 26, account.player.getVarp(Varps.QUEST_POINTS))
        assertEquals(349, account.player.getVarp(Varps.MAX_QUEST_POINTS))
        assertFalse(QuestStatus.hasAllQuestPoints(account.player))
    }

    @Test
    fun `Vampyre Slayer and every implemented quest count as completed with a finished journal`() {
        val account = Account(isNew = true)
        QuestStatus.login(account.player)
        implQuests.forEach { quest ->
            assertTrue(account.player.finishedQuest(quest), quest.name)
            assertTrue(account.player.startedQuest(quest), quest.name)
            assertEquals(QuestStatus.COMPLETE, QuestList.entry(quest.slot)!!.progress.read(account.player), quest.name)
            val journal = quest.getObjective(account.player, account.player.getCurrentStage(quest)).objectives
            assertTrue(journal.any { it.contains("QUEST COMPLETE") }, "${quest.name} journal: $journal")
        }
    }

    @Test
    fun `a legacy account has every quest and all quest points`() {
        val account = Account(isNew = false)
        QuestStatus.login(account.player)
        QuestList.ENTRIES.forEach { assertEquals(QuestStatus.COMPLETE, it.progress.read(account.player), it.name) }
        assertEquals(349, account.player.getVarp(Varps.QUEST_POINTS))
        assertTrue(QuestStatus.hasAllQuestPoints(account.player))
    }

    @Test
    fun `a choice or a played short quest completes it, a started one is in progress`() {
        val account = Account(isNew = true)
        QuestStatus.login(account.player)
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.GEAR, DragonSlayerII))
        assertEquals(State.COMPLETED, QuestStatus.state(account.player, DragonSlayerII))
        assertEquals(QuestStatus.COMPLETE, QuestList.entry(DragonSlayerII.slot)!!.progress.read(account.player))
        // Live: Quest Points follow at once and the list is told to rebuild: the grouping varbit 4536 is flipped and back,
        // which re-sends its varp - a varp the list rebuild (clientscript 2169) is triggered by (1384 from 2165, or 1402).
        assertEquals(349 - 26 + 5, account.player.getVarp(Varps.QUEST_POINTS))
        account.varps.clean()
        KingsRansom.setStage(account.player, 2)
        assertEquals(State.IN_PROGRESS, QuestStatus.state(account.player, KingsRansom))
        assertEquals(1, QuestList.entry(KingsRansom.slot)!!.progress.read(account.player))
        val refreshVarp = DEFINITIONS.get(gg.rsmod.game.fs.def.VarbitDef::class.java, FoundationQuests.QUEST_LIST_GROUPING_VARBIT).varp
        assertTrue(refreshVarp == 1384 || refreshVarp == 1402, "varbit 4536 lives in varp $refreshVarp")
        assertTrue(account.varps.isDirty(refreshVarp), "quest list refresh sent")
        assertTrue(FoundationRewards.complete(account.player, KingsRansom))
        assertEquals(State.COMPLETED, QuestStatus.state(account.player, KingsRansom))
        assertEquals(349 - 26 + 5 + 1, account.player.getVarp(Varps.QUEST_POINTS))
        // The Deadman Groups panel reads the same status.
        QuestStatus.CHOICE_QUESTS.values.forEach { quest ->
            assertEquals(QuestStatus.isCompleted(account.player, quest), DeadmanGroupsView.questFinished(account.player, quest), quest.name)
        }
        assertFalse(QuestStatus.isCompleted(account.player, DesertTreasure))
    }

    @Test
    fun `Song of the Elves is no longer completed by itself`() {
        val account = Account(isNew = true)
        QuestStatus.login(account.player)
        assertFalse(QuestStubs.songOfTheElvesCompleted(account.player))
        assertEquals(State.NOT_STARTED, QuestStatus.state(account.player, SongOfTheElves))
        assertTrue(OsrsQuestRequirements.canUpgradeSceptre(account.player).not())
        QuestChoices.choose(account.player, ChoiceGroup.GEAR, SongOfTheElves)
        assertTrue(QuestStubs.songOfTheElvesCompleted(account.player))
    }

    @Test
    fun `the status never lowers a value an account already has`() {
        val account = Account(isNew = true)
        account.player.setVarp(Varps.PRIEST_IN_PERIL_PROGRESS, 61)
        account.player.setVarp(Varps.ELEMENTAL_WORKSHOP_I_PROGRESS, (1 shl 20) or (1 shl 3))
        account.player.setVarp(Varps.DORICS_QUEST_PROGRESS, 1)
        QuestStatus.login(account.player)
        assertEquals(61, account.player.getVarp(Varps.PRIEST_IN_PERIL_PROGRESS))
        assertEquals((1 shl 20) or (1 shl 3), account.player.getVarp(Varps.ELEMENTAL_WORKSHOP_I_PROGRESS))
        assertEquals(100, account.player.getVarp(Varps.DORICS_QUEST_PROGRESS), "a half-done quest moves on to complete")
        // A played choice quest stays completed through later logins.
        QuestChoices.choose(account.player, ChoiceGroup.PRAYER, KingsRansom)
        QuestStatus.login(account.player)
        assertEquals(State.COMPLETED, QuestStatus.state(account.player, KingsRansom))
    }

    // ------------------------------------------------------------------------------------------------ the cache

    @Test
    fun `the quest list cache has two parts and no filters`() {
        assertTrue(QuestListPart2CacheTool.plan(CACHE_PATH).isEmpty(), "QuestListPart2CacheTool must be applied to the cache")
        assertTrue(QuestListCacheTool.plan(CACHE_PATH).isEmpty(), "QuestListCacheTool stays a no-op")
        val library = CacheLibrary(CACHE_PATH)
        try {
            val list = ProductionTabClientScriptPatchTool.decode(library.data(QuestListCacheTool.CLIENTSCRIPT_INDEX, 2162, 0)!!)
            // Progress grouping, not reversed, no filter, nothing hidden.
            assertEquals(listOf(0 to 1, 34 to 1, 0 to 0, 34 to 2, 0 to 0, 34 to 3, 0 to 0, 34 to 4), list.take(8).map { it.opcode to it.intOperand })
            // A quest's group is its status / 2: 0 not completed (red and yellow together), 1 completed.
            assertEquals(listOf(33 to 10, 45 to 1, 0 to 2, 4003 to 0, 34 to 22), list.subList(299, 304).map { it.opcode to it.intOperand })
            assertEquals(6 to 32, list[582].opcode to list[582].intOperand, "no Showing ... line")
            val transmit = ProductionTabClientScriptPatchTool.decode(library.data(QuestListCacheTool.CLIENTSCRIPT_INDEX, 2169, 0)!!)
            assertEquals(6 to 39, transmit[18].opcode to transmit[18].intOperand, "no dropdown")
            assertTrue(library.data(QuestListCacheTool.ENUM_INDEX, 2249 ushr 8, 2249 and 0xFF)!!.contentEquals(QuestListPart2CacheTool.encodeTitles()))
            QuestListPart2CacheTool.HIDDEN.forEach { c -> assertTrue(QuestListPart2CacheTool.isHidden(library.data(3, 190, c)!!), "190:$c hidden") }
            val layer = library.data(3, 190, 13)!!
            assertEquals(200, ((layer[10].toInt() and 0xFF) shl 8) or (layer[11].toInt() and 0xFF))
            assertTrue(QuestTabInterfaceImportTool.patchField(layer, QuestTabInterfaceImportTool.QUEST_LIST_FIT.first()).contentEquals(layer))
        } finally {
            library.close()
        }
    }

    // ------------------------------------------------------------------------- N462 Beneath Cursed Sands (short quest)

    @Test
    fun `N462 an old account keeps the always-complete Beneath Cursed Sands entry (owner E2)`() {
        val account = Account(isNew = false)
        account.player.setVarp(BeneathCursedSands.questId, 1) // the old CompletedOsrsQuest value
        BeneathCursedSands.migrate(account.player)
        assertEquals(State.COMPLETED, QuestStatus.state(account.player, BeneathCursedSands))
        assertTrue(FoundationRewards.isRewarded(account.player, BeneathCursedSands), "no second reward for a kept completion")
    }

    @Test
    fun `N462 a new account plays Beneath Cursed Sands and its progress is never turned into a completion`() {
        val account = Account(isNew = true)
        BeneathCursedSands.migrate(account.player)
        assertEquals(State.NOT_STARTED, QuestStatus.state(account.player, BeneathCursedSands))
        account.player.setVarp(BeneathCursedSands.questId, 1) // step 1 open
        BeneathCursedSands.migrate(account.player)
        assertEquals(State.IN_PROGRESS, QuestStatus.state(account.player, BeneathCursedSands))
        assertFalse(gg.rsmod.plugins.content.activity.toa.ToaLobby.canEnter(account.player), "no Tombs of Amascut before the quest")
    }

    companion object {
        private const val MAX_VARP = 8000
        private val CACHE_PATH = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(CACHE_PATH))
            assertTrue(DEFINITIONS.getCount(ItemDef::class.java) != 0)
        }
    }
}
