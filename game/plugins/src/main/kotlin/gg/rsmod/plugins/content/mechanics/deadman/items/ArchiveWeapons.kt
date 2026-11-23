package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids

/** Wiki crossbows rev15411492, bolt pages rev15406624/rev15406633 and cache2762. No source-id fallback. */
object ArchiveWeapons {
    const val CROSSBOW_SOURCE = 34681
    val BOLT_SOURCES = listOf(34684, 34690, 34696, 34702, 34708)
    val bolts: Set<Int> get() = BOLT_SOURCES.mapNotNull { Dmm2Ids.item(it) }.toSet()
    fun isCrossbow(id: Int?): Boolean = id != null && id == Dmm2Ids.item(CROSSBOW_SOURCE)
    fun isBolt(id: Int?): Boolean = id != null && id in bolts
    fun accepts(weapon: Int?, ammo: Int?): Boolean = isCrossbow(weapon) && isBolt(ammo)
    fun baseSpeed(pvp: Boolean): Int = if (pvp) 4 else 3
    fun projectile(ammo: Int?): Int? {
        val source = when (ammo) {
            Dmm2Ids.item(34690), Dmm2Ids.item(34696) -> 4439
            Dmm2Ids.item(34702), Dmm2Ids.item(34708) -> 4438
            else -> 4437
        }
        return if (isBolt(ammo)) Dmm2Ids.spot(source) else null
    }
    private val RIGHT_NEXT = AttributeKey<Boolean>()
    fun nextAnimation(player: Player, pvp: Boolean): Int? {
        val right = player.attr[RIGHT_NEXT] == true
        val source = (if (right) 15115 else 15113) + if (pvp) 0 else 1
        val sequence = Dmm2Ids.seq(source) ?: return null
        player.attr[RIGHT_NEXT] = !right
        return sequence
    }
}
