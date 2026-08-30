package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.plugins.content.areas.home.BountyHunterHome
import gg.rsmod.plugins.content.objs.bank_locs.BankObjects
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp

/**
 * Real, cache-verified bank safe zones (R03.1 "explicit bank boundaries; deposit boxes do
 * not automatically make safe bubbles"). Rather than hand-authoring coordinates for every
 * bank in the game - unverifiable without a full map decode - this scans every actually
 * spawned instance of a real bank booth/chest ([BankObjects.ALL], the same ids already bound
 * to the Bank interface) once at world-init and marks a square radius around each as safe.
 * That radius is a simplification of the real building footprint (same trade-off as the home
 * enclave's [BountyHunterHome.SAFE_RADIUS]); it is not guaranteed to exactly match every
 * bank's walls, but it is derived from real placed objects, not invented.
 */
object BankZones {
    /** Matches [BountyHunterHome.SAFE_RADIUS]'s square-bubble shape, one tile wider. */
    const val RADIUS = 6

    private var safeTiles: HashSet<Tile> = HashSet()
    private var initialized = false

    /** Returns a short summary line for the caller to log (see [World.postLoad]). */
    fun init(world: World): String {
        val tiles = HashSet<Tile>()
        var bankObjectsFound = 0
        world.chunks.allChunks().forEach { chunk ->
            val statics = chunk.getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            statics.forEach { obj ->
                if (obj.id in BankObjects.ALL) {
                    bankObjectsFound++
                    val centre = obj.tile
                    for (dx in -RADIUS..RADIUS) {
                        for (dz in -RADIUS..RADIUS) {
                            tiles.add(centre.transform(dx, dz))
                        }
                    }
                }
            }
        }
        safeTiles = tiles
        initialized = true
        return "BankZones: derived ${tiles.size} safe tiles from $bankObjectsFound real bank " +
            "booth/chest objects (R03.1)."
    }

    fun isSafe(tile: Tile): Boolean = initialized && tile in safeTiles
}

/**
 * The single authoritative area-state predicate for PvP permission (R03.3), replacing the
 * old Wilderness-only [BountyHunterHome.isDangerousWilderness] check as the gate for whether
 * two players are allowed to fight. Death/loot-risk classification ([gg.rsmod.plugins.content.mechanics.death.DeathResolver])
 * deliberately still keys off Wilderness specifically - R08.1 requires preserving established
 * Wilderness location-risk rules and treating non-Wilderness PvP death policy as provisional,
 * so widening combat permission here does NOT change item-risk on death.
 */
object AreaState {
    /** True if [tile] is inside any safe zone: the home enclave or a real bank building. */
    fun isSafe(
        tile: Tile,
        home: Tile,
    ): Boolean = BountyHunterHome.isSafe(tile, home) || BankZones.isSafe(tile)

    /** R03.1: PvP is allowed everywhere outside safe zones - not only in the Wilderness. */
    fun isPvpAllowed(
        tile: Tile,
        home: Tile,
    ): Boolean = !isSafe(tile, home)

    fun canPlayersFight(
        attacker: gg.rsmod.game.model.entity.Player,
        target: gg.rsmod.game.model.entity.Player,
    ): Boolean {
        val world = attacker.world
        val home = world.gameContext.home
        return (isPvpAllowed(attacker.tile, home) && isPvpAllowed(target.tile, home)) ||
            PracticePvp.areMatched(attacker, target)
    }
}
