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
        val local = (if (osrsNpc != null) ids.npcSeq(ToaBossNpcs.canonical(osrsNpc), osrs) else ids.seq(osrs)) ?: -1
        // N606 tooling 1: an animate() whose seq has no import for THIS npc's rig is silently dropped -> one PROBLEM line per pair.
        if (local < 0 && osrs >= 0) gg.rsmod.plugins.content.activity.toa.ToaGfxAudit.missing("seq", "osrs seq $osrs for npc ${who.id} (osrs npc $osrsNpc): geen import voor dit skelet -> geen animatie")
        return local
    }

    fun spot(osrs: Int): Int? = gg.rsmod.plugins.content.activity.toa.ToaIds.spot(osrs).also {
        if (it == null && osrs >= 0) gg.rsmod.plugins.content.activity.toa.ToaGfxAudit.missing("spot", "osrs spotanim $osrs: niet geimporteerd -> effect valt weg")
    }
    fun weakness(npc: Npc, element: gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element, percent: Int) {
        require(percent >= 0)
        npc.attr[gg.rsmod.plugins.content.combat.formula.ElementalWeakness.INSTANCE] =
            gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Weakness(element, percent)
    }
    // Existing imported scenery from90-locmap; newly imported dynamic locs are read from Claude's id table.
    private val locations = mapOf(45149 to 64599, 45509 to 64756, 45643 to 64850, 45649 to 64856, 45752 to 64945)
    fun loc(osrs: Int): Int? = gg.rsmod.plugins.content.activity.toa.ToaIds.loc(osrs) ?: locations[osrs]
    /**
     * N609 13-11: a bomb dropped from high above [from] onto [to] (Kephri fireball 2266: Kaoz Entry guide 889.4-891.4 s, the orange
     * drop comes down from well above Kephri onto the target tile). ONZEKER T01: start height / slope are measured against the video,
     * the OSRS packet values are not known.
     */
    fun fallProjectile(source: Npc, from: gg.rsmod.game.model.Tile, to: gg.rsmod.game.model.Tile, osrs: Int, ticks: Int, startHeight: Int = 220) {
        val gfx = spot(osrs) ?: return
        source.world.spawn(gg.rsmod.game.model.entity.Projectile.Builder()
            .setTiles(start = from, target = to).setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = 0)
            .setSlope(angle = 0, steepness = 0)
            .setTimes(delay = 0, lifespan = ticks.coerceAtLeast(1) * 30).build())
    }
    /** N606: tile-to-tile flight (ejected cores, spat objects) - the projectile lands on the floor after [ticks]. */
    fun tileProjectile(source: Npc, from: gg.rsmod.game.model.Tile, to: gg.rsmod.game.model.Tile, osrs: Int, ticks: Int,
                       startHeight: Int = ProjectileType.MAGIC.startHeight, endHeight: Int = 0) {
        val gfx = spot(osrs) ?: return
        val type = ProjectileType.MAGIC
        // ONZEKER T01: standard server packet geometry, as projectile() below.
        source.world.spawn(gg.rsmod.game.model.entity.Projectile.Builder()
            .setTiles(start = from, target = to).setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = endHeight)
            .setSlope(angle = type.angle, steepness = type.steepness)
            .setTimes(delay = 0, lifespan = ticks.coerceAtLeast(1) * 30).build())
    }
    fun projectile(npc: Npc, target: Player, osrs: Int, magic: Boolean, ticks: Int) {
        val gfx = spot(osrs) ?: return
        val type = if (magic) ProjectileType.MAGIC else ProjectileType.ARROW
        // ONZEKER T01: standard server packet geometry; flight is tied to the measured seq duration,30 client cycles/game tick.
        npc.world.spawn(npc.createProjectile(target, gfx, type.startHeight, type.endHeight, 0, type.angle,
            lifespan = ticks.coerceAtLeast(1) * 30, steepness = type.steepness))
    }
}
