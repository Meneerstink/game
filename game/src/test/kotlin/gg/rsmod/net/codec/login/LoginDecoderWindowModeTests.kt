package gg.rsmod.net.codec.login

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A2 round 5 (Keurmeester round-4 FAIL: "FULLSCREEN + Stretched ON, at every login one frame shows the game at the WRONG
 * SIZE - the fixed 765x503 layout in the top-left"). Root cause: the login packet's window mode was read from the WRONG
 * byte. The 667 client writes, right after the username (LoginManager.doLogin, TYPE_GAME):
 *
 *     pjstr(username); p1(anInt7113); p1(InterfaceManager.getWindowMode()); p2(canvasWid); p2(canvasHei); p1(aa); ...
 *
 * `anInt7113` is the social-login id, -1 for every login from the login screen (doLobbyLogin with DISABLE_LOBBY). Our
 * LoginDecoder took THAT byte as the window mode ("clientSettings shr 1 == 1"): -1 is never resizable, so every resizable and
 * fullscreen login was opened in the FIXED gameframe 548 until the client's WINDOW_STATUS switched it to 746 (server traces:
 * keura4new2 18:30:52.680 top=548 -> 18:30:53.263 top=746; sumtest 19:33:54.762 -> 19:33:58.108), and the width/height read
 * one byte too early (window mode as the high byte). On screen: the fixed gameframe, stretched, for a second or more at
 * every login in resizable AND fullscreen, Stretched on or off.
 *
 * Donor: Novite (rev 667) LoginPacketsDecoder l.80-84: `readString() username; readUnsignedByte() // unknown; displayMode =
 * readUnsignedByte(); screenWidth = readUnsignedShort(); screenHeight = readUnsignedShort()`; Void LoginServer l.99-100
 * (`xtea.readUByte() // social login; displayMode = xtea.readUByte()`); resizable = display mode 2 or 3 (Novite
 * InterfaceManager l.110, our set_window_status_logic: WindowMode FIXED 1 / RESIZABLE 2 / FULLSCREEN 3).
 *
 * Class covered: every window mode the client can send (fixed / resizable / fullscreen) x login AND reconnect x every value of
 * the social-login byte before it (-1..254) x the window sizes players use (fixed 765x503, the owner's 944x581, maximised
 * 1648x989 / 1890x989, fullscreen 1890x1080 / 1920x1080, odd sizes). The decoded request must carry the client's own mode
 * (resizable for 2 and 3) and its own canvas size, and the username must survive.
 */
class LoginDecoderWindowModeTests {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
    private val modulus = (keys.public as RSAPublicKey).modulus
    private val publicExponent = (keys.public as RSAPublicKey).publicExponent
    private val privateExponent = (keys.private as RSAPrivateKey).privateExponent

    private class Case(val mode: Int, val social: Int, val width: Int, val height: Int, val reconnect: Boolean) {
        override fun toString() =
            "${if (reconnect) "reconnect" else "login"} mode=$mode (${MODE_NAMES[mode]}) social-byte=$social canvas=${width}x$height"
    }

    @Test
    fun `every window mode the client sends reaches the server as the client's own mode and canvas size`() {
        val failures = mutableListOf<String>()
        var checks = 0
        val cases = mutableListOf<Case>()
        for (reconnect in listOf(false, true)) {
            for (mode in 1..3) {
                for ((w, h) in SIZES) cases += Case(mode, -1, w, h, reconnect)
                // every value the social-login byte can carry: the mode must never be derived from it
                for (social in -1..254) cases += Case(mode, social, 944, 581, reconnect)
            }
        }
        for (case in cases) {
            val request = decode(case)
            if (request == null) {
                failures += "$case: no LoginRequest decoded"
                continue
            }
            checks += 4
            val wantResizable = case.mode == 2 || case.mode == 3
            if (request.resizableClient != wantResizable) {
                failures += "$case: server opens the ${if (request.resizableClient) "RESIZABLE 746" else "FIXED 548"} gameframe " +
                    "(resizableClient=${request.resizableClient}), the client is ${MODE_NAMES[case.mode]}"
            }
            if (request.clientWidth != case.width || request.clientHeight != case.height) {
                failures += "$case: server reads canvas ${request.clientWidth}x${request.clientHeight}, the client sent ${case.width}x${case.height}"
            }
            if (request.username != USER) failures += "$case: username '${request.username}'"
            if (request.reconnecting != case.reconnect) failures += "$case: reconnecting=${request.reconnecting}"
        }
        val verdict = if (failures.isEmpty()) "all passed" else "${failures.size} failed"
        println("LoginDecoderWindowModeTests: ${cases.size} login packets, $checks checks, $verdict")
        for (mode in 1..3) {
            val name = MODE_NAMES[mode]!!
            val ofMode = failures.filter { "($name)" in it }
            val frame = ofMode.count { "gameframe" in it }
            val size = ofMode.count { "server reads canvas" in it }
            println(if (ofMode.isEmpty()) "  $name: all passed" else "  $name: ${ofMode.size} failed (wrong gameframe $frame, wrong canvas size $size)")
            ofMode.filter { "gameframe" in it }.take(3).forEach { println("    FAIL $it") }
            ofMode.filter { "server reads canvas" in it }.take(3).forEach { println("    FAIL $it") }
        }
        if (failures.isNotEmpty()) fail("${failures.size} of $checks login window-mode checks failed, first: ${failures.first()}")
        assertTrue(checks > 0)
    }

