package gg.rsmod.plugins.content.skills.agility

/**
 * Werewolf Agility Course (under Canifis, level 60) - ported from Darkan on 2026-10-04 (owner: "port everything from Darkan";
 * agent-handoffs/darkan-obstacles-map.md section 1e: the course was missing). Donor: import-source/donors/darkan/world-server-dev/
 * .../skills/agility/WerewolfAgility.kt (revision 727). Every Darkan id is in this 667 cache on Darkan's own tiles (placement probe
 * 2026-10-04): stepping stones 35996 (3538,9875 / 3538,9877 / 3540,9877 / 3540,9879 / 3540,9881), hurdles 5133 / 5134 / 5135 (z 9893,
 * 9896, 9899), pipes 5152 (x 3538 / 3541 / 3544, z 9905), skull slope 5136 (3532, 9909-9911), zip line 5139-5141 (3528,9911), ladder
 * 5130 (3549,9864), trapdoor 5131 / 5132 (3543,3462). The Agility Boss 1661, the trainers 1663 and the stick trainer 1664 are spawned
 * by spawns_14234.
 *
 * Animations, client cycles, landings, experience, messages and the zip line's weight / level failure table are Darkan's; damage is
 * Darkan's / 10 (this server counts life points 1:1). Tick mapping as DarkanShortcuts.kt; darkanForceMove resumes when its glide has
 * ended. Where Darkan slides from wherever the route ended to a fixed tile (hurdles, pipes, skull slope), the start is pinned to the
 * landing's own lane in front of the obstacle, the route end of a straight approach - a corner start would glide diagonally over
 * the hurdle's or pipe's edge.
 */

val STICK = 4179
val STICK_TILE = Tile(3540, 9911)

/** Darkan WerewolfAgility.kt:29: the stones in course order. */
val WEREWOLF_STONES = listOf(Tile(3538, 9875), Tile(3538, 9877), Tile(3540, 9877), Tile(3540, 9879), Tile(3540, 9881))

/** Darkan "lastVisitedStoneTile": the index of the last stone jumped to. */
val WEREWOLF_LAST_STONE = AttributeKey<Int>()

fun Player.werewolfXp(xp: Double) = addXp(Skills.AGILITY, xp, checkBrawlingGloves = true)

/** Darkan Agility.hasLevel(player, 60). */
fun Player.werewolfLevel() = hasAgility(60, "You need an agility level of 60 to use this obstacle.")

fun nearbyNpc(
    player: Player,
    id: Int,
    range: Int,
): Npc? = player.world.npcs.firstOrNull { it.id == id && it.tile.height == player.tile.height && it.tile.isWithinRadius(player.tile, range) }

/** Darkan steppingStone (:256): lock, 10 xp, forceMove(stone, 741, 0, 25). */
suspend fun QueueTask.werewolfStone(
    player: Player,
    stone: Tile,
) {
    player.werewolfXp(10.0)
    val to = Tile(stone.x, stone.z, player.tile.height)
    val dx = to.x - player.tile.x
    val dz = to.z - player.tile.z
    val dir = if (Math.abs(dx) >= Math.abs(dz)) (if (dx >= 0) Direction.EAST else Direction.WEST) else (if (dz >= 0) Direction.NORTH else Direction.SOUTH)
    darkanForceMove(player, to, startCycles = 0, speedCycles = 25, anim = 741, direction = dir)
}

/**
 * Darkan throwStick (:262): when the player has no stick and none lies at the fetch spot, the Agility Boss faces north, shouts
 * "FETCH!!!!!", and two ticks later throws (anim 6547; Darkan's projectile 1158 is left out) - the stick lands at (3540, 9911) for
 * the player.
 */
