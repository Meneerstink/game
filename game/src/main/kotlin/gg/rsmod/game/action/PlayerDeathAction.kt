package gg.rsmod.game.action

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.message.impl.MusicEffectMessage
import gg.rsmod.game.model.attr.DEATH_FLAG
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.attr.RESPAWN_TILE_ATTR
import gg.rsmod.game.model.Gender
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.game.task.rethrowIfFatal
import java.lang.ref.WeakReference
import mu.KLogging

/**
 * @author Tom <rspsmods@gmail.com>
 */
object PlayerDeathAction : KLogging() {
    private const val DEATH_ANIMATION = 836

    /**
     * N170/N177 (owner 2026-10-22: "als een speler doodgaat hoor je geen death-geluid"): only the dying player heard anything (the
     * death jingle), the killer and everyone around him heard nothing. His death cry now plays as an area sound with the death
     * animation. Rev-667 sound ids of plugins `Sfx.HUMAN_DEATH` / `Sfx.FEMALE_DEATH` (the game module cannot depend on plugins).
     */
    const val MALE_DEATH_SOUND = 512
    const val FEMALE_DEATH_SOUND = 505
    const val DEATH_SOUND_RADIUS = 10

    fun deathSound(player: Player): AreaSound =
        AreaSound(
            tile = player.tile,
            id = if (player.appearance.gender == Gender.FEMALE) FEMALE_DEATH_SOUND else MALE_DEATH_SOUND,
            radius = DEATH_SOUND_RADIUS,
            volume = 1,
        )

    /**
     * How recently a pawn must have hit the player to be eligible as its killer.
     *
     * The damage map accumulates without any notion of "this fight", so without a window the
     * highest lifetime damage dealer wins: a PKer who hit for 50 and was escaped from minutes ago
     * outranked the dragon that actually landed the kill, and the death was then resolved as a PvP
     * death (ground loot for an absent killer instead of death recovery). 100 game cycles (60
     * seconds), kept in sync with `PvpSkull.AGGRESSOR_WINDOW_CYCLES` (100 cycles) - the same window
     * the PvP aggressor/skull rules already use - because the game module cannot depend on plugins.
     *
     * Audit T-12: counted in game cycles, like the damage map's hit stamps (it was 60 000 ms of wall
     * clock, which lag or a catch-up burst stretched or shrank).
     */
    private const val KILL_CREDIT_WINDOW_CYCLES = 100

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
        player.damageMap.getMostDamage(KILL_CREDIT_WINDOW_CYCLES)?.let { killer ->
            if (killer is Player) {
                world.getService(LoggerService::class.java, searchSubclasses = true)?.logPlayerKill(killer, player)
            }
            player.attr[KILLER_ATTR] = WeakReference(killer)
        }

        // Debug toolkit (TRACES.md): who killed whom where, and what the victim carried before the death rules ran.
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            val killer = player.attr[KILLER_ATTR]?.get()
            val carried = (player.inventory.rawItems.filterNotNull() + player.equipment.rawItems.filterNotNull()).joinToString(",") { "${it.id}x${it.amount}" }
            "death ${player.username} at ${player.tile.x},${player.tile.z},${player.tile.height} killer=${(killer as? Player)?.username ?: (killer as? gg.rsmod.game.model.entity.Npc)?.let { "npc ${it.id}" } ?: "-"} carried=[$carried]"
        }
        // Void PlayerDeath: retribution(this) / wrath(this) fire at the moment of death, before the death animation; only the
        // items wait for the animation (pre-death hook below). Moving the whole pre-death hook (2026-10-04) had delayed both.
        runDeathHook(player, "player-death-start") { world.plugins.executePlayerDeathStart(player) }
        player.resetFacePawn()
        wait(2)
        player.animate(deathAnim.id)
        // N505 (owner 2026-11-01 "geen death-geluiden"; his client has Area sounds and Music at 0 - test client 11-01 played 512): the
        // dying player hears his own cry as an ordinary sound effect (the Sound effects slider), everyone around him as an area sound.
        world.spawn(deathSound(player).also { it.excluded = player })
        player.playDeathCry()
        wait(deathAnim.cycleLength + 1)
        // Void PlayerDeath: the death animation plays out first, then the death jingle and the items (owner 2026-10-04 030147:
        // "the character loses the inventory first, then the death animation; no sound"). The pre-death hook resolves the items
        // on the death tile, before the respawn teleport below.
        player.playJingle(90)
        runDeathHook(player, "player-pre-death") { world.plugins.executePlayerPreDeath(player) }
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
        // Hits that were in flight while he was dying ([Player.isDead] held them) belong to the fight that killed him.
        player.clearHits()

        runDeathHook(player, "player-death") { world.plugins.executePlayerDeath(player) }
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            val kept = (player.inventory.rawItems.filterNotNull() + player.equipment.rawItems.filterNotNull()).joinToString(",") { "${it.id}x${it.amount}" }
            "respawn ${player.username} at ${player.tile.x},${player.tile.z},${player.tile.height} kept=[$kept]"
        }
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
        } catch (e: Throwable) {
            // N56: every non-fatal Throwable (ExceptionInInitializerError, NoClassDefFoundError, TODO()), not only Exception.
            e.rethrowIfFatal()
            logger.error("Player death hook '$stage' failed for username=${player.username}; continuing lifecycle.", e)
        }
    }

    fun handleDeath(player: Player) {
        val deathPluginInstance = Plugin(player)
        deathPlugin(deathPluginInstance)
    }
}

/** N505: the victim's own death cry, sent like a plugin sound effect (SynthSoundMessage) and traced the same way. */
private fun Player.playDeathCry() {
    val id = PlayerDeathAction.deathSound(this).id
    gg.rsmod.game.model.AvTrace.log { "sound player=$username id=$id loops=1 delay=0 volume=255 rate=256 cycle=${world.currentCycle} from=PlayerDeathAction (own death cry)" }
    write(gg.rsmod.game.message.impl.SynthSoundMessage(sound = id, loops = 1, delay = 0, volume = 255, rate = 256))
}

private fun Player.playJingle(
    id: Int,
    volume: Int = 255,
) {
    // The client plays jingles at the music volume (a muted music slider silences them); traced like the plugin jingles.
    gg.rsmod.game.model.AvTrace.log { "jingle player index=$index id=$id volume=$volume (death)" }
    write(MusicEffectMessage(id = id, volume = volume))
}
