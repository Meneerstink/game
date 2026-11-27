package gg.rsmod.util.io

import io.netty.buffer.ByteBuf

/**
 * @author Tom <rspsmods@gmail.com>
 */
object BufferUtils {
    /**
     * 667 cache and client strings are Cp1252 (client Packet.gjstr). The platform default (UTF-8 on the Linux VPS) turned
     * 'Le-sabrè' (0xE8) into 'Le-sabr?' on live while the Windows testserver showed it right (DIARIES-OSRS A9 27-11).
     */
    val JAGEX_CHARSET: java.nio.charset.Charset = java.nio.charset.Charset.forName("windows-1252")

    fun ByteBuf.readString(): String {
        if (isReadable) {
            val start = readerIndex()
            while (readByte().toInt() != 0);
            val size = readerIndex() - start

            val data = ByteArray(size)
            readerIndex(start)
            readBytes(data)

            return String(data, 0, size - 1, JAGEX_CHARSET)
        } else {
            return ""
        }
    }

    fun ByteBuf.readJagexString(): String {
        if (isReadable && readByte().toInt() != 0) {
            val start = readerIndex()
            while (readByte().toInt() != 0);
            val size = readerIndex() - start

            val data = ByteArray(size)
            readerIndex(start)
            readBytes(data)

            return String(data, 0, size - 1, JAGEX_CHARSET)
        } else {
            return ""
        }
    }

    /**
     * Gets a tri-byte from the buffer.
     * @param buffer The buffer.
     * @return The value.
     */
    fun ByteBuf.getTriByte(): Int {
        return (readByte().toInt() and 0xFF shl 16) + (readByte().toInt() and 0xFF shl 8) + (
            readByte()
                .toInt() and 0xFF
        )
    }

    fun getSmart(buf: ByteBuf): Int {
        val peek = buf.readByte().toInt() and 0xFF
        return if (peek <= Byte.MAX_VALUE) {
            peek
        } else {
            (peek shl 8 or (buf.readByte().toInt() and 0xFF)) - 32768
        }
    }

    /**
     * Gets a smart from the buffer.
     * @param buffer The buffer.
     * @return The value.
     */
    fun getBigSmart(buf: ByteBuf): Int {
        var value = 0
        var current: Int = getSmart(buf)
        while (current == 32767) {
            current = getSmart(buf)
            value += 32767
        }
        value += current
        return value
    }
}
