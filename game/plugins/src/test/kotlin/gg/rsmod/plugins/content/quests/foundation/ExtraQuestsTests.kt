package gg.rsmod.plugins.content.quests.foundation

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.foundation.bracket.BracketQuests
import gg.rsmod.plugins.content.quests.foundation.extra.*
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.*

/** Written for Claude's single end build. Real cache definitions, containers, skills and persisted step storage. */
class ExtraQuestsTests {
    private class Account(cohort: String = QuestChoices.ARMAGEDDON) {
        val attributes = AttributeMap()
        val varps = VarpSet((0..8000).toSet())
        val player = mockk<Player>(relaxed = true)
        init {
            attributes[QuestChoices.COHORT] = cohort
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
        fun open(q: ShortQuest) {
            BracketQuests.setTestOpen(player, q.slot, true)
            q.setStage(player, 0)
        }
    }

    @Test fun `extra quests have real list entries and never enter the bracket unlocks`() {
        ExtraQuests.ALL.forEach { q ->
            val entry = QuestList.entry(q.slot)!!
            assertEquals(q.name, entry.name)
            assertEquals(q.pointReward, entry.points)
            assertTrue(q.ownSteps)
            assertEquals(q.steps.size + 1, q.completedValue)
            assertTrue(q.startNpc >= 0 && q.startLocation != null)
            assertTrue(q.steps.all { it.location != null && it.journal.isNotEmpty() && it.done.isNotEmpty() })
            assertFalse(q in BracketQuests.ALL)
            assertTrue(q in FoundationQuests.HELPERS)
        }
    }

    @Test fun `existing completed status is preserved for every cohort`() {
        listOf(QuestChoices.LEGACY, QuestChoices.CHOICE, QuestChoices.ARMAGEDDON).forEach { cohort ->
            val a = Account(cohort)
            QuestStatus.login(a.player)
            ExtraQuests.ALL.forEach { q ->
                assertEquals(QuestStatus.State.COMPLETED, QuestStatus.state(a.player, q))
                assertEquals(2, QuestList.entry(q.slot)!!.progress.read(a.player))
                assertFalse(FoundationRewards.isRewarded(a.player, q))
            }
        }
    }

    @Test fun `explicitly opened quests keep steps across reconnection and show red yellow green`() {
        ExtraQuests.ALL.forEach { q ->
            val a = Account()
            a.open(q)
            assertEquals(QuestStatus.State.NOT_STARTED, QuestStatus.state(a.player, q))
            assertEquals(0, QuestList.entry(q.slot)!!.progress.read(a.player))
            q.setStage(a.player, 1)
            assertEquals(1, QuestList.entry(q.slot)!!.progress.read(a.player))
            assertEquals(QuestStatus.State.IN_PROGRESS, QuestStatus.state(a.player, q))
            assertEquals(1, a.attributes[q.stepKey])
            // A new Player wrapper over the same saved attributes/varps reads the same step.
            val reconnect = mockk<Player>(relaxed = true)
            every { reconnect.attr } returns a.attributes
            every { reconnect.varps } returns a.varps
            every { reconnect.world } returns a.player.world
            assertEquals(1, q.stage(reconnect))
            q.setStage(a.player, q.completedValue)
            assertEquals(2, QuestList.entry(q.slot)!!.progress.read(a.player))
            assertEquals(QuestStatus.State.COMPLETED, QuestStatus.state(a.player, q))
        }
    }

    @Test fun `every bound option exists in the actual cache`() {
        ExtraQuests.OPTIONS.forEach { (id, option) ->
            val def = DEFINITIONS.get(NpcDef::class.java, id)
            assertTrue(def.options.any { it.equals(option, ignoreCase = true) }, "$id lacks $option")
        }
        ExtraQuests.ALL.flatMap { q -> listOf(q.reward.icon) + q.reward.items.map { it.first } +
            q.steps.flatMap { s -> s.gives.map { it.first } + (s.search?.gives?.map { it.first } ?: emptyList()) } }
            .distinct().forEach { assertNotNull(DEFINITIONS.getNullable(ItemDef::class.java, it), "item $it") }
    }

