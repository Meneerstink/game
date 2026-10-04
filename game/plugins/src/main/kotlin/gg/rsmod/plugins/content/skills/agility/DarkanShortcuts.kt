package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.ForcedMovement
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*

/*
 * ---- Darkan shortcut helpers (owner 2026-10-04: "port everything from Darkan", agent-handoffs/darkan-obstacles-map.md) ----
 * Literal translations of Darkan's shared shortcut code (import-source/donors/darkan, revision 727 = our post-2011 map):
 * game/content/world/areas/global/AgilityShortcuts.java and game/content/skills/agility/Agility.java. They sit on top of the
 * Darkan helpers at the bottom of ShortcutSupport.kt (darkanObstacle / darkanForceMove / darkanWalkAgility). Start tile = where
 * the normal route to the object ended (Darkan never computes a lane), landing = Darkan's fixed tile or route tile + offset.
 * Tick mapping: a Darkan handler runs in tick T and WorldTasks.schedule(n) fires at T+n+1; our obstacle task starts one tick after
 * the click, so Darkan's schedule(0) is "now" here and schedule(1) is one wait(1).
 */

/**
 * Darkan `Agility.handleObstacle(player, anim, delay, toTile, xp)` (Agility.java:85) with its own first tick: lock; schedule(1) the
 * animation; schedule(delay + 1) tele(toTile) + anim -1. The body never moves until the teleport - no slide through the rock.
 */
suspend fun QueueTask.darkanHandleObstacle(
    player: Player,
    anim: Int,
    delay: Int,
    to: Tile,
) {
    wait(1)
    darkanObstacle(player, anim, delay, to)
}

/**
 * Darkan `Entity.forceMove(dest, anim, startClientCycles, speedClientCycles)` whose task resumes after [resumeTicks] game ticks
 * instead of at the end of the glide, for Darkan sequences that start the next animation while the glide still runs
 * (AgilityShortcuts.crawlUnder: anim 2591 three ticks into a 130-cycle crawl). The server tile is the destination at once, as in
 * Darkan; the client glide keeps its raw cycles (verbatim, no SlideTiming).
 */
suspend fun QueueTask.darkanForceMoveFor(
    player: Player,
    to: Tile,
    startCycles: Int,
    speedCycles: Int,
    anim: Int,
    resumeTicks: Int,
    direction: Direction = Direction.between(player.tile, to),
) {
    if (anim != -1) player.animate(anim)
    val move =
        ForcedMovement.of(
            player.tile,
            to,
            clientDuration1 = startCycles,
            clientDuration2 = speedCycles,
            directionAngle = direction.ordinal,
            lockState = LockState.NONE,
        )
    player.forceMove(this, move, requestedCycles = resumeTicks.coerceAtLeast(1), verbatim = true)
}

/**
 * Darkan `AnimationDefinitions.getEmoteTime()` (Matrix lineage, same decoder as matrix-reloaded-718 AnimationDefinitions.java:63):
 * the sum of the frame lengths times 30.
 */
fun darkanEmoteTime(
    player: Player,
    anim: Int,
): Int = (player.world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, anim)?.frameLength ?: 0) * 30

/**
 * Darkan `AgilityShortcuts.climbOver(player, toTile, animId)` (AgilityShortcuts.java:27-33): one forced move from the route tile to
 * [to] with [anim], start 33 client cycles, speed emoteTime / 20 - 5 (839: 91, 2923: 134, 3844: 131).
 */
suspend fun QueueTask.darkanClimbOver(
    player: Player,
    to: Tile,
    anim: Int = 839,
) {
    val speed = (darkanEmoteTime(player, anim) / 20 - 5).coerceAtLeast(33)
    darkanForceMove(player, to, startCycles = 33, speedCycles = speed, anim = anim)
}

/**
 * Darkan `AgilityShortcuts.crawlUnder(player, toTile)` (AgilityShortcuts.java:40-44): lock; schedule(1) forceMove(toTile, 2589, 35,
 * 130); schedule(4) anim 2591 - three ticks into the 130-cycle crawl, which then runs out (5 ticks in all).
 */
suspend fun QueueTask.darkanCrawlUnder(
    player: Player,
    to: Tile,
) {
    wait(1)
    darkanForceMoveFor(player, to, startCycles = 35, speedCycles = 130, anim = 2589, resumeTicks = 3)
    player.animate(2591)
    wait(2)
}

