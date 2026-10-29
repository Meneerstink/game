package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * N398 (owner 2026-10-29: "IJsblauw kleuren"): the 667 cache gives the Baby icefiend (npc 7713) the red Imp head (model 20045,
 * also the head of the Imp and the Banner carrier), while its body (16264) is ice blue. The client does not put an npc's
 * recolour on its chathead (N418), so the head gets its own model: a copy of 20045 whose red faces take the hue and
 * saturation of the body's main colour (each face keeps its own lightness), and npc 7713's chathead (opcode 60) points at it.
 * The Imp and the Banner carrier keep 20045. Colours are the client's 16-bit HSL: hue 6 bits, saturation 3, lightness 7.
 *
 * Usage: `./gradlew :game:runPetHeadRecolourTool --args="[--apply]"` (plan by default; CacheTransaction, both caches).
 */
object PetHeadRecolourTool {
    private const val NPC_INDEX = 18
    private const val MODEL_INDEX = 7
    const val ICEFIEND = 7713
    const val IMP_HEAD = 20045
    const val ICEFIEND_BODY = 16264

    fun hue(hsl: Int) = (hsl ushr 10) and 0x3F

    fun saturation(hsl: Int) = (hsl ushr 7) and 0x7

    fun lightness(hsl: Int) = hsl and 0x7F

    /** A red face of the Imp head: hue within 4 of red (0 / 63) and not grey. */
    fun isRed(hsl: Int) = (hue(hsl) <= 4 || hue(hsl) >= 59) && saturation(hsl) >= 2

    /** [hsl] with the hue and saturation of [target] and its lightness moved by [shift] (the head's shading kept). */
    fun tint(
        hsl: Int,
        target: Int,
        shift: Int,
    ) = (hue(target) shl 10) or (saturation(target) shl 7) or (lightness(hsl) + shift).coerceIn(0, 127)

    /** The npc definition [def] with its chathead models (opcode 60) replaced by [heads]. */
    fun withHeads(
        def: ByteArray,
        heads: List<Int>,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            NpcLookCopyTool.segments(def).forEach { (op, bytes) ->
                if (op != 60) {
                    out.write(bytes)
                    return@forEach
                }
                out.write(60)
                out.write(heads.size)
                heads.forEach { out.write(it ushr 8); out.write(it and 0xFF) }
            }
            out.write(0)
            out.toByteArray()
        }

    /** Offset of the face-colour block of a rev-667 `decodeNew` mesh (the section walk of [Rev667ModelDecoder.decode]). */
    fun faceColourOffset(data: ByteArray): Int {
        fun u8(i: Int) = data[i].toInt() and 0xFF
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        val h = data.size - 23
        val vertexCount = u16(h)
        val faceCount = u16(h + 2)
        val texSpaceCount = u8(h + 4)
        val globalFlags = u8(h + 5)
        val priorityFlag = u8(h + 6)
        val faceAlphaFlag = u8(h + 7)
        val faceGroupFlag = u8(h + 8)
        val faceTextureFlag = u8(h + 9)
        val vertexLabelFlag = u8(h + 10)
        val faceDataSize = u16(h + 17)
        val texSpaceSize = u16(h + 19)
        var ptr = texSpaceCount + vertexCount
        if (globalFlags and 0x1 != 0) ptr += faceCount
        ptr += faceCount
        if (priorityFlag == 255) ptr += faceCount
        if (faceGroupFlag == 1) ptr += faceCount
        if (vertexLabelFlag == 1) ptr += vertexCount
        if (faceAlphaFlag == 1) ptr += faceCount
        ptr += faceDataSize
        if (faceTextureFlag == 1) ptr += faceCount * 2
        ptr += texSpaceSize
        return ptr
    }

