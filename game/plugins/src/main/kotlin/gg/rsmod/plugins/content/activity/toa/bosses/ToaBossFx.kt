package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.ext.createProjectile

/** Named upstream seq/spot ids: RuneLite gameval AnimationID/SpotanimID and cache2686.
 * Frame sounds travel with the imported sequence. T01: no guessed server sound/projectile packet parameters.
 */
object ToaBossFx {
    val sequences: Map<Int, Int> = emptyMap()
    val spots: Map<Int, Int> = emptyMap()
    fun seq(osrs: Int): Int = sequences[osrs] ?: -1
    fun spot(osrs: Int): Int? = spots[osrs]
    fun projectile(npc: Npc, target: Player, osrs: Int, magic: Boolean, ticks: Int) {
        val gfx = spot(osrs) ?: return
        val type = if (magic) ProjectileType.MAGIC else ProjectileType.ARROW
        // ONZEKER T01: standard server packet geometry; flight is tied to the measured seq duration,30 client cycles/game tick.
        npc.world.spawn(npc.createProjectile(target, gfx, type.startHeight, type.endHeight, 0, type.angle,
            lifespan = ticks.coerceAtLeast(1) * 30, steepness = type.steepness))
    }
}
