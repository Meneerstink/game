package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.attr.WILDERNESS_AGILITY_STAGE
import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing

/**
 * Wilderness agility course - ported from Darkan on 2026-10-04 (owner: "port everything from Darkan"; Deadman escapes and pursuits
 * run through here; agent-handoffs/darkan-obstacles-map.md section 1c). Donor: import-source/donors/darkan/world-server-dev/.../
 * skills/agility/wilderness/WildernessAgility.kt (revision 727). Darkan's ids are the re-placed 727 locs; this 667 map keeps the
 * older ones, mapped by role (placement probe 2026-10-04): gate 65365 (in) -> door 2309 (2998,3917), gate 65367 (out) -> gates
 * 2307/2308 (2998/2997,3931), pipe 65362 -> 2288 (3004,3938 south end / 3004,3948 north end), rope swing 64696 -> 2283 (3005,3952),
 * stepping stone 64699 -> 37704 (3001,3960), log 64698 -> 2297 (3001,3945), cliffside 65734 -> rocks 2328 (2993-2995,3936).
 *
 * Mechanism, levels (52 ridge, 49 the rest), experience, failure rolls (Darkan Agility.rollSuccess(150, 250) = [agilitySuccess]),
 * fall tiles and messages are Darkan's; damage is Darkan's 26.6% of the current life points (Darkan cap 200 = 20 here, this server
 * counts life points 1:1). Tick mapping as DarkanShortcuts.kt: the task starts at Darkan's T+1, a Darkan coroutine's FIRST
 * wait(n) is n - 1 ticks and every later one n; darkanForceMove resumes when its glide has ended.
 */

val COMPLETION_BONUS_EXPERIENCE = 499.0
val DOOR_OPEN = 1543
val GATE_EAST_OPEN = 2040
val GATE_WEST_OPEN = 2042

/** Darkan WildernessAgility.kt:152: the rope swing is busy while someone swings ("inUse"); here the cycle it is free again. */
val ROPE_SWING_BUSY_UNTIL = AttributeKey<Int>()

fun Player.wildernessXp(xp: Double) = addXp(Skills.AGILITY, xp, checkBrawlingGloves = true)

/** Darkan Agility.hasLevel: "You need an agility level of N to use this obstacle." */
fun Player.wildernessLevel(level: Int) = hasAgility(level, "You need an agility level of $level to use this obstacle.")

/** Darkan `min((hitpoints * 26.6 / 100).roundToInt(), 200)` in this server's 1:1 life points. */
fun Player.wildernessFallHit() {
    hit(Math.min(Math.round(getCurrentLifepoints() * 26.6 / 100).toInt(), 20))
}

/**
 * Darkan WildernessAgility.kt:39 movePlayer: BAS 155 and one collision-free walk along the ridge to [to]; the roll comes after the
 * coroutine's first wait(8) = 7 tiles; a fall plays 771 / 770 (by heading and side) while the walk goes on one more tick, then the
 * teleport to the west (2995,3923) or east (3001,3924) side, BAS -1, and a tick later the hit. Success: BAS -1 after the walk
 * (wait(9) more = 16 ticks in all), "You skillfully balance across the ridge...", 15 xp.
 * Darkan walks through its 727 gates without opening them; on this map the ridge runs through a closed door (2998,3917) and a closed
 * double gate (2998/2997,3931), so both are swung open for the crossing - the body never walks through a shut door.
 */
suspend fun QueueTask.walkRidge(
    player: Player,
    to: Tile,
    headingSouth: Boolean,
) {
    locAt(player.world, Tile(2998, 3917), Objs.DOOR_2309)?.let { DoorCrossing.swingOpenFor(player, it, DOOR_OPEN, ticks = 17) }
    locAt(player.world, Tile(2998, 3931), Objs.GATE_2307)?.let { DoorCrossing.swingOpenFor(player, it, GATE_EAST_OPEN, ticks = 17) }
    player.filterableMessage("You go through the gate and try to edge over the ridge...")
    player.setRenderAnimation(Render.ROPE_BALANCE)
    if (!agilitySuccess(player, 150, 250)) {
        walkOverTile(player, Tile(to.x, player.tile.z + if (headingSouth) -7 else 7), Render.ROPE_BALANCE)
        val fallToWest = java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
        player.animate(if (fallToWest == headingSouth) 771 else 770)
        wait(1)
        player.moveTo(if (fallToWest) Tile(2995, 3923) else Tile(3001, 3924))
        player.filterableMessage("You lose your balance and fall off the ridge.")
        player.resetRenderAnimation()
        player.animate(-1)
        wait(1)
        player.wildernessFallHit()
        return
    }
    // The ridge is 15-16 tiles, longer than walkOverTile's bounded crossing: the same collision-free walk, unbounded.
    val startZ = player.tile.z
    arriveAt(player, to, MovementQueue.StepType.FORCED_WALK, detectCollision = false)
    // Darkan resets the BAS 16 ticks after the walk began (wait(8) + wait(9), the first one 7 ticks).
    val walked = Math.abs(to.z - startZ)
    if (walked < 16) wait(16 - walked)
    player.resetRenderAnimation()
    player.filterableMessage("You skillfully balance across the ridge...")
    player.wildernessXp(15.0)
}

