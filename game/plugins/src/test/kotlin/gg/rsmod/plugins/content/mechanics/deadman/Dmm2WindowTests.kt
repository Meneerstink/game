package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.SAFE_DEPOSIT_BOX_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.interf.listener.InterfaceListener
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.ext.GameframeRebuild
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlin.test.*

/** Real InterfaceSet and UI adapters, in-memory actors. No client/server/cache writes or player saves. */
class Dmm2WindowTests {
    private val f = DeadmanPlayerFixture()
    private val sent = mutableListOf<Message>()
    private val timers = TimerMap()
    private val queues = PawnQueueTaskSet()
    private val windows = InterfaceSet(object : InterfaceListener {
        override fun onInterfaceOpen(interfaceId: Int) {}
        override fun onInterfaceClose(interfaceId: Int) {
            when (interfaceId) {
                2000,2001 -> Dmm2SafeDepositUi.close(f.player)
                2002,2003 -> Dmm2RewardUi.close(f.player)
            }
        }
    })
    private var position = Tile(3200,3200)
    private val wizard = mockk<Npc>(relaxed = true)
    private val seer = mockk<Npc>(relaxed = true)
    private val nigel = mockk<Npc>(relaxed = true)
    private val rewards = NigelRewardStore.Reward.values().associateWith { 1700 + it.ordinal }
    private val rug = rewards.getValue(NigelRewardStore.Reward.RUG)
    init {
        GameframeRebuild.clearForTests()
        every { f.player.interfaces } returns windows
        every { f.player.timers } returns timers
        every { f.player.tile } answers { position }
        every { f.player.write(*anyVararg<Message>()) } answers { sent += firstArg<Array<Message>>().toList() }
        every { f.player.queue(any(), any(), any()) } answers {
            queues.queue(f.player, Dispatchers.Unconfined, firstArg(), thirdArg())
        }
        Dmm2Ids.install(listOf("interface\t230\t2000", "interface\t232\t2001", "interface\t819\t2002", "interface\t806\t2003",
            "npc\t6516\t1600", "npc\t6485\t1601", "npc\t8391\t1602"))
        listOf(wizard,seer,nigel).forEachIndexed { i,n ->
            every { n.id } returns 1600+i; every { n.tile } returns Tile(3200,3201); every { n.isSpawned() } returns true
        }
        rewards.values.forEach { f.define(it).name = "Reward $it" }
        f.define(995,true).tradeable = true; f.define(4151).tradeable = true; f.define(772).tradeable = false
        NigelRewardStore.install(NigelRewardStore.Contract(rewards, emptyList(), null))
        Dmm2Interactions.safeUi = Dmm2SafeDepositUi
        Dmm2Interactions.rewardUi = Dmm2RewardUi
    }
    private fun safe(npc: Npc = wizard) = assertTrue(Dmm2Interactions.openSafe(f.player,npc))
    private fun shop() = assertTrue(Dmm2Interactions.openRewards(f.player,nigel))
    private fun box() = f.containers[SAFE_DEPOSIT_BOX_KEY]!!
    private fun points(value: Int) { f.attr[StoreCatalogue.Currency.DEADMAN.attr] = value }
    private fun points() = NigelRewardStore.balance(f.player)
    private fun count(id: Int) = f.count(id)

    @Test fun `missing mapped windows stay unavailable without fixed local id guesses`() {
        Dmm2Ids.install(emptyList())
        assertFalse(Dmm2SafeDepositUi.ready()); assertFalse(Dmm2RewardUi.ready())
    }

    @Test fun `both financial NPCs open shared physical slots and bulk roundtrips conserve items`() {
        f.inventory[0] = Item(995,100)
        safe(); assertTrue(windows.isVisible(2000)); assertTrue(windows.isVisible(2001))
        Dmm2SafeDepositUi.click(f.player,false,0,3,995)
        assertEquals(10,box().occupiedSlotCount); assertEquals(90,f.inventory[0]!!.amount)
        Dmm2SafeDepositUi.close(f.player); safe(seer)
        Dmm2SafeDepositUi.click(f.player,true,0,5,995)
        assertEquals(100L,count(995)); assertEquals(0,box().occupiedSlotCount)
        repeat(5) {
            Dmm2SafeDepositUi.depositAll(f.player,false)
            Dmm2SafeDepositUi.click(f.player,true,0,5,995)
            assertEquals(100L,count(995)); assertEquals(100,f.inventory[0]!!.amount)
        }
    }