fun throwStick(player: Player) {
    if (player.inventory.contains(STICK)) return
    val world = player.world
    val lying =
        world.chunks.get(STICK_TILE, createIfNeeded = true)
            ?.getEntities<GroundItem>(STICK_TILE, EntityType.GROUND_ITEM)
            ?.any { it.item == STICK && it.isOwnedBy(player) } == true
    if (lying) return
    val boss = nearbyNpc(player, Npcs.AGILITY_BOSS, 8) ?: return
    boss.faceTile(boss.tile.transform(0, 1))
    boss.forceChat("FETCH!!!!!")
    world.queue {
        wait(2)
        boss.animate(6547)
        world.spawn(GroundItem(STICK, amount = 1, tile = STICK_TILE, owner = player))
    }
}

// Darkan WerewolfAgility.kt:42 (1661): the course explanation.
on_npc_option(npc = Npcs.AGILITY_BOSS, option = "Talk-To") {
    player.queue {
        chatPlayer("How do I use the agility course?")
        chatNpc("I'll throw you a stick, which you need to fetch as quickly as possible, from the area beyond the pipes.")
        chatNpc(
            "Be wary of the deathslide - you must hang by your teeth, and if your strength is not up to the job you will fall into a pit " +
                "of spikes. Also, I would advise not carrying too much extra weight.",
        )
        chatNpc("Bring the stick back to the werewolf waiting at the end of the death slide to get your agility bonus.")
        chatNpc("I will throw your stick as soon as you jump onto the first stone.")
    }
}

// Darkan WerewolfAgility.kt:52 (1664): every stick carried -> 380 xp each, "You give the stick to the werewolf."
on_npc_option(npc = Npcs.AGILITY_TRAINER_1664, option = "Give-Stick") {
    val count = player.inventory.getItemCount(STICK)
    if (count > 0) {
        player.inventory.remove(STICK, count)
        player.werewolfXp(380.0 * count)
        player.message("You give the stick to the werewolf.")
    } else {
        player.queue { chatNpc("You haven't got a stick to give me!") }
    }
}

// Darkan WerewolfAgility.kt:74 (35996): lvl 60, not from north of 9880. The first stone: walk to (3538, 9873), the boss throws the
// stick, next tick the jump; every later stone only from a stone exactly two tiles away (orthogonal) and only forwards in the order.
// Each jump: forceMove(stone, 741, 0, 25), 10 xp.
shortcut(35996, "Jump-to", lineOfSightDistance = 2) {
    val target = player.getInteractingGameObj()
    if (!player.werewolfLevel() || player.tile.z > 9880) return@shortcut
    if (target.tile.x == player.tile.x && target.tile.z == player.tile.z) return@shortcut
    val index = WEREWOLF_STONES.indexOfFirst { it.x == target.tile.x && it.z == target.tile.z }
    if (index == -1) return@shortcut
    if (index == 0) {
        if (player.tile.z >= 9875) return@shortcut
        obstacle {
            walkToTile(player, Tile(3538, 9873))
            throwStick(player)
            wait(1)
            werewolfStone(player, target.tile)
            player.attr.remove(WEREWOLF_LAST_STONE)
        }
        return@shortcut
    }
    val dx = Math.abs(player.tile.x - target.tile.x)
    val dz = Math.abs(player.tile.z - target.tile.z)
    if (!((dx == 2 && dz == 0) || (dx == 0 && dz == 2))) return@shortcut
    val last = player.attr[WEREWOLF_LAST_STONE]
    if (last != null && last >= index) return@shortcut
    obstacle {
        werewolfStone(player, target.tile)
        if (index == WEREWOLF_STONES.size - 1) player.attr.remove(WEREWOLF_LAST_STONE) else player.attr[WEREWOLF_LAST_STONE] = index
    }
}

// Darkan WerewolfAgility.kt:111 (5133 / 5134 / 5135): lvl 60, only from the south; 20 xp; forceMove((hurdle.x + (5134 ? 0 : 1),
// hurdle.y + 1), 1603, 0, 45).
intArrayOf(5133, 5134, 5135).forEach { id ->
    shortcut(id, "Jump") {
        val target = player.getInteractingGameObj()
        if (player.tile.z > target.tile.z) return@shortcut
        if (!player.werewolfLevel()) return@shortcut
        obstacle {
            val x = target.tile.x + if (id == 5134) 0 else 1
            walkToTile(player, Tile(x, target.tile.z - 1))
            player.werewolfXp(20.0)
            darkanForceMove(player, Tile(x, target.tile.z + 1), startCycles = 0, speedCycles = 45, anim = 1603, direction = Direction.NORTH)
        }
    }
}

