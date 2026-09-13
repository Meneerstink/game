package gg.rsmod.game.model.varp

import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.util.BitManipulation
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet

/**
 * Holds a set of varps.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class VarpSet(
    varpIds: Set<Int>,
) {
    val maxVarps = varpIds.size

    private val varps =
        mutableMapOf<Int, Varp>().apply {
            for (i in varpIds.sorted()) {
                put(i, Varp(id = i, state = 0))
            }
        }

    /**
     * A collection of dirty varps which will be sent to the client on the next cycle.
     * This collection should be used only if the revision you are trying to
     * support uses them on the client.
     */
    private val dirty = ShortOpenHashSet(varps.size)

    operator fun get(id: Int): Varp = varps[id]!!

    fun getState(id: Int): Int = varps[id]!!.state

    fun setState(
        id: Int,
        state: Int,
    ): VarpSet {
        varps[id]!!.state = state
        if (id < 10000) {
            // Ids above 10000 are custom-made, and should not be transmitted to the client
            dirty.add(id.toShort())
        }
        return this
    }

    /**
     * Get the bits ranging from [startBit] to [endBit] in the [Varp] associated
     * with [id].
     *
     * @param id
     * The [Varp] id.
     *
     * @param startBit
     * The start of the bits to get the value from.
     *
     * @param endBit
     * The end of the bits to get the value from.
     */
    fun getBit(
        id: Int,
        startBit: Int,
        endBit: Int,
    ): Int = BitManipulation.getBit(getState(id), startBit, endBit)

    /**
     * Set the bits ranging from [startBit] to [endBit] to equal [value].
     * This can help store up to [32] possible values per [Varp], due to
     * [Varp.state] being a 32-bit integer.
     *
     * @param id
     * The id of the [Varp].
     *
     * @param startBit
     * The start of the bits to occupy with value.
     *
     * @param endBit
     * The end of the bits that can be occupied by value.
     *
     * @param value
     * The value that will be stored from [startBit] to [endBit].
     *
     */
    fun setBit(
        id: Int,
        startBit: Int,
        endBit: Int,
        value: Int,
    ): VarpSet {
        return setState(id, BitManipulation.setBit(getState(id), startBit, endBit, value))
    }

    fun isDirty(id: Int): Boolean {
        return dirty.contains(id.toShort())
    }

    fun clean() {
        dirty.clear()
    }

    fun setVarbit(
        world: World,
        id: Int,
        value: Int,
    ) {
        val def = world.definitions.get(VarbitDef::class.java, id)
        // RCV-012 B6: a value outside the varbit's bit range is silently masked (e.g. -78 or 434 in an 8-bit field reads
        // back as 178). Trace every such write, for every varbit, with the writer's frame.
        val max = gg.rsmod.util.DataConstants.BIT_SIZES[def.endBit - def.startBit]
        if (value < 0 || value > max) {
            gg.rsmod.game.model.AvTrace.log {
                "varbit overflow id=$id value=$value bits=${def.startBit}..${def.endBit} max=$max stored=${value and max} " +
                    "at=${Throwable().stackTrace.drop(1).take(3).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }}"
            }
        }
        setBit(def.varp, def.startBit, def.endBit, value)
    }

    fun getVarbit(
        world: World,
        id: Int,
    ): Int {
        val def = world.definitions.get(VarbitDef::class.java, id)
        return getBit(def.varp, def.startBit, def.endBit)
    }

    fun getAll(): List<Varp> = varps.values.toList()
}
