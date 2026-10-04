package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.skills.agility.AgilityPyramid as Pyr

/**
 * Agility Pyramid (Jaleustrophos) - ported from Darkan on 2026-10-04 (owner: "port everything from Darkan";
 * agent-handoffs/darkan-obstacles-map.md section 1d). Donor: import-source/donors/darkan/world-server-dev/.../skills/agility/
 * agilitypyramid/AgilityPyramidController.java and AgilityPyramidBlock.java (revision 727; same ids and tiles as this 667 map).
 * One plugin owns every id of the course: stairs 10857 / 10858, jump gaps 10859, ledges 10860 / 10886-10889, hand-over-hand gaps
 * 10861-10864 / 10882-10885, low walls 10865, planks 10867 / 10868, the top 10851, the doorway 10855 / 10856, the climbing rocks
 * 16535 / 16536, the five rolling stone blocks and the two moving pyramid blocks (NPCs 3124 / 3125).
 *
 * Darkan's rules, literally: landings are player- or loc-relative offsets (no home-made lanes) - low wall player +-2, gap player +-3,
 * ledge / hand-holds loc +-4, plank loc +-5, stairs player + (0, +-3, +-1); animations and BAS are Darkan's (ledges 752/753 + BAS
 * 156/157, hand-holds 3057/3053 + BAS 387/386, low wall 1560, rolling block 1115 / 3064, push 3066); client cycles are Darkan's
 * (darkanForceMove, no SlideTiming). The failure chance is Darkan's failed(): success = min(Agility level / 75, 1). Darkan's
 * ledges and hand-holds have a course side: the locs not in its list (crossed backwards) always fall. Experience is Darkan's
 * (ledge and hand-holds 52, plank 56.4 only the course-way 10868, gap 22, low wall 8, block 12); damage is Darkan's / 10 (this
 * server counts life points 1:1). A fall tile that is not standable on this map is moved to the nearest usable one
 * (AgilityPyramid.fallLanding), never left inside scenery. The doorway keeps the OSRS Wiki rule (see there).
 *
 * Tick mapping as DarkanShortcuts.kt: the task starts at Darkan's T+1 (WorldTasks.schedule(0) = now, schedule(n) = wait(n));
 * darkanForceMove resumes when its glide has ended. AgilityPyramid.kt's lane tables stay for AgilityPyramidLaneTests (map checks
 * of every end and fall tile); the handlers below no longer read them.
 */

val PYRAMID_TICK = TimerKey()
val PYRAMID_TOP_ITEM = 6970
val PYRAMID_TOP_JINGLE = 151
val MOVING_BLOCK_NPCS = intArrayOf(3124, 3125)

/** The hand-over-hand gap locs (type 10) a wall-decoration end (10863 / 10864, type 4) stands for (Darkan :245). */
val HANDHOLD_GAPS = intArrayOf(10861, 10862, 10882, 10883, 10884, 10885)

/** Darkan RollingBlock (:184): the failure drop per block (by its varbit) - its rotation's offset, one plane down; D into the main pyramid. */
val BLOCK_DROPS =
    mapOf(
        1551 to Triple(-2, 0, -1),
        1552 to Triple(0, 2, -1),
        1553 to Triple(2, 0, -1),
        1554 to Triple(322, -1856, 1),
        1555 to Triple(0, 2, -1),
    )

fun Player.pyramidXp(xp: Double) = addXp(Skills.AGILITY, xp)

/** Darkan AgilityPyramidController.failed() (:277): success with probability min(Agility level / 75, 1). */
fun Player.pyramidFailed(): Boolean =
    java.util.concurrent.ThreadLocalRandom.current().nextDouble() > Math.min(skills.getCurrentLevel(Skills.AGILITY) / 75.0, 1.0)

/** Darkan's 14-bit glide angle collapsed to this client's cardinal byte: the dominant axis of the move. */
fun cardinal(
    from: Tile,
    to: Tile,
): Direction {
    val dx = to.x - from.x
    val dz = to.z - from.z
    return if (Math.abs(dx) >= Math.abs(dz)) {
        if (dx >= 0) Direction.EAST else Direction.WEST
    } else {
        if (dz >= 0) Direction.NORTH else Direction.SOUTH
    }
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

/** Darkan's drop tile when it is standable here, else the nearest usable tile (AgilityPyramid.fallLanding); null = stay (traced). */
fun pyramidDrop(
    player: Player,
    darkan: Tile,
): Tile? {
    val landing = Pyr.fallLanding(player.world, darkan)
    if (landing == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
            "agility pyramid: no usable fall tile near ${darkan.x},${darkan.z},${darkan.height} for ${player.username} - stays on ${player.tile}"
        }
    }
    return landing
}