    private fun histogram(colours: ShortArray) =
        colours.map { it.toInt() and 0xFFFF }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .joinToString { "${it.key}(h${hue(it.key)} s${saturation(it.key)} l${lightness(it.key)})x${it.value}" }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            val npcBytes = library.data(NPC_INDEX, ICEFIEND ushr 7, ICEFIEND and 0x7F) ?: error("npc $ICEFIEND missing")
            val npc = NpcDef(ICEFIEND).also { it.decode(Unpooled.wrappedBuffer(npcBytes)) }
            val heads = npc.chatheadModels?.toList().orEmpty()
            println("NPC $ICEFIEND '${npc.name}' heads=$heads")
            if (heads != listOf(IMP_HEAD)) {
                println("NOTHING_TO_DO: the head is no longer the Imp head $IMP_HEAD")
                return
            }
            val body = Rev667ModelDecoder.decode(library.data(MODEL_INDEX, ICEFIEND_BODY, 0) ?: error("model $ICEFIEND_BODY missing"), geometryOnly = true)
            // Both meshes carry complex texture mappings, which the encoder cannot write: the head's colour bytes are patched in place.
            val headBytes = library.data(MODEL_INDEX, IMP_HEAD, 0) ?: error("model $IMP_HEAD missing")
            val head = Rev667ModelDecoder.decode(headBytes, geometryOnly = true)
            println("BODY $ICEFIEND_BODY ${histogram(body.faceColour)}")
            println("HEAD $IMP_HEAD ${histogram(head.faceColour)}")
            val target = body.faceColour.map { it.toInt() and 0xFFFF }.filter { saturation(it) >= 2 }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            // The Imp head is dark red (lightness 10-41), the ice body pale (82-90): its most common red shade moves to the body's.
            val mainRed = head.faceColour.map { it.toInt() and 0xFFFF }.filter { isRed(it) }.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            val shift = lightness(target) - lightness(mainRed)
            val colours = faceColourOffset(headBytes)
            val encoded = headBytes.copyOf()
            var changed = 0
            for (i in 0 until head.faceCount) {
                val hsl = head.faceColour[i].toInt() and 0xFFFF
                check(((encoded[colours + 2 * i].toInt() and 0xFF) shl 8 or (encoded[colours + 2 * i + 1].toInt() and 0xFF)) == hsl) {
                    "face $i colour is not at the computed offset"
                }
                if (!isRed(hsl)) continue
                val tinted = tint(hsl, target, shift)
                encoded[colours + 2 * i] = (tinted ushr 8).toByte()
                encoded[colours + 2 * i + 1] = tinted.toByte()
                head.faceColour[i] = tinted.toShort()
                changed++
            }
            println("TINT target=$target (h${hue(target)} s${saturation(target)}) lightness +$shift: $changed of ${head.faceCount} faces")
            println("NEW HEAD ${histogram(head.faceColour)}")
            val reread = Rev667ModelDecoder.decode(encoded, geometryOnly = true)
            check(reread.faceColour.contentEquals(head.faceColour)) { "patched colours do not read back" }
            check(ModelConvertTool.compare(Rev667ModelDecoder.decode(headBytes, geometryOnly = true).also { it.faceColour.indices.forEach { i -> it.faceColour[i] = head.faceColour[i] } }, reread).isEmpty()) {
                "the patch changed more than the face colours"
            }
            // An npc's chathead ids are u16 (NPCType opcode 60, g2) and the models above 65535 are taken: the highest free id below.
            val used = library.index(MODEL_INDEX).archiveIds().toSet()
            val newModel = (0xFFFF downTo 0).first { it !in used }
            val newNpc = withHeads(npcBytes, listOf(newModel))
            val after = NpcDef(ICEFIEND).also { it.decode(Unpooled.wrappedBuffer(newNpc)) }
            check(after.name == npc.name && after.options.toList() == npc.options.toList() && after.chatheadModels?.toList() == listOf(newModel)) {
                "the npc edit changed more than the chathead: ${npc.name}/${after.name} ${npc.options.toList()}/${after.options.toList()} ${after.chatheadModels?.toList()}"
            }
            println("PLAN model $newModel = ice-blue Imp head; npc $ICEFIEND chathead $IMP_HEAD -> $newModel")
            mutations += CacheMutation(MODEL_INDEX, newModel, 0, encoded, "N398 Baby icefiend ice-blue chathead (Imp head $IMP_HEAD recoloured)")
            mutations +=
                CacheMutation(NPC_INDEX, ICEFIEND ushr 7, ICEFIEND and 0x7F, newNpc, "N398 npc $ICEFIEND chathead -> $newModel", CacheItemProbeTool.sha1(npcBytes))
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN transaction=${transaction.id} mutations=${mutations.size}")
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
