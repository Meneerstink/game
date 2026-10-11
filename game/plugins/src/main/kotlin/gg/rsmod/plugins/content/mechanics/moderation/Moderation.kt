package gg.rsmod.plugins.content.mechanics.moderation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.priv.Punishments
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.message
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Owner 2026-10-10 (staff tools: moderators may kick, mute, ban and jail; admins everything): the moderation rules.
 *
 *  - mute / ban: Void `content.social.report.Mute` / `Ban` - an until time in epoch seconds ([Punishments]); a ban kicks an
 *    online player and is kept by name in [gg.rsmod.game.service.world.BannedPlayers], so offline players can be banned too.
 *  - jail: Novite `JailCommand` / `JailControler` / `Player.sendRandomJail` - the Port Sarim jail cells ([JAIL_TILES]), a time
 *    in hours, no teleports and no object clicks while jailed ("You are currently jailed for your delinquent acts."), back into
 *    a cell after a death, and released to the home tile when the time is up ("Your account has been unjailed.").
 *  - every action is written to [LOG] (Void `AuditLog.event`).
 */
object Moderation {
    /** Novite `Player.sendRandomJail`: the Port Sarim jail cells (its cases 5 and 6 are the same tile). */
    val JAIL_TILES =
        listOf(Tile(3014, 3195, 0), Tile(3015, 3189, 0), Tile(3014, 3189, 0), Tile(3014, 3192, 0), Tile(3018, 3180, 0), Tile(3018, 3189, 0))

    /** The Port Sarim jail building around [JAIL_TILES]: a jailed player found outside it is put back. */
    fun inJail(tile: Tile): Boolean = tile.height == 0 && tile.x in 3010..3021 && tile.z in 3177..3198

    const val JAILED_MESSAGE = "You are currently jailed for your delinquent acts."

    /** Novite `JailControler.process`, every [JAIL_CHECK_TICKS]: release when the time is up, otherwise keep the player inside. */
    val JAIL_TIMER = TimerKey()
    const val JAIL_CHECK_TICKS = 10

    private val LOG = gg.rsmod.util.io.SharedDataFiles.of("moderation_log.txt")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Synchronized
    fun log(
        staff: Player,
        action: String,
        target: String,
        detail: String = "",
    ) {
        runCatching {
            LOG.parentFile?.mkdirs()
            LOG.appendText("${LocalDateTime.now().format(STAMP)}\t${staff.username}\t$action\t$target\t$detail\n")
        }
    }

    /**
     * Staff may only punish a lower rank (a moderator cannot mute an admin, nobody punishes the owner); the owner may punish
     * anyone but himself.
     */
    fun mayPunish(
        staff: Player,
        target: Player,
    ): Boolean =
        staff !== target &&
            (staff.privilege.powers.contains(Privilege.OWNER_POWER) || target.privilege.id < staff.privilege.id)

    /** JsonPlayerSerializer's default save folder (game.yml sets no other path). */
    const val SAVES = "./data/saves/"

    private val PRIVILEGE_FIELD = Regex(""""privilege"\s*:\s*(\d+)""")

    /** The privilege id saved for an offline account, or null when no account has that name (a typo). */
    fun offlinePrivilegeId(name: String): Int? {
        val file = listOf(name, name.lowercase(), name.replace(' ', '_')).map { java.io.File(SAVES, it) }.firstOrNull { it.isFile } ?: return null
        return runCatching { PRIVILEGE_FIELD.find(file.readText())?.groupValues?.get(1)?.toInt() ?: 0 }.getOrDefault(0)
    }

    /** [mayPunish] for an offline account's saved privilege id. */
    fun mayPunishOffline(
        staff: Player,
        privilegeId: Int,
    ): Boolean = staff.privilege.powers.contains(Privilege.OWNER_POWER) || privilegeId < staff.privilege.id

    fun jailTile(player: Player): Tile = JAIL_TILES[player.world.random(JAIL_TILES.size - 1)]

    fun jail(
        target: Player,
        until: Int,
    ) {
        target.attr[Punishments.JAILED_UNTIL] = until
        target.stopMovement()
        target.moveTo(jailTile(target))
        target.timers[JAIL_TIMER] = JAIL_CHECK_TICKS
    }

    fun release(target: Player) {
        target.attr.remove(Punishments.JAILED_UNTIL)
        target.timers.remove(JAIL_TIMER)
        if (inJail(target.tile)) target.moveTo(target.world.gameContext.home)
        target.message("Your account has been unjailed.")
    }

    /** The jail timer and login: release an expired jail, or put an escaped player back in a cell. */
    fun check(player: Player) {
        val until = player.attr[Punishments.JAILED_UNTIL] ?: return
        if (until <= Punishments.epochSeconds()) {
            release(player)
            return
        }
        if (!inJail(player.tile)) player.moveTo(jailTile(player))
        player.timers[JAIL_TIMER] = JAIL_CHECK_TICKS
    }
}
