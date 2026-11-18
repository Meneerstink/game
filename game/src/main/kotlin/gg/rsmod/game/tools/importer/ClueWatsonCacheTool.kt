package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.Gson
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.VarbitDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream
import java.io.File

/** Personal pre/post-introduction Watson. Uses the two actual 2686 definitions, not invented options. */
object ClueWatsonCacheTool {
    const val ROOT = 14406
    const val BEFORE = 7303
    const val AFTER = 7304

    fun variants(before: ByteArray, after: ByteArray, control: Int, first: Int, second: Int): ByteArray {
        require(control in 0 until 10000 && first in 0..65534 && second in 0..65534 && first != second)
        require(first != ROOT && second != ROOT) { "Watson root cannot refer to itself" }
        require(before.lastOrNull() == 0.toByte() && after.lastOrNull() == 0.toByte())
        val pre = NpcDef(first).also { it.decode(Unpooled.wrappedBuffer(before)) }
        val post = NpcDef(second).also { it.decode(Unpooled.wrappedBuffer(after)) }
        require(pre.transforms == null && post.transforms == null)
        require(pre.name == "Watson" && post.name == "Watson")
        require(pre.options.map { it?.takeUnless(String::isBlank) } == listOf("Talk-to", null, null, null, null))
        require(post.options.map { it?.takeUnless(String::isBlank) } == listOf("Talk-to", null, "Check", null, null))
        val out = ByteArrayOutputStream()
        fun u16(value: Int) { out.write(value ushr 8); out.write(value) }
        out.write(before, 0, before.size - 1)
        // Native 118: varbit, varp, fallback, count, explicit forms. Two values 0/1.
        out.write(118); u16(65535); u16(control); u16(second); out.write(1); u16(first); u16(second); out.write(0)
        return out.toByteArray()
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <runtime-mapping.tsv> <plan.tsv> [--apply]" }
        val runtime = File(args[0]); val plan = File(args[1]); val apply = "--apply" in args
        require(runtime.canonicalFile != plan.canonicalFile)
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val existing = OsrsFxImportTool.existingFx(assetMap)
        check(existing["npc:$BEFORE"] == ROOT) { "Existing Watson ownership is not the reviewed import" }
        val mapping = linkedMapOf<Pair<String, Int>, Int>()
        val records = mutableListOf<Triple<String, Int, Int>>()
        fun allocate(kind: String, source: Int, ledgerKind: String, next: () -> Int): Int {
            val id = existing["$ledgerKind:$source"] ?: next().also { records += Triple(ledgerKind, source, it) }
            mapping[kind to source] = id
            return id
        }
        val caches = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        try {
            var nextNpc = caches.maxOf { cache -> cache.index(18).archiveIds().maxOf { group ->
                cache.index(18).archive(group)!!.fileIds().maxOf { (group shl 7) or it }
            } } + 1
            var nextVarp = caches.maxOf { it.index(2).archive(16)!!.fileIds().maxOrNull() ?: -1 } + 1
            caches.forEach { cache -> cache.index(22).archiveIds().forEach { group ->
                cache.index(22).archive(group)!!.fileIds().forEach { file ->
                    val def = VarbitDef((group shl 10) or file).also { it.decode(Unpooled.wrappedBuffer(cache.data(22, group, file)!!)) }
                    nextVarp = maxOf(nextVarp, def.varp + 1)
                }
            } }
            val first = allocate("npc", BEFORE, "clue_watson_before") { nextNpc++ }
            val second = allocate("npc", AFTER, "npc") { nextNpc++ }
            val control = allocate("control", BEFORE, "clue_watson_control") { nextVarp }
            mapping["root" to BEFORE] = ROOT
            val nativeBefore = caches.first().data(18, first ushr 7, first and 127)
                ?: caches.first().data(18, ROOT ushr 7, ROOT and 127) ?: error("Installed Watson missing")
            val nativeDef = NpcDef(first).also { it.decode(Unpooled.wrappedBuffer(nativeBefore)) }
            check(nativeDef.transforms == null)
            val mutations = mutableListOf<CacheMutation>()
            fun add(index: Int, group: Int, file: Int, bytes: ByteArray, owned: Boolean, detail: String) {
                val old = caches.first().data(index, group, file)
                check(old == null || owned) { "Unowned destination $index:$group:$file" }
                mutations += CacheMutation(index, group, file, bytes, detail, old?.let(CacheItemProbeTool::sha1))
            }
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
                val pre = OsrsNpcProbeTool.decodeOsrs(BEFORE, source.file(2, 9, BEFORE)!!)
                val post = OsrsNpcProbeTool.decodeOsrs(AFTER, source.file(2, 9, AFTER)!!)
                val gson = Gson()
                fun unchanged(npc: OsrsNpcProbeTool.OsrsNpc) = gson.toJsonTree(npc).asJsonObject.apply { remove("id"); remove("ops") }
                check(unchanged(pre) == unchanged(post)) { "Watson variants changed beyond their options" }
                check(pre.ops.toList() == listOf("Talk-to", null, null, null, null))
                check(post.ops.toList() == listOf("Talk-to", null, "Check", null, null))
                val models = pre.models.map { existing["npc_model:$it"] ?: error("Watson model mapping $it missing") }.toIntArray()
                val heads = pre.chatheads.map { existing["npc_model:$it"] ?: error("Watson chathead mapping $it missing") }.toIntArray()
                val dropped = mutableListOf<String>()
                val preBytes = OsrsNpcImportTool.encode667Npc(pre, models, heads, nativeDef.basId, dropped)
                val postBytes = OsrsNpcImportTool.encode667Npc(post, models, heads, nativeDef.basId, dropped)
                check(preBytes.contentEquals(nativeBefore)) { "Installed pre-introduction Watson differs from the pinned conversion" }
                val root = variants(preBytes, postBytes, control, first, second)
                add(18, first ushr 7, first and 127, preBytes, existing["clue_watson_before:$BEFORE"] == first, "Watson pre-introduction $BEFORE -> $first")
                add(18, second ushr 7, second and 127, postBytes, existing["npc:$AFTER"] == second, "Watson post-introduction $AFTER -> $second")
                add(18, ROOT ushr 7, ROOT and 127, root, true, "Watson root $ROOT personal selector $control")
                add(2, 16, control, byteArrayOf(0), existing["clue_watson_control:$BEFORE"] == control, "Watson personal introduction varp $control")
            }
            val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
            val preflight = tx.preflight()
            check(tx.blockingErrors(preflight).isEmpty()) { "Watson preflight blocked: ${tx.blockingErrors(preflight)}" }
            val text = mapping.entries.joinToString("\n", "# CLUE Watson ${tx.id}; applied=$apply\n", "\n") { "${it.key.first}\t${it.key.second}\t${it.value}" }
            plan.parentFile?.mkdirs(); plan.writeText(text)
            println("PREFLIGHT ${tx.id} mutations=${mutations.size} ${mapping.entries.joinToString()}")
            if (!apply) { println("PLAN_ONLY; cache, ledger and runtime mapping unchanged"); return }
            val result = tx.apply(preflight)
            val errors = tx.verify()
            if (errors.isNotEmpty()) { tx.rollback(); error("Watson verification failed and rolled back: $errors") }
            if (records.isNotEmpty()) assetMap.appendText(records.joinToString("", "\n") { (kind, source, local) ->
                "  - fx_kind: $kind\n    upstream_fx_id: $source\n    local_fx_id: $local\n    status: IMPORTED_BY_CLUE_WATSON_TOOL\n    transaction: ${tx.id}\n"
            })
            runtime.parentFile?.mkdirs(); runtime.writeText(text)
            println("APPLIED ${tx.id} writes=${result.applied} VERIFY_OK")
        } finally { caches.forEach { it.close() } }
    }
}
