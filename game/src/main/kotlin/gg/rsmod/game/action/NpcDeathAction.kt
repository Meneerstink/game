package gg.rsmod.game.action

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.service.log.LoggerService
import java.lang.ref.WeakReference
import mu.KLogging

/**
 * This class is responsible for handling npc death events.
 *
 * @author Tom <rspsmods@gmail.com>
 */
object NpcDeathAction : KLogging() {
    val deathPlugin: Plugin.() -> Unit = {
        val npc = ctx as Npc

        if (npc.getCurrentLifepoints() <= 0) {
            npc.interruptQueues()
            npc.stopMovement()
            npc.lock()

            npc.queue(TaskPriority.STRONG) {
                death(npc)
            }
        }
    }

    private suspend fun QueueTask.death(npc: Npc) {
        val world = npc.world
        val deathAnimation = npc.combatDef.deathAnimation
        val respawnDelay = npc.combatDef.respawnDelay
        val deathDelay = npc.combatDef.deathDelay.coerceAtLeast(0)

        npc.damageMap.getMostDamage()?.let { killer ->
            if (killer is Player) {
                world.getService(LoggerService::class.java, searchSubclasses = true)?.logNpcKill(killer, npc)
                killer.incrementNpcKillCount(npc.id, 1)
                runDeathHook(npc, "npc-killed") { world.plugins.executeNpcKilled(killer, npc) }
            }
            killer.timers.remove(ACTIVE_COMBAT_TIMER)
            npc.attr[KILLER_ATTR] = WeakReference(killer)
        }

        runDeathHook(npc, "npc-pre-death") { world.plugins.executeNpcPreDeath(npc) }

        npc.resetFacePawn()

        runDeathHook(npc, "slayer") { world.plugins.executeSlayerLogic(npc) }

        deathAnimation.filter { it >= 0 }.forEach { anim ->
            val def = npc.world.definitions.get(AnimDef::class.java, anim)
            npc.animate(def.id)
            val timer = if (def.cycleLength >= 6) def.cycleLength - 4 else def.cycleLength
            wait(timer)
        }
        if (deathDelay > 0) {
            wait(deathDelay)
        }
        runDeathHook(npc, "npc-death") { world.plugins.executeNpcDeath(npc) }

        if (npc.respawns) {
            npc.invisible = true
            npc.reset()
            wait(respawnDelay)
            npc.invisible = false
            runDeathHook(npc, "npc-spawn") { world.plugins.executeNpcSpawn(npc) }
        } else {
            world.remove(npc)
        }
    }

    /**
     * Death cleanup must not depend on an optional plugin hook being perfect. The queue runner
     * removes a failed task, but that task is not the NPC's lock owner, so an exception here would
     * otherwise leave a dead NPC locked in-world and skip respawn/removal. Log the hook failure
     * and keep the sourced death lifecycle moving.
     */
    private inline fun runDeathHook(
        npc: Npc,
        stage: String,
        hook: () -> Unit,
    ) {
        try {
            hook()
        } catch (e: Exception) {
            logger.error("NPC death hook '$stage' failed for id=${npc.id}; continuing lifecycle.", e)
        }
    }

    private fun Npc.reset() {
        lock = LockState.NONE
        tile = spawnTile
        setTransmogId(-1)
        attr.clear()
        timers.clear()
        world.setNpcDefaults(this)
        damageMap.reset()
        resetInteractions()
    }
}
