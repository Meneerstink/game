package gg.rsmod.plugins.api.ext

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.tools.importer.ModernModelDecoder
import gg.rsmod.plugins.api.cfg.FacialExpression
import java.util.concurrent.ConcurrentHashMap

/**
 * Which head rig an npc's chathead models are built for, read from the models themselves.
 *
 * Native revision-667 heads are skinned for the HD head rig (base 2165, vertex labels 65 and up); OSRS-imported heads keep
 * the classic rig (base 82, labels 0-50). A head is classic when none of its vertex labels lies above [CLASSIC_MAX_LABEL]
 * (label 255 is the "no group" marker on both). A head whose models cannot be read is treated as HD, the native default.
 * See [FacialExpression] for the measurements.
 */
object ChatheadRig {
    private const val MODEL_INDEX = 7
    private const val CLASSIC_MAX_LABEL = 50
    private const val NO_GROUP = 255

    private val classic = ConcurrentHashMap<Int, Boolean>()

    fun isClassic(
        world: World,
        npcId: Int,
    ): Boolean =
        classic.getOrPut(npcId) {
            val heads = world.definitions.getNullable(NpcDef::class.java, npcId)?.chatheadModels ?: return@getOrPut false
            val labels =
                heads.flatMap { model ->
                    val bytes = runCatching { world.filestore.data(MODEL_INDEX, model, 0) }.getOrNull() ?: return@flatMap emptyList()
                    runCatching { ModernModelDecoder.decode(bytes, flattenTextures = true).vertexLabel?.toList() }.getOrNull() ?: emptyList()
                }.filter { it != NO_GROUP }
            labels.isNotEmpty() && labels.all { it <= CLASSIC_MAX_LABEL }
        }
}

/** The pose [expression] plays on [npcId]'s own head rig. */
fun FacialExpression.chatheadAnimation(
    world: World,
    npcId: Int,
): Int = if (ChatheadRig.isClassic(world, npcId)) classic else hd
