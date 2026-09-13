package gg.rsmod.plugins.content.combat.attack

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.BasDef
import gg.rsmod.game.fs.def.NpcDef
import java.util.concurrent.ConcurrentHashMap

/**
 * RCV-005 root cause (owner 2026-09-13: "zamorak godwars heeft rare animaties"): Void is revision 634. Npcs
 * remodelled before revision 667 (K'ril Tsutsaroth, several God Wars minions) keep Void animation ids that were
 * made for their old skeleton; played on the 667 model they twist into nonsense. The donors disagree with each
 * other, so the rev-667 cache decides: an animation belongs to an npc only when it animates the same skeleton.
 *
 * Skeleton of an animation = the AnimBase id of its first frame (client `AnimFrameset`: frame file byte 0 is a
 * header, bytes 1-2 are the base id; frame id = frameset archive << 16 | file, cache index 0).
 * Skeleton of an npc = the skeleton of its body animation set's idle sequence (NpcType opcode 127 -> BASType).
 */
object AnimSkeletons {
    private const val FRAMES_INDEX = 0
    private const val UNKNOWN = -1

    private val animSkeletons = ConcurrentHashMap<Int, Int>()
    private val npcSkeletons = ConcurrentHashMap<Int, Int>()

    fun skeletonOfAnim(
        definitions: DefinitionSet,
        store: CacheLibrary,
        animId: Int,
    ): Int {
        if (animId < 0) return UNKNOWN
        return animSkeletons.getOrPut(animId) {
            val def = definitions.getNullable(AnimDef::class.java, animId) ?: return@getOrPut UNKNOWN
            val frame = def.frames.firstOrNull() ?: return@getOrPut UNKNOWN
            val data = store.data(FRAMES_INDEX, frame ushr 16, frame and 0xFFFF)
            if (data == null || data.size < 3) UNKNOWN else ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        }
    }

    fun skeletonOfNpc(
        definitions: DefinitionSet,
        store: CacheLibrary,
        npcId: Int,
    ): Int =
        npcSkeletons.getOrPut(npcId) {
            val npc = definitions.getNullable(NpcDef::class.java, npcId) ?: return@getOrPut UNKNOWN
            if (npc.basId < 0) return@getOrPut UNKNOWN
            val bas = definitions.getNullable(BasDef::class.java, npc.basId) ?: return@getOrPut UNKNOWN
            skeletonOfAnim(definitions, store, bas.idleAnimation())
        }

    /** True unless both skeletons are known and differ: an unknown skeleton is never used to reject an id. */
    fun fits(
        definitions: DefinitionSet,
        store: CacheLibrary,
        npcId: Int,
        animId: Int,
    ): Boolean {
        val npcSkeleton = skeletonOfNpc(definitions, store, npcId)
        val animSkeleton = skeletonOfAnim(definitions, store, animId)
        return npcSkeleton == UNKNOWN || animSkeleton == UNKNOWN || npcSkeleton == animSkeleton
    }
}
