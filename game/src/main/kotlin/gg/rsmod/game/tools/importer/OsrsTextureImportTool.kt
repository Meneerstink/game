package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Imports one OSRS texture into both rev-667 caches and puts it back on every imported model that uses it (owner 2026-09-23:
 * "Infernal cape is still not correct like osrs").
 *
 * The importers flatten OSRS textures to one colour because OSRS texture ids index a different table. A texture is portable when
 * it is a plain sprite, which every revision-233 OSRS texture is (u16 sprite id, u16 colour, u8 transparent, u8 animation
 * direction, u8 animation speed - RuneLite `TextureLoader`). Rev 667 (client `Js5TextureSource`) keeps:
 *  - index 26 group 0 file 0: the materials table - u16 count, one presence byte per texture, then 19 columns
 *    ([MATERIAL_COLUMNS] widths) of per-texture metrics, including the scroll speeds `speedU` / `speedV`;
 *  - index 9 group = texture id: a texture-op program; op 39 (`Node_Sub1_Sub8`) param 0 is a u16 sprite id;
 *  - index 8 group = sprite id: the sprite.
 *
 * The new texture clones a rev-667 [template] texture that is itself a scrolling sprite (the fire cape lava, 40, whose program is
 * "sprite op 39 -> sprite 485", the same sprite OSRS texture 40 uses): the program with the new sprite id, and the template's
 * metrics with the scroll speed scaled by the OSRS animation speed ratio (same direction required). The OSRS sprite is written
 * with [StoreArtTool]'s proven 667 sprite layout, without the alpha plane (textures are opaque).
 *
 * Models: every asset-map pair ([TextureColourRepairTool.modelPairs]) whose upstream model uses the texture and whose current
 * local bytes are exactly the importer's (fixed) flattened output is re-encoded keeping those faces textured; any other texture
 * on the same model stays flattened.
 *
 * Usage: `<osrsTextureId> <template667TextureId> <templateOsrsTextureId> [--apply]`.
 */
object OsrsTextureImportTool {
    const val INDEX_SPRITES = 8
    const val INDEX_TEXTURES = 9
    const val INDEX_MATERIALS = 26
    const val OP_SPRITE = 39

    /** Byte width of every per-texture column of the materials table, in file order (Js5TextureSource constructor). */
    val MATERIAL_COLUMNS = intArrayOf(1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 4, 1)
    const val COL_SPEED_U = 8
    const val COL_SPEED_V = 9

    class OsrsTexture(val sprite: Int, val colour: Int, val transparent: Boolean, val direction: Int, val speed: Int)

    fun readOsrsTexture(def: ByteArray): OsrsTexture {
        check(def.size == OSRS_REV233_TEXTURE_SIZE) { "not a revision-233 texture definition (${def.size} bytes)" }
        fun u16(at: Int) = ((def[at].toInt() and 0xFF) shl 8) or (def[at + 1].toInt() and 0xFF)
        return OsrsTexture(u16(0), u16(2), def[4].toInt() == 1, def[5].toInt() and 0xFF, def[6].toInt() and 0xFF)
    }

    /** The materials table as rows of column values (each column kept as its raw bytes). */
    class Materials(val present: BooleanArray, val rows: Array<Array<ByteArray>?>) {
        fun encode(): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(present.size ushr 8)
            out.write(present.size and 0xFF)
            present.forEach { out.write(if (it) 1 else 0) }
            for (c in MATERIAL_COLUMNS.indices) rows.forEach { row -> row?.let { out.write(it[c]) } }
            return out.toByteArray()
        }

