package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import io.mockk.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

class TreasureTrailUriSourceTests {
    private val file = File(RspsPaths.data("../docs/CLUE/cache/uri-source.json"))
    private val raw = file.readText()
    private val root = JsonParser().parse(raw).asJsonObject
    private val catalogue = TreasureTrailUri.parse(raw)

    @Test fun `all25 emitted phrases and refusal are in the exact pinned current transcript and historical phrases are absent`() {
        for ((_, pin) in root["sources"].asJsonObject.entrySet()) {
            val bytes=File(file.parentFile.parentFile,"sources/${pin.asJsonObject["file"].asString}").readBytes()
            assertEquals(pin.asJsonObject["sha256"].asString,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
        }
        val source=File(file.parentFile.parentFile,"sources/Transcript_Uri.wiki").readText()
        val standard=source.substringAfter("==Standard dialogue==").substringBefore("== Speaking with")
        val exact=Regex("\\*\\* '''Uri:''' ([^\\n\\r]+)").findAll(standard).map { it.groupValues[1] }.toList()
        assertEquals(exact,catalogue.phrases)
        assertEquals("What?",catalogue.playerReply)
        assertTrue(source.contains("'''Uri:''' ${catalogue.refusal}"))
        assertEquals("Uri Molotov - shaken, not stirred.",catalogue.examine)
        assertFalse(catalogue.phrases.contains("Brother, do you even lift?"))
    }

    @Test fun `missing or misidentified existing actors never enable a step`() {
        val world=mockk<World>()
        val defs=mockk<DefinitionSet>()
        every { world.definitions } returns defs
        every { defs.getNullable(NpcDef::class.java,any()) } returns null
        for(tier in ClueScrollTier.values()) assertNull(catalogue.actor(world,tier))
        val actor=NpcDef(5141).also {
            it.name="Uri";it.options[0]="Talk-to";it.basId=4;it.chatheadModels=intArrayOf(39836,39232,39417)
        }
        every { defs.getNullable(NpcDef::class.java,any()) } returns actor
        assertEquals(5141,catalogue.actor(world,ClueScrollTier.EASY))
        assertEquals(5142,catalogue.actor(world,ClueScrollTier.MEDIUM))
        assertEquals(5143,catalogue.actor(world,ClueScrollTier.ELITE))
        actor.name="Double agent"
        assertNull(catalogue.actor(world,ClueScrollTier.EASY))
        actor.name="Uri";actor.chatheadModels=null
        assertNull(catalogue.actor(world,ClueScrollTier.EASY))
    }
}
