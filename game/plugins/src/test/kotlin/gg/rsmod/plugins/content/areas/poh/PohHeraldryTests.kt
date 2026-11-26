package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CONSTRUCTION-99 deel 7: heraldic crests. The crest pieces carry one loc per crest in the OSRS Wiki order, the painted items sit
 * at first + 2 x crest (OSRS Wiki ids: Banner (Zamorak) 8680, Steel heraldic helm (Saradomin) 8706, Steel kiteshield (Varrock) 8774,
 * Rune kiteshield (Arrav) 8714, Rune heraldic helm (Asgarnia) 8466), and an unprotected PvP death gives the killer the plain item.
 */
class PohHeraldryTests {
    private val attr = AttributeMap()
    private val player = mockk<Player>().also { every { it.attr } returns attr }

    @Test
    fun `painted item ids follow the OSRS Wiki crest order`() {
        assertEquals(8680, PohHeraldry.BANNER_FIRST + PohHeraldry.Crest.ZAMORAK.ordinal * 2)
        assertEquals(8706, PohHeraldry.Painted.STEEL_HELM.painted(PohHeraldry.Crest.SARADOMIN))
        assertEquals(8774, PohHeraldry.Painted.STEEL_KITE.painted(PohHeraldry.Crest.VARROCK))
        assertEquals(8714, PohHeraldry.Painted.RUNE_KITE.painted(PohHeraldry.Crest.ARRAV))
        assertEquals(8466, PohHeraldry.Painted.RUNE_HELM.painted(PohHeraldry.Crest.ASGARNIA))
        assertEquals(16, PohHeraldry.Crest.values().size)
        assertEquals(64, PohHeraldry.PAINTED_TO_BASE.size)
    }

    @Test
    fun `crest pieces show the owner's crest - wall decorations and shields, OSRS levels`() {
        val oak = PohFurniture.DECOS.getValue("OAK_DECORATION")
        assertEquals(16, oak.objs.size)
        assertEquals(13798, oak.obj(PlayerHouse.Style.FANCY_STONE, PohHeraldry.Crest.ARRAV.ordinal))
        assertEquals(13813, oak.obj(PlayerHouse.Style.BASIC_WOOD, PohHeraldry.Crest.ZAMORAK.ordinal))
        assertEquals(13782 + PohHeraldry.DEFAULT_CREST, PohFurniture.DECOS.getValue("GILDED_DECORATION").obj(PlayerHouse.Style.BASIC_WOOD))
        assertEquals(listOf(66, 76, 86), listOf("ROUND_SHIELD", "SQUARE_SHIELD", "KITE_SHIELD").map { PohFurniture.DECOS.getValue(it).level })
        PohHeraldry.CREST_PIECES.forEach { assertTrue(PohFurniture.DECOS.getValue(it).crestPiece, it) }
        // The throne room and treasure room decoration spaces are buildable now.
        listOf(15434, 15259).forEach { assertEquals("RECURSIVE", PohFurniture.hotspotOf(it)?.type, "$it") }
        assertTrue(PohFurniture.HOTSPOTS.none { it.type == "CREST" })
    }

    @Test
    fun `no crest until Sir Renitee assigns one, then the saved crest`() {
        assertNull(PohHeraldry.crest(player))
        assertEquals(PohHeraldry.Crest.VARROCK.ordinal, PohHeraldry.crestIndex(player))
        PohHeraldry.setCrest(player, PohHeraldry.Crest.SARADOMIN)
        assertEquals(PohHeraldry.Crest.SARADOMIN, PohHeraldry.crest(player))
        assertEquals(12, PohHeraldry.crestIndex(player))
    }

    @Test
    fun `painted helms and kiteshields go to the killer unpainted`() {
        assertEquals(listOf(Items.RUNE_FULL_HELM), PvpDeathBreakables.PVP_BASE[8466])
        assertEquals(listOf(Items.STEEL_KITESHIELD), PvpDeathBreakables.PVP_BASE[8774])
        assertTrue(PvpDeathBreakables.converts(8706))
    }
}
