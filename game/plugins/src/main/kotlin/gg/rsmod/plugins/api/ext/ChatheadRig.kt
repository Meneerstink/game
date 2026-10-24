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

/**
 * N257 (owner 2026-10-24: the Loyalty store pets' chat heads were "te klein" and their animations "kloppen niet"): the 667 pets
 * (kittens, puppies, baby dragons, the baby monkey ...) have creature heads, neither the human HD head nor the OSRS one. The human
 * poses deformed them and without a pose they stay tiny. The cache's own creature chathead sequence - enum 1276 / 1275's default,
 * the Follower Details route of every familiar (SummoningChatheadAnimations) - frames and animates every one of them (checked in
 * the client for the 25 species, screens\N257\anim). Sad poses take the sad one.
 */
object CreatureChathead {
    const val TALK = 8373
    const val SAD_TALK = 8374

    private val SAD =
        setOf(
            FacialExpression.SAD, FacialExpression.SAD_2, FacialExpression.DISTRESSED, FacialExpression.NEARLY_CRYING, FacialExpression.TEARY,
            FacialExpression.UPSET, FacialExpression.CRYING, FacialExpression.WAILING, FacialExpression.AFRAID, FacialExpression.SCARED,
            FacialExpression.REALLY_SAD, FacialExpression.DEPRESSED, FacialExpression.WORRIED, FacialExpression.CHILD_SAD,
        )

    /** A 667 pet follower ([gg.rsmod.plugins.content.skills.summoning.Pets] classic table). */
    fun isCreaturePet(npcId: Int): Boolean = gg.rsmod.plugins.content.skills.summoning.Pets.variantOfNpc(npcId)?.classic == true

    fun animation(expression: FacialExpression): Int = if (expression in SAD) SAD_TALK else TALK
}

/** The pose [expression] plays on [npcId]'s own head rig. */
fun FacialExpression.chatheadAnimation(
    world: World,
    npcId: Int,
): Int =
    when {
        CreatureChathead.isCreaturePet(npcId) -> CreatureChathead.animation(this)
        ChatheadRig.isClassic(world, npcId) -> classic
        else -> hd
    }
