package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N501: real quest conversations and progression, with dialogue replies supplied without a running server/client. */
class DesertTreasureIIRouteTests {
    private class Account(val varps: VarpSet = VarpSet((0..8000).toSet()), new: Boolean = true) {
        val attr = AttributeMap()
        val inventory = ItemContainer(definitions, INVENTORY_KEY)
        val bank = ItemContainer(definitions, BANK_KEY)
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val texts = mutableListOf<IfSetTextMessage>()
        var optionsOpen = false
        var wrongSeal = false
        private var contact: Npc? = null

        init {
            if (new) attr[NEW_ACCOUNT_ATTR] = true
            attr[QuestHelper.OFF] = true
            every { player.attr } returns attr
            every { player.varps } returns varps
            every { player.skills } returns SkillSet(25)
            every { player.world } returns world
            every { world.definitions } returns definitions
            every { player.inventory } returns inventory
            every { player.bank } returns bank
            every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, BANK_KEY to bank)
            every { player.equipment } returns ItemContainer(definitions, EQUIPMENT_KEY)
            every { player.tile } returns Tile(3164, 3491)
            every { player.interfaces.optionsOpen } answers { optionsOpen }
            every { player.interfaces.optionsOpen = any() } answers { optionsOpen = firstArg() }
            every { player.write(*anyVararg<Message>()) } answers {
                texts += firstArg<Array<Message>>().filterIsInstance<IfSetTextMessage>()
            }
        }