/**
 * Darkan AgilityPyramidController.shimmy (:357), a WorldTasks.scheduleLooping(0, 1) from the task start: tick 0 [startAnim] + BAS
 * [render]; tick 1 addWalkSteps([to]) without clipping (walk speed); a failure stops the walk on tick 3 (two tiles along) with
 * [endAnim] and on tick 4 drops one plane onto the nearest free tile with 100 (10 here) damage; a success plays [endAnim] on
 * arrival with 52 xp.
 */
suspend fun QueueTask.shimmy(
    player: Player,
    to: Tile,
    startAnim: Int,
    render: Int,
    endAnim: Int,
    fail: Boolean,
) {
    player.animate(startAnim)
    player.setRenderAnimation(render)
    wait(1)
    if (fail) {
        val from = player.tile
        val stop =
            Tile(
                from.x + Integer.signum(to.x - from.x) * Math.min(2, Math.abs(to.x - from.x)),
                from.z + Integer.signum(to.z - from.z) * Math.min(2, Math.abs(to.z - from.z)),
                from.height,
            )
        walkOverTile(player, stop, render)
        player.animate(endAnim)
        wait(1)
        pyramidDrop(player, player.tile.transform(-1))?.let { player.moveTo(it) }
        player.resetRenderAnimation()
        player.playSound(Sfx.FALL_LAND)
        player.hit(10)
        return
    }
    walkOverTile(player, to, render)
    player.animate(endAnim)
    player.resetRenderAnimation()
    player.pyramidXp(52.0)
}

/** Darkan shimmySideways / shimmyHandholds landing (:328-331, :351-354): the loc's tile +-4 across, by the loc's rotation. */
fun shimmyEnd(
    obj: gg.rsmod.game.model.entity.GameObject,
    from: Tile,
): Tile =
    if (obj.rot % 2 == 0) {
        obj.tile.transform(if (obj.rot == 0) 1 else 0, if (from.z < obj.tile.z) 4 else -4)
    } else {
        obj.tile.transform(if (from.x < obj.tile.x) 4 else -4, if (obj.rot == 3) 1 else 0)
    }

// ---- Stairs ----

// Darkan AgilityPyramidController.java:220 (10857): lvl 30; useStairs(-1, player + (0, 3, +1), 1, 1); on plane 3 into the top
// copy: player + (-320, 1859, -1). No animation. The climb starts from the stairs' front row (the route end on this map).
shortcut(10857, "Climb-up") {
    if (!player.hasAgility(30, "You need an agility level of 30 to use this obstacle.")) return@shortcut
    obstacle { target ->
        val front = Pyr.stairsFront(world, target, player.tile, up = true) ?: return@obstacle unknownPlacement(player, target)
        val moved = !player.tile.sameAs(front)
        walkToTile(player, front)
        face(player, Direction.NORTH)
        if (moved) wait(1)
        player.moveTo(if (player.tile.height == 3) player.tile.transform(-320, 1859, -1) else player.tile.transform(0, 3, 1))
    }
}

// Darkan AgilityPyramidController.java:232 (10858): useStairs(-1, player + (0, -3, -1), 1, 1); in the top copy on plane 2 back into
// the main pyramid: player + (320, -1859, +1).
shortcut(10858, "Climb-down") {
    obstacle { target ->
        val front = Pyr.stairsFront(world, target, player.tile, up = false) ?: return@obstacle unknownPlacement(player, target)
        val moved = !player.tile.sameAs(front)
        walkToTile(player, front)
        face(player, Direction.SOUTH)
        if (moved) wait(1)
        val top = player.tile.z > 4000 && player.tile.height == 2
        player.moveTo(if (top) player.tile.transform(320, -1859, 1) else player.tile.transform(0, -3, -1))
    }
}

// ---- Climbing rocks to the pyramid ----