    @Test fun `deposit five spans physical copies while stale item packets and empty slots do nothing`() {
        repeat(7) { f.inventory[it] = Item(4151).putAttr(ItemAttribute.CHARGES,it+1) }
        safe()
        Dmm2SafeDepositUi.click(f.player,false,0,2,995)
        assertEquals(0,box().occupiedSlotCount)
        Dmm2SafeDepositUi.click(f.player,false,0,2,4151)
        assertEquals(5,box().occupiedSlotCount); assertEquals(7L,count(4151))
        assertEquals(listOf(1,2,3,4,5),box().filterNotNull().map { it.getAttr(ItemAttribute.CHARGES) })
        Dmm2SafeDepositUi.click(f.player,false,0,2,4151)
        assertEquals(5,box().occupiedSlotCount)
    }

    @Test fun `bulk worn deposits preserve charges invoke unequip and leave untradeables`() {
        f.equipment[3] = Item(4151).putAttr(ItemAttribute.CHARGES,321)
        f.equipment[2] = Item(772)
        safe(); Dmm2SafeDepositUi.depositAll(f.player,true)
        assertNull(f.equipment[3]); assertEquals(772,f.equipment[2]!!.id)
        assertEquals(321,box()[0]!!.getAttr(ItemAttribute.CHARGES)); assertEquals(1L,count(4151))
        io.mockk.verify(exactly = 1) { f.world.plugins.executeUnequipItem(f.player,4151) }
        io.mockk.verify(exactly = 1) { f.world.plugins.executeUnequipSlot(f.player,3) }
    }

    @Test fun `partial worn ammo deposit keeps equipped stack active and does not fire unequip hooks`() {
        f.define(892,true).tradeable = true
        f.equipment[13] = Item(892,100)
        safe(); Dmm2SafeDepositUi.depositAll(f.player,true)
        assertEquals(90,f.equipment[13]!!.amount); assertEquals(10,box().occupiedSlotCount)
        assertEquals(100L,count(892))
        io.mockk.verify(exactly = 0) { f.world.plugins.executeUnequipItem(f.player,892) }
        io.mockk.verify(exactly = 0) { f.world.plugins.executeUnequipSlot(f.player,13) }
    }

    @Test fun `full inventory skull movement death and close invalidate withdrawals without losing safe contents`() {
        f.inventory[0] = Item(4151); safe(); Dmm2SafeDepositUi.depositAll(f.player,false)
        repeat(28) { f.inventory[it] = Item(772) }
        Dmm2SafeDepositUi.click(f.player,true,0,1,4151)
        assertEquals(1,box().occupiedSlotCount); f.inventory[0] = null
        timers[SKULL_ICON_DURATION_TIMER] = 100
        Dmm2SafeDepositUi.click(f.player,true,0,1,4151)
        assertEquals(1,box().occupiedSlotCount); timers.remove(SKULL_ICON_DURATION_TIMER)
        safe(); position = Tile(3300,3300)
        Dmm2SafeDepositUi.click(f.player,true,0,1,4151); assertEquals(1,box().occupiedSlotCount)
        position = Tile(3200,3200); safe()
        every { f.player.isDead() } returns true
        Dmm2SafeDepositUi.click(f.player,true,0,1,4151); assertEquals(1,box().occupiedSlotCount)
        every { f.player.isDead() } returns false
        f.attr.removeIf { it.resetOnDeath }
        Dmm2SafeDepositUi.click(f.player,true,0,1,4151); assertEquals(1,box().occupiedSlotCount)
        safe(); f.player.closeInterface(2001)
        assertFalse(windows.isVisible(2000)); assertFalse(windows.isVisible(2001))
        Dmm2SafeDepositUi.click(f.player,true,0,1,4151); assertEquals(1L,count(4151))
        safe(seer); Dmm2SafeDepositUi.click(f.player,true,0,1,4151)
        assertEquals(0,box().occupiedSlotCount); assertEquals(1L,count(4151))
    }

