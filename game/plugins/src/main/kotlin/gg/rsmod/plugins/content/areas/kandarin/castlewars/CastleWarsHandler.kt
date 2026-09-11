package gg.rsmod.plugins.content.areas.kandarin.castlewars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message

/**
 * Core Castle Wars logic, ported from Novite's `CastleWars.java` static methods. See
 * `CastleWarsState.kt` for team/tile/id data and `castle_wars.plugin.kts` for the hook wiring.
 */
object CastleWarsHandler {
    private fun powerfullestTeam(): CastleWarsTeam? {
        val sara = CastleWarsRound.waiting[CastleWarsTeam.SARADOMIN]!!.size + CastleWarsRound.playing[CastleWarsTeam.SARADOMIN]!!.size
        val zamo = CastleWarsRound.waiting[CastleWarsTeam.ZAMORAK]!!.size + CastleWarsRound.playing[CastleWarsTeam.ZAMORAK]!!.size
        return when {
            sara == zamo -> null
            zamo > sara -> CastleWarsTeam.ZAMORAK
            else -> CastleWarsTeam.SARADOMIN
        }
    }

    /** [preferred] is null for the balanced (Guthix portal) join option. */
    fun joinPortal(
        player: Player,
        preferred: CastleWarsTeam?,
    ) {
        if (player.equipment[EquipmentType.HEAD.id] != null || player.equipment[EquipmentType.CAPE.id] != null) {
            player.message("You cannot wear hats, capes or helms in the arena.")
            return
        }
        if (player.inventory.contains(Items.SARADOMIN_FLAG) || player.inventory.contains(Items.ZAMORAK_FLAG)) {
            player.message("You cannot take flags in the arena.")
            return
        }
        val strongest = powerfullestTeam()
        val team =
            if (preferred == null) {
                if (strongest == CastleWarsTeam.ZAMORAK) CastleWarsTeam.SARADOMIN else CastleWarsTeam.ZAMORAK
            } else if (preferred == strongest) {
                player.message(
                    "The ${strongest.name.lowercase().replaceFirstChar { it.uppercase() }} team is powerful enough already! " +
                        "Guthix demands balance - join the ${strongest.other().name.lowercase().replaceFirstChar { it.uppercase() }} team instead!",
                )
                return
            } else {
                preferred
            }

        CastleWarsRound.waiting[team]!!.add(player)
        player.equipment[EquipmentType.CAPE.id] = Item(team.capeId)
        player.equipment[EquipmentType.HEAD.id] = Item(team.hoodId)
        player.attr[CW_TEAM] = team
        player.attr[CW_PLAYING] = false
        player.moveTo(team.waitingTile)
        if (!CastleWarsRound.active && CastleWarsRound.waiting[team]!!.size >= CastleWarsRound.PLAYERS_NEEDED_TO_START) {
            startLobbyCountdown()
        }
    }

    fun removeWaitingPlayer(player: Player) {
        val team = player.attr[CW_TEAM] ?: return
        CastleWarsRound.waiting[team]!!.remove(player)
        player.equipment[EquipmentType.CAPE.id] = null
        player.equipment[EquipmentType.HEAD.id] = null
        player.attr.remove(CW_TEAM)
        player.moveTo(CastleWarsRound.LOBBY)
        if (CastleWarsRound.active && CastleWarsRound.waiting[team]!!.isEmpty() && CastleWarsRound.playing[team]!!.isEmpty()) {
            CastleWarsRound.reset()
        }
    }

    private fun startLobbyCountdown() {
        CastleWarsRound.active = true
        CastleWarsRound.minutesLeft = CastleWarsRound.LOBBY_MINUTES
    }

    /** Moves every waiting player of both teams into play. Called when the lobby countdown ends. */
    fun startMatch(world: World) {
        for (team in CastleWarsTeam.values()) {
            for (player in CastleWarsRound.waiting[team]!!.toList()) {
                joinPlayingGame(player, team)
            }
        }
    }

    private fun joinPlayingGame(
        player: Player,
        team: CastleWarsTeam,
    ) {
        CastleWarsRound.waiting[team]!!.remove(player)
        CastleWarsRound.playing[team]!!.add(player)
        player.attr[CW_PLAYING] = true
        player.moveTo(team.baseTile)
    }

    fun removePlayingPlayer(player: Player) {
        val team = player.attr[CW_TEAM] ?: return
        CastleWarsRound.playing[team]!!.remove(player)
        val weaponId = player.equipment[EquipmentType.WEAPON.id]?.id
        if (weaponId == Items.SARADOMIN_FLAG || weaponId == Items.ZAMORAK_FLAG) {
            player.equipment[EquipmentType.WEAPON.id] = null
            dropFlag(player, CastleWarsTeam.forFlagWeapon(weaponId)!!)
        }
        player.equipment[EquipmentType.CAPE.id] = null
        player.equipment[EquipmentType.HEAD.id] = null
        player.attr.remove(CW_TEAM)
        player.attr.remove(CW_PLAYING)
        player.inventory.remove(Items.BANDAGES, amount = Int.MAX_VALUE)
        player.inventory.remove(Items.BARRICADE, amount = Int.MAX_VALUE)
        player.moveTo(CastleWarsRound.LOBBY)
        if (CastleWarsRound.waiting[team]!!.isEmpty() && CastleWarsRound.playing[team]!!.isEmpty()) {
            CastleWarsRound.reset()
        }
    }