// Darkan AgilityPyramidController.java:395 climbRocks (16535 / 16536): from the east side a collision-free walk 4 tiles west with
// BAS 0 (the climbing render); from the west anim 740 + forceMove(player + (4, 0), 20, 120), anim -1. Each rock band is two rock
// rows two tiles apart (three blocked tiles); the climb starts on the band's edge in the rock's own row (the straight route end).
intArrayOf(16535, 16536).forEach { id ->
    shortcut(id, "Climb") {
        val target = player.getInteractingGameObj()
        // 2009scape (Darkan has no check): "You must be level 30 agility or higher to climb down the rocks." (16536 gates the course).
        if (id == 16536 && player.skills.getCurrentLevel(Skills.AGILITY) < 30) {
            player.message("You must be level 30 agility or higher to climb down the rocks.")
            return@shortcut
        }
        obstacle {
            val partnerEast = locAt(world, target.tile.transform(2, 0), id) != null
            val westEdge = (if (partnerEast) target.tile.x else target.tile.x - 2) - 1
            val eastEdge = (if (partnerEast) target.tile.x + 2 else target.tile.x) + 1
            val fromEast = player.tile.x > target.tile.x
            walkToTile(player, Tile(if (fromEast) eastEdge else westEdge, target.tile.z))
            player.playSound(Sfx.CLIMBING_LOOP, loops = 6)
            if (fromEast) {
                walkOverTile(player, player.tile.transform(-4, 0), Render.CLIMBING)
                player.resetRenderAnimation()
            } else {
                darkanForceMove(player, player.tile.transform(4, 0), startCycles = 20, speedCycles = 120, anim = 740, direction = Direction.EAST)
                player.animate(-1)
            }
        }
    }
}

// ---- Low walls ----

// Darkan AgilityPyramidController.java:471 climbOver (10865): failed() first - 40 (4 here) damage, "Ouch!", locked 3 ticks, no
// crossing; else the landing is the player's tile +-2 along the wall's axis (rotation even: x, odd: z), anim 1560 +
// forceMove(landing, 5, 60), 8 xp.
shortcut(10865, "Climb-over") {
    obstacle { target ->
        if (player.pyramidFailed()) {
            player.hit(4)
            player.forceChat("Ouch!")
            wait(3)
            return@obstacle
        }
        val to =
            if (target.rot % 2 == 0) {
                player.tile.transform(if (player.tile.x < target.tile.x) 2 else -2, 0)
            } else {
                player.tile.transform(0, if (player.tile.z < target.tile.z) 2 else -2)
            }
        player.playSound(Sfx.CLIMB_WALL, delay = 40)
        darkanForceMove(player, to, startCycles = 5, speedCycles = 60, anim = 1560, direction = cardinal(player.tile, to))
        player.pyramidXp(8.0)
    }
}

// ---- Ledges ----

// Darkan AgilityPyramidController.java:335 shimmySideways (10860 / 10886-10889): 10886, 10888 and 10860 at (3372,2839) / (3364,2851)
// are the course-way locs - anim 753, BAS 157, end 759 (fail roll: end 761); every other ledge loc (crossed backwards) is anim 752,
// BAS 156 and always falls with 760. Landing = the loc +-4 across ([shimmyEnd]).
intArrayOf(10860, 10886, 10887, 10888, 10889).forEach { id ->
    shortcut(id, "Cross") {
        obstacle { target ->
            val courseWay =
                id == 10886 || id == 10888 ||
                    (id == 10860 && ((target.tile.x == 3372 && target.tile.z == 2839) || (target.tile.x == 3364 && target.tile.z == 2851)))
            val fail = if (courseWay) player.pyramidFailed() else true
            player.filterableMessage("You put your foot on the ledge and try to edge across...")
            player.playSound(Sfx.BALANCING_LEDGE, loops = 5)
            shimmy(
                player,
                shimmyEnd(target, player.tile),
                startAnim = if (courseWay) 753 else 752,
                render = if (courseWay) 157 else 156,
                endAnim = if (!courseWay) 760 else if (fail) 761 else 759,
                fail = fail,
            )
        }
    }
}

// ---- Hand-over-hand gaps ----

