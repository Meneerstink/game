package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.npcs.TranscriptDialogue
import gg.rsmod.plugins.content.skills.summoning.Pets
import io.mockk.*
import kotlin.test.*

class CluePetDigTests {
    @BeforeTest fun setup()=mockkObject(Pets)
    @AfterTest fun cleanup()=unmockkAll()
    private fun pair(upgraded: Boolean=false): Pair<Player,Npc> {
        val world=mockk<World>(relaxed=true)
        val p=mockk<Player>(relaxed=true);every { p.world } returns world;every { p.isOnline } returns true
        val npc=mockk<Npc>(relaxed=true);every { npc.world } returns world;every { npc.isSpawned() } returns true
        val id=if(upgraded)14844 else TranscriptDialogue.BLOODHOUND
        every { npc.id } returns id
        every { Pets.baseNpc(id) } returns TranscriptDialogue.BLOODHOUND
        every { Pets.current(p) } returns npc
        return p to npc
    }
    @Test fun `both pet forms animate the real follower and use the owner's dig contract without a player animation`() {
        for(upgraded in listOf(false,true)) {
            val(p,npc)=pair(upgraded);var progress=0
            assertTrue(CluePetDig.dig(p,npc,visual={9000},progress={ assertSame(p,it);progress++;true }))
            assertEquals(1,progress);verify(exactly=1) { npc.animate(9000) };verify(exactly=0) { p.animate(any<Int>()) }
        }
    }
    @Test fun `wrong owner ordinary dog foreign world death and logout cannot dig a clue`() {
        val(p,npc)=pair();val stranger=mockk<Player>(relaxed=true)
        every { stranger.isOnline } returns true;every { Pets.current(stranger) } returns null
        val failVisual={ _:World ->error("No foreign or stale follower may animate") }
        val failProgress={ _:Player ->error("No foreign or stale follower may advance") }
        assertFalse(CluePetDig.dig(stranger,npc,failVisual,failProgress))
        every { Pets.baseNpc(TranscriptDialogue.BLOODHOUND) } returns 123
        assertFalse(CluePetDig.dig(p,npc,failVisual,failProgress))
        every { Pets.baseNpc(TranscriptDialogue.BLOODHOUND) } returns TranscriptDialogue.BLOODHOUND
        every { npc.world } returns mockk<World>()
        assertFalse(CluePetDig.dig(p,npc,failVisual,failProgress))
        every { npc.world } returns p.world;every { npc.isDead() } returns true
        assertFalse(CluePetDig.dig(p,npc,failVisual,failProgress))
        every { npc.isDead() } returns false;every { npc.isSpawned() } returns false
        assertFalse(CluePetDig.dig(p,npc,failVisual,failProgress))
        every { npc.isSpawned() } returns true;every { p.isOnline } returns false
        assertFalse(CluePetDig.dig(p,npc,failVisual,failProgress))
    }
    @Test fun `missing source animation retains clue and wrong tile can animate without advancing`() {
        val(p,npc)=pair()
        assertFalse(CluePetDig.dig(p,npc,visual={null},progress={error("Missing assets cannot spend a clue")}))
        verify(exactly=0) { npc.animate(any<Int>()) }
        var attempts=0
        assertTrue(CluePetDig.dig(p,npc,visual={9000},progress={attempts++;false}))
        assertEquals(1,attempts)
    }
    @Test fun `only one applied source14498 identity can enter gameplay`() {
        assertEquals(9000,CluePetDig.parse("# dual-cache-ledger-verified; applied=true\nseq\t14498\t9000\n"))
        for(text in listOf("# plan; applied=false\nseq\t14498\t9000\n", "# applied=true\nseq\t14499\t9000\n",
            "# applied=true\nseq\t14498\t9000\nseq\t14498\t9001\n", "# applied=true\nseq\t14498\t-1\n")) {
            assertFailsWith<IllegalArgumentException> { CluePetDig.parse(text) }
        }
    }
}
