package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The Grand Exchange home banners in the 78 house style (owner 2026-09-23: purple with gold, crown and "78"). The GE
 * banners are flat textured quads; their look is the texture's sprite, so repainting that sprite restyles every banner
 * of the home at once (gate facade, pillars, wall banners) without touching a model or a placement.
 *
 * Modes:
 *  * `probe <modelId...>` - texture ids used by each model, and each texture's sprite exported as PNG to [OUT_DIR];
 *  * `sprites <textureId...>` - export only;
 *  * `plan|apply <textureId>=<png> ...` - replace the sprite behind each texture with the PNG (same size) in both caches.
 */
object GeHomeBannerTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val OUT_DIR = "C:/RSPS/tools/ge-banner"

    private fun spriteOf(library: CacheLibrary, texture: Int): Int? {
        val program = library.data(OsrsTextureImportTool.INDEX_TEXTURES, texture) ?: return null
        if (program.size < 8 || program[2].toInt() != OsrsTextureImportTool.OP_SPRITE) return null
        return ((program[6].toInt() and 0xFF) shl 8) or (program[7].toInt() and 0xFF)
    }

    private fun export(library: CacheLibrary, texture: Int) {
        val sprite = spriteOf(library, texture) ?: return println("  TEXTURE $texture has no plain sprite op")
        val data = library.data(OsrsTextureImportTool.INDEX_SPRITES, sprite) ?: return println("  TEXTURE $texture sprite $sprite missing")
        val frame = FeroxMapSpriteProbeTool.decodeSprites(data).first()
        val image = BufferedImage(frame.w, frame.h, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, frame.w, frame.h, frame.argb, 0, frame.w)
        val out = File(OUT_DIR, "texture_${texture}_sprite_$sprite.png")
        out.parentFile.mkdirs()
        ImageIO.write(image, "png", out)
        println("  TEXTURE $texture sprite=$sprite size=${frame.w}x${frame.h} -> $out")
    }

    /** Byte offset of the face texture block (u16 per face, texture + 1), or null when the mesh has none. */
    fun texturePointer(data: ByteArray): Int? = texturePointerInternal(data)

    /** Face colours (HSL16) and face textures of a new-format 667 mesh, read without decoding its geometry. */
    fun colourAndTextureBlocks(data: ByteArray): Pair<IntArray, IntArray?> {
        val h = ModelBuffer(data, data.size - 23)
        val vertexCount = h.u16()
        val faceCount = h.u16()
        val texSpaceCount = h.u8()
        val globalFlags = h.u8()
        if (globalFlags and 0x8 != 0) {
            h.position -= 7
            h.u8()
            h.position += 6
        }
        val priorityFlag = h.u8()
        val faceAlphaFlag = h.u8()
        val faceGroupFlag = h.u8()
        val faceTextureFlag = h.u8()
        val vertexLabelFlag = h.u8()
        h.u16()
        h.u16()
        h.u16()
        val faceDataSize = h.u16()
        val texSpaceSize = h.u16()
        var ptr = texSpaceCount + vertexCount
        if (globalFlags and 0x1 != 0) ptr += faceCount
        ptr += faceCount
        if (priorityFlag == 255) ptr += faceCount
        if (faceGroupFlag == 1) ptr += faceCount
        if (vertexLabelFlag == 1) ptr += vertexCount
        if (faceAlphaFlag == 1) ptr += faceCount
        ptr += faceDataSize
        val texturePtr = ptr
        if (faceTextureFlag == 1) ptr += faceCount * 2
        ptr += texSpaceSize
        lastTexturePointer = if (faceTextureFlag == 1) texturePtr else null
        val colours = ModelBuffer(data, ptr).let { b -> IntArray(faceCount) { b.u16() } }
        val textures = if (faceTextureFlag == 1) ModelBuffer(data, texturePtr).let { b -> IntArray(faceCount) { b.u16() - 1 } } else null
        return colours to textures
    }

    private var lastTexturePointer: Int? = null

    private fun texturePointerInternal(data: ByteArray): Int? {
        colourAndTextureBlocks(data)
        return lastTexturePointer
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: error("Usage: probe <modelId...> | sprites <textureId...> | plan|apply <textureId>=<png> ...")
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            when (mode) {
                "probe" -> args.drop(1).map { it.toInt() }.forEach { modelId ->
                    val data = library.data(7, modelId, 0) ?: return@forEach println("MODEL $modelId missing")
                    val (colourBlock, textureBlock) = colourAndTextureBlocks(data)
                    val textures = textureBlock?.filter { it >= 0 }?.groupingBy { it }?.eachCount()
                    val colours = colourBlock.indices.filter { textureBlock == null || textureBlock[it] < 0 }.groupingBy { colourBlock[it] }.eachCount()
                    println("MODEL $modelId faces=${colourBlock.size} textures=$textures")
                    println("  colours(untextured) ${colours.entries.sortedByDescending { it.value }.joinToString { "${it.key}x${it.value}" }}")
                    textures?.keys?.forEach { export(library, it) }
                }
                "sprites" -> args.drop(1).map { it.toInt() }.forEach { export(library, it) }
                "art" -> {
                    File(OUT_DIR).mkdirs()
                    ImageIO.write(Banner78Art.cloth(), "png", File(OUT_DIR, "banner78_cloth.png"))
                    ImageIO.write(Banner78Art.texture(), "png", File(OUT_DIR, "banner78_texture.png"))
                    println("ART -> $OUT_DIR")
                }
                "locmodels" -> args.drop(1).map { it.toInt() }.forEach { loc ->
                    val def = Rev667LocType.decode(loc, library.data(16, loc ushr 8, loc and 0xFF) ?: return@forEach println("LOC $loc missing"))
                    def.allModels.forEach { m ->
                        val data = library.data(7, m, 0) ?: return@forEach
                        val d = runCatching { Rev667ModelDecoder.decode(data) }.getOrNull()
                        println("LOC $loc '${def.name}' model $m " + (d?.describe()?.lines()?.filter { it.startsWith("bounds") || it.startsWith("vertexCount") }?.joinToString(" ") ?: "undecodable"))
                    }
                }
                "programs" -> args.drop(1).map { it.toInt() }.forEach { texture ->
                    val program = library.data(OsrsTextureImportTool.INDEX_TEXTURES, texture) ?: return@forEach println("TEXTURE $texture missing")
                    println("TEXTURE $texture bytes=${program.size} ${program.take(64).joinToString(" ") { "%02x".format(it) }}")
                    // Every "39 xx 01 00 hi lo" run (sprite op, one param, param 0 = sprite id) inside the program.
                    for (i in 0 until program.size - 5) {
                        if (program[i].toInt() == OsrsTextureImportTool.OP_SPRITE && program[i + 2].toInt() == 1 && program[i + 3].toInt() == 0) {
                            val sprite = ((program[i + 4].toInt() and 0xFF) shl 8) or (program[i + 5].toInt() and 0xFF)
                            val data = library.data(OsrsTextureImportTool.INDEX_SPRITES, sprite)
                            val frame = data?.let { runCatching { FeroxMapSpriteProbeTool.decodeSprites(it).first() }.getOrNull() }
                            println("  sprite op at $i -> sprite $sprite ${frame?.let { "${it.w}x${it.h}" } ?: "(absent)"}")
                            if (frame != null) {
                                val img = BufferedImage(frame.w, frame.h, BufferedImage.TYPE_INT_ARGB)
                                img.setRGB(0, 0, frame.w, frame.h, frame.argb, 0, frame.w)
                                File(OUT_DIR).mkdirs()
                                ImageIO.write(img, "png", File(OUT_DIR, "texture_${texture}_sprite_$sprite.png"))
                            }
                        }
                    }
                }
                "sheet" -> {
                    // Every plain-sprite texture as a 64 px thumbnail with its id, 24 per row.
                    val cell = 72
                    val ids = (0 until 4000).filter { spriteOf(library, it) != null }
                    val sheet = BufferedImage(24 * cell, ((ids.size + 23) / 24) * cell, BufferedImage.TYPE_INT_RGB)
                    val g = sheet.createGraphics()
                    ids.forEachIndexed { i, texture ->
                        val data = library.data(OsrsTextureImportTool.INDEX_SPRITES, spriteOf(library, texture)!!) ?: return@forEachIndexed
                        val frame = runCatching { FeroxMapSpriteProbeTool.decodeSprites(data).first() }.getOrNull() ?: return@forEachIndexed
                        val img = BufferedImage(frame.w, frame.h, BufferedImage.TYPE_INT_ARGB)
                        img.setRGB(0, 0, frame.w, frame.h, frame.argb, 0, frame.w)
                        val x = (i % 24) * cell
                        val y = (i / 24) * cell
                        g.drawImage(img, x + 4, y, 64, 60, null)
                        g.color = java.awt.Color.WHITE
                        g.drawString("$texture", x + 4, y + 70)
                    }
                    g.dispose()
                    val out = File(OUT_DIR, "texture_sheet.png")
                    out.parentFile.mkdirs()
                    ImageIO.write(sheet, "png", out)
                    println("SHEET ${ids.size} textures -> $out")
                }
                "retex-plan", "retex-apply" -> {
                    // retex-* <fromTexture> <toTexture> <modelId...>: swap one texture id on every face of those models.
                    val from = args[1].toInt()
                    val to = args[2].toInt()
                    args.drop(3).map { it.toInt() }.forEach { modelId ->
                        val data = library.data(7, modelId, 0) ?: error("model $modelId missing")
                        val ptr = texturePointer(data) ?: error("model $modelId has no face textures")
                        val faces = colourAndTextureBlocks(data).first.size
                        val updated = data.copyOf()
                        var swapped = 0
                        for (i in 0 until faces) {
                            val at = ptr + i * 2
                            val t = (((updated[at].toInt() and 0xFF) shl 8) or (updated[at + 1].toInt() and 0xFF)) - 1
                            if (t == from) {
                                updated[at] = ((to + 1) shr 8).toByte()
                                updated[at + 1] = (to + 1).toByte()
                                swapped++
                            }
                        }
                        println("RETEX model $modelId: $swapped faces $from -> $to")
                        if (swapped > 0) mutations += CacheMutation(7, modelId, 0, updated, "GE home model $modelId: texture $from -> $to on $swapped faces", CacheItemProbeTool.sha1(data))
                    }
                }
                "plan", "apply" -> args.drop(1).forEach { pair ->
                    val (texture, png) = pair.split('=').let { it[0].toInt() to File(it[1]) }
                    val sprite = spriteOf(library, texture) ?: error("texture $texture has no plain sprite op")
                    val current = library.data(OsrsTextureImportTool.INDEX_SPRITES, sprite) ?: error("sprite $sprite missing")
                    val old = FeroxMapSpriteProbeTool.decodeSprites(current).first()
                    val image = ImageIO.read(png)
                    check(image.width == old.w && image.height == old.h) { "$png is ${image.width}x${image.height}, sprite $sprite is ${old.w}x${old.h}" }
                    val updated = OsrsTextureImportTool.opaqueSprite(image)
                    val back = FeroxMapSpriteProbeTool.decodeSprites(updated).first()
                    check(back.w == old.w && back.h == old.h) { "re-decode size mismatch" }
                    mutations += CacheMutation(OsrsTextureImportTool.INDEX_SPRITES, sprite, 0, updated, "GE home banner texture $texture sprite $sprite: 78 house style", CacheItemProbeTool.sha1(current))
                }
                else -> error("unknown mode $mode")
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) return
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode.endsWith("plan")) return println("PLAN_ONLY transaction=${tx.id} (nothing written)")
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