// Darkan AgilityPyramidController.java:317 shimmyHandholds (10861-10864 / 10882-10885): a click on a wall-decoration end (type 4:
// 10863 / 10864) acts as the gap loc within 2 tiles (:246); 10883, 10885 and 10862 (crossed backwards) are anim 3053, BAS 386,
// end 3055 and always fall; the others anim 3057, BAS 387, end 3058 (fail roll: 3056). Landing = the loc +-4 across.
intArrayOf(10861, 10862, 10863, 10864, 10882, 10883, 10884, 10885).forEach { id ->
    shortcut(id, "Cross") {
        obstacle { clicked ->
            var found: GameObject? = null
            if (clicked.type == 4) {
                val tiles =
                    (-2..2).flatMap { dx -> (-2..2).map { dz -> clicked.tile.transform(dx, dz) } }
                        .sortedBy { Math.abs(it.x - clicked.tile.x) + Math.abs(it.z - clicked.tile.z) }
                search@ for (t in tiles) {
                    for (gap in HANDHOLD_GAPS) {
                        val loc = locAt(world, t, gap)
                        if (loc != null) {
                            found = loc
                            break@search
                        }
                    }
                }
            } else {
                found = clicked
            }
            val target: GameObject = found ?: return@obstacle unknownPlacement(player, clicked)
            val backwards = target.id == 10883 || target.id == 10885 || target.id == 10862
            val fail = backwards || player.pyramidFailed()
            player.playSound(Sfx.HANDHOLDS_GRAB_TO_SECOND)
            shimmy(
                player,
                shimmyEnd(target, player.tile),
                startAnim = if (backwards) 3053 else 3057,
                render = if (backwards) 386 else 387,
                endAnim = if (backwards) 3055 else if (fail) 3056 else 3058,
                fail = fail,
            )
        }
    }
}

// ---- Jump gaps ----

// Darkan AgilityPyramidController.java:457 jumpGap (10859): landing = the player's tile +-3 (rotation even: z, odd: x); anim 3067 +
// forceMove(landing, 5, 60), 22 xp. Darkan has no failure here.
shortcut(10859, "Jump") {
    obstacle { target ->
        val to =
            if (target.rot % 2 == 0) {
                player.tile.transform(0, if (player.tile.z < target.tile.z) 3 else -3)
            } else {
                player.tile.transform(if (player.tile.x < target.tile.x) 3 else -3, 0)
            }
        player.filterableMessage("You jump the gap...")
        player.playSound(Sfx.JUMP2, delay = 30)
        darkanForceMove(player, to, startCycles = 5, speedCycles = 60, anim = 3067, direction = cardinal(player.tile, to))
        player.pyramidXp(22.0)
    }
}

// ---- Planks ----

// Darkan AgilityPyramidController.java:424 walkLog (10867 / 10868): to = the plank loc +5 (10867) / -5 (10868) along its axis
// (rotation even: x, odd: z); one collision-free walk from the route end with BAS 155; 56.4 xp for 10868 (the course way) only.
intArrayOf(10867, 10868).forEach { id ->
    shortcut(id, "Cross") {
        obstacle { target ->
            val d = if (id == 10867) 5 else -5
            val end = if (target.rot % 2 == 0) target.tile.transform(d, 0) else target.tile.transform(0, d)
            player.playSound(Sfx.PLANKWALK, loops = 3)
            walkOverTile(player, Tile(end.x, end.z, player.tile.height), Render.ROPE_BALANCE)
            player.resetRenderAnimation()
            if (id == 10868) player.pyramidXp(56.4)
        }
    }
}

// ---- The top: climbing rocks 10851 take the pyramid top, the doorway 10855 / 10856 leads back to the base ----

// Darkan AgilityPyramidController.java:303 grabTop (10851): face east, anim 3063 now, two ticks later the pyramid top (6970) and
// "You find a pyramid top!"; nothing once it is taken (the top loc shows taken: varbit 1556 = 1 until the doorway).
shortcut(10851, "Climb") {
    obstacle {
        if (player.getVarbit(Pyr.TOP_VARBIT) == 1) return@obstacle
        player.faceTile(player.tile.transform(1, 0))
        player.animate(3063)
        wait(2)
        player.setVarbit(Pyr.TOP_VARBIT, 1)
        if (!player.inventory.add(PYRAMID_TOP_ITEM).hasSucceeded()) {
            world.spawn(GroundItem(PYRAMID_TOP_ITEM, amount = 1, tile = player.tile, owner = player))
        }
        player.playJingle(PYRAMID_TOP_JINGLE)
        player.queue { itemMessageBox("You find a pyramid top!", PYRAMID_TOP_ITEM) }
    }
}

// NOT Darkan (AgilityPyramidController.java:288 finishCourse refuses to leave without the top and gives a flat 500): the OSRS Wiki
// "Doorway (Agility Pyramid)" rule stays - the doorway always leads to the base, 300 + 8 x Agility level (max 1,000) with the top.
intArrayOf(10855, 10856).forEach { id ->
    shortcut(id, "Enter") {
        obstacle {
            wait(1)
            val topTaken = player.getVarbit(Pyr.TOP_VARBIT) == 1
            player.setVarbit(Pyr.TOP_VARBIT, 0)
            player.moveTo(Pyr.BASE)
            if (topTaken) {
                player.pyramidXp(minOf(1000, 300 + player.skills.getMaxLevel(Skills.AGILITY) * 8).toDouble())
            }
            player.queue { messageBox("You climb down the steep passage. It leads to the base of the", "pyramid.") }
        }
    }
}

