package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO

/**
 * The six Deadman emblems and their six Deadman lamps as cache content (owner 2026-09-25, `DeadmanEmblem` on the server).
 *
 * One transaction on both caches writes, per tier: the plate texture (sprite, materials row, texture program - copied
 * from the alpha-tested template the 78 banner uses), the emblem model ([DeadmanEmblemArt.model]), the emblem item
 * (cloned from the OSRS "Archaic emblem (tier 5)" import 23857: its name, model, icon camera, cost; the recolour table and
 * the Grand Exchange flag dropped; options "Inspect" / "Destroy") and the lamp item (cloned from the Antique lamp 4447,
 * option "Rub"). Items are appended directly after the current last item id, models take the proven-free ids above the
 * 78 banner models.
 *
 * Usage: `DeadmanEmblemTool probe|preview|plan|apply` (apply needs the servers stopped; CacheTransaction journals a
 * backup). `preview` only writes PNGs to `tools/deadman-emblem`.
 */
object DeadmanEmblemTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val OUT_DIR = "C:/RSPS/tools/deadman-emblem"
    private const val IDS_FILE = "$OUT_DIR/emblem_ids.properties"
    private const val MODEL_INDEX = 7
    private const val TEMPLATE_TEXTURE = 12
    private const val COL_MIPMAP = 12
    private const val COL_WRAP_U = 13
    private const val COL_WRAP_V = 14
    private const val COL_AVERAGE = 7

    /** Donors: the imported OSRS archaic emblem (a correctly rendering emblem item) and the Antique lamp. */
    private const val EMBLEM_DONOR = 23857
    private const val EMBLEM_DONOR_MODEL = 64065
    private const val LAMP_DONOR = 4447

    /** First model id after the 78 banner's 65522..65525 (ModelIdAllocator's proven-free range ends at 65535). */
    private const val FIRST_MODEL = 65526

    /** Inventory icon camera (zoom, pitch, yaw, x offset, y offset) - tuned with the preview. */
    const val ZOOM = 1400
    const val XAN = 0
    const val YAN = 60
    const val XOF = 0
    const val YOF = 0

    /** Ground size: model resize (128 = 1:1), applied on all three axes. */
    const val RESIZE = 72

    val NAMES = (1..6).map { "Deadman emblem (tier $it)" }
    val LAMP_NAMES = (1..6).map { "Deadman lamp (tier $it)" }

    private fun encodeShort(v: Int) = if (v < 0) v + 65536 else v

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode in setOf("probe", "preview", "plan", "apply")) { "Usage: probe|preview|plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        try {
            val donorModel = Rev667ModelDecoder.decode(library.data(MODEL_INDEX, EMBLEM_DONOR_MODEL, 0) ?: error("donor model missing"))
            val share = DeadmanEmblemArt.clockwiseOutwardShare(donorModel)
            println("DONOR_MODEL $EMBLEM_DONOR_MODEL bounds x=${donorModel.vertexX.minOrNull()}..${donorModel.vertexX.maxOrNull()} y=${donorModel.vertexY.minOrNull()}..${donorModel.vertexY.maxOrNull()} z=${donorModel.vertexZ.minOrNull()}..${donorModel.vertexZ.maxOrNull()} faces=${donorModel.faceCount} clockwiseOutward=${"%.2f".format(share)}")
            // The renderer culls one winding; an outward-facing closed model tells which (the share is near 1 or near 0).
            check(share > 0.8 || share < 0.2) { "donor winding inconclusive ($share)" }
            DeadmanEmblemArt.FRONT_CLOCKWISE = share < 0.5
            println("FRONT_CLOCKWISE=${DeadmanEmblemArt.FRONT_CLOCKWISE}")
            if (mode == "probe") return
            if (mode == "preview") return preview()
            build(library, apply = mode == "apply")
        } finally {
            library.close()
        }
    }

    /**
     * The clockwise-outward share of the donor refers to the model's (x, y down, z) axes; seen from outside a face whose
     * normal (b-a)x(c-a) points outwards is counter-clockwise on screen in a y-down, z-away view. Hence front faces are
     * clockwise on screen exactly when the donor's normals point inwards under that cross product.
     */
    private fun preview() {
        File(OUT_DIR).mkdirs()
        val strip = BufferedImage(36 * 6 * 8, 32 * 8 + 32 * 2 + 8, BufferedImage.TYPE_INT_ARGB)
        val g = strip.createGraphics()
        g.color = Color(0x3e3529)
        g.fillRect(0, 0, strip.width, strip.height)
        for (tier in 1..6) {
            val texture = DeadmanEmblemArt.texture(tier)
            ImageIO.write(texture, "png", File("$OUT_DIR/emblem_t${tier}_texture.png"))
            val model = DeadmanEmblemArt.model(tier, 0)
            val big = DeadmanEmblemArt.previewIcon(model, texture, ZOOM, XAN, YAN, XOF, YOF, 8, RESIZE)
            val small = DeadmanEmblemArt.previewIcon(model, texture, ZOOM, XAN, YAN, XOF, YOF, 1, RESIZE)
            ImageIO.write(big, "png", File("$OUT_DIR/emblem_t${tier}_icon_x8.png"))
            ImageIO.write(small, "png", File("$OUT_DIR/emblem_t${tier}_icon.png"))
            g.drawImage(big, (tier - 1) * 36 * 8, 0, null)
            g.drawImage(small, (tier - 1) * 36 * 8 + 4, 32 * 8 + 4, 72, 64, null)
            g.drawImage(small, (tier - 1) * 36 * 8 + 90, 32 * 8 + 20, null)
            println("TIER $tier model vertices=${model.vertexCount} faces=${model.faceCount} y=${model.vertexY.minOrNull()}..${model.vertexY.maxOrNull()}")
        }
        g.dispose()
        ImageIO.write(strip, "png", File("$OUT_DIR/emblem_preview_strip.png"))
        println("PREVIEW written to $OUT_DIR")
    }

    private fun build(library: CacheLibrary, apply: Boolean) {
        val idsFile = File(IDS_FILE)
        val ids = Properties().apply { if (idsFile.exists()) idsFile.inputStream().use { load(it) } }
        val mutations = ArrayList<CacheMutation>()

        fun fresh(index: Int) = (library.index(index).archiveIds().maxOrNull() ?: 0) + 1
        fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
            val current = library.data(index, group, file)
            if (current != null && current.contentEquals(bytes)) return
            mutations += CacheMutation(index, group, file, bytes, label, current?.let { CacheItemProbeTool.sha1(it) })
        }

        val materials = OsrsTextureImportTool.Materials.decode(library.data(OsrsTextureImportTool.INDEX_MATERIALS, 0, 0) ?: error("materials missing"))
        val firstSprite = ids.getProperty("sprite1")?.toInt() ?: fresh(OsrsTextureImportTool.INDEX_SPRITES)
        val firstTexture = ids.getProperty("texture1")?.toInt() ?: materials.present.size
        val firstModel = ids.getProperty("model1")?.toInt() ?: FIRST_MODEL
        val nextItem = CacheItemProbeTool.maxContiguousItemId(library) + 1
        val firstEmblem = ids.getProperty("emblem1")?.toInt() ?: nextItem
        val firstLamp = ids.getProperty("lamp1")?.toInt() ?: (firstEmblem + 6)
        check(firstModel + 5 <= 0xFFFF) { "model ids do not fit the item model field" }
        if (!ids.containsKey("model1")) {
            for (m in firstModel until firstModel + 6) check(library.data(MODEL_INDEX, m, 0) == null) { "model $m is not free" }
        }
        if (!ids.containsKey("emblem1")) check(firstEmblem == nextItem) { "emblem ids must follow the last item" }
        println("IDS sprites=$firstSprite.. textures=$firstTexture.. models=$firstModel.. emblems=$firstEmblem.. lamps=$firstLamp..")

        // Textures: six sprites, six materials rows (one materials mutation), six texture programs.
        val template = materials.rows[TEMPLATE_TEXTURE] ?: error("template texture $TEMPLATE_TEXTURE has no metrics")
        val templateProgram = library.data(OsrsTextureImportTool.INDEX_TEXTURES, TEMPLATE_TEXTURE) ?: error("template program missing")
        val at = OsrsTextureImportTool.spriteParamOffset(templateProgram)
        var present = materials.present
        var rows = materials.rows
        val needed = firstTexture + 6
        if (present.size < needed) {
            present = present.copyOf(needed)
            rows = rows.copyOf(needed)
        }
        for (tier in 1..6) {
            val sprite = firstSprite + tier - 1
            val texture = firstTexture + tier - 1
            val image = DeadmanEmblemArt.texture(tier)
            File(OUT_DIR).mkdirs()
            ImageIO.write(image, "png", File("$OUT_DIR/emblem_t${tier}_texture.png"))
            put(OsrsTextureImportTool.INDEX_SPRITES, sprite, 0, StoreArtTool.encode(image), "Deadman emblem tier $tier plate sprite")
            val row = Array(template.size) { template[it].copyOf() }
            row[COL_WRAP_U][0] = 0
            row[COL_WRAP_V][0] = 0
            row[COL_MIPMAP][0] = 2
            val average = Banner78Art.averageHsl(image)
            row[COL_AVERAGE][0] = (average shr 8).toByte()
            row[COL_AVERAGE][1] = average.toByte()
            rows[texture] = row
            present[texture] = true
            put(
                OsrsTextureImportTool.INDEX_TEXTURES, texture, 0,
                templateProgram.copyOf().also { it[at] = (sprite ushr 8).toByte(); it[at + 1] = sprite.toByte() },
                "Deadman emblem tier $tier texture program",
            )
        }
        put(OsrsTextureImportTool.INDEX_MATERIALS, 0, 0, OsrsTextureImportTool.Materials(present, rows).encode(), "materials rows $firstTexture..${firstTexture + 5} (Deadman emblems)")

        // Models.
        for (tier in 1..6) {
            val model = DeadmanEmblemArt.model(tier, firstTexture + tier - 1)
            val encoded = Rev667ModelEncoder.encode(model)
            val differences = ModelConvertTool.compare(model, Rev667ModelDecoder.decode(encoded))
            check(differences.isEmpty()) { "model tier $tier round trip: $differences" }
            put(MODEL_INDEX, firstModel + tier - 1, 0, encoded, "Deadman emblem tier $tier model (${model.vertexCount} vertices, ${model.faceCount} faces)")
        }

        // Items.
        val itemIndex = gg.rsmod.game.fs.ArchiveType.ITEM.id
        val emblemDonor = library.data(itemIndex, EMBLEM_DONOR ushr 8, EMBLEM_DONOR and 0xFF) ?: error("emblem donor missing")
        val lampDonor = library.data(itemIndex, LAMP_DONOR ushr 8, LAMP_DONOR and 0xFF) ?: error("lamp donor missing")
        for (tier in 1..6) {
            val emblem =
                ItemDefCodec.cloneWithOverrides(
                    emblemDonor,
                    stringOverrides = mapOf(2 to NAMES[tier - 1], 35 to "Inspect", 39 to "Destroy"),
                    shortOverrides = mapOf(
                        1 to firstModel + tier - 1, 4 to ZOOM, 5 to encodeShort(XAN), 6 to encodeShort(YAN),
                        7 to encodeShort(XOF), 8 to encodeShort(YOF), 110 to RESIZE, 111 to RESIZE, 112 to RESIZE,
                    ),
                    intOverrides = mapOf(12 to 1),
                    removedOpcodes = setOf(40, 41, 65, 97, 98, 121, 122),
                )
            val id = firstEmblem + tier - 1
            check(ItemDefCodec.readName(emblem) == NAMES[tier - 1])
            putItem(library, mutations, id, emblem, "item $id ${NAMES[tier - 1]}")
            val lamp = ItemDefCodec.cloneWithOverrides(lampDonor, stringOverrides = mapOf(2 to LAMP_NAMES[tier - 1]), removedOpcodes = setOf(97, 98, 121, 122))
            val lampId = firstLamp + tier - 1
            putItem(library, mutations, lampId, lamp, "item $lampId ${LAMP_NAMES[tier - 1]}")
        }

        mutations.forEach { println("MUTATION idx${it.indexId}/${it.groupId}/${it.fileId} ${it.label}") }
        ids.setProperty("sprite1", "$firstSprite"); ids.setProperty("texture1", "$firstTexture"); ids.setProperty("model1", "$firstModel")
        ids.setProperty("emblem1", "$firstEmblem"); ids.setProperty("lamp1", "$firstLamp")
        if (mutations.isEmpty()) return println("NOTHING_TO_DO")
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (!apply) return println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        File(OUT_DIR).mkdirs()
        idsFile.outputStream().use { ids.store(it, "Deadman emblem cache ids (DeadmanEmblemTool)") }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir}); ids -> $IDS_FILE")
    }

    private fun putItem(library: CacheLibrary, mutations: MutableList<CacheMutation>, id: Int, bytes: ByteArray, label: String) {
        val current = library.data(gg.rsmod.game.fs.ArchiveType.ITEM.id, id ushr 8, id and 0xFF)
        if (current != null && current.contentEquals(bytes)) return
        mutations += CacheMutation.item(id, bytes, label, current?.let { CacheItemProbeTool.sha1(it) })
    }
}
