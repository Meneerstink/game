package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N462: the ToA windows ported by OsrsInterfacePortTool (OSRS groups 481/482/771-778 -> 667 interfaces, OSRS component N = component N,
 * extra components after them) and the 667 ids of the OSRS sprites they use. Read from data/cfg/toa/interfaces.tsv, which the tool
 * writes in the same transaction as the cache - no id is guessed here.
 */
object ToaInterfaces {
    private val interfaces = HashMap<Int, Int>()
    private val sprites = HashMap<Int, Int>()

    init {
        val file = File(RspsPaths.data("cfg/toa/interfaces.tsv"))
        if (file.isFile) {
            file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val f = line.split('\t')
                when (f[0]) {
                    "interface" -> interfaces[f[1].toInt()] = f[2].toInt()
                    "sprite" -> sprites[f[1].toInt()] = f[2].toInt()
                }
            }
        }
    }

    /** The 667 interface of an OSRS ToA group, or null while the windows are not imported (callers fall back / do nothing). */
    fun of(osrsGroup: Int): Int? = interfaces[osrsGroup]

    fun sprite(osrsSprite: Int): Int? = sprites[osrsSprite]

    const val HUD = 481
    const val SUMMARY = 482
    const val REWARDS = 771
    const val PARTY_LIST = 772
    const val LOBBY = 773
    const val PARTY_DETAILS = 774
    const val STATISTICS = 775
    const val INVOCATIONS = 776
    const val SUPPLIES = 777
    const val BAG = 778
}
