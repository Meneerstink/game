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

/**
 * Retribution's real effect (an explosion on the caster's own death) had no death-processing hook
 * anywhere - `Prayer.RETRIBUTION` only ever drove the overhead icon/toggle in [Prayers.kt]. Only
 * Void has a concrete source (Novite's `Player.java` has no Retribution/Wrath implementation at
 * all): `content/entity/death/PlayerDeath.kt:retribution()`. Ported as faithfully as that single
 * source allows:
 * - `maxHit = floor(prayerLevel * 2.5)` using the *current* (boostable) Prayer level, matching
 *   Void's `levels.get(Skill.Prayer)` in `retribution()` (Void's own `wrath()` next to it instead
 *   uses the base level via `getMax` - the two functions disagree with each other; only
 *   `retribution()` is sourced here, so its own choice is followed rather than its neighbour's).
 * - Damage is `random.nextInt(maxHit)`, i.e. uniform on `[0, maxHit)` - ported via
 *   `world.random(maxHit - 1)` which is `World`'s inclusive-bound equivalent.
 * - Only the eight tiles adjacent to the caster's death tile are checked (not a wider blast
 *   radius) - this is Void's own implementation, not a simplification made here.
 * - In a multi-combat area, every other attackable [Pawn] standing on an adjacent tile is hit; in
 *   single combat, only the caster's killer is hit, and only if the killer is standing adjacent at
 *   the moment of death.
 *
 * No graphic/projectile is ported this batch (Void's `gfx`/`shoot` calls reference donor-specific
 * animation IDs not yet cross-checked against this cache) - visual polish is deferred as a
 * separate follow-up; the damage effect itself is Retribution's actual gameplay mechanic.
 */
object Retribution {
    private const val MAX_HIT_MULTIPLIER = 2.5

    fun onPlayerDeath(player: Player) {
        if (!Prayers.isActive(player, Prayer.RETRIBUTION)) return
        val world = player.world
        val prayerLevel = player.skills.getCurrentLevel(Skills.PRAYER)
        val maxHit = (prayerLevel * MAX_HIT_MULTIPLIER).toInt()
        if (maxHit <= 0) return
        val damage = if (maxHit > 1) world.random(maxHit - 1) else 0

        val killer = player.attr[KILLER_ATTR]?.get()
        val multi = player.tile.isMulti(world)

        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val tile = player.tile.transform(dx, dz)
                if (multi) {
                    hitAllAt(world, tile, player, damage)
                } else if (killer != null && killer.tile == tile) {
                    killer.hit(damage = damage, delay = 1)
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
            if (other !== source) other.hit(damage = damage, delay = 1)
        }
        chunk.getEntities<Npc>(tile, EntityType.NPC).forEach { other ->
            if (other !== source) other.hit(damage = damage, delay = 1)
        }
    }
}
