package gg.rsmod.plugins.content.mechanics.clan

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The clan packets must decode with the client's own readers: `ClanSettings.decode` (version 1-5) and `ClanChannel.decode`, preceded
 * by the `g1 affined` byte that `ServerConnectionReader` reads for CLANSETTINGS_FULL / CLANCHANNEL_FULL. This test replays those
 * readers field for field over the server's bytes and demands the whole body is consumed.
 */
class ClanPacketTests {
    private class In(bytes: ByteArray) {
        val buf: ByteBuffer = ByteBuffer.wrap(bytes)
        fun g1() = buf.get().toInt() and 0xFF
        fun g1b() = buf.get().toInt()
        fun g2() = buf.short.toInt() and 0xFFFF
        fun g4() = buf.int
        fun g8() = buf.long
        fun gjstr(): String {
            val sb = StringBuilder()
            while (true) {
                val c = buf.get().toInt() and 0xFF
                if (c == 0) return sb.toString()
                sb.append(c.toChar())
            }
        }
        fun fastgstr(): String? = if (buf.get(buf.position()).toInt() == 0) { buf.get(); null } else gjstr()
    }

    private val clan =
        Clan("Deadmen").apply {
            members["Owner"] = ClanRank.OWNER
            members["Pal"] = ClanRank.GENERAL
        }

    @Test
    fun `clan settings decode like ClanSettings version 3`() {
        val p = In(Clans.encodeSettings(clan, affined = true, update = 5))
        assertEquals(1, p.g1(), "affined")
        val version = p.g1()
        assertEquals(3, version)
        assertEquals(2, p.g1() and 0x2, "display names")
        assertEquals(5, p.g4())
        assertEquals(0, p.g4(), "creation date")
        val affined = p.g2()
        val banned = p.g1()
        assertEquals(2, affined)
        assertEquals(0, banned)
        assertEquals("Deadmen", p.gjstr())
        assertEquals(1, p.g1(), "guests allowed")
        assertEquals(ClanRank.MEMBER.value, p.g1b())
        assertEquals(ClanRank.ADMIN.value, p.g1b())
        p.g1b()
        p.g1b()
        val members = (0 until affined).associate { p.fastgstr()!! to p.g1b().also { p.g4() } }
        assertEquals(mapOf("Owner" to 126, "Pal" to 5), members)
        // Novite generateClanSettingsDataBlock always sends the motif colours (16, 18); nothing else for a default clan.
        assertEquals(2, p.g2(), "extra settings (version >= 3)")
        val c = Clan.DEFAULT_MOTIF_COLOURS
        assertEquals(16, p.g4()); assertEquals(c[0] or (c[1] shl 16), p.g4())
        assertEquals(18, p.g4()); assertEquals(c[2] or (c[3] shl 16), p.g4())
        assertEquals(0, p.buf.remaining())
    }

    @Test
    fun `motif symbols and colours travel as clan settings 13, 16 and 18`() {
        val c = Clan("Deadmen").apply {
            members["Zed"] = ClanRank.OWNER
            motifTop = 5
            motifBottom = 17
            motifColours[0] = 100; motifColours[1] = 200; motifColours[2] = 300; motifColours[3] = 400
        }
        val p = In(Clans.encodeSettings(c, affined = true, update = 1))
        p.g1(); p.g1(); p.g1(); p.g4(); p.g4(); p.g2(); p.g1(); p.gjstr(); p.g1(); p.g1b(); p.g1b(); p.g1b(); p.g1b()
        p.fastgstr(); p.g1b(); p.g4()
        val extras = (0 until p.g2()).associate { p.g4() to p.g4() }
        assertEquals(mapOf(13 to (5 or (17 shl 16)), 16 to (100 or (200 shl 16)), 18 to (300 or (400 shl 16))), extras)
    }

    @Test
    fun `clan settings carry the ban list and the Clan Settings extra settings`() {
        val c =
            Clan("Deadmen").apply {
                members["Zed"] = ClanRank.OWNER
                members["Abe"] = ClanRank.ORGANISER
                bans += "Griefer"
                guestsCanTalk = true
                motto = "Death to all"
                timeZone = 60
                recruiting = true
                worldId = 3
            }
        val p = In(Clans.encodeSettings(c, affined = true, update = 1))
        p.g1(); p.g1(); p.g1(); p.g4(); p.g4()
        assertEquals(2, p.g2())
        assertEquals(1, p.g1(), "banned count")
        p.gjstr()
        p.g1()
        assertEquals(-1, p.g1b(), "guests may talk: rankTalk -1")
        p.g1b(); p.g1b(); p.g1b()
        val names = (0 until 2).map { p.fastgstr()!!.also { p.g1b(); p.g4() } }
        assertEquals(listOf("Abe", "Zed"), names, "members sorted by name, the index the 1096 member list sends back")
        assertEquals("Griefer", p.fastgstr())
        val extras = (0 until p.g2()).associate {
            val idAndType = p.g4()
            val id = idAndType and 0x3FFFFFFF
            id to when (idAndType ushr 30) { 0 -> p.g4(); 1 -> p.g8(); else -> p.gjstr() }
        }
        assertEquals(60, extras[0], "time zone")
        assertEquals("Death to all", extras[1], "motto")
        assertEquals(1 or (3 shl 2), extras[3], "recruiting | world << 2")
        assertEquals(0, p.buf.remaining())
    }

    @Test
    fun `clan channel decodes like ClanChannel`() {
        val p = In(Clans.encodeChannel(clan, listOf("Owner" to 126, "Guest" to Clans.GUEST_RANK), affined = false, version = 9))
        assertEquals(0, p.g1(), "listened")
        assertEquals(2, p.g1(), "flags: display names, no hashes")
        p.g8()
        assertEquals(9L, p.g8())
        assertEquals("Deadmen", p.gjstr())
        p.g1()
        assertEquals(ClanRank.ADMIN.value, p.g1b(), "kick rank")
        assertEquals(ClanRank.MEMBER.value, p.g1b(), "talk rank")
        val count = p.g2()
        val users = (0 until count).map { Triple(p.gjstr(), p.g1b(), p.g2()) }
        assertEquals(listOf(Triple("Owner", 126, 1), Triple("Guest", -1, 1)), users)
        assertEquals(0, p.buf.remaining())
    }
}
