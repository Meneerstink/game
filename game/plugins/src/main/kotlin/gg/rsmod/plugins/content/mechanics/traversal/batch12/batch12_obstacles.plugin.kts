package gg.rsmod.plugins.content.mechanics.traversal.batch12

import gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports
import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.darkanHandleObstacle
import gg.rsmod.plugins.content.skills.agility.darkanTransform
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.hasAgility
import gg.rsmod.plugins.content.skills.agility.locAt
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.straightCrossing
import gg.rsmod.plugins.content.skills.agility.walkOverTile
import gg.rsmod.plugins.content.skills.agility.walkToTile
import java.lang.ref.WeakReference

/*
 * Obstacle batch 12 (owner priority #1 2026-10-03, agent-handoffs/obstacle-batches/rest-batch-12.tsv): the reachable traversal locs
 * of the rest batch that are more than a plain teleport (the teleports are in data/cfg/object-teleports/batch-12.json, the
 * unreachable content in reference/traversal/not_applicable-batch12.tsv). Every crossing starts with a collision walk to the tile in
 * front of the loc and is a forced move / bounded walk to the usable tile behind it (owner E17). Map facts: MapProbe 2026-10-03
 * (this 667 cache's collision, "map"/"use"/"conn" over the scratchpad probe).
 */

// ---- Grapple shortcuts: shared requirement check -----------------------------------------------------------------------------
// Requirement messages and items as the Void port in skills/agility/grapple_shortcuts.plugin.kts (Void Grapple.kt): mithril grapple
// 9419 in the ammo slot, a crossbow 9174-9185 wielded; the level box is opened in its own task so the click never stays locked.
val B12_MITHRIL_GRAPPLE = 9419
val B12_CROSSBOWS = listOf(9174, 9176, 9177, 9179, 9181, 9183, 9185)
val B12_ANIM_CROSSBOW_ACCURATE = 4230
val B12_ANIM_GRAPPLE_AIM_FIRE = 4460
val B12_ANIM_WATER_OBELISK_SWIM = 4468
val B12_SOUND_GRAPPLE_SHOOT = 2928
val B12_SOUND_GRAPPLE_SPLASH = 2929
val B12_OBJ_GRAPPLE_ROPE = 17034
val B12_OBJ_CATHERBY_ROCKS_ROPE = 17065

fun grappleReady(
    player: Player,
    ranged: Int,
    agility: Int,
    strength: Int,
): Boolean {
    if (player.skills.getCurrentLevel(Skills.RANGED) < ranged ||
        player.skills.getCurrentLevel(Skills.AGILITY) < agility ||
        player.skills.getCurrentLevel(Skills.STRENGTH) < strength
    ) {
        player.queue { messageBox("You need at least $ranged Ranged, $agility Agility and $strength Strength to do that.") }
        return false
    }
    if (player.equipment[EquipmentType.AMMO.id]?.id != B12_MITHRIL_GRAPPLE) {
        player.message("You need a mithril grapple tipped bolt with a rope to do that.")
        return false
    }
    if (player.equipment[EquipmentType.WEAPON.id]?.id !in B12_CROSSBOWS) {
        player.message("You need a crossbow equipped to do that.")
        return false
    }
    return true
}

/** A temporary ground-decor rope (Void GameObjects.add(.., ticks)), removed after [ticks]. */
fun ropeFor(
    id: Int,
    tile: Tile,
    rot: Int,
    ticks: Int,
) {
    val obj = DynamicObject(id, 22, rot, tile)
    world.spawn(obj)
    world.queue {
        wait(ticks)
        world.remove(obj)
    }
}

// ---- Karamja river grapple: Strong tree 244 (2872,3128 south bank / 2872,3138 north bank, region 11312/11313) ----------------
// RuneScape Wiki "Strong tree (Karamja)": a two-way grapple between Musa Point and the harpie bug swarm area, 53 Agility,
// 42 Ranged, 21 Strength, a mithril grapple and any crossbow. 2009scape KaramjaGrapple (two-way, the same levels, animation 4230,
// then the player is put on the other bank) agrees. This 667 map: the river is z 3131-3137 (clipped water), the trees' own tiles
// are walkable except their trunks 2873,3129 / 2873,3139, so the lane is x 2872 from 2872,3127 (south, MapProbe reach 54) to
// 2872,3141 (north, reach 61) - never through the other tree's trunk. The swim along the rope is Void's water_obelisk_swim 4468
// (the same grapple-and-swim across water in Void Grapple.kt); 2009scape's 9-tick wait is that crossing (14 tiles, 320 cycles).
// 244 is also the River Lum strong tree (3256,3180, rest batch 10): any other placement falls through to the object-teleport table.
val KARAMJA_SOUTH = Tile(2872, 3127)
val KARAMJA_NORTH = Tile(2872, 3141)

