package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.ArchiveType

/**
 * LEGACY / MANUAL-ONLY, NOT THE APPROVED PRODUCTION IMPORT PATH (`RSPS_CURRENT_SPRINT.json` gate
 * A7, task 4 "legacy bypass risk"). This tool writes to exactly **one** `cachePath` and has no
 * journal, no rollback and no cross-cache verification - it is what produced the original
 * game-cache/file-server-cache divergence gate A0 had to repair. The approved production path for
 * a real import is [ItemTransactionTool], which writes every target cache inside one
 * [CacheTransaction]. Only use this tool for single, throwaway, non-production cache
 * experimentation where dual-cache consistency does not matter.
 *
 * Clones an existing donor item's full archive bytes into a brand-new, previously-unused local
 * item id, only overriding its name and option-menu text - see [ItemDefCodec]'s class doc for why
 * this is the safe boundary of what a purely server-side tool can originate versus what still
 * needs a real model/icon import pass.
 *
 * Usage: `runItemImportTool --args="<cachePath> <donorId> <newId> <newName>"`
 *
 * Refuses to run if [newId] already has archive data in the cache (collision check) - this tool
 * only ever allocates into genuinely unused ids, it never overwrites an existing item.
 */
object ItemImportTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 4) {
            "Usage: <cachePath> <donorId> <newId> <newName> [groundOption0] [inventoryOption0]"
        }
        val cachePath = args[0]
        val donorId = args[1].toInt()
        val newId = args[2].toInt()
        val newName = args[3]
        val groundOption0 = args.getOrNull(4)
        val inventoryOption0 = args.getOrNull(5)

        val library = CacheLibrary(cachePath)
        try {
            val donorBytes =
                library.data(ArchiveType.ITEM.id, donorId ushr 8, donorId and 0xFF)
                    ?: error("Donor item $donorId has no archive data in this cache - pick a real, existing donor id.")

            val existing = library.data(ArchiveType.ITEM.id, newId ushr 8, newId and 0xFF)
            check(existing == null) {
                "Refusing to overwrite: item $newId already has archive data in this cache. Choose a genuinely unused id."
            }

            val overrides = mutableMapOf(2 to newName)
            if (groundOption0 != null) overrides[30] = groundOption0
            if (inventoryOption0 != null) overrides[35] = inventoryOption0
            val cloned = ItemDefCodec.cloneWithOverrides(donorBytes, overrides)

            library.put(ArchiveType.ITEM.id, newId ushr 8, newId and 0xFF, cloned)
            library.update()

            // Re-open read-only-style: re-read straight back from the same library instance to
            // confirm the write actually landed and decodes to the intended name.
            val written =
                library.data(ArchiveType.ITEM.id, newId ushr 8, newId and 0xFF)
                    ?: error("Post-write validation failed: item $newId has no archive data after update().")
            val writtenName = ItemDefCodec.readName(written)
            check(writtenName == newName) {
                "Post-write validation failed: item $newId decoded name was '$writtenName', expected '$newName'."
            }

            println(
                "OK: cloned donor item $donorId into new item $newId ('$newName'), ${cloned.size} bytes, " +
                    "name verified by re-read.",
            )
        } finally {
            library.close()
        }
    }
}