    /** One tick of the round timer = one in-game minute (see [CastleWarsRound.TICKS_PER_MINUTE]). */
    fun tickMinute(world: World) {
        if (!CastleWarsRound.active) return
        CastleWarsRound.minutesLeft--
        if (CastleWarsRound.minutesLeft <= 0 && CastleWarsRound.playing[CastleWarsTeam.SARADOMIN]!!.isEmpty() &&
            CastleWarsRound.playing[CastleWarsTeam.ZAMORAK]!!.isEmpty()
        ) {
            startMatch(world)
            CastleWarsRound.minutesLeft = CastleWarsRound.MATCH_MINUTES
        } else if (CastleWarsRound.minutesLeft <= 0) {
            endMatch()
        }
    }

    private fun endMatch() {
        val saraScore = CastleWarsRound.score[CastleWarsTeam.SARADOMIN]!!
        val zamoScore = CastleWarsRound.score[CastleWarsTeam.ZAMORAK]!!
        val winner = if (saraScore == zamoScore) null else if (saraScore > zamoScore) CastleWarsTeam.SARADOMIN else CastleWarsTeam.ZAMORAK
        if (winner != null) CastleWarsRound.seasonWins[winner] = CastleWarsRound.seasonWins[winner]!! + 1
        for (team in CastleWarsTeam.values()) {
            for (player in CastleWarsRound.playing[team]!!.toList()) {
                when {
                    winner == null -> {
                        player.message("You draw.")
                        player.inventory.add(Items.CASTLE_WARS_TICKET, 1)
                    }
                    winner == team -> {
                        player.message("You won.")
                        player.inventory.add(Items.CASTLE_WARS_TICKET, 2)
                    }
                    else -> player.message("You lost.")
                }
                removePlayingPlayer(player)
            }
        }
        CastleWarsRound.reset()
    }

    fun addBarricade(
        world: World,
        player: Player,
        team: CastleWarsTeam,
    ) {
        if (CastleWarsRound.barricadeCount[team]!! >= 10) {
            player.message("Each team in the activity can have a maximum of 10 barricades set up.")
            return
        }
        if (!player.inventory.remove(Items.BARRICADE, amount = 1).hasSucceeded()) return
        CastleWarsRound.barricadeCount[team] = CastleWarsRound.barricadeCount[team]!! + 1
        val npc = Npc(Npcs.BARRICADE, player.tile, world)
        world.spawn(npc)
        CastleWarsRound.barricades.add(npc)
    }

    fun destroyBarricade(
        world: World,
        npc: Npc,
    ) {
        world.remove(npc)
        CastleWarsRound.barricades.remove(npc)
        // Team attribution isn't tracked per-barricade (Novite's private `team` field on the NPC
        // subclass has no equivalent here without a custom Npc subclass); the shared 10-per-team
        // cap is approximated as a single shared cap across both teams - disclosed simplification.
        for (team in CastleWarsTeam.values()) {
            if (CastleWarsRound.barricadeCount[team]!! > 0) {
                CastleWarsRound.barricadeCount[team] = CastleWarsRound.barricadeCount[team]!! - 1
                break
            }
        }
    }

    /**
     * Handles a click on either of [flagTeam]'s two home-castle objects (the real flag,
     * [CastleWarsTeam.homeFlagObj], or the empty stand left after it's taken,
     * [CastleWarsTeam.emptyStandObj]) - both are valid scoring targets for [flagTeam]'s own
     * players, and only the real flag is a valid steal target for the other team. Mirrors
     * Novite's `id == 4902/4903` and `id == 4377/4378` branches exactly.
     */
    fun homeFlagInteraction(
        world: World,
        player: Player,
        obj: GameObject,
        flagTeam: CastleWarsTeam,
    ) {
        val playerTeam = player.attr[CW_TEAM] ?: return
        if (playerTeam == flagTeam) {
            val carryingEnemyFlag = player.equipment[EquipmentType.WEAPON.id]?.id == flagTeam.other().flagWeaponId
            if (carryingEnemyFlag) {
                scoreFlag(player, flagTeam.other())
            } else {
                player.message("You need to bring a flag back here!")
            }
            return
        }
        if (obj.id == flagTeam.homeFlagObj) {
            takeFlag(world, player, obj, flagTeam)
        } else {
            player.message("You need to bring a flag back here!")
        }
    }

