package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.message.impl.*
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.ToaIds
import gg.rsmod.plugins.content.activity.toa.ToaRoomScope

/** Existing667 packet encoders, including MapAnimUpdate's absolute tile marker; targeted Sight effects never leak to teammates. */
object ToaPuzzleVisuals {
    private val missing = hashSetOf<String>()
    private fun unavailable(asset: String) {
        if (missing.add(asset)) Trace.problem(Trace.Cat.COMBAT) { "ToA puzzle missing visual $asset; regenerate ToaIds/import, N462" }
    }
    fun spot(viewer: Player, osrs: Int, tile: Tile) {
        val id = ToaIds.spot(osrs) ?: run { unavailable("spot:$osrs"); return }
        if (viewer.tile.height != tile.height || !viewer.tile.isWithinRadius(tile, 32)) return
        viewer.write(MapAnimMessage(id, 0, 0,
            (tile.height shl 28) or (tile.x shl 14) or (tile.z and 0x3fff) or (1 shl 30), 0))
    }
    /**45494/45495 use the SAME model45425, differing in sequence9503/9502 (50-loc-defs).
     * Animate the existing45494 per viewer: replacing it with a fake45495 makes OpLoc's exact-ID lookup reject every solo repair.
     */
    fun pillar(viewer: Player, tile: Tile, shaking: Boolean) {
        val id = ToaIds.loc(45494) ?: run { unavailable("loc:45494"); return }
        val base = viewer.lastKnownRegionBase ?: return
        if (viewer.tile.height != tile.height || !viewer.tile.isWithinRadius(tile, 32)) return
        val local = base.toLocal(tile)
        if (local.x !in 0..103 || local.z !in 0..103) return //Wait for the destination scene after an instance teleport.
        val sourceLoc = if (shaking) 45495 else 45494
        val seq = if (shaking) 9502 else 9503
        val animation = ToaIds.seq(seq) ?: ToaIds.loc(sourceLoc)?.let {
            viewer.world.definitions.get(ObjectDef::class.java, it).animation.takeIf { anim -> anim >= 0 }
        }
        if (animation == null && shaking) {
            unavailable("seq:9502 (Apmeken shaking support)")
            spot(viewer, 2139, tile) //ONZEKER T01 fallback: existing same-room roof-rubble cue until the shaking clip is imported.
        } else viewer.write(LocAnimMessage(DynamicObject(id, 10, 3, tile), animation ?: -1))
    }
    /** Darkan WorldEncoder.sendSpotAnim: player index | 1<<28, tile target |1<<30. Existing MapAnim encoder already carries that hash. */
    fun playerSpot(viewer: Player, target: Player, osrs: Int?) {
        if (!viewer.isOnline || !target.isOnline || target.index <= 0 || viewer.tile.height != target.tile.height ||
            !viewer.tile.isWithinRadius(target.tile, 32)) return
        val id = if (osrs == null) -1 else ToaIds.spot(osrs) ?: run { unavailable("spot:$osrs"); return }
        viewer.write(MapAnimMessage(id, 0, 0, (target.index and 0xffff) or (1 shl 28), 0))
    }
    fun projectile(npc: Npc, player: Player, osrs: Int, type: ProjectileType) {
        val room = npc.attr[ToaPuzzles.OWNER] ?: return
        if (!room.eligible(player)) return
        val id = ToaIds.spot(osrs) ?: run { unavailable("projectile:$osrs"); return }
        val projectile = npc.createProjectile(player, id, type.startHeight, type.endHeight, type.delay, type.angle, steepness = type.steepness)
        room.scope.spawn(projectile)
    }
}

/** Projectiles are one-cycle zone updates, not retained world actors. Their damage callbacks are guarded by the owning room. */
private fun ToaRoomScope.spawn(projectile: Projectile) {
    if (active) raid.world.spawn(projectile)
}
