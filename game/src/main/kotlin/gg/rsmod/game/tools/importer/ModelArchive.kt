package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Where a model id lives (N462, 2026-11-06; owner "structurele fix voor de tekorte modelnummers, dit hadden we vaker").
 *
 * Archive 7 holds one group per model id and is full: 65,536 groups, and both the rev-667 disk sector header and the JS5 request
 * carry a 16-bit group id. Model ids [EXT_BASE]..[MAX_MODEL_ID] therefore live in a second archive, [MODELS_EXT] (37), as group
 * `id - EXT_BASE`. The 78 client routes every MODELS lookup of such an id there (`js5.overflow`), and the file server's version table
 * lists archive 37 automatically once it exists, so the JS5 protocol, the disk format and the file server stay unchanged.
 *
 * Definitions address an id above 0xFFFF through the wide (g4) model opcodes the 78 client decodes: loc 241 (= opcode 1),
 * npc 241 (= 1) and 242 (= 60), obj 240 (target opcode + g4), idk 242 (= 2) and 240 (head slot + g4), spotanim 241 (= 1).
 * An id <= 0xFFFF keeps the original 16-bit opcode, so every existing definition and every old cache stays byte-identical.
 *
 * Tools keep writing models as `CacheMutation(MODELS, modelId, 0, ...)`: [CacheMutation] maps an id >= [EXT_BASE] to archive 37 itself.
 */
object ModelArchive {
    const val MODELS = 7
    const val MODELS_EXT = 37
    const val EXT_BASE = 65536
    const val MAX_MODEL_ID = EXT_BASE * 2 - 1

    /** Wide model opcodes (g4 ids) of the 78 client, see the class doc. */
    const val LOC_WIDE_MODELS = 241
    const val NPC_WIDE_MODELS = 241
    const val NPC_WIDE_HEADS = 242
    const val OBJ_WIDE_MODEL = 240
    const val IDK_WIDE_HEAD = 240
    const val IDK_WIDE_MODELS = 242
    const val SPOT_WIDE_MODEL = 241

    /** Item opcodes whose single u16 field is a model id - the targets [OBJ_WIDE_MODEL] can set. */
    val OBJ_MODEL_OPCODES = setOf(1, 23, 24, 25, 26, 78, 79, 90, 91, 92, 93)

    fun isWide(modelId: Int) = modelId > 0xFFFF

    /** The archive a mutation of (index, group) really addresses: model ids from [EXT_BASE] up move to [MODELS_EXT]. */
    fun indexOf(
        indexId: Int,
        groupId: Int,
    ) = if (indexId == MODELS && groupId >= EXT_BASE) MODELS_EXT else indexId

    fun groupOf(
        indexId: Int,
        groupId: Int,
    ) = if (indexId == MODELS && groupId >= EXT_BASE) groupId - EXT_BASE else groupId

    /** Every model id physically present: archive 7 groups plus archive 37 groups + [EXT_BASE]. */
    fun physicalIds(library: CacheLibrary): Set<Int> {
        val ids = library.index(MODELS).archiveIds().toMutableSet()
        if (library.exists(MODELS_EXT)) library.index(MODELS_EXT).archiveIds().forEach { ids += it + EXT_BASE }
        return ids
    }

    /** The bytes of a local model, from whichever archive holds it; null when absent. */
    fun read(
        library: CacheLibrary,
        modelId: Int,
    ): ByteArray? {
        val index = indexOf(MODELS, modelId)
        if (!library.exists(index)) return null
        return library.data(index, groupOf(MODELS, modelId), 0)
    }

    /**
     * Creates archive 37 with archive 7's table settings when a cache does not have it yet. displee appends an index as
     * `last().id + 1`, so this refuses unless the cache ends at 36 (archive 36 VIDEOS exists, empty, in the 667 caches).
     */
    fun ensureExtIndex(library: CacheLibrary) {
        if (library.exists(MODELS_EXT)) return
        val last = library.last()?.id
        check(last == MODELS_EXT - 1) { "cannot create model archive $MODELS_EXT: the cache's last index is $last, not ${MODELS_EXT - 1}" }
        val models = library.index(MODELS)
        val created = library.createIndex(models.compressionType, models.version, 0, models.isNamed(), false)
        check(created.id == MODELS_EXT) { "displee created index ${created.id} instead of $MODELS_EXT" }
    }
}
