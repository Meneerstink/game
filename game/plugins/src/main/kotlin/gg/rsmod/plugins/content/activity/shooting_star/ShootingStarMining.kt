package gg.rsmod.plugins.content.activity.shooting_star

import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.skills.mining.PickaxeType
import kotlin.math.min

/**
 * Self-contained mining loop for crashed stars, deliberately not reusing the shared
 * `RockType`/`Mining.mineRock` machinery: crashed stars deplete from ONE shared, world-level
 * pool across every player mining them simultaneously (not a per-attempt personal respawn cycle
 * like a normal rock), which `Mining.canMine`/`onSuccess` has no hook for. Keeping this isolated
 * avoids risking any change to the shared Mining subsystem used by all other rock tiers.
 *
 * Level/xp/chance sourced verbatim from Void's `data/skill/mining/ores.tables.toml` `[.stardust]`
 * entry (level=1, xp=800, chance=[10,75] at level 1/99) - identical for all 9 tiers in that table,
 * so no per-tier level/xp/chance distinction was invented.
 */
object ShootingStarMining {
    private const val MINING_ANIMATION_TIME = 16
    private const val LEVEL = 1
    private const val EXPERIENCE = 800.0
    private const val LOW_CHANCE = 10
    private const val HIGH_CHANCE = 75

    suspend fun mine(
        it: QueueTask,
        obj: GameObject,
    ) {
        val player = it.player
        if (!canMine(it, player, obj)) {
            return
        }
        player.filterableMessage("You swing your pick at the crashed star.")
        val pick =
            PickaxeType.values.reversed().firstOrNull {
                player.skills.getMaxLevel(Skills.MINING) >= it.level &&
                    (player.equipment.contains(it.item) || player.inventory.contains(it.item))
            }!!
        var ticks = 0
        var animations = 0
        while (canMine(it, player, obj)) {
            val animationWait = if (animations < 2) MINING_ANIMATION_TIME + 1 else MINING_ANIMATION_TIME
            if (ticks % animationWait == 0) {
                player.animate(pick.animation, delay = 30)
                animations++
            }
            if (ticks % pick.ticksBetweenRolls == 0) {
                val chance = interpolate(LOW_CHANCE, HIGH_CHANCE, player.skills.getMaxLevel(Skills.MINING))
                if (chance > RANDOM.nextInt(255)) {
                    onSuccess(player, obj)
                }
            }
            val time =
                min(
                    animationWait - ticks % animationWait,
                    pick.ticksBetweenRolls - ticks % pick.ticksBetweenRolls,
                )
            it.wait(time)
            ticks += time
        }
        player.animate(Anims.RESET)
    }

    private suspend fun canMine(
        it: QueueTask,
        player: Player,
        obj: GameObject,
    ): Boolean {
        if (!player.world.isSpawned(obj) || ShootingStarState.obj !== obj) {
            return false
        }
        val pick =
            PickaxeType.values.reversed().firstOrNull {
                player.skills.getMaxLevel(Skills.MINING) >= it.level &&
                    (player.equipment.contains(it.item) || player.inventory.contains(it.item))
            }
        if (pick == null) {
            it.messageBox(
                "You need a pickaxe to mine this rock. You do not have a pickaxe<br><br>which you have the Mining level to use.",
            )
            return false
        }
        if (player.skills.getMaxLevel(Skills.MINING) < LEVEL) {
            it.messageBox("You need a Mining level of $LEVEL to mine this rock.")
            return false
        }
        if (player.inventory.isFull) {
            it.messageBox("Your inventory is too full to hold any more ores.")
            return false
        }
        return true
    }

    private fun onSuccess(
        player: Player,
        obj: GameObject,
    ) {
        player.inventory.add(Items.STARDUST)
        if (player.timers.has(ShootingStarBonusOreTimer)) {
            player.inventory.add(Items.STARDUST)
        }
        player.addXp(Skills.MINING, EXPERIENCE, checkBrawlingGloves = true)
        player.filterableMessage("You manage to collect some stardust.")
        ShootingStarHandler.onOreCollected(player.world, obj)
    }
}
