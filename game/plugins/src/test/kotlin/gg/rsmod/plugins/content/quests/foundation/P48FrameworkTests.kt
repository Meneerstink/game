package gg.rsmod.plugins.content.quests.foundation

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.QuestStatus.State
import gg.rsmod.plugins.content.quests.foundation.bracket.BracketQuests
import gg.rsmod.plugins.content.quests.foundation.bracket.ErnestTheChicken
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * PROJECT48 F8: the bracket mini-quest framework (own step storage, requirements, status per account group, reward once) on the
 * first quest, Ernest the Chicken. Varps, varbits (real cache definitions), skills and containers are real; the rest of the player is a
 * relaxed mock. No dialogue is run here (rsmod: a messageBox in a tested path hangs) - the conversations are the bot scenarios.
 */
class P48FrameworkTests {
    private class Account(cohort: String?) {
        val attributes = AttributeMap()
        val varps = VarpSet((0..MAX_VARP).toSet())
        val player = mockk<Player>(relaxed = true)

        init {
            if (cohort != null) {
                attributes[NEW_ACCOUNT_ATTR] = true
                attributes[QuestChoices.COHORT] = cohort
            }
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

    private val ernest = ErnestTheChicken

    @Test
    fun `a legacy account has every bracket quest completed`() {
        val account = Account(null)
        QuestStatus.login(account.player)
        BracketQuests.ALL.forEach { assertEquals(State.COMPLETED, QuestStatus.state(account.player, it), it.name) }
        assertEquals(QuestStatus.COMPLETE, QuestList.entry(ernest.slot)!!.progress.read(account.player))
        assertEquals(ernest.completedValue, ernest.stage(account.player))
    }

    @Test
    fun `an Armageddon account is given the fixed quests of its bracket without a reward, and a forced-open one is playable`() {
        val account = Account(QuestChoices.ARMAGEDDON)
        QuestStatus.login(account.player)
        // (f) the bracket gives Ernest (3-50 fixed list): completed, no reward paid.
        assertEquals(State.COMPLETED, QuestStatus.state(account.player, ernest))
        assertFalse(FoundationRewards.isRewarded(account.player, ernest))
        // (b) opened for play (the test tool ::p48 reset): not started, stage 0, requirements met (Ernest has none).
        BracketQuests.setTestOpen(account.player, ernest.slot, true)
        ernest.setStage(account.player, 0)
        QuestStatus.sync(account.player)
        assertEquals(State.NOT_STARTED, QuestStatus.state(account.player, ernest))
        assertEquals(0, ernest.stage(account.player))
        assertTrue(ernest.needs.met(account.player))
    }

    @Test
    fun `a missing requirement is named exactly`() {
        val account = Account(QuestChoices.ARMAGEDDON)
        val needs = QuestRequirements(skills = listOf(Skills.FARMING to 49), quests = listOf("Desert Treasure II"), questPoints = 10)
        val missing = needs.missing(account.player)
        assertTrue("You need level 49 Farming." in missing, missing.toString())
        assertTrue("You need to complete Desert Treasure II." in missing, missing.toString())
        assertTrue("You need 10 Quest points." in missing, missing.toString())
        assertFalse(needs.met(account.player))
        account.player.skills.setBaseLevel(Skills.FARMING, 49)
        assertFalse("You need level 49 Farming." in needs.missing(account.player))
        // extra lines never block
        assertTrue(QuestRequirements(extra = listOf("Ability to defend from a level 22 Skeleton.")).met(account.player))
    }

    @Test
    fun `the step is kept in its own attribute, never in the cache variable, and finishing completes the list entry once`() {
        val account = Account(QuestChoices.ARMAGEDDON)
        BracketQuests.setTestOpen(account.player, ernest.slot, true)
        ernest.setStage(account.player, 0)
        ernest.setStage(account.player, 2)
        // (d) the persisted attribute carries the step: sq_step_<slot>
        assertEquals(2, account.attributes[AttributeKey<Int>(persistenceKey = "sq_step_${ernest.slot}")])
        assertEquals(2, ernest.stage(account.player))
        assertEquals(0, account.player.getVarp(Varps.ERNEST_THE_CHICKEN_PROGRESS), "the cache variable stays 0 while playing")
        assertEquals(State.IN_PROGRESS, QuestStatus.state(account.player, ernest))
        // (e) completing: list entry complete, reward once
        assertTrue(FoundationRewards.complete(account.player, ernest))
        assertEquals(QuestStatus.COMPLETE, QuestList.entry(ernest.slot)!!.progress.read(account.player))
        assertEquals(State.COMPLETED, QuestStatus.state(account.player, ernest))
        assertEquals(null, account.attributes[AttributeKey<Int>(persistenceKey = "sq_step_${ernest.slot}")])
        assertFalse(FoundationRewards.complete(account.player, ernest), "no second reward")
        assertEquals(1, FoundationRewards.pendingLamps(account.player).size, "one lamp, paid once")
        assertTrue(QuestStatus.points(account.player) >= ernest.pointReward)
    }

    @Test
    fun `the eight old short quests keep the cache variable`() {
        val account = Account(QuestChoices.CHOICE)
        FoundationQuests.OLD_SHORT.forEach { assertFalse(it.ownSteps, it.name) }
        account.player.setVarbit(DesertTreasure.questId, 5)
        assertEquals(5, DesertTreasure.stage(account.player))
        assertEquals(5, account.player.getVarbit(DesertTreasure.questId))
        assertNotEquals(0, FoundationQuests.SHORT.size)
        assertTrue(FoundationQuests.SHORT.containsAll(FoundationQuests.OLD_SHORT))
    }

    @Test
    fun `every bracket quest names real list quests as requirements and sits on its own list slot`() {
        BracketQuests.ALL.forEach { q ->
            assertTrue(QuestList.ENTRIES.any { it.slot == q.slot && it.name == q.name }, "${q.name}: slot ${q.slot} is not its list entry")
            q.needs.quests.forEach { name -> assertTrue(QuestList.ENTRIES.any { it.name == name }, "${q.name} needs unknown quest $name") }
            assertTrue(q.ownSteps, q.name)
            assertEquals(q.steps.size + 1, q.completedValue, "${q.name}: internal completed value = steps + 1")
        }
        assertEquals(BracketQuests.ALL.map { it.slot }.distinct().size, BracketQuests.ALL.size)
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
