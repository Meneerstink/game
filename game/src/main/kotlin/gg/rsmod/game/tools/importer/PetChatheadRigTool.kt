package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.File

/**
 * Puts every OSRS pet chathead on the classic head rig (owner 2026-10-04 025631: "when spawning one of the osrs pets some
 * osrs pets the chathead animation do not seem correct").
 *
 * Measured in the own client (screens\bugrun-2026-10-04\run12h\rig): an OSRS head is modelled for the OSRS dialogue zoom,
 * and the classic head poses (588-617, base 82) are what scale it up to this client's chathead size - base 82's last group
 * scales labels 0-50. TzRek-Zuk, Hellpuppy and Vet'ion Jr. come out right with pose 601 and at under half size with no pose.
 * A pet head whose vertices carry labels outside 0-50 (its own OSRS skeleton: Abyssal orphan 89-143, Little Nightmare
 * 71-224, Vorki 144-170, TzRek-Zuk 65-73) or no label (255) is not moved by any classic pose, so those vertices stayed at
 * the OSRS size: a tiny head, or a head with tiny parts. This relabels exactly those vertices to label [WHOLE_HEAD]
 * (50: in base 82's whole-head rotate and scale groups only, in none of its eye/brow/jaw groups), so every pose scales and
 * turns them with the rest of the head. Only the vertex-label bytes change; geometry, faces and colours are untouched.
 *
 * Usage: `[--apply]` (dry run by default).
 */
object PetChatheadRigTool {
    private const val NPC_INDEX = 18
    private const val MODEL_INDEX = 7
    private const val CLASSIC_MAX_LABEL = 50
    private const val NO_GROUP = 255
    const val WHOLE_HEAD = 50
    private val PETS = RspsPaths.data("cfg/pets/pets.tsv")

    /** Offset of the vertex-label block of a rev-667 `decodeNew` mesh (same walk as [Rev667ModelDecoder]), or null when it has none. */
    fun vertexLabelOffset(data: ByteArray): Int? {
        if (data[data.size - 1].toInt() != -1 || data[data.size - 2].toInt() != -1) return null
        val h = data.size - 23
        fun u8(i: Int) = data[i].toInt() and 0xFF
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        val vertexCount = u16(h)
        val faceCount = u16(h + 2)
        val texSpaceCount = u8(h + 4)
        val globalFlags = u8(h + 5)
        val priorityFlag = u8(h + 6)
        val faceGroupFlag = u8(h + 8)
        val vertexLabelFlag = u8(h + 10)
        if (vertexLabelFlag != 1) return null
        var ptr = texSpaceCount + vertexCount
        if (globalFlags and 0x1 != 0) ptr += faceCount
        ptr += faceCount
        if (priorityFlag == 255) ptr += faceCount
        if (faceGroupFlag == 1) ptr += faceCount
        return ptr
    }

    /** [data] with every vertex label outside 0..[CLASSIC_MAX_LABEL] (and every unlabelled vertex) set to [WHOLE_HEAD]. */
    fun relabel(data: ByteArray): ByteArray {
        val offset = vertexLabelOffset(data) ?: return data
        val vertexCount = ((data[data.size - 23].toInt() and 0xFF) shl 8) or (data[data.size - 22].toInt() and 0xFF)
        val out = data.copyOf()
        for (i in 0 until vertexCount) {
            val label = out[offset + i].toInt() and 0xFF
            if (label > CLASSIC_MAX_LABEL || label == NO_GROUP) out[offset + i] = WHOLE_HEAD.toByte()
        }
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val npcs =
            File(PETS).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t')[3].trim().toInt() }
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val models = sortedSetOf<Int>()
            npcs.forEach { npc ->
                val bytes = library.data(NPC_INDEX, npc ushr 7, npc and 0x7F) ?: error("npc $npc missing")
                val def = NpcDef(npc).also { it.decode(Unpooled.wrappedBuffer(bytes)) }
                def.chatheadModels?.forEach { models += it }
            }
            // A model also used as the head of a non-pet npc would change for that npc too: report it.
            val petSet = npcs.toSet()
            library.index(NPC_INDEX).archiveIds().forEach { group ->
                library.index(NPC_INDEX).archive(group)?.fileIds()?.forEach { file ->
                    val npc = (group shl 7) or file
                    if (npc in petSet) return@forEach
                    val bytes = library.data(NPC_INDEX, group, file) ?: return@forEach
                    val heads = runCatching { NpcDef(npc).also { it.decode(Unpooled.wrappedBuffer(bytes)) }.chatheadModels }.getOrNull() ?: return@forEach
                    heads.filter { it in models }.forEach { println("SHARED model $it is also the head of npc $npc") }
                }
            }
            models.forEach { model ->
                val current = ModelArchive.read(library, model) ?: error("model $model missing")
                if (vertexLabelOffset(current) == null) {
                    println("SKIP model $model: no vertex labels")
                    return@forEach
                }
                val patched = relabel(current)
                if (!patched.contentEquals(current)) {
                    val changed = patched.indices.count { patched[it] != current[it] }
                    println("PLAN model $model: $changed vertices -> label $WHOLE_HEAD")
                    mutations += CacheMutation(MODEL_INDEX, model, 0, patched, "pet chathead $model: own-rig vertices on the classic head rig", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN ${mutations.size} models")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
