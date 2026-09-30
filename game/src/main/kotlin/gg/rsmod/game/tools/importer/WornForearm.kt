package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.Appearance

/**
 * The lower forearm under OSRS bodies that hide the player's arms kit.
 *
 * Owner 2026-09-30 (`foto\invisible.png`, `fdd.png`): with Eclipse, Crystal, Masori, ... the forearm between the sleeve and
 * the hand was not drawn and the gloves floated below it. Measured per vertex label on both caches (fine units, 2026-09-30):
 *  * OSRS splits the arm at mid-forearm. The arms kit (idk 26, model 26632) and every body that hides it end the forearm
 *    (labels 19/22) at y -456; the HANDS kit (idk 33, model 176; bracers idk 34, model 10218) draws the lower forearm
 *    -456..-412 and the hand.
 *  * Rev 667 splits it at the wrist. The arms kit (idk 26, model 151) runs the forearm down to -416; the hands kit (idk 33,
 *    model 176) is only a wrist ring (-424..-416) and the hand - no 667 hands kit has a forearm.
 * An OSRS body that hides the 667 arms kit therefore leaves the male lower forearm (-483..-416 after [OsrsBodyFrame]) with
 * nothing drawn: 33 of the 34 imported arm-hiding bodies, with or without an OSRS arms model of their own (Eclipse, Crystal,
 * Masori, Ancestral, Dragon chainbody (g), Infinity, ...). The female bodies end at -467 or lower, past the end of the 667
 * female arm (332: -480), so they have no gap.
 *
 * The fix, per item and gender: when the item hides the arms ([OsrsItemImportTool.removesArms]) and its worn meshes do not
 * reach as far down the forearm as the 667 arm they replace, the item also wears the bare forearm of the 667 default arms
 * kit ([Appearance] defaults: idk 26 male / 61 female): that kit's faces whose three vertices all carry forearm labels,
 * unchanged - skin colour 4550, so the player's skin colour applies (`PlayerModel` recolours the merged mesh), labels 19/22,
 * so it moves with the forearm in every 667 animation. ADAPTED: OSRS draws the player's own hands style there (bare or
 * bracers); the 667 hands kits have no forearm, so the bare 667 forearm is the closest the 667 kits allow.
 */
object WornForearm {
    /** Left / right forearm (elbow to wrist) - the same labels in the OSRS and the rev-667 player skeleton. */
    val FOREARM_LABELS = setOf(19, 22)

    /** Model role of the forearm piece in the asset map; its `upstream_id` is the rev-667 arms kit model it is cut from. */
    const val ROLE = "rev667_forearm"

    private const val ARMS_LOOK = 3

    /** Item opcodes of the worn meshes (ObjType manwear/manwear2/manwear3, womanwear/womanwear2/womanwear3). */
    val MALE_WORN_OPS = listOf(23, 24, 78)
    val FEMALE_WORN_OPS = listOf(25, 26, 79)

    /** The piece for one gender: the kit model it is cut from, where that kit's forearm ends, and the encoded piece. */
    class Piece(val female: Boolean, val kitModel: Int, val kitForearmEnd: Int, val bytes: ByteArray) {
        val sourceIdentity get() = "upstream_model:$kitModel"
        val wornOps get() = if (female) FEMALE_WORN_OPS else MALE_WORN_OPS
    }

    /** Lowest point (largest y, fine units) of the forearm across [meshes], or null when none of them has a forearm vertex. */
    fun forearmEnd(meshes: List<ModelData>): Int? =
        meshes.flatMap { mesh ->
            val labels = mesh.vertexLabel ?: return@flatMap emptyList()
            val scale = if (mesh.version < OsrsBodyFrame.FRAME_VERSION) 4 else 1
            (0 until mesh.vertexCount).filter { labels[it] in FOREARM_LABELS }.map { mesh.vertexY[it] * scale }
        }.maxOrNull()

    /** Whether worn [meshes] that hide the arms kit leave part of the forearm the kit drew (it ended at [kitForearmEnd]) bare. */
    fun leavesForearmGap(
        meshes: List<ModelData>,
        kitForearmEnd: Int,
    ): Boolean = (forearmEnd(meshes) ?: Int.MIN_VALUE) < kitForearmEnd

