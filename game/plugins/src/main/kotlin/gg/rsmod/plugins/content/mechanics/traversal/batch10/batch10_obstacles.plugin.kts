package gg.rsmod.plugins.content.mechanics.traversal.batch10

import gg.rsmod.plugins.content.skills.agility.animateLoc
import gg.rsmod.plugins.content.skills.agility.doorStep
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.hasAgility
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * Obstacle batch 10 (owner priority #1, 2026-10-03), rest rows of agent-handoffs/obstacle-batches/rest-batch-10.tsv. Pure teleports of
 * the same batch are data: data/cfg/object-teleports/batch-10.json. Every start and landing tile below was checked on this 667 map
 * with MapProbe (walkable, connected to the side it belongs to). Quest gates: QuestStatus treats every quest that is not one of the
 * server's choice quests as completed, so the post-quest behaviour of a source applies (Tree Gnome Village, The Path of Glouphrie,
 * The Fremennik Isles, Regicide).
 */

// ---- Ogre island ropeswing 2324 (2511,3090, 1x6 over the river west of Yanille) --------------------------------------------------------
// OSRS Wiki Ropeswing (Ogre island): leaves the island for the mainland, 10 Agility, 3 xp, permanent rope. 2009scape JangerBerryPlugin:
// stand at 2511,3092 (within 2 tiles, else "I can't reach that."), loc animation 497, player animation 751, forced move to 2511,3096,
// "You skillfully swing across.". Map: the river is 2508-2520,3093-3095; 2511,3092 (island) and 2511,3096 (mainland) are walkable.
val ROPESWING_START = Tile(2511, 3092, 0)
val ROPESWING_LANDING = Tile(2511, 3096, 0)

shortcut(2324, "Swing-on") {
    val rope = player.getInteractingGameObj()
    if (rope.tile.x != 2511 || rope.tile.z != 3090) {
        gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.fallback(player)
        return@shortcut
    }
    if (player.tile.z > ROPESWING_START.z) {
        player.message("I can't reach that.")
        return@shortcut
    }
    if (!player.hasAgility(10, "You need an Agility level of 10 to use this ropeswing.")) return@shortcut
    obstacle { target ->
        walkToTile(player, ROPESWING_START)
        face(player, Direction.NORTH)
        wait(1)
        animateLoc(world, target, 497)
        player.animate(751)
        exactMove(player, ROPESWING_LANDING, direction = Direction.NORTH)
        player.addXp(Skills.AGILITY, 3.0)
        player.message("You skillfully swing across.")
    }
}

// ---- Battlefield of Khazard crumbled wall 12762 (2509,3253) ----------------------------------------------------------------------------
// RS3 Wiki Crumbled wall (Battlefield) 12762: the Khazard stronghold wall reduced by the gnome ballista (Tree Gnome Village); players
// leave either via the wall again or the door. 2009scape TreeGnomeVillageListeners ClimbWall (quest stage > 30): the dialogue "The wall
// has been reduced to rubble. It should be possible to climb over the remains", then animation 839 and a forced move 2 tiles north or
// south. Map: 2509,3252 (south) and 2509,3254 (north, inside) are walkable. (The 2009scape commander chat only belongs to the quest.)
shortcut(12762, "Climb-over") {
    obstacle { target ->
        val north = player.tile.z <= target.tile.z
        val start = Tile(target.tile.x, target.tile.z + if (north) -1 else 1, 0)
        val landing = Tile(target.tile.x, target.tile.z + if (north) 1 else -1, 0)
        walkToTile(player, start)
        face(player, if (north) Direction.NORTH else Direction.SOUTH)
        player.animate(839)
        exactMove(player, landing, delay = 60, direction = if (north) Direction.NORTH else Direction.SOUTH)
        // Its own unlocked task (ROOT_CAUSES #152): inside the obstacle the line held the player FULL-locked until "continue".
        player.queue { messageBox("The wall has been reduced to rubble. It should be possible to climb over the remains") }
    }
}

// ---- Mourner Headquarters fence 2068 (2541,3331, West Ardougne) ------------------------------------------------------------------------
// OSRS Wiki Fence (Biohazard) 2068: "at the back of the Mourner Headquarters", option Squeeze-through (examine "I can squeeze through
// it."). The fence is a wall on the edge between 2541,3331 and 2542,3331 (MapProbe: 2541 blocked east, 2542 blocked west, both
// walkable). No source gives an animation or message, so it is crossed as the shared doorway step (doorStep: walk to the tile in front,
// one step through the wall edge), never through scenery.
shortcut(2068, "Squeeze-through") {
    obstacle { target ->
        val far = if (player.tile.x <= target.tile.x) Tile(target.tile.x + 1, target.tile.z, 0) else Tile(target.tile.x, target.tile.z, 0)
        doorStep(player, far)
    }
}

// ---- Arandar Huge Gate 3944 / 3945 (2384-2387,3334) ------------------------------------------------------------------------------------
// OSRS Wiki Huge Gate: "Enter" passes through the gate into Arandar (two-way after Regicide - completed here, QuestStatus); 3944/3945
// are the unlocked state. Map: the gate fills 2384-2387 on row 3334; 3333 (south) and 3335 (north) are walkable on those columns. The
// player walks to the tile in front of the gate in his own column and is moved through the open gate to the tile behind it (no source
// animation).
listOf(3944, 3945).forEach { gate ->
    shortcut(gate, "Enter") {
        obstacle { _ ->
            val x = player.tile.x.coerceIn(2384, 2387)
            val north = player.tile.z <= 3334
            walkToTile(player, Tile(x, if (north) 3333 else 3335, 0))
            val direction = if (north) Direction.NORTH else Direction.SOUTH
            face(player, direction)
            wait(1)
            // A forced move through the open gate (owner E17: no collision-free walk through the loc); no source has an animation.
            exactMove(player, Tile(x, if (north) 3335 else 3333, 0), delay = 60, direction = direction)
        }
    }
}

