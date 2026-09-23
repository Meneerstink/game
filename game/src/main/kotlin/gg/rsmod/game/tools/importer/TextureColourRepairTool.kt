package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Repairs every imported OSRS model whose textured faces were flattened with the wrong colour (owner 2026-09-23: "Infernal cape
 * is still not correct like osrs it now shows a purple cape").
 *
 * Root cause ([osrsTextureAverageHsl]): all importers read the first u16 of a revision-233 OSRS texture definition - the texture's
 * sprite id - as its average HSL colour. This tool walks every upstream -> local model pair in the asset map
 * ([FeroxImportTool.ASSET_MAP]: item models, npc/spotanim/loc models, Ferox and Watson-house loc models), rebuilds the model the
 * way the importer did with the buggy colour and, only when that reproduces the bytes now in the game cache exactly, replaces it
 * with the same conversion using the real colour. A model that does not reproduce byte-for-byte (edited later, different
 * pipeline) is reported and left alone. Both caches are written in one [CacheTransaction] (preflight, journal, verify).
 *
 * Usage: `[--apply]`; without it only the plan runs.
 */
object TextureColourRepairTool {
    private val SOURCE_CACHES = listOf(OsrsItemImportTool.SOURCE_CACHE, FeroxImportTool.MODERN_CACHE)

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val pairs = modelPairs(File(FeroxImportTool.ASSET_MAP))
        println("PAIRS ${pairs.size} distinct local models in the asset map")
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val readers = SOURCE_CACHES.map { ModernCacheReader(File(it)) }
        val mutations = ArrayList<CacheMutation>()
        val skipped = ArrayList<String>()
        var untextured = 0
        try {
            pairs.forEach { (local, upstream) ->
                val current = library.data(ModelConvertTool.MODEL_INDEX, local)
                if (current == null) {
                    skipped.add("local $local absent")
                    return@forEach
                }
                var matched = false
                var sawTextures = false
                for (reader in readers) {
                    val bytes = reader.file(ModernCacheReader.INDEX_MODEL, upstream, 0) ?: continue
                    val strict = runCatching { ModernModelDecoder.decode(bytes) }.getOrNull()
                    val candidates = listOfNotNull(strict, runCatching { ModernModelDecoder.decode(bytes, flattenTextures = true) }.getOrNull())
                    for (raw in candidates) {
                        val texFaces = raw.faceTexture?.count { it.toInt() != -1 } ?: 0
                        if (texFaces == 0 && raw.texSpaceCount == 0) continue
                        sawTextures = true
                        fun texture(tex: Int) = reader.file(ModernCacheReader.INDEX_TEXTURE, 0, tex) ?: error("texture $tex missing")
                        val buggy = runCatching { Rev667ModelEncoder.encode(stripTextures(raw) { t -> firstU16(texture(t)) }) }.getOrNull() ?: continue
                        if (!buggy.contentEquals(current)) continue
                        val fixed = Rev667ModelEncoder.encode(stripTextures(raw) { t -> osrsTextureAverageHsl(texture(t)) })
                        matched = true
                        if (!fixed.contentEquals(current)) {
                            mutations +=
                                CacheMutation(
                                    indexId = ModelConvertTool.MODEL_INDEX,
                                    groupId = local,
                                    fileId = 0,
                                    newBytes = fixed,
                                    label = "texture colour repair model $local (upstream $upstream)",
                                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                                )
                        }
                        break
                    }
                    if (matched) break
                    // Older pipelines (Ferox, Watson house) encoded differently, so the bytes cannot match; the faces can: every
                    // textured face must carry exactly its texture's sprite id and every other face its source colour. Only then
                    // are those faces recoloured, on the model as it is stored now.
                    val raw = candidates.firstOrNull { (it.faceTexture?.count { t -> t.toInt() != -1 } ?: 0) > 0 } ?: continue
                    val stored = runCatching { Rev667ModelDecoder.decode(current) }.getOrNull() ?: continue
                    if (stored.faceCount != raw.faceCount) continue
                    val tex = raw.faceTexture!!
                    fun texture(t: Int) = reader.file(ModernCacheReader.INDEX_TEXTURE, 0, t) ?: error("texture $t missing")
                    val consistent =
                        (0 until raw.faceCount).all { i ->
                            val t = tex[i].toInt()
                            val want = if (t == -1) raw.faceColour[i].toInt() and 0xFFFF else firstU16(texture(t and 0xFFFF))
                            (stored.faceColour[i].toInt() and 0xFFFF) == want
                        }
                    if (!consistent) {
                        if (System.getenv("REPAIR_DEBUG") != null) {
                            val rows =
                                (0 until raw.faceCount).filter { tex[it].toInt() != -1 }.take(3).map { i ->
                                    val d = texture(tex[i].toInt() and 0xFFFF)
                                    "face $i tex=${tex[i]} stored=${stored.faceColour[i].toInt() and 0xFFFF} sprite=${firstU16(d)} avg=${osrsTextureAverageHsl(d)} src=${raw.faceColour[i].toInt() and 0xFFFF}"
                                }
                            val untexturedBad = (0 until raw.faceCount).count { tex[it].toInt() == -1 && stored.faceColour[it] != raw.faceColour[it] }
                            println("  DEBUG $local $rows untexturedMismatch=$untexturedBad")
                        }
                        continue
                    }
                    for (i in 0 until raw.faceCount) {
                        val t = tex[i].toInt()
                        if (t != -1) stored.faceColour[i] = osrsTextureAverageHsl(texture(t and 0xFFFF)).toShort()
                    }
                    val fixed = Rev667ModelEncoder.encode(stored)
                    val differences = ModelConvertTool.compare(stored, Rev667ModelDecoder.decode(fixed))
                    check(differences.isEmpty()) { "model $local face-recolour round trip mismatch: $differences" }
                    matched = true
                    mutations +=
                        CacheMutation(
                            indexId = ModelConvertTool.MODEL_INDEX,
                            groupId = local,
                            fileId = 0,
                            newBytes = fixed,
                            label = "texture colour repair (faces) model $local (upstream $upstream)",
                            expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                        )
                    break
                }
                when {
                    matched -> Unit
                    sawTextures -> skipped.add("local $local upstream $upstream: textured but current bytes are not the importer's output")
                    else -> untextured++
                }
            }
        } finally {
            library.close()
            readers.forEach { it.close() }
        }
        println("REPAIR ${mutations.size} models; untextured=$untextured; skipped=${skipped.size}")
        skipped.forEach { println("  SKIP $it") }
        if (mutations.isEmpty()) return
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        println("PREFLIGHT transaction=${tx.id} outcomes=${preflight.groupBy { it.outcome }.mapValues { it.value.size }}")
        if (!apply) {
            println("PLAN_ONLY (nothing written); re-run with --apply")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        if (verify.isNotEmpty()) {
            verify.forEach { println("VERIFY_ERROR: $it") }
            val restored = tx.rollback()
            error("Verification failed; rolled back $restored location(s).")
        }
        println("VERIFY_OK both caches hold the repaired models")
    }

    private fun firstU16(def: ByteArray) = ((def[0].toInt() and 0xFF) shl 8) or (def[1].toInt() and 0xFF)

    /** local model id -> upstream model id, from `upstream_id`/`local_id` model rows and `*model` fx rows. */
    fun modelPairs(assetMap: File): Map<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        val lines = assetMap.readLines()
        val inline = Regex("""\{\s*upstream_id:\s*(\d+),\s*local_id:\s*(\d+)""")
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("- role:") || line.startsWith("role:")) {
                val up = lines.getOrNull(i + 1)?.trim()?.removePrefix("upstream_id:")?.trim()?.toIntOrNull()
                val local = lines.getOrNull(i + 2)?.trim()?.removePrefix("local_id:")?.trim()?.toIntOrNull()
                if (up != null && local != null) out.putIfAbsent(local, up)
            } else if (line.startsWith("- fx_kind:") && line.endsWith("model")) {
                val up = lines.getOrNull(i + 1)?.trim()?.removePrefix("upstream_fx_id:")?.trim()?.toIntOrNull()
                val local = lines.getOrNull(i + 2)?.trim()?.removePrefix("local_fx_id:")?.trim()?.toIntOrNull()
                if (up != null && local != null) out.putIfAbsent(local, up)
            } else {
                // Inline `{ upstream_id, local_id }` rows (Ferox/Watson loc lists): only a byte-exact match is ever rewritten, so a
                // row that is not a model simply never matches.
                inline.find(line)?.let { m -> out.putIfAbsent(m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
            }
        }
        return out
    }
}
