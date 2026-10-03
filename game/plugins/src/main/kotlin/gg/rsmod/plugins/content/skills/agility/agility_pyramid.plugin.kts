package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.skills.agility.AgilityPyramid as Pyr

/**
 * Agility Pyramid (Jaleustrophos), owner priority #1 2026-10-03: one plugin owns every id of the course - stairs 10857 / 10858,
 * jump gaps 10859, ledges 10860 / 10886-10889, hand-over-hand gaps 10861-10864 / 10882-10885, low walls 10865, planks 10867 /
 * 10868, the top's climbing rocks 10851, the doorway 10855 / 10856, the climbing rocks 16535 / 16536 to the pyramid, the five
 * rolling stone blocks and the two moving pyramid blocks (NPCs 3124 / 3125).
 *
 * Source: 2009scape AgilityPyramidCourse / RollingBlock / MovingBlockNPC (tiles, animations, messages, fail tiles; Void has no
 * course code), checked against the OSRS Wiki obstacle pages: level 30; XP stone block 12, low wall 8, ledge 52, plank 56.4,
 * gap (cross) 56.4, gap (jump) 22, climbing rocks 0, doorway 300 + 8 x Agility level (max 1000, base level, only with the top
 * taken - "if you forget to get the relic beforehand, you will earn nothing"); success chances = the wiki's Jagex
 * success charts; damage stone block 6 (1 backwards, always fails), low wall 4, ledge 10, plank 10, gaps 8, pyramid block 8.
 *
 * Owner hard rule (E17): every crossing walks normally to the tile in front of the obstacle, then one exactMove with the sourced
 * animation to the usable tile behind it; a fall is a fall animation on a walkable tile and a move to a usable tile one floor down
 * (Pyr.fallLanding) - never a slide into a blocked tile. Tiles: AgilityPyramid.kt, guarded by AgilityPyramidLaneTests.
 */

val PYRAMID_TICK = TimerKey()
val PYRAMID_LAST_TILE = AttributeKey<Tile>()
val PYRAMID_TOP_ITEM = 6970
val PYRAMID_TOP_JINGLE = 151
val MOVING_BLOCK_NPCS = intArrayOf(3124, 3125)

fun Player.pyramidXp(xp: Double) = addXp(Skills.AGILITY, xp)

fun Player.pyramidSuccess(chance: Pair<Int, Int>) = agilitySuccess(this, chance.first, chance.second)

/** The farthest tile up to [steps] along [dir] reachable without passing a blocked tile (a failed crossing stops there). */
fun slideBeforeGap(
    player: Player,
    dir: Direction,
    steps: Int,
): Tile {
    var tile = player.tile
    repeat(steps) {
        val next = tile.add(dir)
        if (player.world.collision.isClipped(next)) return tile
        tile = next
    }
    return tile
}

/** One fall down a floor: the fall animation where the player stands, then the move to a usable tile below and the hit. */
suspend fun QueueTask.fallDown(
    player: Player,
    target: Tile,
    anim: Int,
    damage: Int,
    message: String? = null,
    wait: Int = 2,
) {
    val landing = Pyr.fallLanding(player.world, target)
    player.animate(anim)
    message?.let { player.filterableMessage(it) }
    wait(wait)
    if (landing == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
            "agility pyramid: no usable fall tile near ${target.x},${target.z},${target.height} for ${player.username} - stays on ${player.tile}"
        }
    } else {
        player.moveTo(landing)
    }
    player.playSound(Sfx.FALL_LAND)
    player.animate(-1)
    player.hit(damage)
}

fun unknownPlacement(
    player: Player,
    obj: gg.rsmod.game.model.entity.GameObject,
) {
    gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
        "agility pyramid: ${obj.id} at ${obj.tile.x},${obj.tile.z},${obj.tile.height} has no sourced crossing"
    }
    player.message(gg.rsmod.game.model.entity.Entity.NOTHING_INTERESTING_HAPPENS)
}

// ---- Stairs (2009scape handleStairs: up = +2 north one floor up, down = one south one floor down; no climb animation) ----