        fun talk(npcId: Int) = runBlocking {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.id } returns npcId
            every { npc.getTransform(player) } returns npcId
            every { npc.getTransmogId() } returns -1
            contact = npc // keep the mock alive while production dialogue follows its WeakReference
            attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
            val task = spyk(QueueTask(player, TaskPriority.STANDARD))
            coEvery { task.waitReturnValue() } answers {
                if (optionsOpen) {
                    val title = texts.last { it.parent in 228..240 && it.child == 1 }
                    val rows = texts.filter { it.parent == title.parent && it.child >= 2 }.takeLast((title.parent - 224) / 2)
                    val target = when {
                        title.text == "Travel" -> "I'll find my own way."
                        title.text.startsWith("Which seal") -> when {
                            wrongSeal -> "The Whisperer"
                            "first" in title.text -> "Vardorvis"
                            "second" in title.text -> "Duke Sucellus"
                            "third" in title.text -> "The Leviathan"
                            else -> "The Whisperer"
                        }
                        title.text == "Quests" -> "Desert Treasure II"
                        else -> "I'll investigate the vault."
                    }
                    val row = rows.firstOrNull { it.text.startsWith(target) }
                        ?: error("No answer '$target' in ${title.text}: $rows")
                    task.requestReturnValue = ResumePauseButtonMessage(title.parent, 0, row.child)
                }
            }
            assertTrue(FoundationQuests.talk(task, npcId))
        }
    }

    @Test
    fun `start dialogues puzzle and final reward form a playable short quest`() {
        val account = Account()
        account.talk(Npcs.ARCHAEOLOGIST)
        assertEquals(1, DesertTreasureII.stage(account.player))
        account.talk(Npcs.ALI_THE_WISE)
        assertEquals(2, DesertTreasureII.stage(account.player))
        account.wrongSeal = true
        account.talk(Npcs.ALI_THE_WISE)
        assertEquals(2, DesertTreasureII.stage(account.player), "a wrong order is retryable")
        account.wrongSeal = false
        account.talk(Npcs.ALI_THE_WISE)
        assertEquals(3, DesertTreasureII.stage(account.player))
        val fight = DesertTreasureII.steps[2].fight!!
        assertEquals(Npcs.MUMMY_1961, fight.npc)
        assertEquals(103, fight.level)
        assertTrue(DesertTreasureII.steps.all { it.gives.isEmpty() }, "no intermediate quest items are required")
        // The actual NPC spawn/combat/kill packet is Claude's client check. This is the completion call used by onKilled.
        FoundationQuests.completeStep(account.player, DesertTreasureII, 3)
        assertEquals(4, DesertTreasureII.stage(account.player))
        account.talk(Npcs.AZZANADRA)
        assertEquals(5, DesertTreasureII.stage(account.player))
        account.talk(Npcs.AZZANADRA)
        assertTrue(DesertTreasureII.isFinished(account.player))
        assertTrue(account.texts.any { "Ruinous Powers" in it.text }, "N490: Azzanadra explains the book requirement on completion")
        assertFalse(account.attr[gg.rsmod.plugins.content.mechanics.prayer.RuinousUnlocks.UNLOCKED] == true)
        assertEquals(1, account.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED))
        assertEquals(3, FoundationRewards.pendingLamps(account.player).size)
        assertTrue(account.attr[UnlockNpcRewards.ANCIENT_RINGS_UNLOCKED] == true)
        assertFalse(FoundationRewards.complete(account.player, DesertTreasureII))
        assertEquals(1, account.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED), "no duplicate reward")
    }

    @Test
    fun `every unfinished stage survives varp reload and keeps its journal and fight`() {
        for (stage in 1..5) {
            val account = Account()
            DesertTreasureII.setStage(account.player, stage)
            val saved = VarpSet((0..8000).toSet())
            account.varps.getAll().filter { it.state != 0 }.forEach { saved.setState(it.id, it.state) }
            val reloaded = Account(saved)
            assertEquals(stage, DesertTreasureII.stage(reloaded.player))
            assertTrue(DesertTreasureII.getObjective(reloaded.player, stage).objectives.containsAll(DesertTreasureII.steps[stage - 1].journal))
            assertFalse(DesertTreasureII.isFinished(reloaded.player))
            assertEquals(stage == 3, DesertTreasureII.steps[stage - 1].fight != null)
        }
    }

    @Test
    fun `full inventory sends the ring to the bank and the quest choice pays the same reward`() {
        val account = Account()
        account.inventory.add(Items.BRONZE_DAGGER, 28)
        assertTrue(FoundationRewards.complete(account.player, DesertTreasureII))
        assertEquals(1, account.bank.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED))
        val choice = Account()
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(choice.player, ChoiceGroup.MAGIC, DesertTreasureII))
        assertEquals(1, choice.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED))
    }

    @Test
    fun `legacy completion explains why an old account has no ring reward to claim`() {
        // Audit evidence, not a new reward policy: legacy login intentionally marks rewards paid without granting items.
        val account = Account(new = false)
        QuestChoices.applyLegacy(account.player)
        assertTrue(DesertTreasureII.isFinished(account.player))
        assertTrue(FoundationRewards.isRewarded(account.player, DesertTreasureII))
        assertEquals(0, account.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED))
        assertFalse(FoundationRewards.complete(account.player, DesertTreasureII))
    }

    @Test
    fun `N501 a legacy account gets the Ring of shadows once at login, an account that has one gets none`() {
        val account = Account(new = false)
        QuestChoices.applyLegacy(account.player)
        RingOfShadowsReclaim.onLogin(account.player)
        assertEquals(1, account.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED))
        account.inventory.remove(Items.RING_OF_SHADOWS_UNCHARGED, 1)
        RingOfShadowsReclaim.onLogin(account.player)
        assertEquals(0, account.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED), "only once - a lost ring is bought back")

        val owner = Account(new = false)
        QuestChoices.applyLegacy(owner.player)
        owner.bank.add(gg.rsmod.game.model.item.Item(Items.RING_OF_SHADOWS))
        RingOfShadowsReclaim.onLogin(owner.player)
        assertEquals(0, owner.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED))

        val fresh = Account()
        RingOfShadowsReclaim.onLogin(fresh.player)
        assertEquals(0, fresh.inventory.getItemCount(Items.RING_OF_SHADOWS_UNCHARGED), "no quest, no ring")
    }

    companion object {
        // The worktree has no cache. The route needs nonstacking reward items, NPC names and independent varbit storage;
        // model data, collision, NPC combat and the cache bindings are explicitly left to the real-client check.
        private val definitions = mockk<DefinitionSet>(relaxed = true).also { defs ->
            every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
            every { defs.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()).apply { name = "Quest contact" } }
            every { defs.getNullable(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
            every { defs.get(VarbitDef::class.java, any()) } answers {
                VarbitDef(secondArg()).apply {
                    varp = 4000 + id % 4000
                    startBit = 0
                    endBit = 15
                }
            }
        }
    }
}
