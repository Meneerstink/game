package gg.rsmod.plugins.content.activity.soul_wars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarc
import kotlin.random.Random

/**
 * Core Soul Wars logic, adapted (not copy-pasted) from a same-cache-family GitHub Kotlin source
 * (kennethyork/single-rs-2012, `darkan-world-server`) into this project's KotlinPlugin DSL. See
 * `SoulWarsData.kt` for real ids/tiles and `soul_wars.plugin.kts` for hook wiring.
 */
object SoulWarsHandler {
    private fun within(
        tile: Tile,
        bounds: Pair<Tile, Tile>,
    ): Boolean {
        val (min, max) = bounds
        return tile.x in min.x..max.x && tile.z in min.z..max.z
    }

    fun joinLobby(player: Player) {
        if (player.equipment[EquipmentType.CAPE.id] != null) {
            player.message("You cannot enter the lobby with a cape.")
            return
        }
        SoulWarsMatch.lobbyPlayers.add(player)
        player.attr[SW_ZEAL] = player.attr[SW_ZEAL] ?: 0
        updateLobbyVars(player)
    }

    fun leaveLobby(player: Player) {
        SoulWarsMatch.lobbyPlayers.remove(player)
    }

    fun updateLobbyVars(player: Player) {
        player.setVarc(SoulWarsData.GAME_ACTIVE_VARC, if (SoulWarsMatch.active) 1 else 0)
        val needed = (SoulWarsData.PLAYER_MINIMUM - SoulWarsMatch.lobbyPlayers.size).coerceAtLeast(0)
        player.setVarc(SoulWarsData.PLAYERS_NEEDED_BLUE_VARC, needed)
        player.setVarc(SoulWarsData.PLAYERS_NEEDED_RED_VARC, needed)
        player.setVarc(SoulWarsData.LOBBY_MINUTES_PASSED_VARC, SoulWarsMatch.lobbyTicks / 100)
    }

    fun updateIngameVars(player: Player) {
        player.setVarc(SoulWarsData.GAME_MINUTES_PASSED_VARC, SoulWarsMatch.ticks / 100)
        player.setVarc(SoulWarsData.BLUE_TEAM_SIZE_VARC, SoulWarsMatch.blueTeam.size)
        player.setVarc(SoulWarsData.RED_TEAM_SIZE_VARC, SoulWarsMatch.redTeam.size)
        player.setVarc(SoulWarsData.BLUE_AVATAR_HEALTH_VARC, healthPercent(SoulWarsMatch.blueAvatar, SoulWarsMatch.blueAvatarLevel))
        player.setVarc(SoulWarsData.RED_AVATAR_HEALTH_VARC, healthPercent(SoulWarsMatch.redAvatar, SoulWarsMatch.redAvatarLevel))
        player.setVarc(SoulWarsData.BLUE_AVATAR_LEVEL_VARC, SoulWarsMatch.blueAvatarLevel)
        player.setVarc(SoulWarsData.RED_AVATAR_LEVEL_VARC, SoulWarsMatch.redAvatarLevel)
        player.setVarc(SoulWarsData.BLUE_AVATAR_DEATH_VARC, SoulWarsMatch.blueDeaths)
        player.setVarc(SoulWarsData.RED_AVATAR_DEATH_VARC, SoulWarsMatch.redDeaths)
        player.setVarc(SoulWarsData.MID_CLAIM_VARC, SoulWarsMatch.midCapVal)
        player.setVarc(SoulWarsData.EAST_CLAIM_VARC, SoulWarsMatch.eastCapVal)
        player.setVarc(SoulWarsData.WEST_CLAIM_VARC, SoulWarsMatch.westCapVal)
    }

    private fun healthPercent(
        avatar: Npc?,
        level: Int,
    ): Int {
        if (avatar == null || avatar.isDead()) return 0
        return level.coerceIn(0, 100)
    }

