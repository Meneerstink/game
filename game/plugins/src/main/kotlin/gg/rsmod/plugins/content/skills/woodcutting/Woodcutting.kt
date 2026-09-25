package gg.rsmod.plugins.content.skills.woodcutting

import gg.rsmod.plugins.content.skills.summoning.SummoningBoosts

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.skills.farming.logic.PatchState

/**
 * @author Tom <rspsmods@gmail.com>
 */

val BIRD_NEST_DROP_SYNTH = 1997
val TREE_FALLING_SYNTH = 2734
val RESPAWN_TIMER_MAX_RANGE = 98
val INFERNO_ADZE_PROJECTILE_ID = 1776

object Woodcutting {
    /** A felled world tree: its stump and what grows back (the tree and, for big trees, its canopy). */
    class FelledTree(val stump: GameObject, val tree: GameObject, val canopy: GameObject?)

    /** Currently felled world trees by stump tile (farming-patch trees use their own patch state). */
    val felledStumps = java.util.concurrent.ConcurrentHashMap<gg.rsmod.game.model.Tile, FelledTree>()

    /**
     * Regrows the felled tree whose stump is [stump] at once (Hydra Regrowth; Void donor: fire the stump's pending regrow).
     * Returns false when [stump] is not a felled world tree's stump.
     */
    fun regrowStump(
        world: gg.rsmod.game.model.World,
        stump: GameObject,
    ): Boolean {
        val felled = felledStumps[stump.tile] ?: return false
        if (felled.stump.id != stump.id) return false
        felledStumps.remove(stump.tile)
        world.remove(felled.stump)
        world.spawn(DynamicObject(felled.tree))
        felled.canopy?.let { world.spawn(DynamicObject(it)) }
        return true
    }

    suspend fun chopDownTree(
        it: QueueTask,
        obj: GameObject,
        tree: TreeType,
        farmingTreeState: PatchState? = null,
    ) {
        val player = it.player
        if (!canChop(player, obj, tree)) {
            return
        }
        val axe =
            AxeType.values.reversed().firstOrNull {
                player.skills
                    .getMaxLevel(Skills.WOODCUTTING) >= it.level &&
                    (
                        player.equipment.contains(it.item) ||
                            player.inventory.contains(
                                it.item,
                            )
                    )
            }!!
        player.filterableMessage("You swing your hatchet at the ${if (tree == TreeType.IVY) "ivy" else "tree"}.")
        val axeAnimation = if (tree == TreeType.IVY) axe.ivyAnimation else axe.animation
        while (canChop(player, obj, tree)) {
            player.animate(axeAnimation, idleOnly = true)
            it.wait(2)
            val success =
                interpolate(
                    (tree.lowChance * axe.ratio).toInt(),
                    (tree.highChance * axe.ratio).toInt(),
                    // Invisible familiar boost: raises the success roll only, never a requirement.
                    SummoningBoosts.effectiveLevel(player, Skills.WOODCUTTING),
                ) > RANDOM.nextInt(255)
            if (success) {
                val wasChoppedDown = onSuccess(player, obj, tree, axe.item == Items.INFERNO_ADZE, farmingTreeState)
                if (wasChoppedDown) {
                    break
                }
            }
            it.wait(2)
        }
        player.animate(Anims.RESET)
    }

