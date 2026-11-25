package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import io.netty.buffer.Unpooled

/**
 * N537-HOEDEN (owner 2026-11-25 'ja' fix round): OSRS head items are modelled on the smaller OSRS head; on the 667 head (and the
 * fuller 667 female hair) small hats sink into the skull / hair.
 *
 * - `native`: for an OSRS hat whose OSRS worn model id is ALSO a native 667 item's worn model (same hat, same OSRS item id - e.g.
 *   cavalier 186/362, wizard hat (g) 11331/11354, headband 201/376), Jagex rebuilt that model for the 667 head. The import carried a
 *   copy of the OSRS mesh; this points the item's worn models (opcodes 23/25) back at the 667 model and, where the 667 mesh uses a
 *   different source colour, moves the recolour table's 'from' colour (White cavalier feather 127 -> 123).
 * - `fit`: for a hat without a 667 version: scale a worn model about its top centre (x/z centre, y top) and shift it (dy, dz;
 *   +y = down, -z = front). Refused when any item other than the named ones uses the model.
 *
 * Usage: HeadFitTool plan|apply native <item>:<male>:<female>[:<oldFrom>=<newFrom>[/...]] ...
 *        HeadFitTool plan|apply fit <items,comma>@<model>:<sx>:<sy>:<sz>:<dy>[:<dz>] ...
 */
object HeadFitTool {
    private const val ITEM_INDEX = 19

    private fun recolours(def: ByteArray): List<Pair<Int, Int>> {
        val start = ItemDefCodec.payloadOffsets(def).firstOrNull { it.first == 40 }?.second ?: return emptyList()
        val buf = Unpooled.wrappedBuffer(def, start, def.size - start)
        return List(buf.readUnsignedByte().toInt()) { buf.readUnsignedShort() to buf.readUnsignedShort() }
    }

    private fun item(library: CacheLibrary, id: Int) = library.data(ITEM_INDEX, id ushr 8, id and 0xFF) ?: error("item $id missing")

