package gg.rsmod.plugins.content.quests.foundation

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
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
            val skills = SkillSet(25)
            every { player.skills } returns skills
            // Quest XP goes through Player.addXp (a member): the relaxed mock would drop it, so book it like NewPlayerFoundationTests.
            every { player.addXp(any(), any(), any(), any()) } answers {
                skills.setBaseXp(firstArg<Int>(), skills.getCurrentXp(firstArg<Int>()) + secondArg<Double>())
            }
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

    @Test fun `new Armageddon accounts play the extra quests, an earlier completion mirror stays completed (owner 23-11)`() {
        val fresh = Account(QuestChoices.ARMAGEDDON)
        QuestStatus.login(fresh.player)
        ExtraQuests.ALL.forEach { q ->
            assertEquals(QuestStatus.State.NOT_STARTED, QuestStatus.state(fresh.player, q), q.name)
            assertEquals(0, QuestList.entry(q.slot)!!.progress.read(fresh.player), q.name)
        }
        val earlier = Account(QuestChoices.ARMAGEDDON)
        ExtraQuests.ALL.forEach { q -> QuestList.entry(q.slot)!!.progress.complete(earlier.player) }
        QuestStatus.login(earlier.player)
        ExtraQuests.ALL.forEach { q -> assertEquals(QuestStatus.State.COMPLETED, QuestStatus.state(earlier.player, q), q.name) }
    }

    @Test fun `existing completed status is preserved for the older cohorts`() {
        listOf(QuestChoices.LEGACY, QuestChoices.CHOICE).forEach { cohort ->
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
            a.attributes.remove(BracketQuests.TEST_OPEN) // Session-only flags do not survive a real reconnect.
            QuestStatus.login(reconnect)
            assertEquals(1, q.stage(reconnect), "${q.name}: login must not auto-complete an explicitly started quest")
            assertEquals(QuestStatus.State.IN_PROGRESS, QuestStatus.state(reconnect, q))
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
        ExtraQuests.OBJECT_OPTIONS.forEach { (id, option) ->
            val def = DEFINITIONS.get(ObjectDef::class.java, id)
            assertTrue(def.options.any { it.equals(option, ignoreCase = true) }, "object $id lacks $option")
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

    @Test fun `Tower of Life has the wiki start skills three steps reward and completion mirror`() {
        val q = TowerOfLife
        assertEquals(5578, q.startNpc)
        assertEquals(3, q.steps.size)
        assertEquals(listOf(Skills.CONSTRUCTION to 10), q.needs.skills)
        assertEquals(listOf(Skills.CONSTRUCTION to 1_000.0, Skills.CRAFTING to 500.0, Skills.THIEVING to 500.0), q.reward.xp)
        assertEquals(2, q.pointReward)
        assertEquals(listOf(Items.HAMMER to 1, Items.SAW to 1), q.steps[1].search!!.tools)
        val a = Account(); a.open(q)
        assertFalse(q.needs.met(a.player))
        a.player.skills.setBaseLevel(Skills.CONSTRUCTION, 10)
        assertTrue(q.needs.met(a.player))
        q.setStage(a.player, q.completedValue)
        assertEquals(18, a.player.getVarbit(Varbits.TOWER_OF_LIFE_PROGRESS))
    }

    @Test fun `only No fingers supplies missing quest boots and bank or worn copies prevent duplicates`() {
        val a = Account(); val q = TowerOfLife; a.open(q)
        assertFalse(q.wantsBoots(a.player, Npcs.NO_FINGERS))
        q.setStage(a.player, 1)
        assertFalse(q.wantsBoots(a.player, Npcs.NO_FINGERS), "Bonafido must ask for the outfit first")
        a.attributes[q.OUTFIT_REQUESTED] = true
        assertFalse(q.wantsBoots(a.player, Npcs.GUMMY))
        assertFalse(q.wantsBoots(a.player, Npcs.BLACKEYE))
        assertTrue(q.awardBoots(a.player))
        assertFalse(q.awardBoots(a.player))
        a.player.inventory.remove(Items.BUILDERS_BOOTS)
        a.player.bank.add(Items.BUILDERS_BOOTS, 1)
        assertFalse(q.awardBoots(a.player))
        a.player.bank.remove(Items.BUILDERS_BOOTS)
        assertTrue(q.awardBoots(a.player), "lost boots are replaceable")
        q.setStage(a.player, q.completedValue)
        a.player.inventory.remove(Items.BUILDERS_BOOTS)
        assertTrue(q.awardBoots(a.player), "wiki allows replacement after completion")
    }

    @Test fun `The Guns takes one regular beer per missing shirt and never charges for a banked shirt`() {
        val a = Account(); val q = TowerOfLife; a.open(q)
        q.setStage(a.player, 1); a.attributes[q.OUTFIT_REQUESTED] = true
        assertFalse(q.shirt(a.player))
        a.player.inventory.add(Items.BEER, 2)
        assertTrue(q.shirt(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.BEER))
        assertFalse(q.shirt(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.BEER))
        a.player.inventory.remove(Items.BUILDERS_SHIRT)
        a.player.bank.add(Items.BUILDERS_SHIRT, 1)
        assertFalse(q.shirt(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.BEER))
    }

    @Test fun `reset clears investigation flags without changing any other quest`() {
        val a = Account(); a.open(TheLostTribe)
        a.attributes[TheLostTribe.HAM_FOUND] = true
        a.attributes[TheLostTribe.THEFT_KNOWN] = true
        a.attributes[TheLostTribe.BROOCH_RETURNED] = true
        a.attributes[TowerOfLife.OUTFIT_REQUESTED] = true
        TheLostTribe.setStage(a.player, 0)
        assertNull(a.attributes[TheLostTribe.HAM_FOUND])
        assertNull(a.attributes[TheLostTribe.THEFT_KNOWN])
        assertNull(a.attributes[TheLostTribe.BROOCH_RETURNED])
        assertTrue(a.attributes[TowerOfLife.OUTFIT_REQUESTED] == true)
    }

    @Test fun `each extra quest pays the exact wiki experience and reward items once`() {
        ExtraQuests.ALL.forEach { q ->
            val a = Account(); a.open(q)
            q.setStage(a.player, q.steps.size)
            val before = q.reward.xp.associate { it.first to a.player.skills.getCurrentXp(it.first) }
            assertTrue(FoundationRewards.complete(a.player, q), q.name)
            q.reward.xp.forEach { (skill, amount) ->
                assertEquals(before.getValue(skill) + amount, a.player.skills.getCurrentXp(skill), 0.001, q.name)
            }
            q.reward.items.forEach { (item, amount) -> assertEquals(amount, a.player.inventory.getItemCount(item), q.name) }
            assertFalse(FoundationRewards.complete(a.player, q), "${q.name} must never pay twice")
            q.reward.xp.forEach { (skill, amount) ->
                assertEquals(before.getValue(skill) + amount, a.player.skills.getCurrentXp(skill), 0.001, q.name)
            }
            q.reward.items.forEach { (item, amount) -> assertEquals(amount, a.player.inventory.getItemCount(item), q.name) }
        }
    }

    @Test fun `a completed cache mirror wins over any stale saved step`() {
        ExtraQuests.ALL.forEach { q ->
            val a = Account(QuestChoices.LEGACY)
            QuestList.entry(q.slot)!!.progress.complete(a.player)
            a.attributes[q.stepKey] = 1
            assertEquals(q.completedValue, q.stage(a.player), q.name)
            assertEquals(QuestStatus.State.COMPLETED, QuestStatus.state(a.player, q), q.name)
        }
    }

    @Test fun `a search requires its spade but never consumes that tool`() {
        val a = Account(); val q = FishingContest; a.open(q)
        q.setStage(a.player, 1)
        every { a.player.tile } returns q.steps[0].location!!
        assertFalse(FoundationQuests.trySearch(a.player, q, 1))
        assertEquals(1, q.stage(a.player))
        a.player.inventory.add(Items.SPADE, 1)
        assertTrue(FoundationQuests.trySearch(a.player, q, 1))
        assertEquals(1, a.player.inventory.getItemCount(Items.SPADE))
        assertEquals(1, a.player.inventory.getItemCount(Items.RED_VINE_WORM))
        assertEquals(2, q.stage(a.player))
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        @BeforeClass @JvmStatic fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toString()))
        }
    }
}