// ---- Water Obelisk island -> Catherby: Grapple column 251 (2840,3425) and Strong tree 250 (2840,3435) -------------------------
// This 667 map places the 2011 hidey-hole variants instead of Void's catherby_crossbow_tree 17062 (the same swap as the Falador wall
// 213/214 in grapple_shortcuts). RuneScape Wiki "Grapple column": the grapple from the island back to Catherby, 36 Agility, 39 Ranged,
// 22 Strength, one-way. The crossing is Void Grapple.kt's catherby_crossbow_tree verbatim (start 2841,3425, aim 4460, fire 4230,
// ropes 2841,3427-3433 + rocks rope 2841,3426, swim 4468, exact move to 2841,3432 in 160 cycles); MapProbe: 2841,3425 is walkable on
// the island (reach 64), 2841,3432 on the Catherby shore (reach 59), the island is not connected to Catherby on foot. Void's tree
// swap to 17063 is not done: the clicked loc here is the column, not the tree that id draws.
// RuneScape Wiki "Strong tree (Catherby)": "This is a one-way grapple; players cannot go from the strong tree in Catherby to the
// Obelisk of Water." - the Grapple option there says "You cannot see a good spot to grapple the rock."
/** Void `water_obselisk_island` (catherby.areas.toml), the polygon's bounding box. */
fun onObeliskIsland(tile: Tile) = tile.height == 0 && tile.x in 2833..2849 && tile.z in 3416..3427

// ---- Death Plateau rocks 35391 (2834,3626 and 2900,3608, 1x5) ---------------------------------------------------------------
// RuneScape Wiki "Rock (Death Plateau)" lists object ids 3748 and 35391 (the 2011 re-placement): 15 Agility; OSRS Wiki "Rocks
// (Death Plateau)": "You need climbing boots to negotiate these rocks." (either direction). The climb is the same Void
// trollheim_rocks_advanced sequence the 3748 rock uses in sourced_shortcuts ("You climb onto the rock...", rocks_pile_climb 839,
// "...and step down the other side."), over this map's own lane through the 1x5 loc (ShortcutSupport.climbCliff).
// Climbing boots in this cache: 3105 "Rock climbing boots" and 18788 "Climbing boots" (items.yml, same examine).
val B12_CLIMBING_BOOTS = setOf(Items.ROCK_CLIMBING_BOOTS, Items.CLIMBING_BOOTS)

// ---- Ice gates 5043 / 5044 (2838,3739 and 2838,3736, 1x3) - the Ice Path gate above Trollheim ---------------------------------
// OSRS Wiki "Ice gate": 5043 (north) and 5044 (south) lead to the ice troll area and the Ice Path; "Only the northern gate functions
// for passage; attempting the southern gate redirects players through the northern one"; players exit by clicking the gate from
// within. This 667 map (MapProbe): only the southern gate's rows 3737-3738 have a walkable tile on both sides (2837 west, reach 47 /
// 2839 east, reach 42; the north gate's east side 2839,3739-3741 is ice), the west side is Trollheim (conn from the Trollheim
// teleport 2831,3677: true), the east side is reachable only through the gate. So either gate passes through the one gate that has a
// lane here. The passage is the bounded walk through the gate (a "Go-through" passage, no climb render).
// Not enforced, recorded: OSRS lets only players who gave the troll child a cake (Desert Treasure) through; this server's Desert
// Treasure is the foundation short quest without the troll child step, and its Kamil step already sends players onto the Ice Path.
val ICE_GATE_NORTH = Tile(2838, 3739)
val ICE_GATE_SOUTH = Tile(2838, 3736)

