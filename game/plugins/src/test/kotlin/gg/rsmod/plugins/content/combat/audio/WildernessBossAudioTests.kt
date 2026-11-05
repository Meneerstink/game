package gg.rsmod.plugins.content.combat.audio

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.*

class WildernessBossAudioTests {
    @Test fun `every boss form and minion has explicit contracts and enabled cues always carry provenance`() {
        val ids = setOf(15076,15077,15078,15079,15080,15069,15071,15075,15072,15073,15086,15087)
        assertEquals(ids, WildernessBossAudio.contracts.keys)
        WildernessBossAudio.contracts.values.forEach { cues ->
            assertTrue("defend" in cues && "death" in cues)
            assertTrue(cues.keys.any { it.startsWith("attack.") })
            assertTrue(cues.keys.any { it.startsWith("impact.") })
            cues.values.filter { it.sound != null }.forEach { cue ->
                assertTrue(cue.source?.contains("N522-GELUIDSBRONNEN") == true)
                assertEquals(15, cue.radius)
                assertEquals(0, cue.delay)
            }
        }
    }
    @Test fun `owner approved current OSRS sounds map to their existing local imports while unproven cues stay empty`() {
        fun sound(id: Int, cue: String) = WildernessBossAudio.contracts.getValue(id).getValue(cue).sound
        assertEquals(listOf(10562,10563,10467,586,10575), listOf("attack.melee","impact.melee","attack.roar","attack.shockwave","death").map { sound(15076,it) })
        for (id in listOf(15077,15078)) {
            assertEquals(listOf(10583,10586,10453,10587,10589), listOf("attack.melee","impact.melee","attack.lightning","attack.earthquake","defend").map { sound(id,it) })
        }
        assertNull(sound(15077,"death"), "normal form completion is a transition, not a completed-boss death cue")
        assertEquals(10597,sound(15078,"death")); assertEquals(10518,sound(15078,"spawn"))
        for (id in listOf(15079,15080)) {
            assertEquals(listOf(10305,10306,10307),listOf("attack.melee","defend","death").map { sound(id,it) })
            assertNull(sound(id,"spawn"), "OSRS 6910 is not imported")
        }
        for (id in listOf(15069,15071,15075,15072,15073,15086,15087)) {
            assertTrue(WildernessBossAudio.contracts.getValue(id).values.all { it.sound == null })
        }
        assertNull(sound(15076,"defend")); assertNull(sound(15076,"attack.heal"))
    }
    @Test fun `attack and actual impact emit distinct area cues once each per cycle and an unproven defend emits nothing`() {
        val world = mockk<World>(relaxed=true)
        val npc = mockk<Npc>(relaxed=true)
        val areas = mutableListOf<AreaSound>()
        every { npc.id } returns 15076
        every { npc.attr } returns AttributeMap()
        every { npc.world } returns world
        every { npc.tile } returns Tile(3324,3847,0)
        every { world.currentCycle } returns 100
        every { world.spawn(any<AreaSound>()) } answers { areas += firstArg<AreaSound>() }
        WildernessBossAudio.begin(npc,"melee")
        WildernessBossAudio.dispatch(npc,"attack.melee")
        WildernessBossAudio.dispatch(npc,"impact.melee")
        WildernessBossAudio.dispatch(npc,"impact.melee")
        WildernessBossAudio.dispatch(npc,"defend")
        assertEquals(listOf(10562,10563),areas.map { it.id })
        assertTrue(areas.all { it.tile.sameAs(npc.tile) && it.radius==15 && it.delay==0 && it.volume==1 })
    }
    @Test fun `all enabled local sounds have physical synth data without any cache import`() {
        val store = CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..","..","data","cache").toString())
        try {
            WildernessBossAudio.contracts.values.flatMap { it.values }.mapNotNull { it.sound }.toSet().forEach { id ->
                assertTrue(store.data(4,id,0)?.isNotEmpty()==true,"enabled local synth $id is missing")
            }
        } finally { store.close() }
    }
    @Test fun `a sound ID cannot be enabled without provenance radius and timing`() {
        assertFailsWith<IllegalArgumentException> { WildernessBossAudio.Cue("attack", sound = 1) }
        assertFailsWith<IllegalArgumentException> { WildernessBossAudio.Cue("attack", source = "", sound = 1, radius = 15, delay = 0) }
    }
}
