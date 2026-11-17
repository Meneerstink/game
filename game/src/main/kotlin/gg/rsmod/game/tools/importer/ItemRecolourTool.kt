package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * CLUE 78 presentation (owner 14-11 22:4x, GOEDGEKEURDE-ONTWERPEN '78 CLUE + 78 KIST'): '78 clue scroll' = scroll option 1
 * 'Goud & onyx' (gold paper, onyx seal + roller knobs), 'Reward casket (78)' = casket option A (gold body, onyx bands + lock).
 * The OSRS master meshes stay untouched; the item definition gets a recolour table (opcode 40: count, then g2 from / g2 to),
 * exactly how the client paints any recoloured item (inventory, ground and worn model alike).
 *
 * Usage (kc_tool.sh ItemRecolourTool ...):
 *   probe <itemId>...                       model id + face-colour histogram (client 16-bit HSL: h6 s3 l7)
 *   set <itemId> <from>:<to>[,<from>:<to>...] [--apply]   replace the item's opcode-40 table (plan by default, both caches)
 */
object ItemRecolourTool {
    private const val ITEM_INDEX = 19

    fun hue(hsl: Int) = (hsl ushr 10) and 0x3F
    fun saturation(hsl: Int) = (hsl ushr 7) and 0x7
    fun lightness(hsl: Int) = hsl and 0x7F

    private fun modelOf(def: ByteArray): Int {
        val ops = ItemDefCodec.describeOpcodes(def)
        // A wide (archive-37) model is reported as "1=<int>" next to the plain g2 "1=<low16>": take the largest value.
        return ops.mapNotNull { op -> op.takeIf { it.startsWith("1=") }?.substringAfter('=')?.toInt() }.maxOrNull()
            ?: error("item has no inventory model (opcode 1): $ops")
    }

    /** [def] with every opcode-40 block removed and one new table appended before the terminator. */
    fun withRecolours(def: ByteArray, pairs: List<Pair<Int, Int>>): ByteArray {
        val offsets = ItemDefCodec.payloadOffsets(def)
        val out = ByteArrayOutputStream()
        offsets.forEachIndexed { i, (op, start) ->
            val end = if (i + 1 < offsets.size) offsets[i + 1].second - 1 else def.size - 1
            if (op == 40) return@forEachIndexed
            out.write(op)
            out.write(def, start, end - start)
        }
        if (pairs.isNotEmpty()) {
            out.write(40)
            out.write(pairs.size)
            pairs.forEach { (from, to) -> listOf(from ushr 8, from, to ushr 8, to).forEach { out.write(it and 0xFF) } }
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun recolours(def: ByteArray): List<Pair<Int, Int>> {
        val start = ItemDefCodec.payloadOffsets(def).firstOrNull { it.first == 40 }?.second ?: return emptyList()
        val buf = Unpooled.wrappedBuffer(def, start, def.size - start)
        return List(buf.readUnsignedByte().toInt()) { buf.readUnsignedShort() to buf.readUnsignedShort() }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            when (args.firstOrNull()) {
                "probe" -> args.drop(1).map(String::toInt).forEach { id ->
                    val def = library.data(ITEM_INDEX, id ushr 8, id and 0xFF) ?: error("item $id missing")
                    val model = modelOf(def)
                    val mesh = Rev667ModelDecoder.decode(ModelArchive.read(library, model) ?: error("model $model missing"), geometryOnly = true)
                    println("ITEM $id '${ItemDefCodec.readName(def)}' model=$model faces=${mesh.faceCount} recolours=${recolours(def)} ops=${ItemDefCodec.describeOpcodes(def)}")
                    mesh.faceColour.map { it.toInt() and 0xFFFF }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                        .forEach { (c, n) -> println("  colour $c h${hue(c)} s${saturation(c)} l${lightness(c)} x$n") }
                }
                // verify <ITEM-PREFLIGHT.tsv>: every row's local item must use exactly its planned inventory model (no truncated u16).
                "verify" -> {
                    var bad = 0; var ok = 0
                    java.io.File(args[1]).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                        val f = line.split('\t'); val local = f[1].toIntOrNull() ?: return@forEach
                        val planned = Regex("""->(\d+)""").findAll(f.getOrElse(4) { "" }).map { it.groupValues[1].toInt() }.toList()
                        if (planned.isEmpty()) return@forEach
                        val def = library.data(ITEM_INDEX, local ushr 8, local and 0xFF)
                        val model = def?.let { runCatching { modelOf(it) }.getOrNull() }
                        if (model == null || model !in planned) { bad++; println("BAD item $local '${f.getOrElse(2) { "" }}' model=$model planned=$planned") } else ok++
                    }
                    println("VERIFY ok=$ok bad=$bad")
                }
                "model" -> args.drop(1).map(String::toInt).forEach { model ->
                    val mesh = Rev667ModelDecoder.decode(ModelArchive.read(library, model) ?: error("model $model missing"), geometryOnly = true)
                    println("MODEL $model faces=${mesh.faceCount}")
                    mesh.faceColour.map { it.toInt() and 0xFFFF }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                        .forEach { (c, n) -> println("  colour $c h${hue(c)} s${saturation(c)} l${lightness(c)} x$n") }
                }
                "set" -> {
                    val id = args[1].toInt()
                    val pairs = args[2].split(',').map { p -> p.split(':').let { it[0].toInt() to it[1].toInt() } }
                    require(pairs.all { (a, b) -> a in 0..0xFFFF && b in 0..0xFFFF }) { "colours are 16-bit HSL" }
                    val before = library.data(ITEM_INDEX, id ushr 8, id and 0xFF) ?: error("item $id missing")
                    val after = withRecolours(before, pairs)
                    check(recolours(after) == pairs) { "recolour table does not read back" }
                    check(ItemDefCodec.describeOpcodes(after).filterNot { it.startsWith("40=") } ==
                        ItemDefCodec.describeOpcodes(before).filterNot { it.startsWith("40=") }) { "other opcodes changed" }
                    println("ITEM $id '${ItemDefCodec.readName(before)}' recolours ${recolours(before)} -> $pairs")
                    if (before.contentEquals(after)) { println("NOTHING_TO_DO"); return }
                    val tx = CacheTransaction(OsrsItemImportTool.TARGETS, listOf(CacheMutation(ITEM_INDEX, id ushr 8, id and 0xFF, after,
                        "CLUE 78 presentation: item $id recolour table (owner design 14-11)", CacheItemProbeTool.sha1(before))))
                    val preflight = tx.preflight()
                    check(tx.blockingErrors(preflight).isEmpty()) { "preflight blocked; nothing written" }
                    println("PREFLIGHT ${tx.id}")
                    if ("--apply" !in args) { println("PLAN_ONLY (nothing written)"); return }
                    tx.apply(preflight)
                    println("APPLIED ${tx.id}")
                }
                else -> error("Usage: probe <itemId>... | set <itemId> <from>:<to>[,...] [--apply]")
            }
        } finally { library.close() }
    }
}