on_world_init {
    shortcut(Objs.STRONG_TREE_244, "Grapple") {
        val tree = player.getInteractingGameObj()
        if (tree.tile.height != 0 || tree.tile.x != 2872 || (tree.tile.z != 3128 && tree.tile.z != 3138)) {
            // The River Lum strong tree (3256,3180) is the same id: batch 13's crossing (RS3/OSRS Wiki, Void grapple sequence).
            val lum = gg.rsmod.plugins.content.mechanics.traversal.batch13.RiverLumGrapple
            if (lum.isRiverLum(tree.tile)) {
                if (lum.ready(player)) obstacle { lum.cross(this, player) }
            } else {
                ObjectTeleports.fallback(player)
            }
            return@shortcut
        }
        if (!grappleReady(player, ranged = 42, agility = 53, strength = 21)) return@shortcut
        obstacle {
            val north = player.tile.z >= 3134
            val start = if (north) KARAMJA_NORTH else KARAMJA_SOUTH
            val landing = if (north) KARAMJA_SOUTH else KARAMJA_NORTH
            val direction = if (north) Direction.SOUTH else Direction.NORTH
            walkToTile(player, start)
            face(player, direction)
            wait(1)
            player.animate(B12_ANIM_CROSSBOW_ACCURATE)
            player.playSound(B12_SOUND_GRAPPLE_SHOOT)
            wait(3)
            player.animate(B12_ANIM_WATER_OBELISK_SWIM)
            player.playSound(B12_SOUND_GRAPPLE_SPLASH)
            exactMove(player, landing, delay = 320, direction = direction)
        }
    }

    shortcut(Objs.GRAPPLE_COLUMN, "Grapple") {
        if (!grappleReady(player, ranged = 39, agility = 36, strength = 22)) return@shortcut
        obstacle {
            if (!onObeliskIsland(player.tile)) {
                player.message("I can't do that from here.")
                return@obstacle
            }
            walkToTile(player, Tile(2841, 3425))
            face(player, Direction.NORTH)
            wait(1)
            player.animate(B12_ANIM_GRAPPLE_AIM_FIRE)
            wait(2)
            player.animate(B12_ANIM_CROSSBOW_ACCURATE)
            player.playSound(B12_SOUND_GRAPPLE_SHOOT)
            wait(3)
            for (z in 3427..3433) ropeFor(B12_OBJ_GRAPPLE_ROPE, Tile(2841, z), 1, 14)
            ropeFor(B12_OBJ_CATHERBY_ROCKS_ROPE, Tile(2841, 3426), 1, 14)
            wait(4)
            player.animate(B12_ANIM_WATER_OBELISK_SWIM)
            player.playSound(B12_SOUND_GRAPPLE_SPLASH)
            exactMove(player, Tile(2841, 3432), delay = 160, direction = Direction.NORTH)
        }
    }

    shortcut(Objs.STRONG_TREE_250, "Grapple") {
        player.message("You cannot see a good spot to grapple the rock.")
    }

    shortcut(Objs.ROCKS_35391, "Climb") {
        if (!player.hasAgility(15)) return@shortcut
        if (player.equipment[EquipmentType.BOOTS.id]?.id !in B12_CLIMBING_BOOTS) {
            player.message("You need climbing boots to negotiate these rocks.")
            return@shortcut
        }
        // Darkan Trollheim.kt:259-264 (handleObstacleInteractions 35391): Agility.handleObstacle(3303, 1, player.transform(+-2 along the
        // loc's axis), 1.0) - rotation 1/3: x (+2 when the player is west of the loc), else z. Start = the route tile; the body stays
        // there while 3303 plays and is teleported after 1 tick (the old climbCliff lane slid through the rock at ground height).
        // Darkan's Troll Stronghold quest gate is not applied; level 15 and the climbing boots stay (RS/OSRS Wiki above), xp 1.0 Darkan.
        // Map (667): 35391 at 2834,3626 r3 (lane z 3628: 2833 <-> 2835, lands 2836 / 2832) and 2900,3608 r3 (lanes z 3609-3611).
        obstacle { target ->
            val alongX = target.rot == 1 || target.rot == 3
            val landing =
                if (alongX) {
                    player.darkanTransform(if (player.tile.x < target.tile.x) 2 else -2, 0)
                } else {
                    player.darkanTransform(0, if (player.tile.z < target.tile.z) 2 else -2)
                }
            player.message("You climb onto the rock...")
            darkanHandleObstacle(player, 3303, 1, landing)
            player.addXp(Skills.AGILITY, 1.0)
            player.message("...and step down the other side.")
        }
    }

    listOf(Objs.ICE_GATE, Objs.ICE_GATE_5044).forEach { id ->
        shortcut(id, "Go-through") {
            obstacle { target ->
                val gates = listOfNotNull(target, locAt(world, ICE_GATE_SOUTH, Objs.ICE_GATE_5044), locAt(world, ICE_GATE_NORTH, Objs.ICE_GATE))
                val pass = gates.asSequence().mapNotNull { gate -> straightCrossing(world, gate, player.tile)?.let { gate to it } }.firstOrNull()
                if (pass == null) {
                    gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                        "ice gate ${target.id} at ${target.tile.x},${target.tile.z}: no lane from ${player.tile.x},${player.tile.z}"
                    }
                    player.message(Entity.YOU_CANT_REACH_THAT)
                    return@obstacle
                }
                val (gate, crossing) = pass
                // The gate actually passed is the obstacle of this crossing (OSRS: the other gate redirects through it).
                player.attr[INTERACTING_OBJ_ATTR] = WeakReference(gate)
                walkToTile(player, crossing.start)
                face(player, crossing.direction)
                walkOverTile(player, crossing.landing, Render.NONE)
            }
        }
    }
}