    private fun onSuccess(
        player: Player,
        obj: GameObject,
        tree: TreeType,
        infernoAdze: Boolean,
        farmingTreeState: PatchState?,
    ): Boolean {
        player.addXp(Skills.WOODCUTTING, tree.xp, checkBrawlingGloves = true)
        when (tree) {
            TreeType.IVY -> {
                player.filterableMessage("You successfully chop away some ivy.")
            }
            else -> {
                val triggeredInfernoAdzeEffect = infernoAdze && tree.log != -1 && RANDOM.nextInt(1, 3) == 1
                val logName =
                    player.world.definitions
                        .get(ItemDef::class.java, tree.log)
                        .name
                        .pluralSuffix(2)
                        .lowercase()
                val message =
                    if (triggeredInfernoAdzeEffect) "You chop some $logName. The heat of the inferno adze incinerates them." else "You get some $logName."
                if (tree.log != -1) {
                    if (triggeredInfernoAdzeEffect) {
                        val projectile =
                            player.createProjectile(
                                player.tile.transform(RANDOM.nextInt(1, 3), RANDOM.nextInt(1, 3)),
                                INFERNO_ADZE_PROJECTILE_ID,
                                38,
                                32,
                                26,
                                11,
                                0,
                                100,
                            )
                        player.world.spawn(projectile)
                        player.addXp(Skills.FIREMAKING, tree.xp)
                    } else {
                        player.inventory.add(tree.log)
                    }
                }
                player.filterableMessage(message)
            }
        }
        // OSRS Wiki "Woodcutting cape": "When worn, additional 10% chance of a bird nest falling while chopping trees" (max cape too).
        val nestChance =
            (1.0 / 256) * if (gg.rsmod.plugins.content.skills.SkillcapePerks.worn(player, gg.rsmod.plugins.content.skills.Skillcapes.WOODCUTTING)) 1.1 else 1.0
        if (player.world.randomDouble() < nestChance) {
            val nest = DropTableFactory.getDrop(player, 10_000) ?: return false
            nest.forEach {
                val groundItem = GroundItem(it, player.findWesternTile(), player)
                groundItem.currentCycle = 150
                player.world.spawn(groundItem)
            }
            player.message(
                "<col=FF0000>A bird's nest falls out of the ${if (tree == TreeType.IVY) "ivy" else "tree"}.</col>",
            )
            player.playSound(BIRD_NEST_DROP_SYNTH)
        }
        if (tree.depleteChance == 0 || player.world.random(1..tree.depleteChance) == 1) {
            if (farmingTreeState != null) {
                farmingTreeState.chopDown()
                player.world.queue {
                    wait(tree.respawnTime)
                    if (farmingTreeState.isChoppedDown) {
                        farmingTreeState.regrowChoppedDownCrop()
                    }
                }
            } else {
                val treeStump =
                    player.world.definitions
                        .get(ObjectDef::class.java, obj.id)
                        .depleted
                if (treeStump != -1) {
                    val world = player.world
                    world.queue {
                        val trunk = DynamicObject(obj, treeStump)
                        val offsets =
                            arrayOf(
                                Pair(-1, -1),
                                Pair(0, -1),
                                Pair(-1, 0),
                                Pair(0, 0),
                            )
                        var canopy: GameObject? = null
                        for (offset in offsets) {
                            val tile = obj.tile.transform(offset.first, offset.second, 1)
                            val candidate = world.getObject(tile, ObjectType.INTERACTABLE)
                            if (candidate != null) {
                                canopy = candidate
                                break
                            }
                        }
                        if (canopy != null) {
                            world.remove(canopy)
                        }
                        world.remove(obj)
                        world.spawn(trunk)
                        felledStumps[trunk.tile] = FelledTree(trunk, obj, canopy)
                        val respawnTime =
                            if (tree == TreeType.TREE ||
                                tree == TreeType.ACHEY
                            ) {
                                world.random(tree.respawnTime..RESPAWN_TIMER_MAX_RANGE)
                            } else {
                                tree.respawnTime
                            }
                        wait(respawnTime)
                        // A Hydra's Regrowth may already have restored this tree (regrowStump).
                        if (felledStumps[trunk.tile]?.stump === trunk) {
                            felledStumps.remove(trunk.tile)
                            world.remove(trunk)
                            world.spawn(DynamicObject(obj))
                            if (canopy != null) {
                                world.spawn(DynamicObject(canopy))
                            }
                        }
                    }
                }
            }
            player.animate(Anims.RESET)
            if (tree != TreeType.IVY) {
                player.playSound(TREE_FALLING_SYNTH)
            }
            return true
        }
        return false
    }

    private fun canChop(
        player: Player,
        obj: GameObject,
        tree: TreeType,
    ): Boolean {
        if (!player.world.isSpawned(obj)) {
            return false
        }
        val axe =
            AxeType.values.reversed().firstOrNull {
                player.skills
                    .getMaxLevel(Skills.WOODCUTTING) >= it.level &&
                    (
                        player.equipment.contains(it.item) ||
                            player.inventory.contains(
                                it.item,
                            )
                    )
            }
        if (axe == null) {
            player.message("You do not have an axe which you have the woodcutting level to use.")
            return false
        }
        if (player.skills.getMaxLevel(Skills.WOODCUTTING) < tree.level) {
            player.message("You need a Woodcutting level of ${tree.level} to chop down this tree.")
            return false
        }
        if (player.inventory.isFull) {
            player.message("Your inventory is too full to hold any more logs.")
            return false
        }
        return true
    }
}
