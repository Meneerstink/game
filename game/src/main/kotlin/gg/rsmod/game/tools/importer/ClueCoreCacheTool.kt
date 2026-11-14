package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.Gson
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ItemDef
import io.netty.buffer.Unpooled
import java.io.File

/**
 * CLUE: preserve the eight deployed local clue/casket identities and their models,
 * copying OSRS names, stack flags and inventory options from cache 2686.
 * Default is PLAN/PREFLIGHT only. Claude owns application and items.yml review.
 */
object ClueCoreCacheTool {
    val LEGACY = linkedMapOf(2677 to 2677, 2801 to 2801, 2722 to 2722, 12073 to 19043,
        20546 to 2714, 20545 to 2802, 20544 to 2724, 20543 to 19039)

    @JvmStatic fun main(args: Array<String>) {
        val apply = "--apply" in args
        val ids = LinkedHashMap(LEGACY)
        // The master imports have allocator-selected local IDs; never guess them.
        val mapping = args.firstOrNull { it.startsWith("--mapping=") }?.substringAfter('=')
            ?.let(::File) ?: File(RspsPaths.data("cfg/clue/items.tsv"))
        if (mapping.isFile) mapping.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach {
            val f = it.split('\t')
            if (f[0].toInt() in setOf(19835, 19836)) ids[f[0].toInt()] = f[1].toInt()
        }
        val mutations = mutableListOf<CacheMutation>()
        val yml = StringBuilder()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
            val definitions = source.files(2, 10)
            val local = CacheLibrary(OsrsItemImportTool.TARGETS[0])
            try {
                for ((osrs, id) in ids) {
                    val def = ModernItemDefDecoder.decode(osrs, definitions.getValue(osrs))
                    val name = when (osrs) { 19835 -> "78 clue scroll"; 19836 -> "Reward casket (78)"; else -> def.name }
                    val strings = mutableMapOf(2 to name)
                    def.inventoryOptions.forEachIndexed { i, op -> if (op != null) strings[35 + i] = op }
                    val remove = (35..39).filter { def.inventoryOptions[it - 35] == null }.toSet() + 11
                    val before = local.data(19, id ushr 8, id and 255) ?: error("Local CLUE item $id missing")
                    val clone = ItemDefCodec.cloneWithOverrides(before, strings, removedOpcodes = remove)
                    require(def.stackable in 0..1)
                    val edited = if (def.stackable == 1) clone.copyOf(clone.size + 1).also {
                        it[clone.size - 1] = 11; it[clone.size] = 0
                    } else clone
                    val decoded = ItemDef(id).also { it.decode(Unpooled.wrappedBuffer(edited)) }
                    check(decoded.name == name && decoded.stacks == (def.stackable == 1))
                    check(decoded.inventoryMenu.toList() == def.inventoryOptions.toList())
                    if (!before.contentEquals(edited)) mutations += CacheMutation(19, id ushr 8, id and 255, edited,
                        "CLUE core $osrs -> $id: OSRS name/stack/options", CacheItemProbeTool.sha1(before))
                    println("PLAN core osrs=$osrs local=$id name='$name' stack=${decoded.stacks} options=${decoded.inventoryMenu.toList()}")
                    // Review/replacement fragment: do not blindly append duplicate IDs to items.yml.
                    yml.append("- id: $id\n  name: ${Gson().toJson(name)}\n  examine: ${Gson().toJson(def.examine ?: "")}\n")
                    yml.append("  tradeable: ${def.tradeable}\n  weight: ${def.weight / 1000.0}\n  equipment: null\n")
                }
            } finally { local.close() }
        }
        args.firstOrNull { it.startsWith("--yml-out=") }?.substringAfter('=')?.let {
            File(it).apply { parentFile?.mkdirs(); writeText(yml.toString()) }
        }
        if (ids.keys.none { it == 19835 }) println("PENDING master 19835/19836: import and regenerate items.tsv before the final plan")
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val transaction = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val preflight = transaction.preflight()
        val blocking = transaction.blockingErrors(preflight)
        println("PREFLIGHT ${transaction.id} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        blocking.forEach { println("BLOCKING $it") }
        check(blocking.isEmpty()) { "CLUE core preflight blocked; nothing written" }
        if (!apply) { println("PLAN_ONLY (nothing written to either cache)"); return }
        val result = transaction.apply(preflight)
        val errors = transaction.verify()
        check(errors.isEmpty()) { "CLUE core verification failed: $errors; journal=${result.journalDir}" }
        println("APPLIED ${transaction.id} writes=${result.applied} VERIFY_OK")
    }
}
