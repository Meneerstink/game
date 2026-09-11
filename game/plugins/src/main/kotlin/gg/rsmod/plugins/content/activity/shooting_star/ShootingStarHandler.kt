package gg.rsmod.plugins.content.activity.shooting_star

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.RANDOM
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message

/**
 * World-scheduled Shooting Star event. Sourced from Void's `content/activity/shooting_star/
 * ShootingStar.kt`, re-architected onto this project's real scheduling primitive
 * (`world.timers[TimerKey]` + `on_timer`, see `living_rock_caverns.plugin.kts` for the existing
 * precedent this reuses) since Void's own scheduler (`world.gregs.voidps` `World.queue`) does not
 * exist in this codebase.
 *
 * Disclosed simplification vs Void: Void spawns a `shooting_star_shadow` NPC that visibly walks
 * to the landing tile over ~6 ticks before a separate falling-object model appears and lands.
 * This project's `Npc` pathing API was not verified safely for a one-off scripted walk within
 * this batch, so the shadow-walk/falling-object cosmetic sequence is omitted - the crashed star
 * appears directly, with the same landing damage to anyone already standing on it. The mechanic
 * (world event, landing damage, tiered mining, stardust reward exchange) is real and functional;
 * only the walk-in animation flourish is missing. `Npcs.SHOOTING_STAR_SHADOW` is intentionally
 * unused this batch, recorded here rather than wired in without walking verified.
 */
object ShootingStarHandler {
    private const val EVENT_INTERVAL_TICKS = 100 * 30 // ~30 minutes; Void's default is a
    // 60-minute Settings-configurable window (`events.shootingStars.minRespawnTimeMinutes`/
    // `maxRespawnTimeMinutes`, both default 60) - this project has no equivalent settings store
    // wired up, so a flat provisional interval is used instead of a guessed settings key.
    private const val SPRITE_DESPAWN_TICKS = 100 * 10 // 10 minutes, sourced from Void (`TimeUnit.
    // MINUTES.toTicks(10)`), same value

    fun scheduleNext(world: World) {
        world.timers[ShootingStarEventTimer] = EVENT_INTERVAL_TICKS
    }

    fun startEvent(world: World) {
        if (ShootingStarState.active) {
            cleanse(world, spawnSprite = false)
        }
        val locations = StarLocation.values()
        val location = locations[RANDOM.nextInt(locations.size)]
        val tierIndex = RANDOM.nextInt(ShootingStarTierObjects.size)
        ShootingStarState.tile = location.tile
        ShootingStarState.tierIndex = tierIndex

        world.players.forEach { p ->
            p?.message("A star has crashed at ${location.description}")
        }

        world.queue {
            wait(3)
            val tile = ShootingStarState.tile ?: return@queue
            val nearby = world.players.entries.filterNotNull().filter { it.tile.getDistance(tile) <= 1 }
            nearby.forEach { p ->
                p.hit(damage = RANDOM.nextInt(10, 50))
            }
            val obj = DynamicObject(ShootingStarTierObjects[tierIndex], 10, 0, tile)
            world.spawn(obj)
            ShootingStarState.obj = obj
        }
    }

    /** Called by [ShootingStarMining] every time a player successfully collects stardust. */
    fun onOreCollected(
        world: World,
        obj: GameObject,
    ) {
        if (ShootingStarState.obj !== obj) {
            return
        }
        ShootingStarState.collected++
        if (ShootingStarState.collected >= ShootingStarState.PROVISIONAL_LAYER_CAPACITY) {
            cleanse(world, spawnSprite = true)
        }
    }

    private fun cleanse(
        world: World,
        spawnSprite: Boolean,
    ) {
        ShootingStarState.obj?.let { world.remove(it) }
        val tile = ShootingStarState.tile
        if (spawnSprite && tile != null) {
            val sprite = Npc(Npcs.STAR_SPRITE, tile, world)
            sprite.respawnOverride = false
            world.spawn(sprite)
            world.timers[ShootingStarSpriteDespawnTimer] = SPRITE_DESPAWN_TICKS
            ShootingStarState.spriteRef = sprite
        }
        ShootingStarState.reset()
    }

    fun despawnSprite(world: World) {
        ShootingStarState.spriteRef?.let { world.remove(it) }
        ShootingStarState.spriteRef = null
    }
}
