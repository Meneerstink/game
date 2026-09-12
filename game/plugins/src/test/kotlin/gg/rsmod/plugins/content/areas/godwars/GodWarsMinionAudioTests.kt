package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GodWarsMinionAudioTests {
    @Test
    fun `attack registry contains only explicit bodyguard source events`() {
        assertEquals(GodWarsMinionAudio.SourcedSound(469, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.SERGEANT_STRONGSTACK))
        assertEquals(GodWarsMinionAudio.SourcedSound(3870, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.SERGEANT_STEELWILL))
        assertEquals(GodWarsMinionAudio.SourcedSound(3851, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.SERGEANT_GRIMSPIKE))
        assertEquals(GodWarsMinionAudio.SourcedSound(2699, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.FLOCKLEADER_GEERIN))
        assertEquals(GodWarsMinionAudio.SourcedSound(2699, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.FLIGHT_KILISA))
        assertEquals(GodWarsMinionAudio.SourcedSound(3877, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.GROWLER))
        assertEquals(GodWarsMinionAudio.SourcedSound(2693, area = true), GodWarsMinionAudio.attackSoundFor(Npcs.BREE))
        assertEquals(GodWarsMinionAudio.SourcedSound(3884, area = false), GodWarsMinionAudio.attackSoundFor(Npcs.BALFRUG_KREEYATH))
    }

    @Test
    fun `attack registry leaves unresolved bodyguard source keys empty`() {
        for (npc in listOf(Npcs.WINGMAN_SKREE, Npcs.STARLIGHT, Npcs.TSTANON_KARLAK, Npcs.ZAKLN_GRITCH)) {
            assertNull(GodWarsMinionAudio.attackSoundFor(npc), "NPC $npc has no explicit donor attack sound")
        }
    }

    @Test
    fun `death registry preserves the existing explicit bodyguard events`() {
        assertEquals(GodWarsMinionAudio.SourcedSound(471, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.SERGEANT_STRONGSTACK))
        assertEquals(GodWarsMinionAudio.SourcedSound(3854, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.WINGMAN_SKREE))
        assertEquals(GodWarsMinionAudio.SourcedSound(3867, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.GROWLER))
        assertEquals(GodWarsMinionAudio.SourcedSound(403, area = true), GodWarsMinionAudio.deathSoundFor(Npcs.ZAKLN_GRITCH))
        assertNull(GodWarsMinionAudio.deathSoundFor(Npcs.SERGEANT_STEELWILL))
        assertNull(GodWarsMinionAudio.deathSoundFor(Npcs.STARLIGHT))
        assertNull(GodWarsMinionAudio.deathSoundFor(Npcs.TSTANON_KARLAK))
    }
}
