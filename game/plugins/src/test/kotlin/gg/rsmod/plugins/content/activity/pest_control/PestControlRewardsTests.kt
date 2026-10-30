package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.container.ItemTransaction
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.tools.importer.PestControlRewardsInterfaceImportTool as Tool
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PestControlRewardsTests {
    @Test
    fun `all 47 price labels sent to the extended window agree with the charged cost`() {
        for (balance in listOf(0, 1, 149, 150, 1_000)) {
            val (player, sent) = player(balance)
            PestControlRewards.open(player)
            val texts = texts(sent)
            var options = 0
            for (reward in PestControlRewards.Reward.values()) {
                for (option in 1 until reward.children.size) {
                    val label = texts.getValue(reward.children[option]).replace(Regex("<[^>]*>"), "")
                    val cost = Regex("\\((\\d+) Pts?\\)").matchEntire(label)!!.groupValues[1].toInt()
                    assertEquals(reward.cost(option), cost, "${reward.name} option $option at $balance points")
                    options++
                }
            }
            assertEquals(47, options, "XP, packs, every void piece, elite upgrades, deflector and charms")
            assertEquals("<col=784F1C>(150 Pts)", texts[Tool.DEFLECTOR_BUTTON])
            // The existing shop follows OSRS's 200-point elite upgrade rule (PestControlData), not RS3's 100.
            assertEquals("<col=784F1C>(200 Pts)", texts[Tool.ELITE_TOP_BUTTON])
            assertEquals("<col=784F1C>(200 Pts)", texts[Tool.ELITE_ROBE_BUTTON])
        }
    }

    @Test
    fun `selecting every option keeps its actual price and confirm charges it`() {
        for (reward in PestControlRewards.Reward.values()) {
            for (option in 1 until reward.children.size) {
                val (player, sent) = player(1_000)
                PestControlRewards.select(player, reward.children[option])
                assertEquals("<col=FFFFFF>${reward.optionText(option)}", texts(sent)[reward.children[option]])
                PestControlRewards.confirm(player)
                assertEquals(1_000 - reward.cost(option), player.attr[PEST_POINTS], "${reward.name} option $option")
                if (!reward.isSkill) {
                    val article = if (reward.name.startsWith("ELITE_")) "an" else "a"
                    assertTrue(sent.filterIsInstance<MessageGameMessage>().any {
                        it.message == "The Void Knight has given you $article ${reward.title}."
                    }, "${reward.name}: confirmation uses the correct article")
                }
            }
        }
    }

    @Test
    fun `the legacy window receives every legacy price and no extension component`() {
        val (player, sent) = player(1_000, extended = false)
        PestControlRewards.open(player)
        val texts = texts(sent)
        for (reward in PestControlRewards.Reward.values()) {
            for (option in 1 until reward.children.size) {
                assertEquals(!reward.extended, reward.children[option] in texts, reward.name)
            }
        }
    }

    @Test
    fun `level requirement messages use the correct article for every skill title`() {
        for (reward in PestControlRewards.Reward.values().filter { it.isSkill }) {
            val (player, sent) = player(1_000)
            every { player.skills.getMaxLevel(reward.skill) } returns 1
            PestControlRewards.select(player, reward.children[1])
            val article = if (reward == PestControlRewards.Reward.ATTACK) "an" else "a"
            assertTrue(sent.filterIsInstance<MessageGameMessage>().any {
                it.message == "You need $article ${reward.title} level of 25 to purchase this."
            })
            assertEquals(1_000, player.attr[PEST_POINTS])
        }
    }

    @Test
    fun `deflector refuses 149 points and buys the wearable 19712 for exactly 150`() {
        for (balance in listOf(149, 150)) {
            val (player, sent) = player(balance)
            val inventory = player.inventory
            PestControlRewards.select(player, Tool.DEFLECTOR_BUTTON)
            PestControlRewards.confirm(player)
            if (balance == 149) {
                assertEquals(149, player.attr[PEST_POINTS])
                verify(exactly = 0) { inventory.add(19712, 1) }
                assertTrue(sent.filterIsInstance<MessageGameMessage>().any { it.message == "You don't have enough points." })
            } else {
                assertEquals(0, player.attr[PEST_POINTS])
                verify(exactly = 1) { inventory.add(19712, 1) }
            }
        }
    }

    private fun texts(sent: List<Message>) = sent.filterIsInstance<IfSetTextMessage>()
        .filter { it.parent == PC_REWARD_INTERFACE }.associate { it.child to it.text }

    private fun player(balance: Int, extended: Boolean = true): Pair<Player, MutableList<Message>> {
        val player = mockk<Player>(relaxed = true)
        val attr = AttributeMap().apply { this[PEST_POINTS] = balance }
        val sent = mutableListOf<Message>()
        every { player.attr } returns attr
        every { player.interfaces.displayMode } returns DisplayMode.FIXED
        every { player.skills.getMaxLevel(any()) } returns 99
        every { player.skills.getCurrentLevel(any()) } returns 99
        every { player.inventory.freeSlotCount } returns 28
        every { player.inventory.add(any<Int>(), any<Int>()) } answers {
            val amount = secondArg<Int>()
            ItemTransaction(amount, amount, emptyList())
        }
        every { player.inventory.contains(any<Int>()) } returns true
        every { player.inventory.remove(any<Int>()) } returns ItemTransaction(1, 1, emptyList())
        every { player.world.definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { player.world.filestore.index(3).archive(PC_REWARD_INTERFACE)!!.fileIds() } returns
            IntArray(if (extended) Tool.COMPONENT_COUNT else Tool.LEGACY_COMPONENT_COUNT) { it }
        every { player.write(*anyVararg<Message>()) } answers { sent.addAll(firstArg<Array<Message>>()); Unit }
        return player to sent
    }
}