        companion object {
            fun decode(data: ByteArray): Materials {
                var p = 0
                val count = ((data[p++].toInt() and 0xFF) shl 8) or (data[p++].toInt() and 0xFF)
                val present = BooleanArray(count) { data[p++].toInt() == 1 }
                val rows = arrayOfNulls<Array<ByteArray>>(count)
                for (i in 0 until count) if (present[i]) rows[i] = Array(MATERIAL_COLUMNS.size) { ByteArray(0) }
                for (c in MATERIAL_COLUMNS.indices) {
                    for (i in 0 until count) {
                        val row = rows[i] ?: continue
                        row[c] = data.copyOfRange(p, p + MATERIAL_COLUMNS[c])
                        p += MATERIAL_COLUMNS[c]
                    }
                }
                check(p == data.size) { "materials table has ${data.size - p} trailing bytes; layout mismatch" }
                return Materials(present, rows)
            }
        }
    }

    /** Offset of the u16 sprite id of the first sprite op (type 39, param 0) in a texture program. */
    fun spriteParamOffset(program: ByteArray): Int {
        // Program: u8 opCount, then per op: u8 skip, u8 type, u8 cacheSize, u8 paramCount, (u8 paramId, data)... , u8 input per op
        // input. Only programs whose first op is the sprite op with a single param are accepted (the template is checked).
        check(program[2].toInt() == OP_SPRITE && program[4].toInt() == 1 && program[5].toInt() == 0) {
            "template texture's first op is not a single-parameter sprite op"
        }
        return 6
    }

    fun opaqueSprite(image: BufferedImage): ByteArray {
        val withAlpha = StoreArtTool.encode(image)
        // StoreArtTool layout: flags(0x2) raster alpha palette trailer. Drop the alpha plane and clear the flag.
        val pixels = image.width * image.height
        val out = ByteArrayOutputStream()
        out.write(0)
        out.write(withAlpha, 1, pixels)
        out.write(withAlpha, 1 + 2 * pixels, withAlpha.size - (1 + 2 * pixels))
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val pos = args.filterNot { it.startsWith("--") }.map { it.toInt() }
        require(pos.size == 3) { "Usage: <osrsTextureId> <template667TextureId> <templateOsrsTextureId> [--apply]" }
        val (osrsTex, template, templateOsrs) = pos
        val targets = listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE)
        val mutations = ArrayList<CacheMutation>()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val tex = readOsrsTexture(reader.file(ModernCacheReader.INDEX_TEXTURE, 0, osrsTex) ?: error("OSRS texture $osrsTex missing"))
            val tmpl = readOsrsTexture(reader.file(ModernCacheReader.INDEX_TEXTURE, 0, templateOsrs) ?: error("OSRS texture $templateOsrs missing"))
            check(tex.direction == tmpl.direction) { "animation direction ${tex.direction} differs from the template's ${tmpl.direction}" }
            val frames = FeroxMapSpriteProbeTool.decodeSprites(reader.file(INDEX_SPRITES, tex.sprite, 0) ?: error("OSRS sprite ${tex.sprite} missing"))
            val frame = frames.single()
            val image = BufferedImage(frame.w, frame.h, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until frame.h) for (x in 0 until frame.w) image.setRGB(x, y, frame.argb[x + y * frame.w] or (0xFF shl 24))
            val sprite = opaqueSprite(image)

            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            val newSprite: Int
            val newTexture: Int
            val materialsBytes: ByteArray
            val program: ByteArray
            val pairs = TextureColourRepairTool.modelPairs(File(FeroxImportTool.ASSET_MAP))
            val models = LinkedHashMap<Int, ByteArray>()
            try {
                newSprite = (library.index(INDEX_SPRITES).archiveIds().maxOrNull() ?: 0) + 1
                val materials = Materials.decode(library.data(INDEX_MATERIALS, 0, 0) ?: error("materials table missing"))
                newTexture = materials.present.size
                check(library.data(INDEX_TEXTURES, newTexture) == null) { "texture group $newTexture already exists" }
                val templateRow = materials.rows[template] ?: error("template texture $template has no metrics")
                val row = Array(MATERIAL_COLUMNS.size) { templateRow[it].copyOf() }
                for (col in intArrayOf(COL_SPEED_U, COL_SPEED_V)) {
                    val v = row[col][0].toInt()
                    // 667 scroll speeds are whole steps: a moving template never rounds down to a still texture (ADAPTED:
                    // the slowest 667 step when the OSRS ratio falls below it).
                    val scaled = v * tex.speed / tmpl.speed
                    row[col][0] = (if (v != 0 && scaled == 0) Integer.signum(v) else scaled).toByte()
                }
                materialsBytes = Materials(materials.present + true, materials.rows + arrayOf(row)).encode()
                check(Materials.decode(materialsBytes).present.size == newTexture + 1)
                val templateProgram = library.data(INDEX_TEXTURES, template) ?: error("template texture $template program missing")
                val at = spriteParamOffset(templateProgram)
                program = templateProgram.copyOf().also { it[at] = (newSprite ushr 8).toByte(); it[at + 1] = newSprite.toByte() }
                println(
                    "TEXTURE osrs=$osrsTex sprite=${tex.sprite} ${frame.w}x${frame.h} dir=${tex.direction} speed=${tex.speed} -> 667 texture $newTexture " +
                        "sprite $newSprite (template $template speedU=${templateRow[COL_SPEED_U][0]} speedV=${templateRow[COL_SPEED_V][0]} -> ${row[COL_SPEED_U][0]}/${row[COL_SPEED_V][0]})",
                )

                pairs.forEach { (local, upstream) ->
                    val bytes = reader.file(ModernCacheReader.INDEX_MODEL, upstream, 0) ?: return@forEach
                    val raw = runCatching { ModernModelDecoder.decode(bytes) }.getOrNull() ?: return@forEach
                    val faceTex = raw.faceTexture ?: return@forEach
                    if (faceTex.none { it.toInt() == osrsTex }) return@forEach
                    val current = library.data(ModelConvertTool.MODEL_INDEX, local) ?: return@forEach
                    fun colour(t: Int) = osrsTextureAverageHsl(reader.file(ModernCacheReader.INDEX_TEXTURE, 0, t) ?: error("texture $t missing"))
                    val flattened = Rev667ModelEncoder.encode(stripTextures(raw) { colour(it) })
                    if (!flattened.contentEquals(current)) {
                        println("  SKIP model $local (upstream $upstream): current bytes are not the flattened import")
                        return@forEach
                    }
                    val kept = keepTexture(raw, osrsTex, newTexture) { colour(it) }
                    val encoded = Rev667ModelEncoder.encode(kept)
                    val differences = ModelConvertTool.compare(kept, Rev667ModelDecoder.decode(encoded))
                    check(differences.isEmpty()) { "model $local textured round trip mismatch: $differences" }
                    models[local] = encoded
                    mutations += CacheMutation(ModelConvertTool.MODEL_INDEX, local, 0, encoded, "textured model $local (upstream $upstream)", CacheItemProbeTool.sha1(current))
                }
                mutations +=
                    CacheMutation(INDEX_MATERIALS, 0, 0, materialsBytes, "materials + texture $newTexture", CacheItemProbeTool.sha1(library.data(INDEX_MATERIALS, 0, 0)!!))
            } finally {
                library.close()
            }
            mutations += CacheMutation(INDEX_SPRITES, newSprite, 0, sprite, "sprite $newSprite (OSRS sprite ${tex.sprite})")
            mutations += CacheMutation(INDEX_TEXTURES, newTexture, 0, program, "texture program $newTexture")
            println("MODELS ${models.size}: ${models.keys}")
        }
        val tx = CacheTransaction(targets, mutations)
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
        println("VERIFY_OK")
    }

    /** [source] with faces of [osrsTexture] kept (as [localTexture]) and every other textured face flattened to [colour]. */
    fun keepTexture(
        source: ModelData,
        osrsTexture: Int,
        localTexture: Int,
        colour: (Int) -> Int,
    ): ModelData {
        val types = source.texMappingType
        check(types == null || types.all { it.toInt() == 0 }) { "model has non-planar texture spaces" }
        val out = ModelData(source.vertexCount, source.faceCount, source.texSpaceCount)
        source.vertexX.copyInto(out.vertexX)
        source.vertexY.copyInto(out.vertexY)
        source.vertexZ.copyInto(out.vertexZ)
        source.faceA.copyInto(out.faceA)
        source.faceB.copyInto(out.faceB)
        source.faceC.copyInto(out.faceC)
        source.faceColour.copyInto(out.faceColour)
        val texture = ShortArray(source.faceCount) { -1 }
        val space = ByteArray(source.faceCount) { -1 }
        source.faceTexture!!.forEachIndexed { i, t ->
            when (t.toInt()) {
                -1 -> Unit
                osrsTexture -> {
                    texture[i] = localTexture.toShort()
                    space[i] = source.faceTexSpace?.get(i) ?: -1
                }
                else -> out.faceColour[i] = colour(t.toInt() and 0xFFFF).toShort()
            }
        }
        out.faceTexture = texture
        out.faceTexSpace = space
        out.texMappingType = types?.copyOf()
        out.texSpaceDefA = source.texSpaceDefA?.copyOf()
        out.texSpaceDefB = source.texSpaceDefB?.copyOf()
        out.texSpaceDefC = source.texSpaceDefC?.copyOf()
        out.shadingType = source.shadingType?.copyOf()
        out.facePriority = source.facePriority?.copyOf()
        out.globalPriority = source.globalPriority
        out.faceAlpha = source.faceAlpha?.copyOf()
        out.faceLabel = source.faceLabel?.copyOf()
        out.vertexLabel = source.vertexLabel?.copyOf()
        return out
    }
}
