package gg.rsmod.plugins.content.areas.wilderness.npcs

/** N522 report section 8; compiled plugin, real cache definitions and actual Drop Viewer fractions. */
class WildernessMammothTests : WildernessNpcContractTests(listOf(15101)) {
    @kotlin.test.Test fun `compiled mammoth registers only the three NPC-specific sourced sound IDs`() {
        WildernessNpcTestSupport.Fixture()
        val row = gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.rowFor(15101)!!
        kotlin.test.assertEquals(listOf(623),row.attack.map { it.id })
        kotlin.test.assertEquals(625,row.defend)
        kotlin.test.assertEquals(624,row.death)
    }
}