// ---- Poison Waste river grapple: Tree 26724 / 26725 (2333,3077 / 2332,3077) ---------------------------------------------------------------
// RS3 Wiki Mithril grapple: "Cross to and from south of Castle Wars and the spirit tree in the Poison Wastes" - 47 Ranged, 45 Agility,
// 43 Strength; OSRS Wiki The Path of Glouphrie: "Use a crossbow and mith grapple on the tree ... to cross". Equipment checks and the
// grapple class animations are Void Grapple.kt's river crossing (River Lum, grapple_shortcuts.plugin.kts): crossbow shot 4230 + sound
// 2928, into the water 4466 + 2929, across, out 4467 (no source has this tree's own sequence). Map lane x=2334: 2334,3077 (south bank,
// connected to Castle Wars side 2345,3070) and 2334,3085 (north bank, connected to the sewer entrance 2321,3100); 3078-3084 is river.
val GRAPPLE_ITEM = 9419
val GRAPPLE_CROSSBOWS = listOf(9174, 9176, 9177, 9179, 9181, 9183, 9185)
val POISON_WASTE_SOUTH = Tile(2334, 3077, 0)
val POISON_WASTE_NORTH = Tile(2334, 3085, 0)

listOf(26724, 26725).forEach { tree ->
    shortcut(tree, "Grapple") {
        obstacle { _ ->
            val north = player.tile.z <= POISON_WASTE_SOUTH.z
            val start = if (north) POISON_WASTE_SOUTH else POISON_WASTE_NORTH
            val landing = if (north) POISON_WASTE_NORTH else POISON_WASTE_SOUTH
            val direction = if (north) Direction.NORTH else Direction.SOUTH
            walkToTile(player, start)
            face(player, direction)
            wait(1)
            if (player.skills.getCurrentLevel(Skills.RANGED) < 47 ||
                player.skills.getCurrentLevel(Skills.AGILITY) < 45 ||
                player.skills.getCurrentLevel(Skills.STRENGTH) < 43
            ) {
                player.queue { messageBox("You need at least 47 Ranged, 45 Agility and 43 Strength to do that.") }
                return@obstacle
            }
            if (player.equipment[EquipmentType.AMMO.id]?.id != GRAPPLE_ITEM) {
                player.message("You need a mithril grapple tipped bolt with a rope to do that.")
                return@obstacle
            }
            if (player.equipment[EquipmentType.WEAPON.id]?.id !in GRAPPLE_CROSSBOWS) {
                player.message("You need a crossbow equipped to do that.")
                return@obstacle
            }
            player.animate(4230)
            player.playSound(2928)
            wait(3)
            player.animate(4466)
            player.playSound(2929)
            exactMove(player, landing, delay = 160, direction = direction)
            player.animate(4467)
        }
    }
}

// ---- Neitiznot rope bridges 21306-21315 (plane-1 decks) -------------------------------------------------------------------------------
// OSRS Wiki Rope bridge (Neitiznot): "Walk-across", Agility shortcut, 40 Agility, 0 xp, The Fremennik Isles (completed here) - the
// north-eastern bridge to the central Fremennik Isles mine, i.e. 21314/21315 at x=2378. The 667 map builds every bridge as a walkable
// plane-1 deck between two end locs (MapProbe plane 1: a one-tile corridor, east/west blocked) over the water, with plane-0 ground one
// tile beyond each end. The crossing: walk to the plane-0 tile before the near end, step onto the deck, walk the deck (collision
// respecting), step off one tile beyond the far end. Only the 21314/21315 bridge has a sourced level; the others (the troll-island
// bridges, repaired in the quest) have none in any source.
data class RopeBridge(val x: Int, val south: Int, val north: Int, val agility: Int?)

val ROPE_BRIDGES =
    mapOf(
        21306 to RopeBridge(2317, 3824, 3831, null),
        21307 to RopeBridge(2317, 3824, 3831, null),
        21308 to RopeBridge(2343, 3821, 3828, null),
        21309 to RopeBridge(2343, 3821, 3828, null),
        21310 to RopeBridge(2314, 3840, 3847, null),
        21311 to RopeBridge(2314, 3840, 3847, null),
        21312 to RopeBridge(2355, 3840, 3847, null),
        21313 to RopeBridge(2355, 3840, 3847, null),
        21314 to RopeBridge(2378, 3840, 3847, 40),
        21315 to RopeBridge(2378, 3840, 3847, 40),
    )

ROPE_BRIDGES.forEach { (id, bridge) ->
    val option = if (id == 21314) "Cross-bridge" else "Walk-across"
    shortcut(id, option) {
        val level = bridge.agility
        if (level != null && !player.hasAgility(level)) return@shortcut
        obstacle { _ ->
            val northward = player.tile.z <= (bridge.south + bridge.north) / 2
            val before = Tile(bridge.x, if (northward) bridge.south - 1 else bridge.north + 1, 0)
            val deckIn = Tile(bridge.x, if (northward) bridge.south else bridge.north, 1)
            val deckOut = Tile(bridge.x, if (northward) bridge.north else bridge.south, 1)
            val after = Tile(bridge.x, if (northward) bridge.north + 1 else bridge.south - 1, 0)
            if (player.tile.height == 0) {
                walkToTile(player, before)
                player.moveTo(deckIn)
                wait(1)
            }
            walkToTile(player, deckOut)
            player.moveTo(after)
            wait(1)
        }
    }
}
