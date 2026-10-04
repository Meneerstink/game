package gg.rsmod.plugins.content.objs

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.skills.mining.PickaxeType
import gg.rsmod.plugins.content.skills.woodcutting.AxeType
import java.util.concurrent.ThreadLocalRandom

/**
 * The Abyss outer-ring obstacles, ported from Void `content/area/wilderness/abyss/AbyssObstacles.kt` (owner priority #1, audit
 * 2026-10-04 section 5). The map places the multiloc parents 7142-7153 (Void `abyss_obstacle*`), which show a rock / tendrils /
 * boil / eyes / gap / passage per player through varbit 625 (`abyss_obstacles`). The client sends the parent id, so the old
 * Harley handlers bound on the child ids 7158 / 7161 / ... never fired (checked in the agent's client 2026-10-04: a click on the
 * rock 7143 at 3026,4813 did nothing), and they teleported by a per-x offset from wherever the click left the player, spawning
 * the cleared obstacle for everyone. Now, as Void: the obstacle clears for this player only (varbit 625 states 12-19), the player
 * walks into the cleared gap (walk tile) and is placed on the inner side (Void's table).
 */

val ABYSS_POSITIONS = AbyssObstacles.POSITIONS

val ABYSS_OBSTACLES_VARBIT = 625
val ABYSS_PARENTS = (7142..7153)
val ABYSS_ROCK = 7158
val ABYSS_TENDRILS = 7161
val ABYSS_GAP = 7164
val ABYSS_BOIL = 7165
val ABYSS_EYES = 7168
val ABYSS_PASSAGE = 7154

// Void abyss / emote / skill tomls.
val ANIM_ABYSS_KNEEL = 1331
val ANIM_ABYSS_STAND = 1332
val ANIM_CRAWLING_CAVE = 844
val ANIM_LIGHT_FIRE = 733
val ABYSS_DISTRACTIONS = intArrayOf(865, 794, 1835) // emote_clap, stomp_eyes, distract_eyes
val GFX_FIRE_WAVE_IMPACT = 2740
val SFX_BOIL_BURST = 2711
val SFX_ABYSSAL_SQUEEZETHROUGH = 2709

/** Void `{pickaxe}_swing_low`; a pickaxe Void has no low swing for keeps its mining animation. */
val PICKAXE_SWING_LOW =
    mapOf(
        PickaxeType.BRONZE to 625, PickaxeType.IRON to 626, PickaxeType.STEEL to 627, PickaxeType.MITHRIL to 629,
        PickaxeType.ADAMANT to 628, PickaxeType.RUNE to 624, PickaxeType.DRAGON to 12189,
    )

/** Void `Level.success(level, 2..100)`: the chance climbs from 2 at level 1 to 100 at level 99, out of 256. */
fun abyssSuccess(p: Player, skill: Int): Boolean {
    val level = p.skills.getCurrentLevel(skill).coerceIn(1, 99)
    val chance = (2 * (99 - level) + 100 * (level - 1)) / 98
    return chance > ThreadLocalRandom.current().nextInt(256)
}

/** Void: walk into the cleared gap, then onto the inner ring; the obstacle closes again for this player. */
suspend fun gg.rsmod.game.model.queue.QueueTask.passAbyssObstacle(p: Player, target: GameObject, message: String, settle: Boolean) {
    val (walkTile, teleTile) = ABYSS_POSITIONS[Tile(target.tile.x, target.tile.z)] ?: return
    try {
        gg.rsmod.plugins.content.skills.agility.walkToTileFrom(this, p, walkTile)
    } catch (e: gg.rsmod.plugins.content.skills.agility.ObstacleApproachFailed) {
        p.setVarbit(ABYSS_OBSTACLES_VARBIT, 0)
        p.message("I can't reach that!")
        return
    }
    p.filterableMessage(message)
    if (settle) wait(1)
    p.animate(-1)
    p.moveTo(teleTile)
    wait(1)
    p.setVarbit(ABYSS_OBSTACLES_VARBIT, 0)
}

fun abyssKind(world: World, parent: Int): Int? {
    val def = world.definitions.getNullable(ObjectDef::class.java, parent) ?: return null
    val kinds = setOf(ABYSS_ROCK, ABYSS_TENDRILS, ABYSS_GAP, ABYSS_BOIL, ABYSS_EYES, ABYSS_PASSAGE)
    return def.transforms?.firstOrNull { it in kinds }
}

