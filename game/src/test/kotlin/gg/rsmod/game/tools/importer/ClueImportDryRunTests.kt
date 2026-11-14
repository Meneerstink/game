package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/** Real throwaway caches/journal only. No production cache or shared ledger is a fixture. */
class ClueImportDryRunTests {
    @get:Rule val scratch = TemporaryFolder()

    private fun cache(name: String): String {
        val dir = scratch.newFolder(name)
        File(dir,"main_file_cache.dat2").createNewFile()
        File(dir,"main_file_cache.idx255").createNewFile()
        val library = CacheLibrary.create(dir.absolutePath)
        try {
            while (!library.exists(ArchiveType.SPOTANIM.id)) library.createIndex()
            library.update()
        } finally { library.close() }
        return dir.absolutePath
    }

    private fun item(id: Int) = ImportBatchOrchestrator.ItemSource("upstream_item:$id", "Fixture $id", emptyList()) {
        byteArrayOf(2) + "Fixture $id".toByteArray() + byteArrayOf(0,0)
    }

    private fun snapshot(targets: List<String>): Map<String,List<Byte>> = targets.flatMap { path ->
        File(path).listFiles()!!.filter { it.isFile }.map {
            it.absolutePath to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
        }
    }.toMap()

    @Test fun `plan with recoverable applied history does not edit the ledger caches or journals`() {
        val targets = listOf(cache("game"),cache("files"))
        val ledger = scratch.newFile("ledger.yml").apply { writeText("imports:\n") }
        val journal = scratch.newFolder("journals")
        val mapping = ImportBatchOrchestrator.readExistingMapping(ledger)
        val applied = ImportBatchOrchestrator.plan(targets,listOf(item(1)),mapping,
            ImportBatchOrchestrator.nextFreeItemId(targets),500,510,journal)
        applied.transaction.apply(applied.transaction.preflight())
        assertTrue(applied.transaction.verify().isEmpty())
        // Simulate a crash before finalizing the mapping, then plan a separate item.
        val beforeLedger = ledger.readBytes()
        val beforeCache = snapshot(targets)
        val beforeJournals = journal.list()!!.toSet()
        val result = ImportBatchOrchestrator.orchestrate(targets,listOf(item(2)),ledger,500,510,
            apply = false,journalRoot = journal)
        assertEquals(1,result.recoveredAndFinalized.size)
        assertNull(result.applyResult)
        assertContentEquals(beforeLedger,ledger.readBytes())
        assertEquals(beforeCache,snapshot(targets))
        assertEquals(beforeJournals,journal.list()!!.toSet())
    }

    @Test fun `custom source identities already in the ledger are recognized and never re-appended`() {
        val ledger = scratch.newFile("custom.yml").apply { writeText("""
            imports:
              - local_item_id: 24232
                upstream_item_id: n423_trinket:6
              - local_item_id: 100
                upstream_item_id: 2677
        """.trimIndent()+"\n") }
        val mapping = ImportBatchOrchestrator.readExistingMapping(ledger)
        assertEquals(24232,mapping.itemLocalIdBySourceIdentity["n423_trinket:6"])
        assertEquals(100,mapping.itemLocalIdBySourceIdentity["upstream_item:2677"])
        val before = ledger.readBytes()
        val appended = ImportBatchOrchestrator.finalizeDurableMapping(ledger,
            listOf(ImportBatchOrchestrator.MappedItem("n423_trinket:6","Kalphite burden trinket",24232,emptyList())),mapping)
        assertTrue(appended.isEmpty())
        assertContentEquals(before,ledger.readBytes())
    }
}
