package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.displee.cache.index.ReferenceTable
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Exercises the documented command after the transaction instance has gone away. */
class CacheJournalRestoreToolTests {
    @get:Rule val temp = TemporaryFolder()
    private val key = intArrayOf(-308446968, 133463944, -1258259786, 567848919)
    private val original = ByteArray(600) { (it * 31 + 7).toByte() }

    private class Fixture(val root: File, val targets: List<String>, val journal: File, val group: Int)

    private fun fixture(encrypted: Boolean = true): Fixture {
        val root = temp.newFolder()
        val targets = listOf("game/game/data/cache", "file-server/cache").map {
            File(root, it).apply { mkdirs() }.absolutePath.replace('\\', '/')
        }
        targets.forEach { target ->
            File(target, "main_file_cache.dat2").createNewFile()
            File(target, "main_file_cache.idx255").createNewFile()
            CacheLibrary.create(target).use { lib ->
                while (!lib.exists(5)) lib.createIndex()
                lib.index(5).flagMask(ReferenceTable.FLAG_NAME or ReferenceTable.FLAG_WHIRLPOOL)
                lib.put(5, "m1_1", "TILES".toByteArray())
                lib.put(5, "l1_1", original, if (encrypted) key else null)
                lib.update()
            }
        }
        val group = CacheLibrary(targets[0]).use { it.index(5).archiveId("l1_1") }
        val stored = CacheLibrary(targets[0]).use { it.index(5).readArchiveSector(group)!!.data }
        val tx = CacheTransaction(
            targets, listOf(CacheMutation(5, group, 0, byteArrayOf(0), "N10 fixture",
                expectedCurrentSha1 = if (encrypted) null else CacheItemProbeTool.sha1(original),
                undecodableCurrentSha1 = if (encrypted) CacheItemProbeTool.sha1(stored) else null)),
            File(root, "import-journal"), "tx-original",
        )
        assertEquals(2, tx.apply().applied)
        assertEquals(emptyList<String>(), tx.verify())
        return Fixture(root, targets, tx.journalDir, group)
    }

    private fun runTool(f: Fixture, apply: Boolean): Pair<Int, String> {
        val output = File(f.root, "child-${System.nanoTime()}.txt")
        val command = mutableListOf(
            File(System.getProperty("java.home"), "bin/java").path, "-Xmx256m",
            "-Drsps.root=${f.root.path}", "-cp", System.getProperty("java.class.path"),
            CacheJournalRestoreTool::class.java.name, f.journal.path, "idx5_grp[0-9]+_file0",
        )
        if (apply) command += "--apply"
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("restore process timed out: ${output.readText()}")
        }
        val result = process.exitValue() to output.readText()
        println("RESTORE_CHILD exit=${result.first}\n${result.second}")
        return result
    }

    @Test fun storedJournalRestoresBytesAndReferenceInFreshProcess() {
        val f = fixture()
        val journalBefore = f.journal.listFiles()!!.associate { it.name to it.readBytes().toList() }
        val dry = runTool(f, false)
        assertEquals(dry.second, 0, dry.first)
        assertTrue(dry.second, dry.second.contains("DRY RUN"))
        f.targets.forEach { target ->
            CacheLibrary(target).use { assertArrayEquals(byteArrayOf(0), it.data(5, f.group, 0)) }
        }
        val applied = runTool(f, true)
        assertEquals(applied.second, 0, applied.first)
        assertTrue(applied.second, applied.second.contains("VERIFY_OK"))
        f.targets.forEach { target ->
            val slug = target.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            val prefix = File(f.journal, "${slug}__idx5_grp${f.group}_file0")
            val reference = File("$prefix.ref").readLines().associate { it.substringBefore('=') to it.substringAfter('=') }
            CacheLibrary(target).use { lib ->
                assertArrayEquals(File("$prefix.stored").readBytes(), lib.index(5).readArchiveSector(f.group)!!.data)
                val archive = lib.index(5).archive(f.group, null, true)!!
                assertEquals(reference.getValue("crc").toInt(), archive.crc)
                assertEquals(reference.getValue("revision").toInt(), archive.revision)
                assertEquals(reference.getValue("whirlpool"), archive.whirlpool!!.joinToString("") { "%02x".format(it) })
                assertEquals(f.group, lib.index(5).archiveId("l1_1"))
                assertArrayEquals(original, lib.data(5, f.group, 0, key))
                assertArrayEquals("TILES".toByteArray(), lib.data(5, "m1_1"))
            }
        }
        assertEquals("source journal must remain immutable", journalBefore,
            f.journal.listFiles()!!.associate { it.name to it.readBytes().toList() })
        val again = runTool(f, true)
        assertEquals(again.second, 0, again.first)
        assertTrue(again.second, again.second.contains("APPLIED 0"))
    }

    @Test fun legacyPlaintextJournalStillRestoresInFreshProcess() {
        val f = fixture(false)
        val result = runTool(f, true)
        assertEquals(result.second, 0, result.first)
        f.targets.forEach { target ->
            CacheLibrary(target).use { assertArrayEquals(original, it.data(5, f.group, 0)) }
        }
    }

    @Test fun incompleteStoredJournalRefusesBeforeAnyWrite() {
        val f = fixture()
        val ref = f.journal.listFiles()!!.first { it.extension == "ref" }
        // Corrupt metadata, keeping the journal files present (no production journal touched).
        ref.writeText("crc=invalid\nrevision=1\nwhirlpool=\n")
        val result = runTool(f, true)
        assertTrue(result.second, result.first != 0)
        f.targets.forEach { target ->
            CacheLibrary(target).use { assertArrayEquals(byteArrayOf(0), it.data(5, f.group, 0)) }
        }
    }

    private fun <T> CacheLibrary.use(block: (CacheLibrary) -> T): T = try { block(this) } finally { close() }
}
