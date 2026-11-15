package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/** The one proven native search-menu gap; default PLAN/PREFLIGHT, application belongs to Claude. */
object ClueSearchMenuCacheTool {
    const val BUCKET = 9568
    fun patch(before: ByteArray, source: ModernObjectDef): ByteArray {
        require(source.id == BUCKET && source.name == "Bucket" && source.options.toList() == listOf("Search", null, null, null, null))
        val native = Rev667LocType.decode(BUCKET, before)
        require(native.name == source.name && before.last() == 0.toByte())
        if (native.options.toList() == source.options.toList()) return before.copyOf()
        require(native.options.all { it == null }) { "Bucket menu differs from the reviewed source/native pair" }
        val out = ByteArrayOutputStream()
        out.write(before, 0, before.size-1)
        out.write(30)
        out.write("Search".toByteArray(Charsets.ISO_8859_1))
        out.write(0); out.write(0)
        return out.toByteArray().also { edited ->
            val after = Rev667LocType.decode(BUCKET, edited)
            check(after.options.toList() == source.options.toList())
            check(after.modelsByShape == native.modelsByShape)
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
            val def = ModernObjectDef.decode(BUCKET, source.file(2,6,BUCKET) ?: error("Source bucket missing"))
            val native = CacheLibrary(OsrsItemImportTool.TARGETS[0])
            try {
                val before = native.data(16,BUCKET ushr 8,BUCKET and 255) ?: error("Native bucket missing")
                val after = patch(before, def)
                if (!before.contentEquals(after)) mutations += CacheMutation(16,BUCKET ushr 8,BUCKET and 255,after,
                    "CLUE bucket9568 source Search option; geometry and all other bytes retained",CacheItemProbeTool.sha1(before))
            } finally { native.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(OsrsItemImportTool.TARGETS,mutations)
        val preflight = tx.preflight()
        check(tx.blockingErrors(preflight).isEmpty()) { "CLUE bucket preflight blocked; nothing written" }
        println("PREFLIGHT ${tx.id} mutations=${mutations.size}")
        if ("--apply" !in args) { println("PLAN_ONLY (nothing written to either cache)"); return }
        tx.apply(preflight)
        check(tx.verify().isEmpty())
        println("APPLIED ${tx.id} VERIFY_OK")
    }
}