    fun attemptStartGame(world: World) {
        if (SoulWarsMatch.active) return
        if (SoulWarsMatch.lobbyPlayers.size < SoulWarsData.PLAYER_MINIMUM) return
        SoulWarsMatch.active = true
        val sorted = SoulWarsMatch.lobbyPlayers.toList().sortedBy { it.combatLevel }
        var blueTotal = 0
        var redTotal = 0
        for (player in sorted) {
            SoulWarsMatch.lobbyPlayers.remove(player)
            if (blueTotal <= redTotal) {
                SoulWarsMatch.blueTeam.add(player)
                player.attr[SW_TEAM] = SoulWarsTeam.BLUE
                blueTotal += player.combatLevel
                player.moveTo(randomTile(SoulWarsData.BLUE_RESPAWN_AREA))
                player.equipment[EquipmentType.CAPE.id] = Item(Items.BLUE_CAPE)
            } else {
                SoulWarsMatch.redTeam.add(player)
                player.attr[SW_TEAM] = SoulWarsTeam.RED
                redTotal += player.combatLevel
                player.moveTo(randomTile(SoulWarsData.RED_RESPAWN_AREA))
                player.equipment[EquipmentType.CAPE.id] = Item(Items.RED_CAPE)
            }
            player.message("The game has started! Fight for your team's soul avatar.")
        }
        SoulWarsMatch.blueAvatar = Npc(Npcs.AVATAR_OF_CREATION, SoulWarsData.BLUE_AVATAR_SPAWN, world).also { world.spawn(it) }
        SoulWarsMatch.redAvatar = Npc(Npcs.AVATAR_OF_DESTRUCTION, SoulWarsData.RED_AVATAR_SPAWN, world).also { world.spawn(it) }
    }

    private fun randomTile(bounds: Pair<Tile, Tile>): Tile {
        val (min, max) = bounds
        val x = if (max.x > min.x) Random.nextInt(min.x, max.x) else min.x
        val z = if (max.z > min.z) Random.nextInt(min.z, max.z) else min.z
        return Tile(x, z, min.height)
    }

    fun tick(world: World) {
        if (!SoulWarsMatch.active) return
        SoulWarsMatch.ticks++
        if (SoulWarsMatch.ticks >= SoulWarsData.GAME_DURATION_TICKS) {
            endGame(world)
            return
        }
        if (SoulWarsMatch.ticks % 5 != 0) return
        SoulWarsMatch.midCapVal = shift(SoulWarsMatch.midCapVal, SoulWarsData.MID_CAP_ZONE, world)
        SoulWarsMatch.eastCapVal = shift(SoulWarsMatch.eastCapVal, SoulWarsData.EAST_CAP_ZONE, world)
        SoulWarsMatch.westCapVal = shift(SoulWarsMatch.westCapVal, SoulWarsData.WEST_CAP_ZONE, world)
    }

    private fun shift(
        current: Int,
        zone: Pair<Tile, Tile>,
        world: World,
    ): Int {
        var blueCount = 0
        var redCount = 0
        for (player in SoulWarsMatch.blueTeam) if (within(player.tile, zone)) blueCount++
        for (player in SoulWarsMatch.redTeam) if (within(player.tile, zone)) redCount++
        val delta = (redCount - blueCount).coerceIn(-1, 1)
        return (current + delta).coerceIn(0, 30)
    }

    fun bury(
        player: Player,
        team: SoulWarsTeam,
    ): Boolean {
        val currentLevel = if (team == SoulWarsTeam.BLUE) SoulWarsMatch.blueAvatarLevel else SoulWarsMatch.redAvatarLevel
        if (currentLevel >= 100) {
            player.message("Your avatar is already a high enough level.")
            return false
        }
        if (team == SoulWarsTeam.BLUE) SoulWarsMatch.blueAvatarLevel++ else SoulWarsMatch.redAvatarLevel++
        return true
    }