// Darkan WerewolfAgility.kt:119 (5152): lvl 60, only from the south; lock 1; forceMove((pipe.x, pipe.y + 4), 10580, 10, 60); 15 xp.
// The pipes' south ends are the z 9905 locs; a click on a north end (z 9908) from the south crawls the same pipe from its south end.
shortcut(5152, "Squeeze-through") {
    val target = player.getInteractingGameObj()
    if (player.tile.z > target.tile.z) return@shortcut
    if (!player.werewolfLevel()) return@shortcut
    obstacle {
        val southEnd = 9905
        walkToTile(player, Tile(target.tile.x, southEnd - 1))
        darkanForceMove(player, Tile(target.tile.x, southEnd + 4), startCycles = 10, speedCycles = 60, anim = 10580, direction = Direction.NORTH)
        player.werewolfXp(15.0)
    }
}

// Darkan WerewolfAgility.kt:128 (5136): lvl 60, only from the east; forceMove((slope.x - 2, slope.y), 2049, 0, 105), anim -1, 25 xp.
shortcut(5136, "Climb-up") {
    val target = player.getInteractingGameObj()
    if (player.tile.x < target.tile.x) return@shortcut
    if (!player.werewolfLevel()) return@shortcut
    obstacle {
        walkToTile(player, Tile(target.tile.x + 1, target.tile.z))
        darkanForceMove(player, Tile(target.tile.x - 2, target.tile.z), startCycles = 0, speedCycles = 105, anim = 2049, direction = Direction.WEST)
        player.animate(-1)
        player.werewolfXp(25.0)
    }
}

/** Darkan WerewolfAgility.kt:149-174: the zip line's success rates (rate at level 1, rate at 99, out of 256) by weight and levels. */
fun zipLineRates(player: Player): Pair<Int, Int> {
    val weight = player.weight
    val agility = player.skills.getCurrentLevel(Skills.AGILITY)
    val strength = player.skills.getCurrentLevel(Skills.STRENGTH)
    if (agility >= 80 && strength >= 80 && weight <= 2) return 256 to 256
    val base =
        when {
            weight < -5 -> -30.0
            weight < 0 -> -15.0
            weight <= 0 -> 5.0
            weight <= 2 -> 15.0
            weight <= 5 -> 20.0
            weight <= 10 -> 25.0
            weight <= 20 -> 30.0
            weight <= 30 -> 35.0
            weight <= 40 -> 40.0
            weight <= 50 -> 45.0
            weight <= 60 -> 50.0
            else -> 80.0
        }
    val adjusted =
        when {
            agility >= 80 && strength >= 80 -> base * 0.5
            agility >= 70 && strength >= 70 -> base * 0.75
            agility >= 60 && strength >= 60 -> base * 0.9
            else -> base
        }
    return (135 - adjusted).coerceIn(0.0, 256.0).toInt() to 250
}