shortcut(10857, "Climb-up") {
    obstacle { target ->
        val front = Pyr.stairsFront(world, target, player.tile, up = true)
        val landing = Pyr.stairsLanding(world, target, player.tile, up = true)
        if (front == null || landing == null) return@obstacle unknownPlacement(player, target)
        walkToTile(player, front)
        face(player, Direction.NORTH)
        wait(1)
        player.moveTo(landing)
    }
}

shortcut(10858, "Climb-down") {
    obstacle { target ->
        val front = Pyr.stairsFront(world, target, player.tile, up = false)
        val landing = Pyr.stairsLanding(world, target, player.tile, up = false)
        if (front == null || landing == null) return@obstacle unknownPlacement(player, target)
        walkToTile(player, front)
        face(player, Direction.SOUTH)
        wait(1)
        player.moveTo(landing)
    }
}

// ---- Climbing rocks to the pyramid (16535 / 16536: two rock rows two tiles apart, climbed as one band) ----

intArrayOf(16535, 16536).forEach { id ->
    shortcut(id, "Climb") {
        val target = player.getInteractingGameObj()
        // 2009scape: "You must be level 30 agility or higher to climb down the rocks." (the 16536 rocks gate the course).
        if (id == 16536 && player.skills.getCurrentLevel(Skills.AGILITY) < 30) {
            player.message("You must be level 30 agility or higher to climb down the rocks.")
            return@shortcut
        }
        obstacle {
            val east = locAt(world, target.tile.transform(2, 0), id) != null
            val partner = target.tile.transform(if (east) 2 else -2, 0)
            val crossing = pairCrossing(world, target.tile, partner)
            if (crossing == null) return@obstacle unknownPlacement(player, target)
            walkToTile(player, crossing.start)
            face(player, crossing.direction)
            wait(1)
            player.playSound(Sfx.CLIMBING_LOOP, loops = 6)
            // 2009scape handleRockClimb: 1148 climbing eastwards, 740 westwards.
            player.animate(if (crossing.direction == Direction.EAST) 1148 else 740)
            exactMove(player, crossing.landing, delay = 120, direction = crossing.direction)
            player.animate(-1)
        }
    }
}

// ---- Low walls 10865 (2009scape handleLowWall; wiki 8 xp, slip = 4 damage) ----

shortcut(10865, "Climb-over") {
    obstacle { target ->
        val crossing = Pyr.laneOver(world, target, player.tile) ?: return@obstacle unknownPlacement(player, target)
        walkToTile(player, crossing.start)
        face(player, crossing.direction)
        wait(1)
        player.filterableMessage("You climb the low wall...")
        player.playSound(Sfx.CLIMB_WALL, delay = 40)
        if (!player.pyramidSuccess(Pyr.LOW_WALL_CHANCE)) {
            player.animate(1106)
            wait(2)
            player.filterableMessage("You lost your balance!")
            player.animate(-1)
            player.hit(4)
            return@obstacle
        }
        player.animate(1252)
        exactMove(player, crossing.landing, delay = 60, direction = crossing.direction)
        player.animate(-1)
        player.filterableMessage("... and make it over.")
        player.pyramidXp(8.0)
    }
}

// ---- Ledges 10860 / 10886-10889 (2009scape handleLedge; wiki 52 xp, fall 10 damage) ----