// ---- Rolling stone blocks ----

/**
 * Darkan AgilityPyramidController.jumpRoller (:491): the player faces along the block's axis (his last step); success: anim 1115 +
 * forceMove(player + facing x 2, 5, 50), 12 xp; a failure (or a diagonal facing): anim 3064 + forceMove(Darkan's drop, 10, 60) one
 * plane down, then 60 (6 here) damage. The block loc shows rolled (its varbit = 1) for the jump.
 */
fun rollStoneBlock(
    player: Player,
    block: Pyr.StoneBlock,
) {
    val facing = player.faceDirection
    player.stopMovement()
    player.lockingQueue(lockState = LockState.FULL) {
        player.obstacleUntilUnlocked = true
        try {
            player.setVarbit(block.varbit, 1)
            player.playSound(Sfx.PYRAMID_SCRAPE)
            val diagonal = facing.getDeltaX() != 0 && facing.getDeltaZ() != 0
            if (diagonal || player.pyramidFailed()) {
                val (dx, dz, dh) = BLOCK_DROPS[block.varbit] ?: Triple(0, 0, -1)
                val drop = pyramidDrop(player, player.tile.transform(dx, dz, dh))
                player.animate(3064)
                if (drop != null) darkanForceMove(player, drop, startCycles = 10, speedCycles = 60, direction = cardinal(player.tile, drop))
                player.playSound(Sfx.FALL_LAND)
                player.hit(6)
                return@lockingQueue
            }
            player.playSound(Sfx.LAND_FLAT, delay = 40)
            darkanForceMove(player, player.tile.add(facing, 2), startCycles = 5, speedCycles = 50, anim = 1115, direction = facing)
            player.pyramidXp(12.0)
        } finally {
            player.setVarbit(block.varbit, 0)
        }
    }
}

/**
 * Darkan AgilityPyramidController.process (:208): a player standing on a block's 2x2 tiles with no walk steps left and not locked
 * jumps it - on this map the block's far edge is walled, so a walk over the block stops on it and the jump follows. A player who
 * stopped there walking across the block's axis (along the 2-wide lane) does not jump: Darkan's sideways jump would land in the
 * pyramid wall.
 */
fun checkStoneBlocks(player: Player) {
    if (player.isLocked() || player.isDead() || player.hasMoveDestination()) return
    val t = player.tile
    val block =
        Pyr.STONE_BLOCKS.firstOrNull { b -> t.height == b.corner.height && t.x - b.corner.x in 0..1 && t.z - b.corner.z in 0..1 } ?: return
    val facing = player.faceDirection
    val axisX = block.forward == Direction.EAST || block.forward == Direction.WEST
    val sideways = if (axisX) facing.getDeltaX() == 0 else facing.getDeltaZ() == 0
    if (sideways) return
    rollStoneBlock(player, block)
}

listOf(Pyr.REGION_MAIN, Pyr.REGION_TOP).forEach { region ->
    on_enter_region(region) {
        player.timers[PYRAMID_TICK] = 1
    }
    on_exit_region(region) {
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

// ---- Moving pyramid blocks (NPCs 3124 / 3125) ----

/**
 * Darkan AgilityPyramidBlock.processNPC (:41): a player on the extended block's tiles is pushed off - anim 3066 and forceMove(player
 * + the push to two tiles past the block's edge, one plane down, 10, 30), then 80 (8 here) damage. The block's own cycle stays the
 * OSRS Wiki / 2009scape one (15 ticks, out for 5; Darkan: 11 ticks, 3 of them dangerous) - NPC movement, not a crossing.
 */
fun pushOff(
    player: Player,
    block: Pyr.MovingBlock,
) {
    player.stopMovement()
    player.lockingQueue(lockState = LockState.FULL) {
        player.obstacleUntilUnlocked = true
        player.playSound(Sfx.LAND_FLAT, delay = 50)
        val drop = pyramidDrop(player, block.fall(player.tile))
        player.animate(3066)
        if (drop != null) darkanForceMove(player, drop, startCycles = 10, speedCycles = 30, direction = block.push)
        player.hit(8)
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