// Darkan WildernessAgility.kt:29 (65365, in): lvl 52; from (2998, 3916) the ridge walk to (gate.x, 3931).
on_obj_option(obj = Objs.DOOR_2309, option = "Open") {
    obstacle { door ->
        if (player.tile.z > 3916) {
            // On the ridge side of the door: just back out through it (Darkan has no click from this side).
            passDoor(player, door, DOOR_OPEN)
            return@obstacle
        }
        if (!player.wildernessLevel(52)) return@obstacle
        walkToTile(player, Tile(2998, 3916))
        walkRidge(player, Tile(2998, 3931), headingSouth = false)
    }
}

// Darkan WildernessAgility.kt:29 (65367, out): from (2998, 3931) the ridge walk to (gate.x + 1 = 2998, 3915).
listOf(Objs.GATE_2307 to GATE_EAST_OPEN, Objs.GATE_2308 to GATE_WEST_OPEN).forEach { (id, opened) ->
    on_obj_option(obj = id, option = "Open") {
        obstacle { gate ->
            if (player.tile.z < 3931) {
                // On the ridge side of the gate: just up through it into the course (Darkan has no click from this side).
                passDoor(player, gate, opened)
                return@obstacle
            }
            walkToTile(player, Tile(2998, 3931))
            walkRidge(player, Tile(2998, 3915), headingSouth = true)
        }
    }
}

// Darkan WildernessAgility.kt:114 (65362): lvl 49; route to (pipe.x, 3937 | 3950); anim 10646 + forceMove((x, 3940 | 3947), 20, 90);
// wait 5 (first wait(6)); anim -1, tele (x, 3947 | 3940); wait(0) = 1 tick; anim 12458 + forceMove((x, 3950 | 3937), 10, 60); wait 3;
// 12.5 xp. Both directions, as Darkan.
on_obj_option(obj = Objs.OBSTACLE_PIPE_2288, option = "Squeeze-through") {
    if (!player.wildernessLevel(49)) return@on_obj_option
    obstacle { target ->
        val north = target.tile.z == 3938
        val x = target.tile.x
        val dir = if (north) Direction.NORTH else Direction.SOUTH
        walkToTile(player, Tile(x, if (north) 3937 else 3950))
        darkanForceMove(player, Tile(x, if (north) 3940 else 3947), startCycles = 20, speedCycles = 90, anim = 10646, direction = dir)
        wait(2)
        player.animate(-1)
        player.moveTo(Tile(x, if (north) 3947 else 3940))
        wait(1)
        darkanForceMove(player, Tile(x, if (north) 3950 else 3937), startCycles = 10, speedCycles = 60, anim = 12458, direction = dir)
        wait(1)
        player.wildernessXp(12.5)
        // The pipe starts a lap.
        player.attr[WILDERNESS_AGILITY_STAGE] = 1
    }
}

// Darkan WildernessAgility.kt:145 (64696): lvl 49; refused north of 3953; one swinger at a time; route to (3005, 3953); face, anim
// 751, rope anim 497; success: forceMove((rope.x, 3958), 30, 90), first wait(4) = 3 ticks, "You skillfully swing across the rope.",
// 20 xp; fail: forceMove((rope.x, 3955), 30, 55), 3 ticks, "You lose your grip and fall down.", tele (3005, 10356) onto the spikes,
// "Ouch!", hit, a tick later one collision-free step north.
on_obj_option(obj = Objs.ROPESWING_2283, option = "Swing-on") {
    val target = player.getInteractingGameObj()
    if (!player.wildernessLevel(49)) return@on_obj_option
    if (player.tile.z > 3953) {
        player.message("You can't see a good way to jump from here.")
        return@on_obj_option
    }
    if ((target.attr[ROPE_SWING_BUSY_UNTIL] ?: 0) > world.currentCycle) {
        player.message("Someone else is using the ropeswing right now.")
        return@on_obj_option
    }
    // A cycle stamp instead of Darkan's flag: a swing cut off by death or logout can never leave the rope blocked.
    target.attr[ROPE_SWING_BUSY_UNTIL] = world.currentCycle + 20
    obstacle {
        try {
            walkToTile(player, Tile(3005, 3953))
            face(player, Direction.NORTH)
            animateLoc(world, target, 497)
            if (agilitySuccess(player, 150, 250)) {
                darkanForceMove(player, Tile(target.tile.x, 3958), startCycles = 30, speedCycles = 90, anim = 751, direction = Direction.NORTH)
                player.filterableMessage("You skillfully swing across the rope.")
                player.wildernessXp(20.0)
                player.advanceCourse(WILDERNESS_AGILITY_STAGE, 2)
            } else {
                darkanForceMove(player, Tile(target.tile.x, 3955), startCycles = 30, speedCycles = 55, anim = 751, direction = Direction.NORTH)
                wait(1)
                player.filterableMessage("You lose your grip and fall down.")
                player.moveTo(Tile(3005, 10356))
                player.animate(-1)
                animateLoc(world, target, -1)
                player.forceChat("Ouch!")
                player.wildernessFallHit()
                wait(1)
                walkOverTile(player, Tile(3005, 10357), Render.NONE)
            }
        } finally {
            target.attr.remove(ROPE_SWING_BUSY_UNTIL)
        }
    }
}