// Bound at load, like `shortcut(..)`: the definitions are loaded before the plugins.
run {
    ABYSS_PARENTS.forEach { parent ->
        val kind = abyssKind(world, parent) ?: return@forEach
        if (1 in world.plugins.boundObjectOptions(parent)) return@forEach
        on_obj_option(obj = parent, option = 1) {
            val target = player.getInteractingGameObj()
            if (ABYSS_POSITIONS[Tile(target.tile.x, target.tile.z)] == null) return@on_obj_option
            player.lockingQueue(lockState = LockState.FULL) {
                when (kind) {
                    ABYSS_ROCK -> {
                        player.filterableMessage("You attempt to mine your way through...")
                        val pick =
                            PickaxeType.values.reversed().firstOrNull {
                                player.skills.getMaxLevel(Skills.MINING) >= it.level && (player.equipment.contains(it.item) || player.inventory.contains(it.item))
                            }
                        if (pick == null) {
                            player.message("You need a pickaxe for which you have the required Mining level to mine this rock.")
                            return@lockingQueue
                        }
                        player.animate(PICKAXE_SWING_LOW[pick] ?: pick.animation)
                        wait(7)
                        if (!abyssSuccess(player, Skills.MINING)) {
                            player.filterableMessage("...but fail to break-up the rock.")
                            player.animate(-1)
                            return@lockingQueue
                        }
                        wait(2)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 12)
                        wait(2)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 13)
                        passAbyssObstacle(player, target, "...and manage to break through the rock.", settle = false)
                        player.addXp(Skills.MINING, 25.0)
                    }
                    ABYSS_TENDRILS -> {
                        player.filterableMessage("You attempt to chop your way through...")
                        wait(4)
                        val axe =
                            AxeType.values.reversed().firstOrNull {
                                player.skills.getMaxLevel(Skills.WOODCUTTING) >= it.level && (player.equipment.contains(it.item) || player.inventory.contains(it.item))
                            }
                        if (axe == null) {
                            player.message("You need a hatchet to chop through the tendrils.")
                            player.message("You do not have a hatchet that you have the Woodcutting level to use.")
                            return@lockingQueue
                        }
                        player.animate(axe.animation)
                        wait(2)
                        if (!abyssSuccess(player, Skills.WOODCUTTING)) {
                            player.filterableMessage("...but fail to cut through the tendrils.")
                            return@lockingQueue
                        }
                        wait(2)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 14)
                        player.animate(axe.animation)
                        wait(2)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 15)
                        passAbyssObstacle(player, target, "...and manage to cut a way through the tendrils.", settle = true)
                        player.addXp(Skills.WOODCUTTING, 25.0)
                    }
                    ABYSS_BOIL -> {
                        player.filterableMessage("You attempt to set the blockade on fire...")
                        wait(3)
                        if (!player.inventory.contains(Items.TINDERBOX_590)) {
                            player.message("...but you don't have a tinderbox to burn it!")
                            return@lockingQueue
                        }
                        if (!abyssSuccess(player, Skills.FIREMAKING)) {
                            player.filterableMessage("...but fail to burn it out of your way.")
                            return@lockingQueue
                        }
                        player.animate(ANIM_LIGHT_FIRE)
                        wait(6)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 16)
                        wait(6)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 17)
                        world.spawn(TileGraphic(Tile(target.tile), GFX_FIRE_WAVE_IMPACT, 128))
                        player.playSound(SFX_BOIL_BURST)
                        passAbyssObstacle(player, target, "...and manage to burn it down and get past.", settle = true)
                        player.addXp(Skills.FIREMAKING, 25.0)
                    }
                    ABYSS_EYES -> {
                        player.filterableMessage("You use your thieving skills to misdirect the eyes...")
                        wait(2)
                        if (!abyssSuccess(player, Skills.THIEVING)) {
                            player.filterableMessage("...but fail to distract them enough to get past.")
                            return@lockingQueue
                        }
                        player.animate(ABYSS_DISTRACTIONS.random())
                        wait(4)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 18)
                        player.animate(ABYSS_DISTRACTIONS.random())
                        wait(2)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 19)
                        passAbyssObstacle(player, target, "...and sneak past while they're not looking.", settle = true)
                        player.addXp(Skills.THIEVING, 25.0)
                    }
                    ABYSS_GAP -> {
                        player.filterableMessage("You attempt to squeeze through the narrow gap...")
                        wait(4)
                        player.animate(ANIM_ABYSS_KNEEL)
                        wait(2)
                        if (!abyssSuccess(player, Skills.AGILITY)) {
                            player.filterableMessage("...but you are not agile enough to get through the gap.")
                            player.animate(ANIM_ABYSS_STAND)
                            return@lockingQueue
                        }
                        wait(2)
                        player.filterableMessage("...and you manage to crawl through.")
                        player.animate(ANIM_CRAWLING_CAVE)
                        player.moveTo(ABYSS_POSITIONS[Tile(target.tile.x, target.tile.z)]!!.second)
                        player.playSound(SFX_ABYSSAL_SQUEEZETHROUGH, loops = 4)
                        wait(1)
                        player.setVarbit(ABYSS_OBSTACLES_VARBIT, 0)
                        player.addXp(Skills.AGILITY, 25.0)
                    }
                    ABYSS_PASSAGE -> {
                        wait(2)
                        player.moveTo(ABYSS_POSITIONS[Tile(target.tile.x, target.tile.z)]!!.second)
                    }
                }
            }
        }
    }
}
