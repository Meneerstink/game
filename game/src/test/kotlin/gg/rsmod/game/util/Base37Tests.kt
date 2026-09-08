package gg.rsmod.game.util

import gg.rsmod.util.Base37
import org.junit.Assert.assertEquals
import org.junit.Test

class Base37Tests {
    @Test
    fun `a display name survives a round trip`() {
        listOf("Zezima", "Mod Ash", "A", "Player1", "Woox16").forEach { name ->
            assertEquals(name, Base37.decodeName(Base37.encode(name)))
        }
    }

    @Test
    fun `the raw decoding is the client's underscore form`() {
        // What the client's own decode() returns, and the only form that round-trips exactly: the
        // display form capitalises every word, so "Sparc mac" would come back as "Sparc Mac".
        assertEquals("mod_ash", Base37.decode(Base37.encode("Mod Ash")))
        assertEquals("sparc_mac", Base37.decode(Base37.encode("Sparc mac")))
    }

    @Test
    fun `encoding ignores case`() {
        assertEquals(Base37.encode("Zezima"), Base37.encode("zEZIMA"))
    }

    @Test
    fun `underscores and spaces are the same symbol`() {
        assertEquals(Base37.encode("Mod Ash"), Base37.encode("Mod_Ash"))
    }

    @Test
    fun `trailing spaces do not change the value`() {
        // Stripping them is what makes the encoding stable for names padded to a fixed width.
        assertEquals(Base37.encode("Zezima"), Base37.encode("Zezima   "))
    }

    @Test
    fun `leading spaces do not change the value either`() {
        // The space symbol is 0, so a leading run of them only ever multiplies 0 by 37. Names that
        // differ solely in leading whitespace are therefore the same channel, and callers that care
        // about the distinction have to normalise before encoding rather than after.
        assertEquals(Base37.encode("Zezima"), Base37.encode(" Zezima"))
        assertEquals(Base37.encode("Zezima"), Base37.encode("___Zezima"))
    }

    @Test
    fun `an empty name encodes to zero`() {
        assertEquals(0L, Base37.encode(""))
        assertEquals(0L, Base37.encode("   "))
    }

    @Test
    fun `values the encoding can never produce decode to an empty name`() {
        // Arrives over the network, so a malformed value must not be able to throw.
        listOf(0L, -1L, Long.MAX_VALUE, 6582952005840035281L, 37L).forEach { value ->
            assertEquals("", Base37.decode(value))
            assertEquals("", Base37.decodeName(value))
        }
    }

    @Test
    fun `decoding capitalises the way the client renders a name`() {
        assertEquals("Zezima", Base37.decodeName(Base37.encode("zezima")))
        assertEquals("Mod Ash", Base37.decodeName(Base37.encode("mod ash")))
    }

    @Test
    fun `a name longer than the encoding holds is truncated rather than overflowing`() {
        // Twelve symbols is the whole range; the thirteenth would push the value past the bound the
        // client rejects, and a rejected channel name is a channel nobody can see.
        val twelve = "abcdefghijkl"
        assertEquals("abcdefghijkl", Base37.decode(Base37.encode(twelve)))
        assertEquals(Base37.encode(twelve), Base37.encode(twelve + "mnop"))
    }
}