// Darkan WerewolfAgility.kt:138 (5139 / 5140 / 5141): lvl 60; walk to (3528, 9910); a trainer (1663) cheers; face south, first
// wait(2) = 1 tick, anim 1601 "You bravely cling on to the death slide by your teeth ...", 2 ticks, "WAAAAAARRRGGGHHH!!!!!!";
// success: forceMove((3528, 9873), 1602, 0, 240), anim -1, 200 xp, "... and land safely on your feet."; failure (half / half):
// forceMove(midway (3528, 9890 | 9882), 1602, 0, 180 | 200), anim -1, forceMove((3526, 9887 | 9879), 766, 0, 20), anim -1,
// "... only to fall from a great height!", 150-300 | 120-200 (/10 here) damage, 120 | 140 xp. Then the player's sticks still lying
// at the fetch spot are removed.
intArrayOf(5139, 5140, 5141).forEach { id ->
    shortcut(id, "Teeth-grip") {
        if (!player.werewolfLevel()) return@shortcut
        val (rate1, rate99) = zipLineRates(player)
        obstacle {
            walkToTile(player, Tile(3528, 9910))
            nearbyNpc(player, Npcs.AGILITY_TRAINER, 20)?.forceChat(
                if (java.util.concurrent.ThreadLocalRandom.current().nextBoolean()) "Now a true test of teeth..." else "Don't let the spikes or the blood put you off...",
            )
            face(player, Direction.SOUTH)
            wait(1)
            player.animate(1601)
            player.message("You bravely cling on to the death slide by your teeth ...")
            wait(2)
            player.forceChat("WAAAAAARRRGGGHHH!!!!!!")
            if (agilitySuccess(player, rate1, rate99)) {
                darkanForceMove(player, Tile(3528, 9873), startCycles = 0, speedCycles = 240, anim = 1602, direction = Direction.SOUTH)
                player.animate(-1)
                player.werewolfXp(200.0)
                player.message("... and land safely on your feet.")
                nearbyNpc(player, Npcs.AGILITY_TRAINER_1664, 20)?.forceChat(
                    if (java.util.concurrent.ThreadLocalRandom.current().nextBoolean()) "Remember - no stick, no agility bonus!" else "Don't forget to give me the stick when you're done!",
                )
            } else {
                val first = java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                val midway = if (first) Tile(3528, 9890) else Tile(3528, 9882)
                val fall = if (first) Tile(3526, 9887) else Tile(3526, 9879)
                darkanForceMove(player, midway, startCycles = 0, speedCycles = if (first) 180 else 200, anim = 1602, direction = Direction.SOUTH)
                player.animate(-1)
                darkanForceMove(player, fall, startCycles = 0, speedCycles = 20, anim = 766, direction = Direction.SOUTH)
                player.animate(-1)
                player.message("... only to fall from a great height!")
                player.hit(if (first) java.util.concurrent.ThreadLocalRandom.current().nextInt(15, 31) else java.util.concurrent.ThreadLocalRandom.current().nextInt(12, 21))
                player.werewolfXp(if (first) 120.0 else 140.0)
            }
            world.chunks.get(STICK_TILE, createIfNeeded = true)
                ?.getEntities<GroundItem>(STICK_TILE, EntityType.GROUND_ITEM)
                ?.filter { it.item == STICK && it.isOwnedBy(player) }
                ?.forEach { world.remove(it) }
        }
    }
}

// Darkan WerewolfAgility.kt:245 (5130): useLadder((3543, 3463, 0)) - anim 828, teleport two ticks later.
shortcut(Objs.LADDER_5130, "Climb-up") {
    obstacle {
        player.animate(828)
        wait(2)
        player.moveTo(Tile(3543, 3463, 0))
    }
}

// Darkan WerewolfAgility.kt:239 (5132): useLadder((3549, 9865, 0)) - anim 828, teleport two ticks later.
shortcut(Objs.TRAPDOOR_5132, "Climb-down") {
    obstacle {
        player.animate(828)
        wait(2)
        player.moveTo(Tile(3549, 9865, 0))
    }
}

// The closed trapdoor 5131 (Darkan opens it with its generic trapdoor handler): it opens as 5132 for a minute.
shortcut(Objs.TRAPDOOR_5131, "Open") {
    val trapdoor = player.getInteractingGameObj()
    world.remove(trapdoor)
    val open = DynamicObject(id = Objs.TRAPDOOR_5132, type = trapdoor.type, rot = trapdoor.rot, tile = Tile(trapdoor.tile))
    world.spawn(open)
    world.queue {
        wait(100)
        world.remove(open)
        world.spawn(DynamicObject(trapdoor))
    }
}