    @Test fun `Fishing Contest wiki start requirements reward and client complete value`() {
        val q = FishingContest
        assertEquals(232, q.startNpc)
        assertEquals(3, q.steps.size)
        assertEquals(listOf(Skills.FISHING to 10), q.needs.skills)
        assertEquals(listOf(Skills.FISHING to 2_437.0), q.reward.xp)
        assertEquals(1, q.pointReward)
        val a = Account(); a.open(q)
        assertFalse(q.needs.met(a.player))
        a.player.skills.setBaseLevel(Skills.FISHING, 10)
        assertTrue(q.needs.met(a.player))
        q.setStage(a.player, q.completedValue)
        assertEquals(5, a.player.getVarp(Varps.FISHING_CONTEST_PROGRESS))
    }

    @Test fun `Bonzo refuses insufficient coins without taking supplies and accepts payment once`() {
        val a = Account(); a.open(FishingContest)
        FishingContest.setStage(a.player, 2)
        listOf(Items.FISHING_PASS, Items.FISHING_ROD, Items.GARLIC, Items.RED_VINE_WORM).forEach { a.player.inventory.add(it, 1) }
        a.player.inventory.add(Items.COINS_995, 4)
        assertFalse(FishingContest.enter(a.player))
        assertTrue(a.player.inventory.contains(Items.GARLIC))
        assertTrue(a.player.inventory.contains(Items.RED_VINE_WORM))
        assertEquals(4, a.player.inventory.getItemCount(Items.COINS_995))
        a.player.inventory.add(Items.COINS_995, 1)
        assertTrue(FishingContest.enter(a.player))
        assertEquals(3, FishingContest.stage(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.FISHING_TROPHY))
        assertFalse(FishingContest.enter(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.FISHING_TROPHY))
    }

    @Test fun `Scorpion Catcher keeps its hard missing prerequisite and exact wiki reward`() {
        val q = ScorpionCatcher
        assertEquals(389, q.startNpc)
        assertEquals(5, q.steps.size)
        assertEquals(listOf(Skills.PRAYER to 31), q.needs.skills)
        assertEquals(listOf("Alfred Grimhand's Barcrawl"), q.needs.quests)
        assertEquals(listOf(Skills.STRENGTH to 6_625.0), q.reward.xp)
        val a = Account(); a.open(q)
        a.player.skills.setBaseLevel(Skills.PRAYER, 31)
        assertFalse(q.needs.met(a.player), "missing Barcrawl must never be waived")
        assertTrue(q.needs.missing(a.player).any { it.contains("Alfred Grimhand") })
        q.setStage(a.player, q.completedValue)
        assertEquals(6, a.player.getVarp(Varps.SCORPION_CATCHER_PROGRESS))
    }

    @Test fun `scorpions can be caught out of order and a lost cage requires recapture`() {
        val a = Account(); val q = ScorpionCatcher; a.open(q)
        q.setStage(a.player, 2)
        q.reclaimCage(a.player)
        assertFalse(q.capture(a.player, 123))
        assertTrue(q.capture(a.player, 386))
        assertEquals(2, q.stage(a.player))
        assertFalse(q.capture(a.player, 386))
        assertTrue(q.capture(a.player, 385))
        assertEquals(3, q.stage(a.player))
        assertTrue(q.capture(a.player, 387))
        assertEquals(5, q.stage(a.player))
        assertTrue(a.player.inventory.contains(463))
        a.player.inventory.remove(463)
        q.reclaimCage(a.player)
        assertEquals(2, q.stage(a.player))
        assertTrue(a.player.inventory.contains(456))
        q.reclaimCage(a.player)
        assertEquals(1, a.player.inventory.getItemCount(456))
    }

