package gg.rsmod.plugins.content.mechanics.travel

import com.google.gson.Gson
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.interf.listener.InterfaceListener
import gg.rsmod.game.model.varp.VarpSet
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner bug 2026-10-04 (bug-20261004-024316-anudd): "when using the trinket of fairy the travel log does not remember the locations
 * you have visited". The visited codes were stored, but the travel log 735 only shows entries whose text is set (667 client script
 * 959) and the server never set any. These tests guard: a journey records its code, the record survives the save/load path of
 * JsonPlayerSerializer, and opening the dial (ring, house ring or trinket - one [FairyRings.open]) fills the log.
 */
class FairyRingTravelLogTests {
    private val sent = mutableListOf<Message>()

    private fun newPlayer(attributes: AttributeMap = AttributeMap()): Player {
        val player = mockk<Player>(relaxed = true)
        val definitions = mockk<DefinitionSet>(relaxed = true)
        // 667 cache: dial varbits 2341-2343 sit in varp 816 at bits 0-1, 3-4, 6-7 (the owner's trace: 0 -> 24 -> 216 -> 219).
        every { definitions.get(VarbitDef::class.java, any()) } answers {
            val id = secondArg<Int>()
            VarbitDef(id).apply {
                varp = 816
                startBit =
                    when (id) {
                        2341 -> 0
                        2342 -> 3
                        2343 -> 6
                        else -> 19
                    }
                endBit = startBit + if (id == FairyRings.RESORT_VARBIT) 0 else 1
            }
        }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { player.world } returns world
        every { player.attr } returns attributes
        every { player.varps } returns VarpSet((0 until 4000).toSet())
        every { player.varcs } returns MutableList(4000) { 0 }
        val interfaces =
            InterfaceSet(
                object : InterfaceListener {
                    override fun onInterfaceOpen(interfaceId: Int) = Unit

                    override fun onInterfaceClose(interfaceId: Int) = Unit
                },
            )
        interfaces.displayMode = DisplayMode.RESIZABLE_NORMAL
        every { player.interfaces } returns interfaces
        every { player.write(*anyVararg<Message>()) } answers { sent.addAll(firstArg<Array<Message>>()) }
        return player
    }

    private fun logTexts(): Map<Int, String> =
        sent.filterIsInstance<IfSetTextMessage>().filter { it.parent == FairyRings.TRAVEL_LOG_INTERFACE }.associate { it.child to it.text }

    @Test
    fun `travel log components follow the 667 cache order`() {
        // 735:14 op "A I P" .. 735:77 op "D L S"; the Void travel_log ids for the real destinations.
        assertEquals(14, FairyRings.logComponent("aip"))
        assertEquals(15, FairyRings.logComponent("aiq"))
        assertEquals(19, FairyRings.logComponent("ajq"))
        assertEquals(38, FairyRings.logComponent("bkp"))
        assertEquals(61, FairyRings.logComponent("cls"))
        assertEquals(72, FairyRings.logComponent("dkr"))
        assertEquals(77, FairyRings.logComponent("dls"))
        assertNull(FairyRings.logComponent("zzz"))
        for (component in FairyRings.LOG_FIRST_COMPONENT..FairyRings.LOG_LAST_COMPONENT) {
            assertEquals(component, FairyRings.logComponent(FairyRings.codeOfLogComponent(component)!!))
        }
        FairyRings.CODES.keys.forEach { assertTrue(FairyRings.logComponent(it) != null, "no log entry for $it") }
    }

    @Test
    fun `a journey records its code once and an unknown code records nothing`() {
        val player = newPlayer()
        FairyRings.record(player, "aiq")
        FairyRings.record(player, "dkr")
        FairyRings.record(player, "aiq")
        FairyRings.record(player, "bjq") // no destination: lands in Zanaris, not logged
        assertEquals(listOf("aiq", "dkr"), FairyRings.travelLog(player))
        assertEquals("aiq", player.attr[FairyRings.LAST_CODE])
    }

    @Test
    fun `the travel log survives save and load`() {
        val player = newPlayer()
        FairyRings.record(player, "cks")
        FairyRings.record(player, "alq")
        // JsonPlayerSerializer: attr.toPersistentMap() -> Gson -> AttributeKey<Any>(key) per entry on load.
        val gson = Gson()
        val json = gson.toJson(player.attr.toPersistentMap())
        @Suppress("UNCHECKED_CAST")
        val loaded = gson.fromJson(json, Map::class.java) as Map<String, Any>
        val restored = AttributeMap()
        loaded.forEach { (key, value) -> restored[AttributeKey<Any>(key)] = if (value is Double) value.toInt() else value }
        val reloaded = newPlayer(restored)
        assertEquals(listOf("cks", "alq"), FairyRings.travelLog(reloaded))
        assertEquals("alq", reloaded.attr[FairyRings.LAST_CODE])
    }

    @Test
    fun `opening the dial through the trinket shows every visited code in the travel log`() {
        val player = newPlayer()
        FairyRings.record(player, "aiq")
        FairyRings.record(player, "dkr")
        sent.clear()
        assertTrue(FairyRings.open(player, FairyRings.Source.TRINKET))
        assertTrue(player.interfaces.isVisible(FairyRings.TRAVEL_LOG_INTERFACE))
        assertEquals(
            mapOf(15 to "<br>Asgarnia: Mudskipper Point", 72 to "<br>Misthalin: Edgeville"),
            logTexts(),
        )
        // The dials open on the last destination.
        assertEquals("dkr", FairyRings.dialledCode(player))
    }

    @Test
    fun `an account without journeys gets an empty log`() {
        val player = newPlayer()
        assertTrue(FairyRings.open(player, FairyRings.Source.TRINKET))
        assertTrue(logTexts().isEmpty())
    }

    @Test
    fun `clicking a log entry puts its code on the dials`() {
        val player = newPlayer()
        FairyRings.dialFromLog(player, FairyRings.logComponent("cls")!!)
        assertEquals("cls", FairyRings.dialledCode(player))
        FairyRings.dialFromLog(player, FairyRings.logComponent("ajr")!!)
        assertEquals("ajr", FairyRings.dialledCode(player))
    }
}
