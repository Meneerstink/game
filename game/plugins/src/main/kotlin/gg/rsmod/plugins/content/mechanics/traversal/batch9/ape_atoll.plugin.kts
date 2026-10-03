package gg.rsmod.plugins.content.mechanics.traversal.batch9

import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.content.skills.agility.*

/*
 * Ape Atoll Agility Course (obstacle batch 9, owner priority #1 2026-10-03), ported from Void
 * content/skill/agility/course/ApeAtoll.kt (ids: Void course_obstacles.objs.toml; anims: ape_atoll_agility.anims.toml,
 * shortcut.anims.toml, human.anims.toml, wilderness_agility.anims.toml; render emotes render_emotes.toml; sounds *.sounds.toml;
 * gfx big_splash 68 ape_atoll.gfx.toml). OSRS Wiki "Ape Atoll Agility Course": only a player transformed by a ninja monkey
 * greegree can complete it; a human always falls ("...you're not monkey enough to try this!").
 *
 * Ape Atoll is reachable on this server (fairy ring CLR 2735,2742 FairyRings.kt; Teleport to Ape Atoll TeleportSpell.kt; the
 * Port Sarim boat void_outpost_boat.plugin.kts), but there is NO greegree transformation here (areas/apeatroll/*: "TODO Add missing
 * function greegree"). So every player is a human: this port carries Void's human (failure) branch of every obstacle and Void's
 * refusal for a wielded greegree other than the small ninja monkey greegree. Not ported, recorded: Void's monkey (success) branches
 * (agility stages, lap bonus, ninja monkey anims/renders) - they need the greegree transformation, a shared feature that does not exist.
 * A player wielding the small ninja monkey greegree is still a human model here, so he gets the human branch.
 *
 * Map (this 667 cache, MapProbe 2026-10-03): start tiles are the tile Void's player stands on, approached with collision
 * (walkToTile): stepping stone from 2755,2742 (east, jumping west); tropical tree from the islet 2752,2742; monkey bars from
 * 2753,2741,2; skull slope from 2747,2741; rope swing from 2751,2731; tree rope from 2758,2734. Void's returnToShore ends at
 * 2756,2748 / 2757,2748, which are water (clipped) on this map; the swim ends instead on the nearest walkable shore 2758,2748
 * (reach 56, connected to the course start 2755,2742). Every other landing of Void's human branches is walkable here:
 * 2755,2742 (rope swing) and 2764,2737 (tree rope).
 */

val STEPPING_STONE = 12568
val TROPICAL_TREE = 12570
val MONKEYBARS = 12573
val SKULL_SLOPE = 12576
val ROPE_SWING = 12578
val TROPICAL_TREE_ROPE = 12618

val ANIM_STEPPING_STONE_JUMP = 741
val ANIM_ROPE_WALK_FALL_DOWN = 764
val ANIM_CLIMB_UP = 828
val ANIM_HUMAN_DEATH = 836
val ANIM_JUMP_ONTO_MONKEY_BARS = 742
val ANIM_ROCKS_CLIMB_UP_FAIL = 739
val ANIM_FAIL_ROPE_SWING = 3131
val LOC_ANIM_ROPE_SWING = 3123
val GFX_BIG_SPLASH = 68

val SFX_JUMP = 2461
val SFX_STUMBLE_LOOP = 2493
val SFX_WATER_SPLASH = 2496
val SFX_LAND_FLATTER = 2455
val SFX_LAND_FLAT = 2469
val SFX_SWIM_STROKE = 1436
val SFX_MONKEYBARS_ON = 2474
val SFX_MONKEYBARS_LOOP = 2466
val SFX_CLIMB_WALL = 2453
val SFX_FALL_SPLASH = 1421
val SFX_MALE_DEFEND_3 = 521

/** Void items: small_ninja_monkey_greegree 4024 .. monkey_greegree 4031 (monkey_madness.items.toml). */
val SMALL_NINJA_MONKEY_GREEGREE = 4024
val GREEGREES = 4024..4031

val NOT_MONKEY = "...you're not monkey enough to try this!"
val SHORE = Tile(2758, 2748)

/** Void: a greegree other than the small ninja monkey greegree is refused with this message. */
fun Player.wrongGreegree(): Boolean {
    val weapon = equipment[gg.rsmod.plugins.api.EquipmentType.WEAPON.id]?.id ?: return false
    if (weapon in GREEGREES && weapon != SMALL_NINJA_MONKEY_GREEGREE) {
        message("Only the stealthiest and most agile monkey can use this!")
        return true
    }
    return false
}

