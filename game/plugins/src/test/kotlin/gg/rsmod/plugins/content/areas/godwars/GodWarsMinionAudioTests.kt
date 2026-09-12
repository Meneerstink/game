package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GodWarsMinionAudioTests {
    @Test
    fun `attack registry contains only explicit source events`() {
        assertEquals(GodWarsMinionAudio.SourcedSound(469, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.SERGEANT_STRONGSTACK))
        assertEquals(GodWarsMinionAudio.SourcedSound(3870, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.SERGEANT_STEELWILL))
        assertEquals(GodWarsMinionAudio.SourcedSound(3851, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.SERGEANT_GRIMSPIKE))
        assertEquals(GodWarsMinionAudio.SourcedSound(2699, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.FLOCKLEADER_GEERIN))
        assertEquals(GodWarsMinionAudio.SourcedSound(2699, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.FLIGHT_KILISA))
        assertEquals(GodWarsMinionAudio.SourcedSound(3877, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.GROWLER))
        assertEquals(GodWarsMinionAudio.SourcedSound(2693, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.BREE))
        assertEquals(GodWarsMinionAudio.SourcedSound(3884, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.BALFRUG_KREEYATH))
        assertEquals(GodWarsMinionAudio.SourcedSound(2508, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.SPIRITUAL_WARRIOR))
        assertEquals(GodWarsMinionAudio.SourcedSound(2548, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.SPIRITUAL_WARRIOR_6255))
        assertEquals(GodWarsMinionAudio.SourcedSound(2867, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.SPIRITUAL_WARRIOR_6277))
    }

    @Test
    fun `attack registry leaves unresolved bodyguard source keys empty`() {
        for (npc in listOf(Npcs.WINGMAN_SKREE, Npcs.STARLIGHT, Npcs.TSTANON_KARLAK, Npcs.ZAKLN_GRITCH)) {
            assertNull(GodWarsMinionAudio.attackSoundFor(npc), "NPC $npc has no explicit donor attack sound")
        }
    }

    @Test
    fun `defend registry contains only cache-backed Void events`() {
        assertEquals(GodWarsMinionAudio.SourcedSound(472, area = false), GodWarsMinionAudio.defendSoundFor(Npcs.SERGEANT_STRONGSTACK))
        assertEquals(GodWarsMinionAudio.SourcedSound(3874, area = true), GodWarsMinionAudio.defendSoundFor(Npcs.SERGEANT_STEELWILL))
        assertEquals(GodWarsMinionAudio.SourcedSound(3859, area = true), GodWarsMinionAudio.defendSoundFor(Npcs.WINGMAN_SKREE))
        assertEquals(GodWarsMinionAudio.SourcedSound(3859, area = true), GodWarsMinionAudio.defendSoundFor(Npcs.STARLIGHT))
        assertEquals(GodWarsMinionAudio.SourcedSound(3869, area = false), GodWarsMinionAudio.defendSoundFor(Npcs.GROWLER))
        assertEquals(GodWarsMinionAudio.SourcedSound(3866, area = false), GodWarsMinionAudio.defendSoundFor(Npcs.BREE))
        assertEquals(GodWarsMinionAudio.SourcedSound(404, area = false), GodWarsMinionAudio.defendSoundFor(Npcs.ZAKLN_GRITCH))
        assertEquals(GodWarsMinionAudio.SourcedSound(3841, area = false), GodWarsMinionAudio.defendSoundFor(Npcs.SPIRITUAL_WARRIOR))
        assertEquals(GodWarsMinionAudio.SourcedSound(29, area = false), GodWarsMinionAudio.defendSoundFor(Npcs.SPIRITUAL_WARRIOR_6255))
        assertEquals(GodWarsMinionAudio.SourcedSound(2869, area = false), GodWarsMinionAudio.defendSoundFor(Npcs.SPIRITUAL_WARRIOR_6277))
    }

    @Test
    fun `defend registry leaves Tstanon without an invented donor event`() {
        assertNull(GodWarsMinionAudio.defendSoundFor(Npcs.TSTANON_KARLAK))
    }

    @Test
    fun `death registry preserves the existing explicit bodyguard events`() {
        assertEquals(GodWarsMinionAudio.SourcedSound(471, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.SERGEANT_STRONGSTACK))
        assertEquals(GodWarsMinionAudio.SourcedSound(3854, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.WINGMAN_SKREE))
        assertEquals(GodWarsMinionAudio.SourcedSound(3867, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.GROWLER))
        assertEquals(GodWarsMinionAudio.SourcedSound(403, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.ZAKLN_GRITCH))
        assertEquals(GodWarsMinionAudio.SourcedSound(3880, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.SPIRITUAL_WARRIOR))
        assertEquals(GodWarsMinionAudio.SourcedSound(2868, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.SPIRITUAL_WARRIOR_6277))
        assertNull(GodWarsMinionAudio.deathSoundFor(Npcs.SPIRITUAL_WARRIOR_6255))
        assertNull(GodWarsMinionAudio.deathSoundFor(Npcs.SERGEANT_STEELWILL))
        assertNull(GodWarsMinionAudio.deathSoundFor(Npcs.STARLIGHT))
        assertNull(GodWarsMinionAudio.deathSoundFor(Npcs.TSTANON_KARLAK))
    }
}
