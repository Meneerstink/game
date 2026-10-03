package gg.rsmod.plugins.content.mechanics.traversal.batch13

import gg.rsmod.plugins.content.skills.agility.*

/*
 * Obstacle batch 13 (owner priority #1, 2026-10-03), agent-handoffs/obstacle-batches/rest-batch-13.tsv: Erjolf's cave (the Ice
 * Strykewyrm cave, region 13910) and Arposandra rows. Tiles checked on this 667 map with MapProbe on 2026-10-03.
 */

// ---- Erjolf's cave / Ice Strykewyrm cave: flat rocks 42844-42849 'Jump', floating bridge 42877 / 42878 'Cross' --------------------------
// RS3 Wiki "The Tale of the Muspah" (completed here - QuestStatus): "You can walk around the cave by jumping on the flat rocks"; "Floating
// bridge" (ids 42877 at 3485,5542 / 42878 at 3485,5539): "It can be crossed to reach the centre of the icy cave". The cave is reached
// through 42793 (2737,3729 -> 3485,5511, object-teleports.json). The 667 map places the rocks in pairs across a stream, each rock a
// walkable tile with two water tiles between it and its partner (MapProbe 2026-10-03):
//   3486,5515 <-> 3486,5518, 3467,5543 <-> 3467,5546, 3506,5542 <-> 3506,5545, 3490,5534 <-> 3493,5534, 3492,5558 <-> 3495,5558.
// The jump: stand on the near rock, face the partner, stepping_stone jump 741 (Void, shared sourced_shortcuts) and one exact move onto
// the partner (45 cycles, start 30, as the shared stepping-stone jump). The floating bridge runs 3485,5539-5542 (42878, 42757, 42759,
// 42877) from the cave floor 3485,5538 to the island 3486,5543: one slide along the bridge and the step onto the island (or back).
// No donor gives these animations for this cave; the jump and slide are the shared stepping-stone / crossing patterns.
val FLAT_ROCK_PAIRS =
    listOf(
        Tile(3486, 5515, 0) to Tile(3486, 5518, 0),
        Tile(3467, 5543, 0) to Tile(3467, 5546, 0),
        Tile(3506, 5542, 0) to Tile(3506, 5545, 0),
        Tile(3490, 5534, 0) to Tile(3493, 5534, 0),
        Tile(3492, 5558, 0) to Tile(3495, 5558, 0),
    )
val ANIM_FLAT_ROCK_JUMP = 741
val MUSPAH_BRIDGE_FLOOR = Tile(3485, 5538, 0)
val MUSPAH_BRIDGE_NORTH_END = Tile(3485, 5542, 0)
val MUSPAH_ISLAND = Tile(3486, 5543, 0)

for (rock in 42844..42849) {
    shortcut(rock, "Jump") {
        obstacle { target ->
            val pair = FLAT_ROCK_PAIRS.firstOrNull { it.first.sameAs(target.tile) || it.second.sameAs(target.tile) }
            if (pair == null) {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                    "flat rock ${target.id} at ${target.tile.x},${target.tile.z}: no partner rock on this map"
                }
                player.message(gg.rsmod.game.model.entity.Entity.NOTHING_INTERESTING_HAPPENS)
                return@obstacle
            }
            val nearFirst = player.tile.getDistance(pair.first) <= player.tile.getDistance(pair.second)
            val start = if (nearFirst) pair.first else pair.second
            val landing = if (nearFirst) pair.second else pair.first
            val direction = Direction.between(start, landing)
            walkToTile(player, start)
            face(player, direction)
            wait(1)
            player.animate(ANIM_FLAT_ROCK_JUMP)
            exactMove(player, landing, delay = 45, direction = direction, startDelay = 30)
        }
    }
}

for (bridge in intArrayOf(42877, 42878)) {
    shortcut(bridge, "Cross") {
        obstacle {
            val toIsland = player.tile.z <= MUSPAH_BRIDGE_FLOOR.z
            if (toIsland) {
                walkToTile(player, MUSPAH_BRIDGE_FLOOR)
                face(player, Direction.NORTH)
                wait(1)
                exactMove(player, MUSPAH_BRIDGE_NORTH_END, delay = 120, direction = Direction.NORTH)
                exactMove(player, MUSPAH_ISLAND, delay = 30, direction = Direction.NORTH)
            } else {
                walkToTile(player, MUSPAH_ISLAND)
                face(player, Direction.SOUTH)
                wait(1)
                exactMove(player, MUSPAH_BRIDGE_NORTH_END, delay = 30, direction = Direction.SOUTH)
                exactMove(player, MUSPAH_BRIDGE_FLOOR, delay = 120, direction = Direction.SOUTH)
            }
        }
    }
}

// ---- Arposandra stairs 20653 'Climb-up' (3546,4581) -----------------------------------------------------------------------------------
// RS3 Wiki "Stairs (Arposandra)" (The Prisoner of Glouphrie): the middle-floor stairs up (20653) to the top floor are inaccessible -
// Player: "I can hear commotion upstairs, probably from all the gnomes we saw earlier. Now is probably not the best moment to barge in."
shortcut(20653, "Climb-up") {
    player.queue {
        chatPlayer(
            "I can hear commotion upstairs, probably from all the gnomes we saw earlier.",
            "Now is probably not the best moment to barge in.",
        )
    }
}
