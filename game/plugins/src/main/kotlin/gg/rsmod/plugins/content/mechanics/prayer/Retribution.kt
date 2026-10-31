package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.createProjectile

/**
 * Retribution's real effect (an explosion on the caster's own death) had no death-processing hook
 * anywhere - `Prayer.RETRIBUTION` only ever drove the overhead icon/toggle in [Prayers.kt]. Only
 * Void has a concrete source (Novite's `Player.java` has no Retribution/Wrath implementation at
 * all): `content/entity/death/PlayerDeath.kt:retribution()`. Ported as faithfully as that single
 * source allows:
 * - `maxHit = floor(prayerLevel * 0.25)` (1:1 life points) using the *current* (boostable) Prayer level, matching
 *   Void's `levels.get(Skill.Prayer)` in `retribution()` (Void's own `wrath()` next to it instead
 *   uses the base level via `getMax` - the two functions disagree with each other; only
 *   `retribution()` is sourced here, so its own choice is followed rather than its neighbour's).
 * - Damage is uniform on `[0, maxHit]` (OSRS "up to 25 %" of the Prayer level; owner audit 2026-09-22 - the
 *   donor's exclusive bound made the full max hit unreachable).
 * - Only the eight tiles adjacent to the caster's death tile are checked (not a wider blast
 *   radius) - this is Void's own implementation, not a simplification made here.
 * - In a multi-combat area, every other attackable [Pawn] standing on an adjacent tile is hit; in
 *   single combat, only the caster's killer is hit, and only if the killer is standing adjacent at
 *   the moment of death.
 *
 * N485 (owner 2026-10-31, balance plan step 4): the blast is visible - Void `retribution()` plays gfx [GRAPHIC] 437 on the
 * caster and shoots [SPLASH] 438 at each of the eight adjacent tiles (Void `prayer.gfx.toml`: retribution_splash delay 10,
 * time_offset 10, heights 30/30). Both ids exist in this cache.
 */
object Retribution {
    // OSRS Wiki "Retribution": up to 25 % of the Prayer level. The donor's 2.5 was on its x10 life points; this server is 1:1.
    private const val MAX_HIT_MULTIPLIER = 0.25
    const val GRAPHIC = 437
    const val SPLASH = 438
    private const val SPLASH_HEIGHT = 30
    private const val SPLASH_DELAY = 10
    private const val SPLASH_FLIGHT = 10

    fun onPlayerDeath(player: Player) {
        if (!Prayers.isActive(player, Prayer.RETRIBUTION)) return
        val world = player.world
        val prayerLevel = player.skills.getCurrentLevel(Skills.PRAYER)
        val maxHit = (prayerLevel * MAX_HIT_MULTIPLIER).toInt()
        if (maxHit <= 0) return
        // "up to 25 %": 0..maxHit inclusive, so 99 Prayer can hit the full 24.
        val damage = world.random(maxHit)

        val killer = player.attr[KILLER_ATTR]?.get()
        val multi = player.tile.isMulti(world)
        player.graphic(GRAPHIC)

        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val tile = player.tile.transform(dx, dz)
                world.spawn(
                    player.createProjectile(
                        tile, SPLASH, startHeight = SPLASH_HEIGHT, endHeight = SPLASH_HEIGHT, angle = 0, steepness = 0,
                        delay = SPLASH_DELAY, lifespan = SPLASH_DELAY + SPLASH_FLIGHT,
                    ),
                )
                if (multi) {
                    hitAllAt(world, tile, player, damage)
                } else if (killer != null && killer.tile == tile) {
                    killer.hit(damage = damage, delay = 1, source = player)
                }
            }
        }
    }

    private fun hitAllAt(
        world: World,
        tile: Tile,
        source: Pawn,
        damage: Int,
    ) {
        val chunk = world.chunks.get(tile, createIfNeeded = false) ?: return
        chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT).forEach { other ->
            // Same PvP area/level rules as Wrath; players outside a PvP area are never hit by a death effect.
            if (other !== source && (source !is Player || gg.rsmod.plugins.content.mechanics.pvp.AreaState.canDeathEffectHit(source, other))) {
                other.hit(damage = damage, delay = 1, source = source)
            }
        }
        chunk.getEntities<Npc>(tile, EntityType.NPC).forEach { other ->
            if (other !== source) other.hit(damage = damage, delay = 1, source = source)
        }
    }
}
