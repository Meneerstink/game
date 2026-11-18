package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.drops.*
import io.mockk.*
import java.io.File
import java.security.SecureRandom
import kotlin.test.*

class ClueGuardianDropsTests {
    private val catalogue = ClueGuardianDropCatalogue.parse(File(gg.rsmod.game.RspsPaths.data("cfg/clue/guardian-drops.json")).readText())
    @BeforeTest fun setup() { mockkObject(ClueItems); every { ClueItems.local(any()) } returns null; every { ClueItems.local(22879) } returns 35000 }
    @AfterTest fun cleanup() { unmockkAll() }
    private fun tables(source: Int, wealth: Boolean = false, legends: Boolean = true, tile: Tile = Tile(3000,3400)) =
        DropTableBuilder(mockk(relaxed=true), SecureRandom()).apply(catalogue.table(source, wealth, legends, tile)).tables
    private fun chances(table: DropTable): Map<Int,Double> {
        val result = mutableMapOf<Int,Double>()
        fun add(id:Int,p:Double) { result[id] = (result[id] ?: 0.0) + p }
        val probabilities = if (table.name == "guaranteed") DoubleArray(table.entries.size) { 1.0 } else DropTableFactory.lineChances(table, 1.0)
        table.entries.forEachIndexed { i,entry ->
            when(val drop = entry.drop) {
                is DropEntry.ItemDrop -> add(drop.item.id, probabilities[i])
                is DropEntry.ItemRangeDrop -> add(drop.item.id, probabilities[i])
                is DropEntry.TableDrop -> chances(drop.table).forEach { (id,p) -> add(id,p*probabilities[i]) }
                DropEntry.NothingDrop -> Unit
                is DropEntry.MultiDrop -> error("Guardian loot must be a single main outcome")
            }
        }
        return result
    }
    private fun nested(parent: DropTable, label: String): DropTable = parent.entries.mapNotNull { it.drop as? DropEntry.TableDrop }
        .single { it.label == label }.table

    @Test fun `all eleven sourced variants have bones but no donor looting bag`() {
        assertEquals(setOf(1777,1778,7312,2954,2955,6587,6588,7307,7308,7309,7310),catalogue.sources)
        for (source in catalogue.sources) {
            val ts = tables(source)
            assertEquals(mapOf(526 to 1.0), chances(ts.getValue("guaranteed")))
            assertFalse(ts.values.any { 11941 in chances(it) })
            if (source in setOf(1777,1778,7312)) assertEquals(setOf("guaranteed"), ts.keys)
        }
    }
    @Test fun `hard wizards retain the unfilled sixteen slots and exact rune quantities`() {
        val sara = tables(2955).getValue("main")
        val zam = tables(2954).getValue("main")
        assertEquals(128,sara.totalSlots); assertEquals(112,sara.entries.last().index)
        assertEquals(128,zam.totalSlots); assertEquals(112,zam.entries.last().index)
        assertEquals(12.0/128, chances(sara).getValue(565),1e-14)
        assertEquals(4.0/128,chances(zam).getValue(1033),1e-14)
        assertEquals(3.0/128,chances(zam).getValue(1035),1e-14)
        assertEquals(0.875,chances(sara).values.sum(),1e-14)
        val blood = sara.entries.map { it.drop }.filterIsInstance<DropEntry.ItemDrop>().single { it.item.id == 565 }
        assertEquals(8,blood.item.amount)
    }
    @Test fun `elite god items are a single pre-roll replacing main loot for both guards`() {
        for(source in listOf(6587,6588)) {
            val ts = tables(source)
            val pre = ts.getValue("pre_roll")
            assertEquals(2000000,pre.totalSlots)
            assertEquals(mapOf(11718 to 1.0/2000000,11728 to 1.0/2000000),chances(pre))
            assertEquals(1.0-1.0/1000000,DropTableFactory.mainTableWeight(-1,ts.values.toList(),1.0),1e-14)
            assertFalse(11718 in chances(ts.getValue("main")))
        }
    }
    @Test fun `herbs seeds and notes use their source nested outcomes`() {
        for(source in listOf(7307,7308,7309,7310)) {
            val main = tables(source).getValue("main")
            assertEquals(50,main.totalSlots)
            assertEquals(7.0/50,chances(main).getValue(7937),1e-14)
            assertEquals(3.0/50/128,chances(main).getValue(35000),1e-14)
            val herbs = nested(main,"Herb drop table")
            assertEquals(11,herbs.entries.size)
            assertEquals(32.0/128,chances(herbs).getValue(199),1e-14)
            val seeds = nested(main,"Allotment seed drop table")
            assertEquals(1.0,chances(seeds).values.sum(),1e-14)
        }
    }
    @Test fun `wealth removes only source empty gem slots and respects independent quest and geography`() {
        for(wealth in listOf(false,true)) for(legends in listOf(false,true)) for(underground in listOf(false,true)) {
            val main = tables(6587,wealth,legends,Tile(3000,if(underground) 10000 else 3400)).getValue("main")
            val gems = nested(main,"Gem drop table")
            assertEquals(if(wealth)65 else 128,gems.totalSlots)
            val p = chances(gems)
            assertEquals(32.0/gems.totalSlots,p.getValue(1623),1e-14)
            assertEquals((if(legends)3.0 else 4.0)/gems.totalSlots,p.getValue(if(underground)1452 else 1462),1e-14)
            assertFalse((if(underground)1462 else 1452) in p)
            assertEquals(legends,1249 in p)
            if(legends) assertEquals(3.0/gems.totalSlots/(if(wealth)15 else 128),p.getValue(1249),1e-14)
        }
    }
    @Test fun `missing imported seed blocks only reachable tables and never reweights loot`() {
        every { ClueItems.local(22879) } returns null
        val defs = mockk<DefinitionSet>()
        every { defs.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val world = mockk<World>(); every { world.definitions } returns defs
        assertFalse(catalogue.available(world,7310))
        assertTrue(catalogue.available(world,2955))
        assertTrue(catalogue.available(world,6587))
        assertFailsWith<IllegalStateException> { tables(7310) }
        every { ClueItems.local(22879) } returns 35000
        assertTrue(catalogue.available(world,7310))
    }
}