    @Test fun `pending deposit X cannot continue into a reopened session or changed item`() {
        f.inventory[0] = Item(995,100); safe()
        Dmm2SafeDepositUi.click(f.player,false,0,4,995)
        queues.cycle()
        Dmm2SafeDepositUi.close(f.player); safe(seer)
        queues.submitReturnValue(10); repeat(5) { queues.cycle() }
        assertEquals(0,box().occupiedSlotCount); assertEquals(100L,count(995))
        Dmm2SafeDepositUi.click(f.player,false,0,4,995); queues.cycle()
        f.inventory[0] = Item(995,100)
        queues.submitReturnValue(10); repeat(5) { queues.cycle() }
        assertEquals(0,box().occupiedSlotCount); assertEquals(100L,count(995))
    }

    @Test fun `all seven products buy and refund through real mapped windows without item or point inflation`() {
        shop()
        for ((index,reward) in Dmm2RewardUi.PRODUCTS.withIndex()) {
            points(reward.price*5)
            val id = rewards.getValue(reward)
            Dmm2RewardUi.product(f.player,index,1)
            Dmm2RewardUi.buySelected(f.player,1)
            assertEquals(0,points()); assertEquals(5L,count(id))
            val slot = f.inventory.rawItems.indexOfFirst { it?.id == id }
            Dmm2RewardUi.inventory(f.player,slot,1,id)
            Dmm2RewardUi.sellSelected(f.player,1)
            assertEquals(reward.price*5,points()); assertEquals(0L,count(id))
            Dmm2RewardUi.sellSelected(f.player,1) // replay cannot sell twice
            assertEquals(reward.price*5,points())
            Dmm2RewardUi.closeInfo(f.player)
        }
        assertTrue(sent.filterIsInstance<IfSetTextMessage>().any { it.text == "Deadman Points" })
    }

    @Test fun `no money full inventory invalid rows and oversized purchase never debit anything`() {
        f.define(13307,true); f.inventory[0] = Item(13307,1_000_000)
        shop(); points(0)
        Dmm2RewardUi.product(f.player,3,2)
        assertEquals(0,points()); assertEquals(0L,count(rug)); assertEquals(1_000_000L,count(13307))
        points(100_000); repeat(28) { f.inventory[it] = Item(772) }
        Dmm2RewardUi.product(f.player,3,2)
        Dmm2RewardUi.product(f.player,Int.MAX_VALUE,5)
        Dmm2RewardUi.product(f.player,3,99)
        assertEquals(100_000,points()); assertEquals(0L,count(rug))
        f.inventory[0] = null
        Dmm2RewardUi.product(f.player,3,5)
        assertEquals(100_000,points()); assertEquals(0L,count(rug))
    }

    @Test fun `refund rejects stale modified unsupported and overflow items and accepts noted original rewards`() {
        f.define(1800,true).also { it.noteTemplateId = 799; it.noteLinkId = rug }
        f.inventory[0] = Item(1800,5); points(0); shop()
        Dmm2RewardUi.inventory(f.player,0,3,1800)
        assertEquals(15_000,points()); assertEquals(0L,count(1800))
        f.inventory[0] = Item(rug).putAttr(ItemAttribute.CHARGES,1)
        Dmm2RewardUi.refresh(f.player,NigelRewardStore.active!!)
        Dmm2RewardUi.inventory(f.player,0,2,rug)
        assertEquals(15_000,points()); assertEquals(1L,count(rug))
        f.inventory[0] = Item(rug); points(Int.MAX_VALUE)
        Dmm2RewardUi.refresh(f.player,NigelRewardStore.active!!)
        Dmm2RewardUi.inventory(f.player,0,2,rug)
        assertEquals(Int.MAX_VALUE,points()); assertEquals(1L,count(rug))
        f.inventory[0] = Item(772)
        Dmm2RewardUi.inventory(f.player,0,2,rug)
        assertEquals(772,f.inventory[0]!!.id); assertEquals(Int.MAX_VALUE,points())
    }