    /** The faces of [kit] whose vertices all carry forearm labels, as a mesh of their own (unused vertices dropped). */
    fun cut(kit: ModelData): ModelData {
        val labels = requireNotNull(kit.vertexLabel) { "arms kit mesh has no vertex labels" }
        val faces = (0 until kit.faceCount).filter { f -> listOf(kit.faceA[f], kit.faceB[f], kit.faceC[f]).all { labels[it] in FOREARM_LABELS } }
        require(faces.isNotEmpty()) { "arms kit mesh has no forearm faces" }
        faces.forEach { f -> require((kit.faceTexture?.get(f)?.toInt() ?: -1) == -1) { "forearm face $f is textured" } }
        val vertices = faces.flatMap { listOf(kit.faceA[it], kit.faceB[it], kit.faceC[it]) }.distinct().sorted()
        val index = vertices.withIndex().associate { (new, old) -> old to new }
        val out = ModelData(vertices.size, faces.size, 0)
        vertices.forEachIndexed { new, old ->
            out.vertexX[new] = kit.vertexX[old]
            out.vertexY[new] = kit.vertexY[old]
            out.vertexZ[new] = kit.vertexZ[old]
        }
        out.vertexLabel = IntArray(vertices.size) { labels[vertices[it]] }
        faces.forEachIndexed { new, old ->
            out.faceA[new] = index.getValue(kit.faceA[old])
            out.faceB[new] = index.getValue(kit.faceB[old])
            out.faceC[new] = index.getValue(kit.faceC[old])
            out.faceColour[new] = kit.faceColour[old]
        }
        kit.shadingType?.let { src -> out.shadingType = ByteArray(faces.size) { src[faces[it]] } }
        kit.facePriority?.let { src -> out.facePriority = ByteArray(faces.size) { src[faces[it]] } }
        kit.faceAlpha?.let { src -> out.faceAlpha = ByteArray(faces.size) { src[faces[it]] } }
        kit.faceLabel?.let { src -> out.faceLabel = IntArray(faces.size) { src[faces[it]] } }
        out.globalPriority = kit.globalPriority
        // Fine units already (kit meshes are version 15); 13 is the fine-unit version this pipeline writes (no textures here).
        require(kit.version >= OsrsBodyFrame.FRAME_VERSION) { "arms kit mesh is version ${kit.version}, not fine units" }
        out.version = OsrsBodyFrame.FRAME_VERSION
        return out
    }

    /** The default arms kit's single model for a gender, read from the identity kit in [library]. */
    fun armsKitModel(
        library: CacheLibrary,
        female: Boolean,
    ): Int {
        val kit = (if (female) Appearance.DEFAULT_FEMALE else Appearance.DEFAULT).looks[ARMS_LOOK]
        val data = requireNotNull(library.data(Rev667ModelReferenceWalkers.CONFIG_INDEX, Rev667ModelReferenceWalkers.IDK_GROUP, kit)) { "idk $kit missing" }
        val models = mutableListOf<Int>()
        Rev667ModelReferenceWalkers.walkIdk(kit, data) { id, role -> if (role == "idk_model") models += id }
        return models.singleOrNull() ?: error("arms kit idk $kit has models $models, expected one")
    }

    /** Both pieces, cut from the default arms kits in [library]; the encoding is checked by decoding it back. */
    fun pieces(library: CacheLibrary): List<Piece> =
        listOf(false, true).map { female ->
            val kitModel = armsKitModel(library, female)
            val kit = Rev667ModelDecoder.decode(requireNotNull(library.data(ModelConvertTool.MODEL_INDEX, kitModel, 0)) { "model $kitModel missing" }, geometryOnly = true)
            val piece = cut(kit)
            val bytes = Rev667ModelEncoder.encode(piece)
            val differences = ModelConvertTool.compare(piece, Rev667ModelDecoder.decode(bytes))
            check(differences.isEmpty()) { "forearm piece of model $kitModel does not round-trip: $differences" }
            Piece(female, kitModel, forearmEnd(listOf(kit)) ?: error("model $kitModel has no forearm"), bytes)
        }

    /**
     * The worn-mesh opcode the [piece] goes into on an item whose current worn-mesh opcodes are [usedOps] (its first free
     * wear model 2 / 3), or null when the item's own [meshes] for that gender already cover the forearm.
     */
    fun opcodeFor(
        piece: Piece,
        usedOps: Set<Int>,
        meshes: List<ModelData>,
    ): Int? {
        if (!leavesForearmGap(meshes, piece.kitForearmEnd)) return null
        return piece.wornOps.drop(1).firstOrNull { it !in usedOps } ?: error("no free wear model opcode for the forearm (used: $usedOps)")
    }
}
