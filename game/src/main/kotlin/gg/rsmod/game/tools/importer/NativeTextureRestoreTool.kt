package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Puts the rev-667 textures back on every imported OSRS model whose texture 667 already has (owner 2026-09-27: "de firecape van
 * durial321 is niet goed is roze" - the fire cape lava, OSRS/667 texture 40, had been flattened to one colour).
 *
 * 1. Census: every id in [OsrsModelConversion.NATIVE_SAME_TEXTURES] must be, in the rev-667 game cache, a texture program whose
 *    only op is the sprite op on the OSRS texture's own sprite id (both source caches). Any mismatch aborts before anything is
 *    planned, so the conversion can never put a different image on a model.
 * 2. Every asset-map model pair ([TextureColourRepairTool.modelPairs]) whose upstream model uses one of those textures is
 *    converted again with [OsrsModelConversion.convert]. The new bytes replace the stored model only when the shape is exactly
 *    the stored one (same vertices, same faces) - a model edited since its import is reported and left alone.
 *
 * Usage: `[--apply]`; without it only the census and the plan run. Both caches in one [CacheTransaction].
 */
object NativeTextureRestoreTool {
    private val SOURCE_CACHES = listOf(OsrsItemImportTool.SOURCE_CACHE, FeroxImportTool.MODERN_CACHE)

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val readers = SOURCE_CACHES.map { ModernCacheReader(File(it)) }
        val mutations = ArrayList<CacheMutation>()
        try {
            census(library, readers)
            val pairs = TextureColourRepairTool.modelPairs(File(FeroxImportTool.ASSET_MAP))
            val native = OsrsModelConversion.NATIVE_SAME_TEXTURES
            val skipped = ArrayList<String>()
            pairs.forEach { (local, upstream) ->
                val current = ModelArchive.read(library, local) ?: return@forEach
                for (reader in readers) {
                    val bytes = reader.file(ModernCacheReader.INDEX_MODEL, upstream, 0) ?: continue
                    val raw = runCatching { ModernModelDecoder.decode(bytes) }.getOrNull() ?: continue
                    val used = raw.faceTexture?.map { it.toInt() }?.filter { it in native }?.toSet().orEmpty()
                    if (used.isEmpty()) break
                    val fixed = OsrsModelConversion.convert(reader, upstream, ArrayList())
                    if (fixed.contentEquals(current)) break
                    val decoded = runCatching { Rev667ModelDecoder.decode(current) }
                    if (decoded.isFailure) {
                        skipped += "local $local upstream $upstream: stored model does not decode (${decoded.exceptionOrNull()?.message})"
                        break
                    }
                    val stored = decoded.getOrThrow()
                    val next = Rev667ModelDecoder.decode(fixed)
                    if (!sameShape(stored, next)) {
                        skipped += "local $local upstream $upstream: stored shape differs from the conversion (edited after import)"
                        break
                    }
                    println("RESTORE local $local upstream $upstream textures ${used.sorted()}")
                    mutations +=
                        CacheMutation(
                            indexId = ModelConvertTool.MODEL_INDEX,
                            groupId = local,
                            fileId = 0,
                            newBytes = fixed,
                            label = "native texture restore model $local (upstream $upstream, textures ${used.sorted()})",
                            expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                        )
                    break
                }
            }
            println("RESTORE ${mutations.size} models; skipped=${skipped.size}")
            skipped.forEach { println("  SKIP $it") }
        } finally {
            library.close()
            readers.forEach { it.close() }
        }
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
        println("VERIFY_OK both caches hold the restored models")
    }

    /**
     * Aborts unless every native texture is the same image in 667 as in each OSRS source cache. Client `Texture(Packet)`: u8 op
     * count, per op (u8, u8 type, u8 cacheSize, u8 paramCount, params, one u8 input per op input), then three u8 output selectors
     * (colour first). The fire cape lava 40 program is: op 0 = sprite (type 39, param 0 = u16 sprite id), op 1 / op 2 = constant
     * ops (type 0), colour output = op 0 - bytes 0..20. A texture passes when those 21 bytes equal texture 40's except the sprite
     * id (so its colour is exactly its sprite) and that sprite is the OSRS texture's sprite. Bytes after 20 are not read by the
     * texture decoder.
     */
    fun census(
        library: CacheLibrary,
        readers: List<ModernCacheReader>,
    ) {
        val template = library.data(OsrsTextureImportTool.INDEX_TEXTURES, 40) ?: error("667 texture 40 missing")
        check(template.size > 20 && template[0].toInt() == 3 && template[2].toInt() == OsrsTextureImportTool.OP_SPRITE && template[18].toInt() == 0) {
            "667 texture 40 is not the sprite-colour program this census expects"
        }
        OsrsModelConversion.NATIVE_SAME_TEXTURES.forEach { t ->
            val program = library.data(OsrsTextureImportTool.INDEX_TEXTURES, t) ?: error("667 texture $t missing")
            check(program.size > 20 && (0..20).all { it == 6 || it == 7 || program[it] == template[it] }) {
                "667 texture $t is not the sprite-colour program of texture 40"
            }
            val sprite667 = ((program[6].toInt() and 0xFF) shl 8) or (program[7].toInt() and 0xFF)
            readers.forEach { reader ->
                val def = reader.file(ModernCacheReader.INDEX_TEXTURE, 0, t) ?: return@forEach
                val osrsSprite = OsrsTextureImportTool.readOsrsTexture(def).sprite
                check(osrsSprite == sprite667) { "texture $t: OSRS sprite $osrsSprite, 667 sprite $sprite667" }
            }
            println("CENSUS texture $t = sprite $sprite667 in 667 and OSRS")
        }
    }

    private fun sameShape(
        a: ModelData,
        b: ModelData,
    ): Boolean =
        a.vertexCount == b.vertexCount && a.faceCount == b.faceCount &&
            a.vertexX.contentEquals(b.vertexX) && a.vertexY.contentEquals(b.vertexY) && a.vertexZ.contentEquals(b.vertexZ) &&
            a.faceA.contentEquals(b.faceA) && a.faceB.contentEquals(b.faceB) && a.faceC.contentEquals(b.faceC)
}
