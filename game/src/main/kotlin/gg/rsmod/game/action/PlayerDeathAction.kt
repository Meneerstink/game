package gg.rsmod.game.action

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.message.impl.MusicEffectMessage
import gg.rsmod.game.model.attr.DEATH_FLAG
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.attr.RESPAWN_TILE_ATTR
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.service.log.LoggerService
import java.lang.ref.WeakReference
import mu.KLogging

/**
 * @author Tom <rspsmods@gmail.com>
 */
object PlayerDeathAction : KLogging() {
    private const val DEATH_ANIMATION = 836

    /**
     * How recently a pawn must have hit the player to be eligible as its killer.
     *
     * The damage map accumulates without any notion of "this fight", so without a window the
     * highest lifetime damage dealer wins: a PKer who hit for 50 and was escaped from minutes ago
     * outranked the dragon that actually landed the kill, and the death was then resolved as a PvP
     * death (ground loot for an absent killer instead of death recovery). 60 seconds, kept
     * numerically in sync with `PvpSkull.AGGRESSOR_WINDOW_CYCLES` (100 cycles) - the same window the
     * PvP aggressor/skull rules already use - because the game module cannot depend on plugins.
     */
    private const val KILL_CREDIT_WINDOW_MS = 60_000L

    val deathPlugin: Plugin.() -> Unit = {
        val player = ctx as Player

        player.attr.put(DEATH_FLAG, true)
        player.interruptQueues()
        player.stopMovement()
        // Death ends the persistent combat loop as well as movement. Clear the target immediately
        // so the player cannot unlock with a stale COMBAT_TARGET_FOCUS_ATTR and remain logically
        // engaged with the pre-death opponent until a later interaction happens to reset it.
        player.resetInteractions()
        player.lock()
        player.queue(TaskPriority.STRONG) {
            death(player)
        }
    }

    private suspend fun QueueTask.death(player: Player) {
        val world = player.world
        val deathAnim = world.definitions.get(AnimDef::class.java, DEATH_ANIMATION)
        val instancedMap = world.instanceAllocator.getMap(player.tile)

        // KILLER_ATTR is a per-death snapshot consumed by the death plugin. Clear the previous
        // snapshot first: a PvM/environmental death with no current damage must never inherit the
        // player killer from an earlier death and become PvP loot by stale attribution.
        player.attr.remove(KILLER_ATTR)
        player.damageMap.getMostDamage(KILL_CREDIT_WINDOW_MS)?.let { killer ->
            if (killer is Player) {
                world.getService(LoggerService::class.java, searchSubclasses = true)?.logPlayerKill(killer, player)
            }
            player.attr[KILLER_ATTR] = WeakReference(killer)
        }

        runDeathHook(player, "player-pre-death") { world.plugins.executePlayerPreDeath(player) }

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
            val chosen = player.attr[RESPAWN_TILE_ATTR]?.let { Tile.from30BitHash(it) }
            player.teleportTo(chosen ?: player.world.gameContext.home.transform(0, -1))
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
        // A death ends the fight, so the damage that caused it must not be carried into the next one.
        // [Npc.reset] already clears an npc's map on every death; the player's map was never cleared,
        // so [gg.rsmod.game.model.combat.DamageMap.getMostDamage] - which has no time window - kept
        // returning the highest *lifetime* damage dealer. A later PvM or environmental death then
        // inherited the old player killer, was classified as a PvP death and dropped the victim's
        // items as killer-owned ground loot instead of putting them into death recovery (and paid
        // that stale killer a loot key, killstreak and Trouver compensation). Cleared here, after
        // the KILLER_ATTR snapshot and the pre-death hook have both consumed the map.
        player.damageMap.reset()

        runDeathHook(player, "player-death") { world.plugins.executePlayerDeath(player) }
    }

    /**
     * A plugin death hook is optional presentation/gameplay work and must not strand the
     * player in the locked death queue when one hook throws. Keep the sourced death lifecycle
     * (respawn, unlock and reset-on-death cleanup) authoritative, while retaining diagnostics.
     */
    private inline fun runDeathHook(
        player: Player,
        stage: String,
        hook: () -> Unit,
    ) {
        try {
            hook()
        } catch (e: Exception) {
            logger.error("Player death hook '$stage' failed for username=${player.username}; continuing lifecycle.", e)
        }
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