/**
 * Darkan `AgilityShortcuts.walkLog(player, toTile, delay)` (AgilityShortcuts.java:46-69): the rope-balance BAS 155 and a
 * collision-free walk from the route tile to [to], "You walk carefully across the slippery log..." / "... and make it safely to the
 * other side.", [xp] (Darkan 7.5) at the end.
 */
suspend fun QueueTask.darkanWalkLog(
    player: Player,
    to: Tile,
    xp: Double = 7.5,
) {
    player.filterableMessage("You walk carefully across the slippery log...")
    player.setRenderAnimation(Render.ROPE_BALANCE)
    walkOverTile(player, to, Render.ROPE_BALANCE)
    player.resetRenderAnimation()
    if (xp > 0.0) player.addXp(Skills.AGILITY, xp)
    player.filterableMessage("... and make it safely to the other side.")
}

/**
 * Darkan `Agility.crossMonkeybars(player, startTile, endTile, xp)` (Agility.java:120-130): route onto [start] (the bars' own tile),
 * face the end, next tick anim 742 + the monkey-bar BAS, then walkToAgility(BAS, direction, distance) with the stop anim 743.
 * Darkan's BAS 2405 is not in the 667 cache; BAS 662 is this cache's monkey-bar set (ready 745 / walk 744, [Render.MONKEY_BARS]).
 */
suspend fun QueueTask.darkanCrossMonkeyBars(
    player: Player,
    start: Tile,
    end: Tile,
    xp: Double,
) {
    walkToTile(player, start)
    player.faceTile(end)
    wait(1)
    player.animate(742)
    player.setRenderAnimation(Render.MONKEY_BARS)
    wait(1)
    walkOverTile(player, end, Render.MONKEY_BARS)
    player.animate(743)
    player.resetRenderAnimation()
    if (xp > 0.0) player.addXp(Skills.AGILITY, xp)
}

/**
 * Darkan `Agility.crossLedge(player, startTile, endTile, xp, left)` (Agility.java:108-118): route to [start], face the end, next tick
 * anim 752 (left) / 753 + BAS 156 / 157, then the collision-free walk to [end] and the stop anim 758 / 759.
 */
suspend fun QueueTask.darkanCrossLedge(
    player: Player,
    start: Tile,
    end: Tile,
    xp: Double,
    left: Boolean,
) {
    walkToTile(player, start)
    player.faceTile(end)
    wait(1)
    val bas = if (left) 156 else 157
    player.animate(if (left) 752 else 753)
    player.setRenderAnimation(bas)
    wait(1)
    walkOverTile(player, end, bas)
    player.animate(if (left) 758 else 759)
    player.resetRenderAnimation()
    if (xp > 0.0) player.addXp(Skills.AGILITY, xp)
}

/**
 * Darkan `Agility.swingOnRopeSwing(player, startTile, endTile, object, xp)` (Agility.java:85-95): route to [start], face the rope,
 * anim 751, rope loc anim 497, forceMove(end, 30, 90), "You skillfully swing across the rope.", xp.
 */
suspend fun QueueTask.darkanRopeSwing(
    player: Player,
    rope: gg.rsmod.game.model.entity.GameObject,
    start: Tile,
    end: Tile,
    xp: Double,
) {
    walkToTile(player, start)
    player.faceTile(rope.tile)
    animateLoc(player.world, rope, 497)
    darkanForceMove(player, end, startCycles = 30, speedCycles = 90, anim = 751)
    player.filterableMessage("You skillfully swing across the rope.")
    if (xp > 0.0) player.addXp(Skills.AGILITY, xp)
}

/**
 * Darkan rs-lib `Utils.skillSuccess(level, rate1, rate99)` (the lib is not in the donor tree; used by AgilityDungeon.java): the
 * success chance out of 256 interpolated from [rate1] at level 1 to [rate99] at level 99 - the same interpolation as
 * [agilitySuccess], which it delegates to.
 */
fun darkanSkillSuccess(
    player: Player,
    rate1: Int,
    rate99: Int,
): Boolean = agilitySuccess(player, rate1, rate99)

/** A Darkan percentage hit of the player's maximum hitpoints (Darkan works in life points x10; this server is 1:1). */
fun Player.darkanPercentHit(fraction: Double) {
    val damage = (getMaximumLifepoints() * fraction).toInt().coerceAtLeast(1)
    hit(damage)
}

/** Darkan `player.transform(dx, dz)` on the player's own plane. */
fun Player.darkanTransform(
    dx: Int,
    dz: Int,
): Tile = Tile(tile.x + dx, tile.z + dz, tile.height)