intArrayOf(10860, 10886, 10887, 10888, 10889).forEach { id ->
    shortcut(id, "Cross") {
        obstacle { target ->
            val lane = Pyr.lane(Pyr.LEDGES, target.tile) ?: return@obstacle unknownPlacement(player, target)
            val fromA = player.tile.getDistance(lane.a) <= player.tile.getDistance(lane.b)
            val start = if (fromA) lane.a else lane.b
            val end = if (fromA) lane.b else lane.a
            val dir = Direction.between(start, end)
            walkToTile(player, start)
            face(player, dir)
            wait(1)
            player.filterableMessage("You put your foot on the ledge and try to edge across...")
            player.playSound(Sfx.BALANCING_LEDGE, loops = 5)
            // ADAPTED: 2009scape's walk sequences 156 / 157 are other sequences in this 667 cache (157 = a 107-frame weapon
            // sequence); the ledge is the 667 ledge-balance render (Void barbarian ledge) during the slide.
            player.setRenderAnimation(Render.LEDGE_BALANCE)
            if (!player.pyramidSuccess(Pyr.LEDGE_CHANCE)) {
                val stop = slideBeforeGap(player, dir, 3)
                if (!stop.sameAs(player.tile)) exactMove(player, stop, delay = player.tile.getDistance(stop) * 30, direction = dir)
                player.resetRenderAnimation()
                fallDown(player, lane.fall, 761, 10, "You slip and fall to the level below.")
                return@obstacle
            }
            exactMove(player, end, delay = 150, direction = dir)
            player.resetRenderAnimation()
            player.filterableMessage("You skillfully edge across the gap.")
            player.pyramidXp(52.0)
        }
    }
}

// ---- Hand-over-hand gaps 10861-10864 / 10882-10885 (2009scape handleGapCross; wiki 56.4 xp, fall 8 damage) ----

intArrayOf(10861, 10862, 10863, 10864, 10882, 10883, 10884, 10885).forEach { id ->
    shortcut(id, "Cross") {
        obstacle { target ->
            val lane = Pyr.lane(Pyr.GAP_CROSSINGS, target.tile) ?: return@obstacle unknownPlacement(player, target)
            val fromA = player.tile.getDistance(lane.a) <= player.tile.getDistance(lane.b)
            val start = if (fromA) lane.a else lane.b
            val end = if (fromA) lane.b else lane.a
            val dir = Direction.between(start, end)
            walkToTile(player, start)
            face(player, dir)
            wait(1)
            // 2009scape "387 - mod": mod 0 from the a-ends of all three lanes, 1 from the b-ends (worked out per lane).
            val mod = if (fromA) 0 else 1
            player.playSound(Sfx.HANDHOLDS_GRAB_TO_SECOND)
            if (!player.pyramidSuccess(Pyr.GAP_CROSS_CHANCE)) {
                player.animate(387 - mod)
                val stop = slideBeforeGap(player, dir, 4)
                if (!stop.sameAs(player.tile)) exactMove(player, stop, delay = player.tile.getDistance(stop) * 30, direction = dir)
                fallDown(player, lane.fall, 3056 - mod, 8, wait = 3)
                return@obstacle
            }
            player.animate(387 - mod)
            exactMove(player, end, delay = 150, direction = dir)
            player.animate(-1)
            player.pyramidXp(56.4)
        }
    }
}

// ---- Jump gaps 10859 (2009scape handleJumpGap; wiki 22 xp, fall 8 damage) ----

shortcut(10859, "Jump") {
    obstacle { target ->
        val crossing = Pyr.laneOver(world, target, player.tile)
        val fall = Pyr.JUMP_FALLS.entries.firstOrNull { it.key.sameAs(target.tile) }?.value
        if (crossing == null || fall == null) return@obstacle unknownPlacement(player, target)
        walkToTile(player, crossing.start)
        face(player, crossing.direction)
        wait(1)
        player.filterableMessage("You jump the gap...")
        if (!player.pyramidSuccess(Pyr.GAP_JUMP_CHANCE)) {
            player.playSound(Sfx.JUMP_NO_LAND, delay = 30)
            fallDown(player, fall, 3068, 8, "... and miss your footing.", wait = 4)
            return@obstacle
        }
        player.playSound(Sfx.JUMP2, delay = 30)
        player.animate(3067)
        exactMove(player, crossing.landing, delay = 60, direction = crossing.direction)
        player.animate(-1)
        player.pyramidXp(22.0)
    }
}

// ---- Planks 10867 / 10868 (2009scape handlePlank; wiki 56.4 xp, fall 10 damage) ----