    private fun decode(case: Case): LoginRequest? {
        val channel = EmbeddedChannel(LoginDecoder(REVISION, IntArray(0), 0L, privateExponent, modulus))
        channel.writeInbound(clientLoginPacket(case))
        val request = channel.readInbound() as? LoginRequest
        channel.finish()
        return request
    }

    /** The login packet byte for byte as the 667 client's LoginManager (SEND_LOGIN_PACKET, TYPE_GAME) writes it. */
    private fun clientLoginPacket(case: Case): ByteBuf {
        val xtea = intArrayOf(0x1234567, -0x6543211, 0x0BADF00D, 0x7EEDFACE)
        // credentials block (credentialsPacket(): Packet.createXtea() = 10 + the 4 xtea keys, p8(0), pjstr(password), p8(sso),
        // p8(session)), RSA-encrypted; a reconnect carries the previous xtea keys instead of the password (server format)
        val credentials = Unpooled.buffer()
        credentials.writeByte(10)
        xtea.forEach { credentials.writeInt(it) }
        credentials.writeLong(0L)
        if (case.reconnect) {
            xtea.forEach { credentials.writeInt(it xor 0x55) }
        } else {
            pjstr(credentials, "secretpass")
        }
        credentials.writeLong(0x1122334455L)
        credentials.writeLong(0x66778899AAL)
        val raw = ByteArray(credentials.readableBytes()).also { credentials.readBytes(it) }
        val rsa = BigInteger(raw).modPow(publicExponent, modulus).toByteArray()

        val body = Unpooled.buffer()
        body.writeInt(REVISION) // p4(Client.BUILD)
        body.writeByte(if (case.reconnect) 1 else 0) // p1(reconnecting)
        body.writeShort(rsa.size)
        body.writeBytes(rsa)
        val encryptedFrom = body.writerIndex() // credentialsPos: the XTEA block starts at the username
        pjstr(body, USER)
        body.writeByte(case.social) // p1(anInt7113): the social-login id, -1 from the login screen
        body.writeByte(case.mode) // p1(InterfaceManager.getWindowMode())
        body.writeShort(case.width) // p2(GameShell.canvasWid)
        body.writeShort(case.height) // p2(GameShell.canvasHei)
        body.writeByte(0) // p1(antialiasingQuality)
        body.writeBytes(ByteArray(24)) // pushUID192
        pjstr(body, "") // Client.settings
        body.writeInt(0) // Client.affid
        body.writeByte(4) // clientOptions size
        body.writeBytes(byteArrayOf(17, 0, 1, 2))
        body.writeBytes(ByteArray(33)) // system info
        body.writeInt(0) // VerifyId
        body.writeLong(0L) // userFlow
        body.writeByte(0) // additionalInfo
        body.writeByte(0) // jagtheora
        body.writeByte(0) // js
        body.writeByte(0) // hc
        repeat(29) { body.writeInt(it) } // js5 checksums
        val bytes = ByteArray(body.readableBytes()).also { body.readBytes(it) }
        tinyenc(bytes, xtea, encryptedFrom, bytes.size)

        val frame = Unpooled.buffer()
        frame.writeByte(if (case.reconnect) 18 else 16) // GAMELOGIN / reconnect opcode
        frame.writeShort(bytes.size) // psize2
        frame.writeBytes(bytes)
        return frame
    }

    private fun pjstr(buf: ByteBuf, s: String) {
        buf.writeBytes(s.toByteArray(Charsets.ISO_8859_1))
        buf.writeByte(0)
    }

    /** Verbatim port of the 667 client's Packet.tinyenc (com/jagex/core/io/Packet.java l.627-646). */
    private fun tinyenc(data: ByteArray, key: IntArray, start: Int, end: Int) {
        val bb = java.nio.ByteBuffer.wrap(data)
        val blocks = (end - start) / 8
        var pos = start
        for (i in 0 until blocks) {
            var v0 = bb.getInt(pos)
            var v1 = bb.getInt(pos + 4)
            var sum = 0
            var rounds = 32
            while (rounds-- > 0) {
                v0 += v1 + (v1 shl 4 xor v1.ushr(5)) xor sum + key[sum and 0x3]
                sum -= 1640531527
                v1 += (v0.ushr(5) xor (v0 shl 4)) + v0 xor sum + key[sum.ushr(11) and -0x173ffffd]
            }
            bb.putInt(pos, v0)
            bb.putInt(pos + 4, v1)
            pos += 8
        }
    }

    companion object {
        private const val REVISION = 667
        private const val USER = "keura5new1"
        private val MODE_NAMES = mapOf(1 to "FIXED", 2 to "RESIZABLE", 3 to "FULLSCREEN")
        private val SIZES =
            listOf(
                765 to 503, 795 to 503, 944 to 581, 974 to 581, 1007 to 581, 1216 to 581, 978 to 709,
                1648 to 989, 1890 to 989, 1890 to 1080, 1920 to 1080, 1648 to 1080, 2560 to 1440, 801 to 600,
            )
    }
}