fun Player.damageSound() {
    val male = appearance.gender == gg.rsmod.game.model.Gender.MALE
    // Void: male_defend_0..2 (518..520) at random, female_defend_0 (509); delay 20.
    playSound(if (male) 518 + java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 3) else 509, delay = 20)
}

/** Void ApeAtoll.damage(): (constitution / 100) * 10 life points = a tenth of the current hitpoints. */
fun Player.courseDamage() {
    hit(skills.getCurrentLevel(Skills.HITPOINTS) / 10)
    damageSound()
}

fun splash(tile: Tile) {
    world.spawn(TileGraphic(tile, GFX_BIG_SPLASH, 0, delay = 3))
}

/** Void ApeAtoll.returnToShore(), ending on this map's walkable shore (see header). */
suspend fun QueueTask.returnToShore(player: Player) {
    walkOverTile(player, Tile(2753, 2745), Render.SWIM)
    walkOverTile(player, Tile(2754, 2745), Render.SWIM)
    walkOverTile(player, Tile(2756, 2747), Render.SWIM)
    walkOverTile(player, SHORE, Render.SWIM)
    player.resetRenderAnimation()
    player.courseDamage()
}

on_world_init {
    shortcut(STEPPING_STONE, "Jump-to") {
        obstacle { target ->
            walkToTile(player, Tile(2755, 2742))
            player.faceTile(target.tile)
            wait(1)
            if (!player.hasAgility(48, "You need to have an Agility level of 48.")) return@obstacle
            if (player.wrongGreegree()) return@obstacle
            player.message("The rock is covered in slime and you slip into the water...")
            player.animate(ANIM_STEPPING_STONE_JUMP)
            player.playSound(SFX_JUMP)
            exactMove(player, target.tile, direction = Direction.WEST)
            player.animate(ANIM_ROPE_WALK_FALL_DOWN)
            player.playSound(SFX_STUMBLE_LOOP, loops = 10)
            wait(1)
            player.animate(-1)
            val water = target.tile.transform(0, 1)
            walkOverTile(player, water, Render.SWIM)
            player.playSound(SFX_JUMP)
            splash(water)
            player.playSound(SFX_WATER_SPLASH)
            wait(1)
            walkOverTile(player, SHORE, Render.SWIM)
            player.message(NOT_MONKEY)
            player.courseDamage()
            player.resetRenderAnimation()
        }
    }

    shortcut(TROPICAL_TREE, "Climb") {
        obstacle { target ->
            walkToTile(player, Tile(2752, 2742))
            player.faceTile(target.tile)
            wait(1)
            if (player.wrongGreegree()) return@obstacle
            player.message("You reach for the tree trunk and lose your footing...")
            player.animate(ANIM_CLIMB_UP)
            player.playSound(SFX_LAND_FLATTER, delay = 20)
            wait(1)
            player.message(NOT_MONKEY)
            player.animate(ANIM_HUMAN_DEATH)
            wait(1)
            player.damageSound()
            exactMove(player, Tile(2753, 2743), delay = 30, direction = Direction.SOUTH)
            player.animate(-1)
            player.setRenderAnimation(Render.SWIM)
            player.playSound(SFX_WATER_SPLASH)
            player.playSound(SFX_SWIM_STROKE, loops = 6)
            returnToShore(player)
        }
    }

    shortcut(MONKEYBARS, "Swing Across") {
        obstacle {
            walkToTile(player, Tile(2753, 2741, 2))
            face(player, Direction.WEST)
            wait(1)
            if (player.wrongGreegree()) return@obstacle
            player.animate(ANIM_JUMP_ONTO_MONKEY_BARS)
            player.setRenderAnimation(Render.MONKEY_BARS)
            player.playSound(SFX_MONKEYBARS_ON)
            player.playSound(SFX_MONKEYBARS_LOOP, delay = 30)
            walkOverTile(player, Tile(2750, 2741, 2), Render.MONKEY_BARS)
            player.animate(ANIM_ROPE_WALK_FALL_DOWN)
            wait(1)
            player.message("Your hands slip from the rung...")
            player.message(NOT_MONKEY)
            // Void tele(2750, 2742, 0): the fall into the river below the bars; he swims to the shore in the same task.
            player.moveTo(Tile(2750, 2742, 0))
            player.animate(-1)
            wait(1)
            player.setRenderAnimation(Render.SWIM)
            splash(Tile(2750, 2741))
            player.playSound(SFX_SWIM_STROKE, loops = 9)
            returnToShore(player)
        }
    }

    shortcut(SKULL_SLOPE, "Climb-up") {
        obstacle { target ->
            walkToTile(player, Tile(2747, 2741))
            player.faceTile(target.tile)
            wait(1)
            if (player.wrongGreegree()) return@obstacle
            player.setRenderAnimation(Render.CLIMBING)
            player.playSound(SFX_CLIMB_WALL, loops = 2)
            walkOverTile(player, Tile(2745, 2741), Render.CLIMBING)
            player.message("The hand holds are too small to hold onto...")
            player.animate(ANIM_ROCKS_CLIMB_UP_FAIL)
            player.playSound(SFX_STUMBLE_LOOP, loops = 15)
            exactMove(player, Tile(2750, 2741), delay = 90, direction = Direction.EAST)
            player.playSound(SFX_MALE_DEFEND_3, delay = 20)
            player.message(NOT_MONKEY)
            player.animate(-1)
            player.setRenderAnimation(Render.SWIM)
            splash(target.tile)
            player.playSound(SFX_WATER_SPLASH)
            player.playSound(SFX_SWIM_STROKE, loops = 9)
            walkOverTile(player, Tile(2750, 2742), Render.SWIM)
            returnToShore(player)
        }
    }

    shortcut(ROPE_SWING, "Swing") {
        obstacle { target ->
            walkToTile(player, Tile(2751, 2731))
            face(player, Direction.EAST)
            if (player.wrongGreegree()) return@obstacle
            player.message("You lose your grip on the vine!")
            player.animate(ANIM_FAIL_ROPE_SWING)
            animateLoc(world, target, LOC_ANIM_ROPE_SWING)
            player.playSound(SFX_FALL_SPLASH)
            exactMove(player, Tile(2754, 2731), delay = 45, direction = Direction.EAST)
            splash(Tile(2754, 2731))
            wait(1)
            player.message(NOT_MONKEY)
            player.setRenderAnimation(Render.DROWNING)
            face(player, Direction.NORTH)
            player.playSound(SFX_WATER_SPLASH)
            wait(1)
            player.playSound(SFX_SWIM_STROKE, delay = 6)
            exactMove(player, Tile(2754, 2736), delay = 40, direction = Direction.NORTH)
            walkOverTile(player, Tile(2754, 2739), Render.DROWNING)
            exactMove(player, Tile(2754, 2741), delay = 30, direction = Direction.NORTH)
            walkOverTile(player, Tile(2755, 2742), Render.DROWNING)
            player.resetRenderAnimation()
            player.courseDamage()
        }
    }

    shortcut(TROPICAL_TREE_ROPE, "Climb-down") {
        obstacle { target ->
            walkToTile(player, Tile(2758, 2734))
            // Void refuses after its first slide; here before it, so a refused player is never left on the clipped vine tile.
            if (player.wrongGreegree()) return@obstacle
            exactMove(player, Tile(2758, 2735), delay = 25, direction = Direction.NORTH_EAST)
            wait(1)
            player.message("You jump up to seize the vine...")
            player.faceTile(target.tile)
            player.animate(ANIM_JUMP_ONTO_MONKEY_BARS)
            player.playSound(SFX_MONKEYBARS_ON)
            wait(1)
            player.setRenderAnimation(Render.MONKEY_BARS)
            player.playSound(SFX_MONKEYBARS_LOOP, loops = 2)
            walkOverTile(player, Tile(2759, 2736), Render.MONKEY_BARS)
            walkOverTile(player, Tile(2760, 2737), Render.MONKEY_BARS)
            player.message("...and lose your grip!")
            player.animate(ANIM_ROPE_WALK_FALL_DOWN)
            player.playSound(SFX_STUMBLE_LOOP, loops = 3)
            exactMove(player, Tile(2764, 2737), delay = 30, direction = Direction.NORTH_EAST, startDelay = 10)
            player.courseDamage()
            player.playSound(SFX_LAND_FLAT)
            player.resetRenderAnimation()
        }
    }
}