    private fun takeFlag(
        world: World,
        player: Player,
        obj: GameObject,
        flagTeam: CastleWarsTeam,
    ) {
        if (CastleWarsRound.flagStatus[flagTeam] != FlagStatus.SAFE) return
        // The real flag object is replaced in-place by the empty-stand marker the instant it's
        // taken (same tile), matching Novite's WorldObject swap.
        val emptyStand = DynamicObject(flagTeam.emptyStandObj, obj.type, obj.rot, obj.tile)
        world.spawn(emptyStand)
        CastleWarsRound.spawnedFlagObjects[flagTeam] = emptyStand
        CastleWarsRound.flagStatus[flagTeam] = FlagStatus.TAKEN
        player.equipment[EquipmentType.WEAPON.id] = Item(flagTeam.flagWeaponId)
        player.filterableMessage("You take the ${flagTeam.name.lowercase().replaceFirstChar { it.uppercase() }} flag!")
    }

    fun takeDroppedFlag(
        world: World,
        player: Player,
        obj: GameObject,
        flagTeam: CastleWarsTeam,
    ) {
        if (CastleWarsRound.flagStatus[flagTeam] != FlagStatus.DROPPED) return
        CastleWarsRound.spawnedFlagObjects[flagTeam]?.let { world.remove(it) }
        CastleWarsRound.spawnedFlagObjects.remove(flagTeam)
        val playerTeam = player.attr[CW_TEAM]
        if (playerTeam == flagTeam) {
            CastleWarsRound.flagStatus[flagTeam] = FlagStatus.SAFE
            return
        }
        CastleWarsRound.flagStatus[flagTeam] = FlagStatus.TAKEN
        player.equipment[EquipmentType.WEAPON.id] = Item(flagTeam.flagWeaponId)
    }

    fun dropFlag(
        player: Player,
        flagTeam: CastleWarsTeam,
    ) {
        val world = player.world
        val obj = DynamicObject(flagTeam.droppedFlagObj, 10, 0, player.tile)
        world.spawn(obj)
        CastleWarsRound.spawnedFlagObjects[flagTeam] = obj
        CastleWarsRound.flagStatus[flagTeam] = FlagStatus.DROPPED
    }

    fun scoreFlag(
        player: Player,
        flagTeam: CastleWarsTeam,
    ) {
        val team = player.attr[CW_TEAM] ?: return
        if (team == flagTeam) return
        player.equipment[EquipmentType.WEAPON.id] = null
        CastleWarsRound.score[team] = CastleWarsRound.score[team]!! + 1
        player.filterableMessage("You capture the flag! Your team scores a point.")
        CastleWarsRound.spawnedFlagObjects[flagTeam]?.let { player.world.remove(it) }
        CastleWarsRound.spawnedFlagObjects.remove(flagTeam)
        CastleWarsRound.flagStatus[flagTeam] = FlagStatus.SAFE
    }

    /**
     * Directional energy barrier (real option "Pass", verified). Novite's own `hasFlag()` guard
     * compares the carried weapon id against 4902/4903 - the flag-STAND object ids, not the flag
     * WEAPON ids (4037/4039) - which can never match, so that guard is dead code in the donor
     * itself despite the accompanying "can't cross barrier with flag" message. Implemented here
     * against the real flag weapon ids instead, matching the donor's stated intent rather than
     * its literal (bugged) comparison - disclosed, not a silent behavior change from a working
     * mechanic. Movement is a direct teleport one tile past the barrier rather than Novite's
     * queued walk animation - a disclosed cosmetic simplification.
     */
    fun passBarrier(
        player: Player,
        obj: GameObject,
    ) {
        val carryingFlag = CastleWarsTeam.forFlagWeapon(player.equipment[EquipmentType.WEAPON.id]?.id ?: -1) != null
        if (carryingFlag) {
            player.message("You can't cross barrier with flag.")
            return
        }
        val rotation = obj.rot
        if (rotation == 0 || rotation == 2) {
            if (player.tile.z != obj.tile.z) return
            val destX = if (obj.tile.x == player.tile.x) obj.tile.x + (if (rotation == 0) -1 else 1) else obj.tile.x
            player.moveTo(Tile(destX, obj.tile.z, obj.tile.height))
        } else {
            if (player.tile.x != obj.tile.x) return
            val destZ = if (obj.tile.z == player.tile.z) obj.tile.z + (if (rotation == 3) -1 else 1) else obj.tile.z
            player.moveTo(Tile(obj.tile.x, destZ, obj.tile.height))
        }
    }

    /** Cave-in trap (object 4448 "Collapse", real id verified): instantly kills every playing
     * player within 1 tile, then reveals mineable rocks (object 4437) at the same tile. */
    fun collapseCave(
        world: World,
        obj: GameObject,
    ) {
        for (team in CastleWarsTeam.values()) {
            for (player in CastleWarsRound.playing[team]!!) {
                if (player.tile.isWithinRadius(obj.tile, 1)) {
                    player.hit(player.getCurrentLifepoints(), HitType.REGULAR_HIT)
                }
            }
        }
        world.spawn(DynamicObject(4437, obj.type, obj.rot, obj.tile))
    }
}
