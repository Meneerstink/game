package gg.rsmod.game.service

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit T-11: small persistence writes run on one background thread, in submission order, so the game
 * thread never waits for the disk and the last submitted snapshot is the one that ends up on disk.
 */
class BackgroundIoTests {
    @Test
    fun `jobs run in submission order and flush waits for all of them`() {
        val order = Collections.synchronizedList(mutableListOf<Int>())
        repeat(50) { i -> BackgroundIo.submit("job $i") { order += i } }
        assertTrue(BackgroundIo.flush())
        assertEquals((0 until 50).toList(), order.toList())
    }

    @Test
    fun `the last snapshot of a file wins`() {
        val file = File(Files.createTempDirectory("background-io").toFile(), "book.json")
        repeat(20) { i -> BackgroundIo.submit("write $i") { file.writeText("state $i") } }
        assertTrue(BackgroundIo.flush())
        assertEquals("state 19", file.readText())
    }

    @Test
    fun `a failing job is logged and later jobs still run`() {
        var ran = false
        BackgroundIo.submit("failing") { error("disk full") }
        BackgroundIo.submit("after") { ran = true }
        assertTrue(BackgroundIo.flush())
        assertTrue(ran)
    }

    @Test
    fun `submitting does not wait for a slow write`() {
        val release = CountDownLatch(1)
        val started = System.nanoTime()
        BackgroundIo.submit("slow") { release.await(5, TimeUnit.SECONDS) }
        val submitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        release.countDown()
        assertTrue(BackgroundIo.flush())
        assertTrue(submitMs < 1_000, "submit blocked for $submitMs ms")
    }
}