intArrayOf(10867, 10868).forEach { id ->
    shortcut(id, "Cross") {
        obstacle { target ->
            val lane = Pyr.lane(Pyr.PLANKS, target.tile) ?: return@obstacle unknownPlacement(player, target)
            // The near end is where the walk starts (2009scape walks onto the plank loc first); either end loc can be clicked.
            val fromA = player.tile.getDistance(lane.a) <= player.tile.getDistance(lane.b)
            val start = if (fromA) lane.a else lane.b
            val end = if (fromA) lane.b else lane.a
            val dir = Direction.between(start, end)
            walkToTile(player, start)
            face(player, dir)
            wait(1)
            player.playSound(Sfx.PLANKWALK, loops = 3)
            // 2009scape walks the plank in sequence 155; this cache's render 155 is that balance walk (Render.ROPE_BALANCE).
            player.setRenderAnimation(Render.ROPE_BALANCE)
            if (!player.pyramidSuccess(Pyr.PLANK_CHANCE)) {
                val stop = slideBeforeGap(player, dir, 2)
                if (!stop.sameAs(player.tile)) exactMove(player, stop, delay = player.tile.getDistance(stop) * 30, direction = dir)
                player.resetRenderAnimation()
                fallDown(player, lane.fall, 764, 10)
                return@obstacle
            }
            player.filterableMessage("You walk carefully across the slippery plank...")
            exactMove(player, end, delay = 150, direction = dir)
            player.resetRenderAnimation()
            player.pyramidXp(56.4)
        }
    }
}

// ---- The top: climbing rocks 10851 take the pyramid top, the doorway 10855 / 10856 leads back to the base ----

shortcut(10851, "Climb") {
    obstacle {
        if (player.getVarbit(Pyr.TOP_VARBIT) == 1) {
            player.animate(3063)
            wait(3)
            player.filterableMessage("You find nothing on top of the pyramid.")
            return@obstacle
        }
        player.animate(3063)
        wait(3)
        player.animate(-1)
        // The top loc 10869 shows taken (varbit 1556 = 1) until the doorway; 2009scape addConfig(10869, 1).
        player.setVarbit(Pyr.TOP_VARBIT, 1)
        if (!player.inventory.add(PYRAMID_TOP_ITEM).hasSucceeded()) {
            world.spawn(GroundItem(PYRAMID_TOP_ITEM, amount = 1, tile = player.tile, owner = player))
        }
        player.playJingle(PYRAMID_TOP_JINGLE)
        player.queue { itemMessageBox("You find a golden pyramid!", PYRAMID_TOP_ITEM) }
    }
}

intArrayOf(10855, 10856).forEach { id ->
    shortcut(id, "Enter") {
        obstacle {
            wait(1)
            val topTaken = player.getVarbit(Pyr.TOP_VARBIT) == 1
            player.setVarbit(Pyr.TOP_VARBIT, 0)
            player.moveTo(Pyr.BASE)
            if (topTaken) {
                // OSRS Wiki Doorway (Agility Pyramid): 300 + Agility level x 8, capped at 1,000 (88+); boosts do not count.
                player.pyramidXp(minOf(1000, 300 + player.skills.getMaxLevel(Skills.AGILITY) * 8).toDouble())
            }
            player.queue { messageBox("You climb down the steep passage. It leads to the base of the", "pyramid.") }
        }
    }
}

// ---- Rolling stone blocks (2009scape RollingBlock; wiki 12 xp, fail 6 damage, backwards always fails for 1) ----

fun rollStoneBlock(
    player: Player,
    block: Pyr.StoneBlock,
    from: Tile,
    to: Tile,
    backwards: Boolean,
) {
    player.stopMovement()
    player.lockingQueue(lockState = LockState.FULL) {
        player.obstacleUntilUnlocked = true
        try {
            player.setVarbit(block.varbit, 1)
            player.playSound(Sfx.PYRAMID_SCRAPE)
            player.playSound(Sfx.LAND_FLAT, delay = 40)
            val dir = Direction.between(from, to)
            face(player, dir)
            if (backwards || !player.pyramidSuccess(Pyr.STONE_BLOCK_CHANCE)) {
                // 2009scape: two tiles back, one floor down (the top copy's level 4 falls into the main pyramid's level 3).
                val back = from.add(dir, -1)
                val below = if (back.regionId == Pyr.REGION_TOP && back.height == 2) Tile(back.x + 320, back.z - 1856, 3) else back.transform(-1)
                fallDown(player, below, 1116, if (backwards) 1 else 6, wait = 1)
                return@lockingQueue
            }
            player.animate(1115)
            exactMove(player, to, delay = 90, direction = dir)
            player.animate(-1)
            player.pyramidXp(12.0)
        } finally {
            player.setVarbit(block.varbit, 0)
        }
    }
}