    private fun wornModels(def: ByteArray): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        ItemDefCodec.describeOpcodes(def).forEach { op ->
            val k = op.substringBefore('=').toIntOrNull() ?: return@forEach
            val v = op.substringAfter('=').substringBefore(' ').toIntOrNull() ?: return@forEach
            if (k in setOf(23, 24, 25, 26, 78, 79, 90, 91, 92, 93)) out[k] = maxOf(out[k] ?: 0, v)
        }
        return out
    }

    private fun meshColours(library: CacheLibrary, model: Int): Set<Int> =
        Rev667ModelDecoder.decode(ModelArchive.read(library, model) ?: error("model $model missing"), geometryOnly = true)
            .faceColour.map { it.toInt() and 0xFFFF }.toSet()

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3 && args[0] in setOf("plan", "apply") && args[1] in setOf("native", "fit")) {
            "Usage: plan|apply native <item>:<male>:<female>[:<old>=<new>/...] ... | plan|apply fit <items>@<model>:<sx>:<sy>:<sz>:<dy>[:<dz>] ..."
        }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            if (args[1] == "native") {
                for (spec in args.drop(2)) {
                    val f = spec.split(':')
                    val id = f[0].toInt()
                    val male = f[1].toInt()
                    val female = f[2].toInt()
                    val moves = f.getOrNull(3)?.split('/')?.associate { m -> m.split('=').let { it[0].toInt() to it[1].toInt() } } ?: emptyMap()
                    val before = item(library, id)
                    val old = wornModels(before)
                    var after = ItemDefCodec.cloneWithOverrides(before, emptyMap(), shortOverrides = mapOf(23 to male, 25 to female))
                    val table = recolours(before)
                    val newTable = table.map { (from, to) -> (moves[from] ?: from) to to }
                    if (newTable != table) after = ItemRecolourTool.withRecolours(after, newTable)
                    check(wornModels(after)[23] == male && wornModels(after)[25] == female) { "item $id: worn models do not read back" }
                    for ((model, label, oldModel) in listOf(Triple(male, "male", old[23]), Triple(female, "female", old[25]))) {
                        val colours = meshColours(library, model)
                        // a recolour entry that painted the old mesh must paint the 667 mesh too (OSRS tables carry no-op entries, e.g. headband 41)
                        val used = oldModel?.let { meshColours(library, it) } ?: emptySet()
                        val missing = table.indices.filter { table[it].first in used && newTable[it].first !in colours }.map { newTable[it].first }
                        check(missing.isEmpty()) { "item $id: $label model $model has no face colour $missing for the recolour table $newTable" }
                    }
                    println("NATIVE item $id '${ItemDefCodec.readName(before)}': male ${old[23]} -> $male, female ${old[25]} -> $female, recolours $table -> $newTable")
                    mutations += CacheMutation(ITEM_INDEX, id ushr 8, id and 0xFF, after, "N537-HOEDEN item $id worn models -> 667 $male/$female",
                        CacheItemProbeTool.sha1(before))
                }
            } else {
                val all = library.index(ITEM_INDEX).archiveIds()
                for (spec in args.drop(2)) {
                    val owners = spec.substringBefore('@').split(',').map { it.toInt() }.toSet()
                    val f = spec.substringAfter('@').split(':')
                    val model = f[0].toInt()
                    val (sx, sy, sz) = Triple(f[1].toDouble(), f[2].toDouble(), f[3].toDouble())
                    val dy = f[4].toInt()
                    val dz = f.getOrNull(5)?.toInt() ?: 0
                    require(listOf(sx, sy, sz).all { it in 0.7..1.5 } && dy in -80..80 && dz in -40..40) { "fit $spec outside the safe range" }
                    // every item that wears this model must be named: a shared mesh would change other items too
                    val users = HashSet<Int>()
                    for (group in all) {
                        val archive = library.index(ITEM_INDEX).archive(group) ?: continue
                        for (file in archive.fileIds()) {
                            val def = library.data(ITEM_INDEX, group, file) ?: continue
                            if (runCatching { wornModels(def).values.contains(model) }.getOrDefault(false)) users += (group shl 8) or file
                        }
                    }
                    check(users == owners) { "model $model is worn by $users, named $owners - refusing" }
                    val current = requireNotNull(ModelArchive.read(library, model)) { "model $model missing" }
                    val mesh = Rev667ModelDecoder.decode(current)
                    check(Rev667ModelEncoder.encode(mesh).contentEquals(current)) { "model $model: codec does not round-trip, refusing" }
                    val n = mesh.vertexCount
                    val xs = (0 until n).map { mesh.vertexX[it] }
                    val ys = (0 until n).map { mesh.vertexY[it] }
                    val zs = (0 until n).map { mesh.vertexZ[it] }
                    val cx = (xs.min() + xs.max()) / 2.0
                    val top = ys.min().toDouble()
                    val cz = (zs.min() + zs.max()) / 2.0
                    for (v in 0 until n) {
                        mesh.vertexX[v] = Math.round(cx + (mesh.vertexX[v] - cx) * sx).toInt()
                        mesh.vertexY[v] = Math.round(top + (mesh.vertexY[v] - top) * sy).toInt() + dy
                        mesh.vertexZ[v] = Math.round(cz + (mesh.vertexZ[v] - cz) * sz).toInt() + dz
                    }
                    val ys2 = (0 until n).map { mesh.vertexY[it] }
                    println("FIT model $model (items $owners): x ${xs.min()}..${xs.max()} y ${ys.min()}..${ys.max()} z ${zs.min()}..${zs.max()} " +
                        "-> x ${(0 until n).minOf { mesh.vertexX[it] }}..${(0 until n).maxOf { mesh.vertexX[it] }} y ${ys2.min()}..${ys2.max()} " +
                        "z ${(0 until n).minOf { mesh.vertexZ[it] }}..${(0 until n).maxOf { mesh.vertexZ[it] }} (scale $sx/$sy/$sz, dy $dy, dz $dz)")
                    mutations += CacheMutation(7, model, 0, Rev667ModelEncoder.encode(mesh), "N537-HOEDEN model $model fit $sx/$sy/$sz dy $dy dz $dz",
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
        } finally {
            library.close()
        }
        val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") {
            println("DRY_RUN ${tx.id} mutations=${mutations.size}")
            return
        }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "verify failed ${tx.id}" }
        println("HEAD_FIT transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