    @Test fun `closing death relog moving and despawn revoke selected purchases and refunds`() {
        points(30_000); shop(); Dmm2RewardUi.product(f.player,3,1)
        f.player.closeInterface(2002)
        Dmm2RewardUi.buySelected(f.player,0)
        assertEquals(30_000,points()); assertFalse(windows.isVisible(2003))
        repeat(5) {
            shop(); Dmm2RewardUi.product(f.player,3,2)
            val slot = f.inventory.rawItems.indexOfFirst { it?.id == rug }
            Dmm2RewardUi.inventory(f.player,slot,1,rug)
            Dmm2RewardUi.close(f.player); Dmm2Interactions.closeRewards(f.player)
            Dmm2RewardUi.sellSelected(f.player,0)
            shop(); Dmm2RewardUi.inventory(f.player,slot,2,rug)
            assertEquals(30_000,points()); assertEquals(0L,count(rug))
        }
        Dmm2RewardUi.product(f.player,3,1)
        position = Tile(3300,3300); Dmm2RewardUi.buySelected(f.player,0)
        assertEquals(30_000,points())
        position = Tile(3200,3200); shop(); Dmm2RewardUi.product(f.player,3,1)
        every { nigel.isSpawned() } returns false
        Dmm2RewardUi.buySelected(f.player,0); assertEquals(30_000,points())
        every { nigel.isSpawned() } returns true; shop(); Dmm2RewardUi.product(f.player,3,1)
        every { f.player.isDead() } returns true
        Dmm2RewardUi.buySelected(f.player,0); assertEquals(30_000,points())
        f.attr.removeIf { it.resetOnDeath }
        Dmm2RewardUi.buySelected(f.player,0); assertEquals(30_000,points())
    }

    @Test fun `logout save reload keeps box charges and purchased rewards but discards both UI sessions`() {
        val gson = com.google.gson.Gson()
        f.inventory[0] = Item(4151).putAttr(ItemAttribute.CHARGES,73)
        safe(); Dmm2SafeDepositUi.depositAll(f.player,false)
        Dmm2SafeDepositUi.close(f.player)
        points(12_000); shop(); Dmm2RewardUi.product(f.player,3,2)
        assertEquals(9000,points())
        val saved = f.containers.map { (key,value) ->
            key to gson.toJson(gg.rsmod.game.service.serializer.json.JsonPlayerSerializer.PersistentContainer(key.name,value.toMap()))
        }
        val attributes = f.attr.toPersistentMap()
        assertEquals(setOf(StoreCatalogue.Currency.DEADMAN.attr.persistenceKey),attributes.keys)
        Dmm2RewardUi.close(f.player); Dmm2Interactions.closeSafe(f.player)
        f.attr.clear()
        saved.forEach { (key,json) ->
            val dto = gson.fromJson(json,gg.rsmod.game.service.serializer.json.JsonPlayerSerializer.PersistentContainer::class.java)
            val restored = ItemContainer(f.definitions,key)
            dto.items.forEach { (slot,item) -> restored[slot] = item }
            f.containers[key] = restored
            if (key.name == "inventory") {
                f.inventory.clear(); dto.items.forEach { (slot,item) -> f.inventory[slot] = item }
                f.containers[key] = f.inventory
            }
        }
        points((attributes.getValue(StoreCatalogue.Currency.DEADMAN.attr.persistenceKey!!) as Number).toInt())
        Dmm2RewardUi.buySelected(f.player,0)
        Dmm2SafeDepositUi.click(f.player,true,0,1,4151)
        assertEquals(9000,points()); assertEquals(1L,count(rug)); assertEquals(1L,count(4151))
        safe(seer); Dmm2SafeDepositUi.click(f.player,true,0,1,4151)
        assertEquals(73,f.inventory.filterNotNull().single { it.id == 4151 }.getAttr(ItemAttribute.CHARGES))
        assertEquals(0,box().occupiedSlotCount)
    }

    @Test fun `simultaneous accounts never share a view selection balance or safe items`() {
        val other = DeadmanPlayerFixture()
        every { other.player.tile } returns Tile(3200,3200)
        f.inventory[0] = Item(4151); safe(); Dmm2SafeDepositUi.depositAll(f.player,false)
        points(30_000); shop(); Dmm2RewardUi.product(f.player,3,1)
        Dmm2RewardUi.buySelected(other.player,0)
        Dmm2SafeDepositUi.click(other.player,true,0,1,4151)
        assertEquals(30_000,points()); assertEquals(1L,count(4151))
        assertEquals(0,other.inventory.occupiedSlotCount)
        assertFalse(Dmm2RewardUi.visible(other.player)); assertFalse(Dmm2SafeDepositUi.visible(other.player))
    }
}