fun checkStoneBlocks(player: Player) {
    val prev = player.attr[PYRAMID_LAST_TILE]
    val now = player.tile
    player.attr[PYRAMID_LAST_TILE] = Tile(now)
    if (prev == null || prev.sameAs(now) || player.isLocked() || player.isDead()) return
    for (block in Pyr.STONE_BLOCKS) {
        val dx = block.forward.getDeltaX()
        val dz = block.forward.getDeltaZ()
        val along = (now.x - prev.x) * dx + (now.z - prev.z) * dz
        for ((near, far) in block.lanes) {
            if (now.sameAs(near) && along > 0) return rollStoneBlock(player, block, near, far, backwards = false)
            if (now.sameAs(far) && along < 0) return rollStoneBlock(player, block, far, near, backwards = true)
        }
    }
}

listOf(Pyr.REGION_MAIN, Pyr.REGION_TOP).forEach { region ->
    on_enter_region(region) {
        player.attr.remove(PYRAMID_LAST_TILE)
        player.timers[PYRAMID_TICK] = 1
    }
    on_exit_region(region) {
        player.attr.remove(PYRAMID_LAST_TILE)
        player.timers.remove(PYRAMID_TICK)
    }
}

on_login {
    if (player.tile.regionId == Pyr.REGION_MAIN || player.tile.regionId == Pyr.REGION_TOP) player.timers[PYRAMID_TICK] = 1
}

on_timer(PYRAMID_TICK) {
    if (player.tile.regionId != Pyr.REGION_MAIN && player.tile.regionId != Pyr.REGION_TOP) return@on_timer
    checkStoneBlocks(player)
    player.timers[PYRAMID_TICK] = 1
}

// ---- Moving pyramid blocks (NPCs 3124 / 3125; OSRS Wiki: 15-tick cycle, 5 ticks out; 2009scape MovingBlockNPC pushes) ----

fun pushOff(
    player: Player,
    block: Pyr.MovingBlock,
) {
    player.stopMovement()
    player.lockingQueue(lockState = LockState.FULL) {
        player.obstacleUntilUnlocked = true
        player.playSound(Sfx.LAND_FLAT, delay = 50)
        face(player, block.push.getOpposite())
        fallDown(player, block.fall(player.tile), 3066, 8, wait = 2)
    }
}

on_world_init {
    world.queue {
        var tick = 0
        while (true) {
            wait(1)
            tick++
            for (block in Pyr.MOVING_BLOCKS) {
                val npc = world.npcs.firstOrNull { it.id in MOVING_BLOCK_NPCS && it.spawnTile.sameAs(block.spawn) } ?: continue
                npc.walkRadius = 0
                when ((tick + block.offset) % 15) {
                    0 -> {
                        npc.walkTo(block.out, MovementQueue.StepType.FORCED_WALK, detectCollision = false)
                        world.players.forEach { p ->
                            if (p.tile.height == block.spawn.height && p.tile.isWithinRadius(block.spawn, 6)) p.playSound(Sfx.PYRAMID_BLOCK, volume = 255)
                        }
                    }
                    4 -> npc.walkTo(block.spawn, MovementQueue.StepType.FORCED_WALK, detectCollision = false)
                }
                if ((tick + block.offset) % 15 in 0..4) {
                    world.players.forEach { p ->
                        if (!p.isLocked() && !p.isDead() && block.tiles.any { it.sameAs(p.tile) }) pushOff(p, block)
                    }
                }
            }
        }
    }
}
