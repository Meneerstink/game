package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.setCombatTarget
import io.mockk.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

class TreasureTrailGuardianDialogueTests {
    @AfterTest fun cleanup() = unmockkAll()
    private fun account() = mockk<Player>(relaxed = true).also {
        every { it.isOnline } returns true
        every { it.username } returns "Solver"
    }
    @Test fun `every spawn alternative is exact pinned transcript text and unknown sources stay silent`() {
        val manifest = JsonParser().parse(File(RspsPaths.data("../docs/CLUE/GUARDIAN-DIALOGUE-SOURCES.json")).readText()).asJsonArray
        val ids = mapOf("Ancient" to listOf(7307,7308,7309), "Brassican" to listOf(7310),
            "Armadylean" to listOf(6587), "Bandosian" to listOf(6588))
        val p = account()
        manifest.forEach { entry ->
            val row = entry.asJsonObject
            val raw = File(RspsPaths.data("../docs/CLUE/sources/" + row["file"].asString)).readBytes()
            assertEquals(row["sha256"].asString, MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) })
            val text = raw.toString(Charsets.UTF_8).substringBefore("===When eating")
            val lines = Regex("\\{\\{overhead\\|([^}]+)}}").findAll(text).map { it.groupValues[1] }.toList()
            val sources = ids.entries.single { row["page"].asString.contains(it.key) }.value
            for (source in sources) for (index in lines.indices) {
                val actor = mockk<Npc>(relaxed = true)
                TreasureTrailGuardianDialogue.spawned(p,listOf(source),listOf(actor)) { size -> assertEquals(lines.size,size);index }
                verify(exactly = 1) { actor.forceChat(lines[index]) }
            }
        }
        val actor = mockk<Npc>(relaxed = true)
        TreasureTrailGuardianDialogue.spawned(p,listOf(1778),listOf(actor)) { error("Double agent has its own source dialogue") }
        verify(exactly = 0) { actor.forceChat(any()) }
    }
    @Test fun `cabbage response belongs only to a live personal mage currently fighting this account`() {
        mockkObject(TreasureTrailGuardians,ClueNpcs)
        val p=account();val stranger=account();val world=mockk<World>(relaxed=true)
        every { p.world } returns world
        val actor=mockk<Npc>(relaxed=true)
        every { actor.id } returns 32000;every { actor.world } returns world
        every { actor.isSpawned() } returns true;every { actor.attr } returns AttributeMap()
        actor.setCombatTarget(p)
        val contract=mockk<ClueStepContract>();every { contract.valid(p) } returns true
        val session=mockk<TreasureTrailGuardians.Session>()
        every { session.contract } returns contract;every { session.actors } returns listOf(actor)
        every { TreasureTrailGuardians.session(p) } returns session
        every { TreasureTrailGuardians.session(stranger) } returns null
        every { ClueNpcs.local(7310) } returns 32000
        val texts=mutableListOf<String>();every { actor.forceChat(capture(texts)) } just Runs
        repeat(4) { index -> assertTrue(TreasureTrailGuardianDialogue.ateCabbage(p) { index }) }
        assertEquals(4,texts.distinct().size)
        assertTrue(texts.all { "Solver" in it && "[player name]" !in it })
        assertFalse(TreasureTrailGuardianDialogue.ateCabbage(stranger))
        actor.setCombatTarget(stranger)
        assertFalse(TreasureTrailGuardianDialogue.ateCabbage(p))
        actor.setCombatTarget(p)
        every { actor.isDead() } returns true
        assertFalse(TreasureTrailGuardianDialogue.ateCabbage(p))
        every { actor.isDead() } returns false;every { contract.valid(p) } returns false
        assertFalse(TreasureTrailGuardianDialogue.ateCabbage(p))
        every { contract.valid(p) } returns true;every { p.isOnline } returns false
        assertFalse(TreasureTrailGuardianDialogue.ateCabbage(p))
        assertEquals(4,texts.size)
    }
}