    @Test fun `Haunted Mine has the wiki requirements fight reward and completion mirror`() {
        val q = HauntedMine
        assertEquals(1528, q.startNpc)
        assertEquals(5, q.steps.size)
        assertEquals(listOf(Skills.CRAFTING to 35), q.needs.skills)
        assertEquals(listOf("Priest in Peril"), q.needs.quests)
        assertEquals(setOf(Skills.CRAFTING), q.needs.boostable)
        assertEquals(listOf(Skills.STRENGTH to 22_000.0), q.reward.xp)
        assertEquals(2, q.pointReward)
        val fight = q.steps.single { it.fight != null }.fight!!
        assertEquals(1540, fight.npc)
        assertEquals(95, fight.level)
        assertEquals(listOf(Items.CRYSTALMINE_KEY to 1), q.steps[3].gives)
        assertEquals(listOf(Items.CHISEL to 1, Items.CRYSTALMINE_KEY to 1), q.steps[4].search!!.tools)
        val a = Account(); a.open(q)
        assertFalse(q.needs.met(a.player))
        a.player.skills.setBaseLevel(Skills.CRAFTING, 35)
        assertTrue(q.needs.met(a.player))
        q.setStage(a.player, q.completedValue)
        assertEquals(11, a.player.getVarp(Varps.HAUNTED_MINE_PROGRESS))
    }

    @Test fun `Zealot key before start during quest and after return follows transcript`() {
        val a = Account(); val q = HauntedMine; a.open(q)
        assertFalse(q.stealKey(a.player))
        assertFalse(a.player.inventory.contains(Items.ZEALOTS_KEY))
        q.setStage(a.player, 1)
        assertTrue(q.stealKey(a.player))
        assertEquals(2, q.stage(a.player))
        assertTrue(q.stealKey(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.ZEALOTS_KEY))
        a.player.inventory.remove(Items.ZEALOTS_KEY)
        assertTrue(q.stealKey(a.player))
        q.setStage(a.player, q.completedValue)
        a.player.inventory.remove(Items.ZEALOTS_KEY)
        a.attributes[q.RETURNED_KEY] = true
        assertFalse(q.stealKey(a.player))
        assertFalse(a.player.inventory.contains(Items.ZEALOTS_KEY))
    }

    @Test fun `The Lost Tribe has the wiki requirements reward start and completion mirror`() {
        val q = TheLostTribe
        assertEquals(2082, q.startNpc)
        assertEquals(4, q.steps.size)
        assertEquals(listOf(Skills.AGILITY to 13, Skills.THIEVING to 13, Skills.MINING to 17), q.needs.skills)
        assertEquals(listOf("Goblin Diplomacy", "Rune Mysteries"), q.needs.quests)
        assertEquals(listOf(Skills.MINING to 3_000.0), q.reward.xp)
        assertEquals(listOf(Items.RING_OF_LIFE to 1), q.reward.items)
        assertEquals(1, q.pointReward)
        val a = Account(); a.open(q)
        assertFalse(q.needs.met(a.player))
        q.needs.skills.forEach { (skill, level) -> a.player.skills.setBaseLevel(skill, level) }
        q.needs.quests.forEach { QuestList.ENTRIES.first { e -> e.name == it }.progress.complete(a.player) }
        assertTrue(q.needs.met(a.player))
        q.setStage(a.player, q.completedValue)
        assertEquals(11, a.player.getVarbit(Varbits.THE_LOST_TRIBE_PROGRESS))
    }

    @Test fun `Sigmund investigation requires Duke report key and chest before silverware`() {
        val a = Account(); val q = TheLostTribe; a.open(q)
        assertFalse(q.stealKey(a.player))
        q.setStage(a.player, 3)
        assertFalse(q.stealKey(a.player))
        a.attributes[q.THEFT_KNOWN] = true
        assertFalse(q.stealKey(a.player), "13 Thieving is required at the action too")
        a.player.skills.setBaseLevel(Skills.THIEVING, 13)
        assertFalse(q.searchCrate(a.player))
        assertFalse(q.openChest(a.player))
        assertTrue(q.stealKey(a.player))
        assertTrue(q.openChest(a.player))
        assertTrue(q.openChest(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.HAM_SHIRT))
        assertTrue(q.searchCrate(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.SILVERWARE))
        a.player.inventory.remove(Items.SILVERWARE)
        assertTrue(q.searchCrate(a.player), "a lost silverware can be recovered at its original crate")
        assertEquals(1, a.player.inventory.getItemCount(Items.SILVERWARE))
        q.setStage(a.player, q.completedValue)
        assertFalse(q.stealKey(a.player))
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        @BeforeClass @JvmStatic fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toString()))
        }
    }
}