    /** Weakens the opponent avatar - only works while your team controls the mid obelisk. */
    fun useSoulFragmentOnObelisk(
        player: Player,
        team: SoulWarsTeam,
        amount: Int,
    ): Int {
        val controlledByTeam =
            if (team == SoulWarsTeam.RED) SoulWarsMatch.midCapVal >= 25 else SoulWarsMatch.midCapVal <= 5
        if (!controlledByTeam) {
            player.message("The obelisk is unresponsive as your team is not in control of it.")
            return 0
        }
        val opponentLevel = if (team == SoulWarsTeam.BLUE) SoulWarsMatch.redAvatarLevel else SoulWarsMatch.blueAvatarLevel
        val used = amount.coerceAtMost(opponentLevel)
        if (used <= 0) {
            player.message("Your opponent's avatar cannot be weakened any further.")
            return 0
        }
        if (team == SoulWarsTeam.BLUE) SoulWarsMatch.redAvatarLevel -= used else SoulWarsMatch.blueAvatarLevel -= used
        return used
    }

    fun onAvatarDeath(
        team: SoulWarsTeam,
        world: World,
    ) {
        if (!SoulWarsMatch.active) return
        if (team == SoulWarsTeam.BLUE) {
            SoulWarsMatch.blueDeaths++
            SoulWarsMatch.blueAvatarLevel = 100
            SoulWarsMatch.blueAvatar = Npc(Npcs.AVATAR_OF_CREATION, SoulWarsData.BLUE_AVATAR_SPAWN, world).also { world.spawn(it) }
        } else {
            SoulWarsMatch.redDeaths++
            SoulWarsMatch.redAvatarLevel = 100
            SoulWarsMatch.redAvatar = Npc(Npcs.AVATAR_OF_DESTRUCTION, SoulWarsData.RED_AVATAR_SPAWN, world).also { world.spawn(it) }
        }
    }

    fun endGame(world: World) {
        // Real donor bug found and NOT replicated: the source's own zeal message text says a draw
        // awards "2 Zeal" but its zeal variable only ever produces 1 or 3 (no draw branch) -
        // implemented against the donor's evident intent (win=3, draw=2, loss=1) instead.
        val winner: SoulWarsTeam? =
            when {
                SoulWarsMatch.blueDeaths == SoulWarsMatch.redDeaths && SoulWarsMatch.blueAvatarLevel == SoulWarsMatch.redAvatarLevel -> null
                SoulWarsMatch.blueDeaths == SoulWarsMatch.redDeaths -> if (SoulWarsMatch.blueAvatarLevel > SoulWarsMatch.redAvatarLevel) SoulWarsTeam.BLUE else SoulWarsTeam.RED
                SoulWarsMatch.redDeaths > SoulWarsMatch.blueDeaths -> SoulWarsTeam.BLUE
                else -> SoulWarsTeam.RED
            }
        fun payout(
            team: SoulWarsTeam,
            players: Set<Player>,
            exitArea: Pair<Tile, Tile>,
        ) {
            val zeal = if (winner == null) 2 else if (winner == team) 3 else 1
            for (player in players) {
                player.moveTo(randomTile(exitArea))
                player.equipment[EquipmentType.CAPE.id] = null
                val total = (player.attr[SW_ZEAL] ?: 0) + zeal
                player.attr[SW_ZEAL] = total
                player.message("The game has ended. You are awarded $zeal Zeal. You now have $total Zeal.")
            }
        }
        payout(SoulWarsTeam.BLUE, SoulWarsMatch.blueTeam, SoulWarsData.BLUE_EXIT_AREA)
        payout(SoulWarsTeam.RED, SoulWarsMatch.redTeam, SoulWarsData.RED_EXIT_AREA)
        SoulWarsMatch.blueAvatar?.let { world.remove(it) }
        SoulWarsMatch.redAvatar?.let { world.remove(it) }
        SoulWarsMatch.reset()
    }
}
