package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.World
import io.mockk.*
import kotlin.test.*

class ClueMimicSceneryTests {
    private fun table(region: Int=4160)="# dual-cache-placement-verified; applied=true\nregion\t10819\t$region\n"+
        (34727..34732).joinToString("\n",postfix="\n") { "loc\t$it\t${70000+it-34727}" }
    @AfterTest fun cleanup()=unmockkAll()
    @Test fun `only the complete applied named-scenery set and relocated region may enter runtime`() {
        val mapping=MimicArenaScenery.parse(table())
        assertEquals(4160,mapping.region);assertEquals(6,mapping.locals.size)
        for(text in listOf(table().replace("applied=true","applied=false"),table(10819),
            table()+"loc\t34727\t80000\n",table().replace("34732\t70005","34732\t70004"))) {
            assertFailsWith<IllegalArgumentException> { MimicArenaScenery.parse(text) }
        }
    }
    @Test fun `all six exact source examines install atomically and a wrong actor publishes none`() {
        val rows=MimicArenaScenery.sources;assertEquals(6,rows.size)
        assertEquals("Must be a way out of this weird, weird place.",rows.single { it.id==34727 }.examine)
        assertEquals("wheeeeee sugar rush hype",rows.single { it.id==34731 }.examine)
        val defs=mockk<DefinitionSet>();val world=mockk<World>();every { world.definitions } returns defs
        val locals=rows.mapIndexed { index,row -> ObjectDef(70000+index).also { def ->
            def.name=row.name;row.options.forEachIndexed { slot,option -> def.options[slot]=option }
            def.examine="before"
            every { defs.get(ObjectDef::class.java,70000+index) } returns def
        } }
        val mapping=MimicArenaScenery.parse(table())
        locals.last().name="Unrelated object"
        assertFailsWith<IllegalStateException> { MimicArenaScenery.install(world,mapping,rows) }
        assertTrue(locals.all { it.examine=="before" })
        locals.last().name=rows.last().name
        MimicArenaScenery.install(world,mapping,rows)
        assertEquals(rows.map { it.examine },locals.map { it.examine })
    }
}
