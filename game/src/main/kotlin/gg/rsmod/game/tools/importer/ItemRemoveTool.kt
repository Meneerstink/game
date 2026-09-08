package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType

/**
 * LEGACY / MANUAL-ONLY, NOT THE APPROVED PRODUCTION IMPORT PATH (`RSPS_CURRENT_SPRINT.json` gate
 * A7, task 4 "legacy bypass risk"). Standalone CLI companion to [ItemImportTool]: like that tool,
 * this writes to exactly **one** `cachePath`, with no journal, rollback or cross-cache
 * verification. Removing an item on only one of the two target caches recreates the exact
 * divergence gate A0 had to repair. The approved production path is [ItemTransactionTool]'s
 * `remove` verb, which removes from every target cache inside one [CacheTransaction]. Only use
 * this tool for single, throwaway, non-production cache experimentation.
 *
 * Deletes a previously-cloned item's archive entry from the cache, restoring the id to "no data"
 * (`RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER AUTHORIZATION"). Exists because
 * [gg.rsmod.game.service.game.ItemMetadataService] and [gg.rsmod.game.fs.DefinitionSet] require
 * item ids to be perfectly contiguous starting at 0 - any id allocated with a gap before it
 * (rather than immediately after the current max id) crashes the server at boot. This tool is the
 * safe way to undo an id allocated at the wrong slot without touching any other archive entry.
 *
 * Usage: `runItemRemoveTool --args="<cachePath> <itemId>"`
 */
object ItemRemoveTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <cachePath> <itemId>" }
        val cachePath = args[0]
        val itemId = args[1].toInt()

        val library = CacheLibrary(cachePath)
        try {
            val removed = library.remove(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF)
            library.update()

            val stillPresent = library.data(ArchiveType.ITEM.id, itemId ushr 8, itemId and 0xFF)
            check(stillPresent == null) {
                "Post-removal validation failed: item $itemId still has archive data after update()."
            }

            println("OK: removed item $itemId from cache (had data: ${removed != null}), verified gone by re-read.")
        } finally {
            library.close()
        }
    }
}
