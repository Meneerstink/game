package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
import com.google.gson.JsonParser
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.*
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.*

class TreasureTrailHotColdTests {
    private val service = TreasureTrailHotCold.parse(File(gg.rsmod.game.RspsPaths.data("cfg/clue/hotcold.json")).readText())
    private val zero = object:Random() { override fun nextBits(bitCount:Int)=0 }
    private val row get()=TreasureTrailStepCatalogue.current.rows.single { it.key=="master:jorral" }
    private val accounts=mutableListOf<Player>()
    @BeforeTest fun setup() {
        mockkObject(ClueItems,ClueScrollManager)
        every { ClueItems.local(19835) } returns 30001
        every { ClueItems.local(19939) } returns 30002
    }
    @AfterTest fun cleanup() { accounts.forEach { TreasureTrailGuardians.remove(it) }; unmockkAll() }
    private fun account():Player {
        val definitions=mockk<DefinitionSet>();every { definitions.get(ItemDef::class.java,any()) } answers { ItemDef(secondArg()) }
        val world=mockk<World>(relaxed=true);every { world.definitions } returns definitions
        val p=mockk<Player>(relaxed=true)
        every { p.world } returns world;every { p.attr } returns AttributeMap();every { p.isOnline } returns true
        every { p.inventory } returns ItemContainer(definitions,28,ContainerStackType.NORMAL)
        every { p.tile } returns Tile(3000,3400)
        val step=TreasureTrailSourceStep(row)
        p.inventory[0]=Item(30001).putAttr(ItemAttribute.CLUE_STEP_KEY,step.persistenceId)
        every { ClueScrollManager.activeStep(p,ClueScrollTier.MASTER) } returns step
        every { ClueScrollManager.progress(p,ClueScrollTier.MASTER) } returns 2
        accounts+=p;return p
    }
    @Test fun `six target centres and source clues are exact cache identities rather than approximate Wiki map pins`() {
        val fixture=JsonParser().parse(File(gg.rsmod.game.RspsPaths.data("cfg/clue/hotcold.json")).readText()).asJsonObject
        assertEquals(6,service.targets.size)
        assertEquals(1,service.targets.count { it.guards.size==3 })
        fixture["targets"].asJsonArray.forEach { value ->
            val r=value.asJsonObject;val source=r["location"].asJsonObject;val clue=r["clue"].asJsonObject
            assertEquals(18,source["table"].asInt)
            val packed=source["columns"].asJsonObject["0"].asJsonObject["values"].asJsonArray[0].asInt
            val target=service.targets.single { it.id==r["id"].asInt }
            assertEquals(Tile(packed shr 14 and 16383,packed and 16383,packed ushr 28),target.centre)
            assertEquals(5,clue["columns"].asJsonObject["1"].asJsonObject["values"].asJsonArray[0].asInt)
        }
        fixture["sources"].asJsonObject.entrySet().forEach { (_,v) ->
            val pin=v.asJsonObject;val raw=File("docs/CLUE/sources/"+pin["file"].asString).readBytes()
            assertEquals(pin["sha256"].asString,MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) })
        }
    }
    @Test fun `saved target survives serialisation and new random seed while old contract becomes stale`() {
        val p=account();val first=service.offer(p,zero)!!
        assertEquals(first.target,service.offer(p,Random(982))!!.target)
        p.inventory[0]=Gson().fromJson(Gson().toJson(p.inventory[0]),Item::class.java)
        assertEquals(first.target,service.offer(p,Random(438))!!.target)
        assertFalse(first.contract.valid(p))
        assertEquals(TreasureTrailHotCold.Issue.STALE,service.issue(p,first))
        assertEquals(TreasureTrailHotCold.Issue.GIVEN,service.issue(p,service.offer(p)!!))
        assertEquals(TreasureTrailHotCold.Issue.HELD,service.issue(p,service.offer(p)!!))
    }
    @Test fun `inactive or forged device causes no reading damage or target mutation`() {
        val p=account();val forged=Item(30002)
        assertNull(service.feel(p,forged,zero));assertNull(p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
        p.inventory[1]=forged
        every { ClueScrollManager.activeStep(p,ClueScrollTier.MASTER) } returns null
        assertNull(service.feel(p,forged,zero));assertNull(p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
        verify(exactly=0) { ClueScrollManager.advance(any(),any(),any(),any(),any()) }
        verify(exactly=0) { p.addHit(any()) }
    }
    @Test fun `temperature uses Chebyshev distance master nine by nine square and all exact thresholds`() {
        val t=service.targets.first();val c=t.centre
        assertEquals(4,t.distance(Tile(c.x+4,c.z+4,c.height)))
        assertTrue(t.at(Tile(c.x-4,c.z+4,c.height)))
        assertFalse(t.at(Tile(c.x+5,c.z,c.height)))
        assertFalse(t.at(Tile(c.x,c.z,1)))
        val cases=mapOf(0 to "visibly shaking",4 to "visibly shaking",5 to "incredibly hot",29 to "incredibly hot",
            30 to "very hot",69 to "very hot",70 to "hot",99 to "hot",100 to "warm",149 to "warm",150 to "cold",
            199 to "cold",200 to "very cold",499 to "very cold",500 to "ice cold")
        cases.forEach { (d,text) -> assertEquals(text,TreasureTrailHotCold.temperature(d)) }
    }
    @Test fun `feel compares actual previous distance and keeps account and guardian state separate`() {
        val p=account();val offer=service.offer(p,zero)!!;val c=offer.target.centre
        p.inventory[1]=Item(30002)
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_GUARD_KEY,12).putAttr(ItemAttribute.CLUE_GUARD_PROGRESS,3)
        every { p.tile } returns Tile(c.x+150,c.z)
        assertEquals("The device is cold.",service.feel(p,p.inventory[1]!!,zero)!!.text)
        every { p.tile } returns Tile(c.x+151,c.z)
        assertTrue(service.feel(p,p.inventory[1]!!,zero)!!.text.contains("but colder than last time"))
        every { p.tile } returns Tile(c.x+150,c.z)
        val closer=service.feel(p,p.inventory[1]!!,zero)!!
        assertTrue(closer.text.contains("and warmer than last time"));assertEquals(3,closer.damage)
        verify(exactly=3) { p.addHit(match { it.hitmarks.single().damage==3 }) }
        assertTrue(service.feel(p,p.inventory[1]!!,zero)!!.text.contains("same temperature"))
        assertEquals(12,p.inventory[0]!!.getAttr(ItemAttribute.CLUE_GUARD_KEY))
        val other=account();other.inventory[1]=Item(30002)
        assertFalse(service.feel(other,other.inventory[1]!!,zero)!!.text.contains("last time"))
    }
    @Test fun `full inventory and missing device import cannot consume or advance a clue`() {
        val p=account();val offer=service.offer(p,zero)!!
        for(i in 1..27)p.inventory[i]=Item(995)
        assertEquals(TreasureTrailHotCold.Issue.FULL,service.issue(p,offer))
        assertSame(offer.contract.scroll,p.inventory[0])
        every { ClueItems.local(19939) } returns null
        assertEquals(TreasureTrailHotCold.Issue.MISSING,service.issue(p,offer))
        verify(exactly=0) { ClueScrollManager.advance(any(),any(),any(),any(),any()) }
    }
    @Test fun `banking scroll death logout replacement and stale target cannot expose a dig site`() {
        val p=account();val o=service.offer(p,zero)!!;val step=o.contract.step as TreasureTrailSourceStep
        every { p.tile } returns o.target.centre
        assertNotNull(service.site(p,step))
        p.inventory[0]=null;assertNull(service.site(p,step));p.inventory[0]=o.contract.scroll
        every { p.isDead() } returns true;assertNull(service.site(p,step));every { p.isDead() } returns false
        every { p.isOnline } returns false;assertNull(service.site(p,step));every { p.isOnline } returns true
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY,-923)
        assertNull(service.site(p,step));assertEquals(-923,p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
    }
}
