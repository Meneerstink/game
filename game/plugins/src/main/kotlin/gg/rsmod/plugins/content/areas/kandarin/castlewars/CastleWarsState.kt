package gg.rsmod.plugins.content.areas.kandarin.castlewars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Objs

/**
 * Castle Wars (2011): two teams, Saradomin and Zamorak, fight to capture the enemy flag and
 * return it to their own flag stand. Ported from Novite's `game/minigames/CastleWars.java` +
 * `player/controlers/impl/castlewars/{CastleWarsWaiting,CastleWarsPlaying}.java` (the only donor
 * with real minigame logic - Void only has data tomls, no `content/minigame/castle_wars` Kotlin
 * source). Every id below was verified against this project's own generated `Items.kt`/`Objs.kt`/
 * `Npcs.kt` (via `runObjectDefProbeTool`/`runNpcDefProbeTool` for real option text) before use.
 */
/**
 * Each team's flag lives in that team's OWN castle as [homeFlagObj] - the object the OTHER team
 * must invade and steal. Once stolen, [homeFlagObj] is replaced in-place by [emptyStandObj] (same
 * tile) until the flag is returned. A team's own players score by bringing the ENEMY flag back to
 * either of those two objects (whichever is currently spawned) at their OWN team's location -
 * i.e. "bring the flag back to where your flag normally stands". This dual-object-per-location
 * design and every id below is copied verbatim from Novite's `CastleWarsPlaying.processObjectClick1`
 * (the `id == 4902/4903` and `id == 4377/4378` branches), not simplified or guessed.
 */
enum class CastleWarsTeam(
    val capeId: Int,
    val hoodId: Int,
    val flagWeaponId: Int,
    val homeFlagObj: Int,
    val emptyStandObj: Int,
    val droppedFlagObj: Int,
    val waitingTile: Tile,
    val baseTile: Tile,
) {
    SARADOMIN(
        capeId = Items.HOODED_CLOAK,
        hoodId = Items.SARADOMIN_TEAM_HOOD,
        flagWeaponId = Items.SARADOMIN_FLAG,
        homeFlagObj = Objs.SARADOMIN_FLAG_4902,
        emptyStandObj = Objs.FLAG_STAND,
        droppedFlagObj = Objs.SARADOMIN_FLAG_4900,
        waitingTile = Tile(2381, 9489, 0),
        baseTile = Tile(2426, 3076, 1),
    ),
    ZAMORAK(
        capeId = Items.HOODED_CLOAK_4042,
        hoodId = Items.ZAMORAK_TEAM_HOOD,
        flagWeaponId = Items.ZAMORAK_FLAG,
        homeFlagObj = Objs.ZAMORAK_FLAG_4903,
        emptyStandObj = Objs.FLAG_STAND_4378,
        droppedFlagObj = Objs.ZAMORAK_FLAG_4901,
        waitingTile = Tile(2421, 9523, 0),
        baseTile = Tile(2373, 3131, 1),
    ),
    ;

    fun other() = if (this == SARADOMIN) ZAMORAK else SARADOMIN

    companion object {
        fun forFlagWeapon(itemId: Int) = values().firstOrNull { it.flagWeaponId == itemId }

        fun forHomeFlag(objId: Int) = values().firstOrNull { it.homeFlagObj == objId }

        fun forEmptyStand(objId: Int) = values().firstOrNull { it.emptyStandObj == objId }

        fun forDroppedFlag(objId: Int) = values().firstOrNull { it.droppedFlagObj == objId }
    }
}

enum class FlagStatus { SAFE, TAKEN, DROPPED }

/**
 * Single shared arena, matching Novite's one-game-at-a-time design (no instance/zone manager
 * exists in this engine - see the project's Dungeoneering entry - but Castle Wars in 2011 only
 * ever ran one concurrent game per world anyway, so this is not an architecture blocker here).
 */
object CastleWarsRound {
    val LOBBY = Tile(2442, 3090, 0)

    /** Real arithmetic derived from Novite's TimerTask (60s ticks, not guessed): 5 one-minute
     * lobby ticks before a game with 2+ waiting players starts, then 20 one-minute ticks of
     * actual play before the round ends and scores reset. */
    const val LOBBY_MINUTES = 5
    const val MATCH_MINUTES = 20
    const val PLAYERS_NEEDED_TO_START = 2

    val ROUND_TIMER = TimerKey()
    const val TICKS_PER_MINUTE = 100 // 100 x 600ms = 1 minute, matching Novite's 60000ms cadence

    var active = false
    var minutesLeft = 0
    val waiting = mutableMapOf(CastleWarsTeam.SARADOMIN to mutableListOf<Player>(), CastleWarsTeam.ZAMORAK to mutableListOf())
    val playing = mutableMapOf(CastleWarsTeam.SARADOMIN to mutableListOf<Player>(), CastleWarsTeam.ZAMORAK to mutableListOf())
    val score = mutableMapOf(CastleWarsTeam.SARADOMIN to 0, CastleWarsTeam.ZAMORAK to 0)
    val flagStatus = mutableMapOf(CastleWarsTeam.SARADOMIN to FlagStatus.SAFE, CastleWarsTeam.ZAMORAK to FlagStatus.SAFE)
    val barricadeCount = mutableMapOf(CastleWarsTeam.SARADOMIN to 0, CastleWarsTeam.ZAMORAK to 0)
    val spawnedFlagObjects = mutableMapOf<CastleWarsTeam, GameObject>()
    val barricades = mutableListOf<Npc>()
    val seasonWins = mutableMapOf(CastleWarsTeam.SARADOMIN to 0, CastleWarsTeam.ZAMORAK to 0)

    fun reset() {
        active = false
        minutesLeft = LOBBY_MINUTES
        score[CastleWarsTeam.SARADOMIN] = 0
        score[CastleWarsTeam.ZAMORAK] = 0
        flagStatus[CastleWarsTeam.SARADOMIN] = FlagStatus.SAFE
        flagStatus[CastleWarsTeam.ZAMORAK] = FlagStatus.SAFE
        barricadeCount[CastleWarsTeam.SARADOMIN] = 0
        barricadeCount[CastleWarsTeam.ZAMORAK] = 0
    }
}

val CW_TEAM = AttributeKey<CastleWarsTeam>()
val CW_PLAYING = AttributeKey<Boolean>()
