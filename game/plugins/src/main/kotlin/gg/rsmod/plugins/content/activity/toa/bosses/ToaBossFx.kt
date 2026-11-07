package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.ext.createProjectile

/** Named upstream seq/spot ids: RuneLite gameval AnimationID/SpotanimID and cache2686.
 * Frame sounds travel with the imported sequence. T01: no guessed server sound/projectile packet parameters.
 */
object ToaBossFx {
    // Local ids from Claude's import (data/cfg/toa/ids.tsv via ToaIds).
    fun seq(osrs: Int): Int = gg.rsmod.plugins.content.activity.toa.ToaIds.seq(osrs) ?: -1

    /** The sequence as baked for [who] (skeletal npcs have their own frames per npc; Ba-Ba and each baboon differ). */
    fun seqFor(who: Npc, osrs: Int): Int {
        val ids = gg.rsmod.plugins.content.activity.toa.ToaIds
        // Head-icon copies share their root's rig but are deliberately absent from the import manifest.
        // Real form transmogs still take precedence when they have an imported source identity.
        val osrsNpc = who.getTransmogId().takeIf { it >= 0 }?.let(ids::osrsNpcOf) ?: ids.osrsNpcOf(who.id)
        return (if (osrsNpc != null) ids.npcSeq(ToaBossNpcs.canonical(osrsNpc), osrs) else ids.seq(osrs)) ?: -1
    }

    fun spot(osrs: Int): Int? = gg.rsmod.plugins.content.activity.toa.ToaIds.spot(osrs)
    fun weakness(npc: Npc, element: gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element, percent: Int) {
        require(percent >= 0)
        npc.attr[gg.rsmod.plugins.content.combat.formula.ElementalWeakness.INSTANCE] =
            gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Weakness(element, percent)
    }
    // Existing imported scenery from90-locmap; newly imported dynamic locs are read from Claude's id table.
    private val locations = mapOf(45149 to 64599, 45509 to 64756, 45643 to 64850, 45649 to 64856, 45752 to 64945)
    fun loc(osrs: Int): Int? = gg.rsmod.plugins.content.activity.toa.ToaIds.loc(osrs) ?: locations[osrs]
    fun projectile(npc: Npc, target: Player, osrs: Int, magic: Boolean, ticks: Int) {
        val gfx = spot(osrs) ?: return
        val type = if (magic) ProjectileType.MAGIC else ProjectileType.ARROW
        // ONZEKER T01: standard server packet geometry; flight is tied to the measured seq duration,30 client cycles/game tick.
        npc.world.spawn(npc.createProjectile(target, gfx, type.startHeight, type.endHeight, 0, type.angle,
            lifespan = ticks.coerceAtLeast(1) * 30, steepness = type.steepness))
    }
}
