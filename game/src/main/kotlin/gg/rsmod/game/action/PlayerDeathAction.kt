package gg.rsmod.game.action

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.message.impl.MusicEffectMessage
import gg.rsmod.game.model.attr.DEATH_FLAG
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.service.log.LoggerService
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
object PlayerDeathAction {
    private const val DEATH_ANIMATION = 836

    val deathPlugin: Plugin.() -> Unit = {
        val player = ctx as Player

        player.attr.put(DEATH_FLAG, true)
        player.interruptQueues()
        player.stopMovement()
        player.lock()
        player.queue(TaskPriority.STRONG) {
            death(player)
        }
    }

    private suspend fun QueueTask.death(player: Player) {
        val world = player.world
        val deathAnim = world.definitions.get(AnimDef::class.java, DEATH_ANIMATION)
        val instancedMap = world.instanceAllocator.getMap(player.tile)

        player.damageMap.getMostDamage()?.let { killer ->
            if (killer is Player) {
                world.getService(LoggerService::class.java, searchSubclasses = true)?.logPlayerKill(killer, player)
            }
            player.attr[KILLER_ATTR] = WeakReference(killer)
        }

        world.plugins.executePlayerPreDeath(player)

        player.resetFacePawn()
        wait(2)
        player.animate(deathAnim.id)
        player.playJingle(90)
        wait(deathAnim.cycleLength + 1)
        player.skills.restoreAll()
        player.animate(-1)
        if (instancedMap == null) {
            // Note: maybe add a player attribute for death locations
            // R14.5/HOME_DESIGN_2.png: same south-of-centre arrival offset as first login -
            // never respawn a player inside the bank/GE pavilion at the exact centre tile.
            // BATCH 1: offset updated from (0,-3) to (0,-10) alongside SAFE_RADIUS 5->24 - the
            // game module can't depend on the plugins module, so this must stay numerically in
            // sync with HomeLayout.arrival in gg.rsmod.plugins.content.areas.home.HomeLayout.kt.
            // teleportTo(), not moveTo(): moveTo() only snaps instantly when the destination is
            // beyond normal view distance, so dying just outside Ferox (well within that distance
            // of the respawn tile) rendered as a walk/glide into the enclave instead of a teleport.
            player.teleportTo(player.world.gameContext.home.transform(0, -1))
        } else {
            player.teleportTo(instancedMap.exitTile)
            world.instanceAllocator.death(player)
        }
        player.writeMessage("Oh dear, you are dead!")
        player.setCurrentLifepoints(player.getMaximumLifepoints())
        player.unlock()

        player.attr.removeIf { it.resetOnDeath }
        player.attr.put(DEATH_FLAG, false)
        player.timers.removeIf { it.resetOnDeath }

        world.plugins.executePlayerDeath(player)
    }

    fun handleDeath(player: Player) {
        val deathPluginInstance = Plugin(player)
        deathPlugin(deathPluginInstance)
    }
}

private fun Player.playJingle(
    id: Int,
    volume: Int = 255,
) {
    write(MusicEffectMessage(id = id, volume = volume))
}
