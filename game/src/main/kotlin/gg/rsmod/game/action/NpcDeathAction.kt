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
import gg.rsmod.game.task.rethrowIfFatal
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

        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            val killer = npc.attr[KILLER_ATTR]?.get()
            "npc death ${npc.id}#${npc.index} at ${npc.tile.x},${npc.tile.z} killer=${(killer as? Player)?.username ?: killer?.toString() ?: "-"} deathAnims=$deathAnimation respawns=${npc.respawns}"
        }
        if (deathAnimation.none { it >= 0 }) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.COMBAT) { "npc ${npc.id} died without a death animation (it vanishes)" }
        }
        runDeathHook(npc, "npc-pre-death") { world.plugins.executeNpcPreDeath(npc) }

        npc.resetFacePawn()

        // N451 (owner 2026-10-30, Warriors' Guild: "als ze doodgaan verschijnen ze nog 1 seconde levend"): a short death animation
        // waited its length rounded UP to whole ticks and then the death delay, and once the animation had ended the client drew
        // the npc standing again until it was removed (Animated armour: 4167 runs 1.9 s, removed after 3.0 s). It now waits the
        // whole ticks the animation really plays and is hidden for the death delay, so it vanishes as its death animation ends
        // (OSRS); the drop and the respawn keep their time.
        deathAnimation.filter { it >= 0 }.forEach { anim ->
            val def = npc.world.definitions.get(AnimDef::class.java, anim)
            npc.animate(def.id)
            val timer = if (npc.combatDef.waitFullDeathAnimation) completedDeathAnimationTicks(def)
                else if (def.cycleLength >= 6) def.cycleLength - 4 else deathAnimationTicks(def)
            wait(timer)
        }
        if (deathDelay > 0) {
            if (deathAnimation.any { it >= 0 }) npc.invisible = true
            wait(deathDelay)
        }
        if (npc.combatDef.completesEncounter) {
            (npc.attr[KILLER_ATTR]?.get() as? Player)?.let { killer ->
                runDeathHook(npc, "npc-completed") { world.plugins.executeNpcCompleted(killer, npc) }
            }
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

    /** N451: the whole game ticks a short death animation really plays (client frame lengths are 20 ms, a tick 600 ms), at least 1. */
    fun deathAnimationTicks(def: AnimDef): Int = (if (def.playedFrameLength > 0) def.playedFrameLength / 30 else def.cycleLength).coerceAtLeast(1)

    /** Round UP the played part only: the source's 20000-cycle hold frame is a pose, not another 400 seconds. */
    fun completedDeathAnimationTicks(def: AnimDef): Int =
        (if (def.playedFrameLength > 0) (def.playedFrameLength + 29) / 30 else def.cycleLength).coerceAtLeast(1)

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
        } catch (e: Throwable) {
            // N56: every non-fatal Throwable (ExceptionInInitializerError, NoClassDefFoundError, TODO()), not only Exception.
            e.rethrowIfFatal()
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