// Darkan WildernessAgility.kt:196 (64699): lvl 49; only from (3002, 3960); six hops west, each forceMove((3002 - (i + 1), y), 741,
// 5, 30) and 2 ticks (first wait(3) = 2, then wait(2)); the roll before the fourth hop: anim 771, a tick later tele (3000, 3964) + hit.
on_obj_option(obj = Objs.STEPPING_STONE_37704, option = "Cross") {
    if (!player.wildernessLevel(49)) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(3002, 3960))
        player.message("You carefully start crossing the stepping stones...")
        for (i in 0..5) {
            if (i == 3 && !agilitySuccess(player, 150, 250)) {
                player.message("...You lose your footing and fall into the lava.")
                player.animate(771)
                wait(1)
                player.animate(-1)
                player.moveTo(Tile(3000, 3964))
                player.wildernessFallHit()
                return@obstacle
            }
            player.playSound(2461)
            darkanForceMove(player, Tile(3002 - (i + 1), player.tile.z), startCycles = 5, speedCycles = 30, anim = 741, direction = Direction.WEST)
            wait(1)
        }
        player.message("...You safely cross to the other side.")
        player.wildernessXp(20.0)
        player.advanceCourse(WILDERNESS_AGILITY_STAGE, 3)
    }
}

// Darkan WildernessAgility.kt:229 (64698): lvl 49; from (3002, 3945) addWalkSteps(2994, 3945) without clipping, BAS 155; roll after
// the first wait(5) = 4 tiles: anim 2582, a tick later tele (2999, 10346) onto the spikes, "Ouch!", hit, a tick later one step
// south; success: BAS -1 after wait(5) more (9 ticks for the 8 tiles), 20 xp.
on_obj_option(obj = Objs.LOG_BALANCE_2297, option = "Walk-across") {
    if (!player.wildernessLevel(49)) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(3002, 3945))
        player.filterableMessage("You walk carefully across the slippery log...")
        if (agilitySuccess(player, 150, 250)) {
            walkOverTile(player, Tile(2994, 3945), Render.ROPE_BALANCE)
            wait(1)
            player.resetRenderAnimation()
            player.wildernessXp(20.0)
            player.filterableMessage("...You skillfully edge across the gap.")
            player.advanceCourse(WILDERNESS_AGILITY_STAGE, 4)
        } else {
            walkOverTile(player, Tile(2998, 3945), Render.ROPE_BALANCE)
            player.filterableMessage("...You lose your footing and fall below.")
            player.animate(2582)
            wait(1)
            player.animate(-1)
            player.moveTo(Tile(2999, 10346))
            player.resetRenderAnimation()
            player.forceChat("Ouch!")
            player.wildernessFallHit()
            wait(1)
            walkOverTile(player, Tile(2999, 10345), Render.NONE)
        }
    }
}

// Darkan WildernessAgility.kt:276 (65734): lvl 49; only from the course side; route to the tile in front; face, anim 3378
// (rocks climb, same tick: first wait(1)), wait 4, tele across, anim -1, "You reach the top.", +499 on a lap. Darkan's 727 cliffside lands 4 tiles on
// (and 2 east of its own placement); this map's rocks 2328 are one row (z 3936) with the walkable ground 4 tiles on at z 3933.
on_obj_option(obj = Objs.ROCKS_2328, option = "Climb") {
    if (!player.wildernessLevel(49)) return@on_obj_option
    obstacle { target ->
        if (player.tile.z <= target.tile.z) return@obstacle
        walkToTile(player, Tile(player.tile.x.coerceIn(2993, 2995), 3937))
        face(player, Direction.SOUTH)
        player.animate(3378)
        wait(4)
        player.moveTo(Tile(player.tile.x, 3933))
        player.animate(-1)
        player.filterableMessage("You reach the top.")
        if (player.courseStage(WILDERNESS_AGILITY_STAGE) == 4) {
            player.attr[WILDERNESS_AGILITY_STAGE] = 0
            player.wildernessXp(COMPLETION_BONUS_EXPERIENCE)
            gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.WILDERNESS_COURSE)
        }
    }
}
