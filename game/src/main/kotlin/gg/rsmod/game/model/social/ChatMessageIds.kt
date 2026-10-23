package gg.rsmod.game.model.social

import java.util.concurrent.atomic.AtomicLong

/**
 * N264 / N265 (owner 2026-10-23: "clanchat praten lukt niet", friends chat lines lost without the "/"): the one id source for every
 * chat line the client de-duplicates - private messages, friends chat and clan chat, typed and quick chat (MESSAGE_PRIVATE,
 * MESSAGE_FRIENDCHANNEL, MESSAGE_CLANCHANNEL and their QUICKCHAT forms). The client keeps the last 100 ids of all six in ONE ring
 * (ServerConnectionReader, `Static511.aLongArray17`) and silently drops a line whose id is in it. Friends chat and clan chat each
 * counted from 1, so a clan line was dropped when a friends chat line with the same number had just been shown (and the other
 * way round), and after every server restart the numbers started again at 1 while the open client still held the old ones.
 *
 * Ids are unique for the whole server and keep growing over restarts: the counter starts at the start time in seconds times 256,
 * so a new run begins above the last one unless that run sent more than 256 lines a second on average. They fit the 40 bits the
 * packets carry (16 high bits + 24 low bits, [high] / [low]) until the year 2106, and 0 - the ring's empty slot - is never used.
 */
object ChatMessageIds {
    const val MASK = 0xFF_FFFF_FFFFL

    fun seed(startMillis: Long): Long = ((startMillis / 1000L) shl 8) and MASK

    private val counter = AtomicLong(seed(System.currentTimeMillis()))

    fun next(): Long {
        while (true) {
            val id = counter.getAndIncrement() and MASK
            if (id != 0L) return id
        }
    }

    /** The 16 bits the packets send first (the client reads `g2`). */
    fun high(id: Long): Int = ((id shr 24) and 0xFFFF).toInt()

    /** The 24 bits that follow (`g3`). */
    fun low(id: Long): Int = (id and 0xFFFFFF).toInt()
}
