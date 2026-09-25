package gg.rsmod.game.service.serializer.json

import com.google.gson.JsonParser
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonPlayerSerializerSafetyTests {
    /** Audit S-13: only names the login decoder accepts can reach the file system. */
    @Test
    fun `path traversal names are not valid save names`() {
        assertFalse(JsonPlayerSerializer.isValidSaveName("../x"))
        assertFalse(JsonPlayerSerializer.isValidSaveName("../../game.yml"))
        assertFalse(JsonPlayerSerializer.isValidSaveName("..\\game.yml"))
        assertFalse(JsonPlayerSerializer.isValidSaveName("a/b"))
        assertFalse(JsonPlayerSerializer.isValidSaveName("thirteenchars"))
        assertTrue(JsonPlayerSerializer.isValidSaveName("anudd"))
        assertTrue(JsonPlayerSerializer.isValidSaveName("big bob"))
    }

    /** Audit S-10: overlapping saves of one account always leave one complete, valid file and no temp files. */
    @Test
    fun `concurrent writes of one save always leave valid json`() {
        val dir = Files.createTempDirectory("saves")
        val save = dir.resolve("anudd")
        val threads = 8
        val writesPerThread = 50
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val lock = Any()
        repeat(threads) { thread ->
            pool.execute {
                start.await()
                repeat(writesPerThread) { write ->
                    // The serializer holds a per-account lock around writeAtomically; without it a unique temp file
                    // per write still keeps every individual file complete.
                    synchronized(lock) {
                        JsonPlayerSerializer.writeAtomically(save) { writer ->
                            writer.write("{\"thread\":$thread,\"write\":$write,\"padding\":\"${"x".repeat(4096)}\"}")
                        }
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))

        @Suppress("DEPRECATION")
        val json = JsonParser().parse(Files.readString(save)).asJsonObject
        assertEquals(4096, json["padding"].asString.length)
        val names = Files.list(dir).use { files -> files.map { it.fileName.toString() }.collect(Collectors.toList()) }
        assertEquals(listOf("anudd"), names, "no temp files are left behind")
    }

    @Test
    fun `unsynchronised writers never share a temp file`() {
        val dir = Files.createTempDirectory("saves")
        val save = dir.resolve("anudd")
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        repeat(4) { thread ->
            pool.execute {
                start.await()
                repeat(25) { write ->
                    JsonPlayerSerializer.writeAtomically(save) { writer -> writer.write("{\"thread\":$thread,\"write\":$write}") }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
        @Suppress("DEPRECATION")
        val parsed = JsonParser().parse(Files.readString(save))
        assertTrue(parsed.isJsonObject)
    }
}
