package gg.rsmod.plugins.content.activity.soul_wars

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player

enum class SoulWarsTeam { BLUE, RED }

val SW_TEAM = AttributeKey<SoulWarsTeam>()
val SW_ZEAL = AttributeKey<Int>()

/**
 * Shared match/lobby state - one game in progress at a time, same pattern already used by Castle
 * Wars/Clan-Wars-full/Pest Control this session (real static shared arena, no per-game instancing
 * needed - confirmed before writing this, unlike Stealing Creation's blocked dynamic arena).
 */
object SoulWarsMatch {
    val lobbyPlayers: MutableSet<Player> = HashSet()
    var lobbyTicks = 0

    var active = false
    var ticks = 0

    val blueTeam: MutableSet<Player> = HashSet()
    val redTeam: MutableSet<Player> = HashSet()

    var blueAvatar: Npc? = null
    var redAvatar: Npc? = null
    var blueAvatarLevel = 100
    var redAvatarLevel = 100
    var blueDeaths = 0
    var redDeaths = 0

    var midCapVal = 15
    var eastCapVal = 15
    var westCapVal = 15

    fun reset() {
        active = false
        ticks = 0
        blueTeam.clear()
        redTeam.clear()
        blueAvatar = null
        redAvatar = null
        blueAvatarLevel = 100
        redAvatarLevel = 100
        blueDeaths = 0
        redDeaths = 0
        midCapVal = 15
        eastCapVal = 15
        westCapVal = 15
    }
}
