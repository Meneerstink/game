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
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.game.model.LockState
import gg.rsmod.plugins.content.skills.agility.ObstacleApproachFailed
import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.walkOverTile
import gg.rsmod.plugins.content.skills.agility.walkToTile

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
        player.openInterface(WAITING_OVERLAY, InterfaceDestination.PVP_OVERLAY)
        refreshLobby()
    }

    /**
     * K07 N442 (owner 2026-10-30: "castlewars volledig speelbaar net als pest control, de gui wanneer die start, mag starten als
     * 1 tegen 1 dan een countdown"). Novite started the countdown only when ONE team had 2 waiting, so a 1-against-1 never began.
     * Now the lobby counts down [CastleWarsRound.LOBBY_MINUTES] while each team has a player waiting, stops when a team empties,
     * and every waiting player sees the minutes left in the waiting-room overlay (Novite CastleWarsWaiting overlay 57, var 380).
     */
    fun refreshLobby() {
        if (!CastleWarsRound.matchRunning) {
            val ready = CastleWarsTeam.values().all { CastleWarsRound.waiting[it]!!.size >= CastleWarsRound.PLAYERS_NEEDED_PER_TEAM }
            if (ready && !CastleWarsRound.active) {
                CastleWarsRound.active = true
                CastleWarsRound.minutesLeft = CastleWarsRound.LOBBY_MINUTES
                forEachWaiting { it.message("Both teams have a player - the game starts in ${CastleWarsRound.LOBBY_MINUTES} minutes.") }
            } else if (!ready && CastleWarsRound.active) {
                CastleWarsRound.active = false
                CastleWarsRound.minutesLeft = CastleWarsRound.LOBBY_MINUTES
                forEachWaiting { it.message("The countdown stops: each team needs a player.") }
            }
        }
        forEachWaiting { sendTimeLeft(it) }
    }

    private fun forEachWaiting(action: (Player) -> Unit) = CastleWarsTeam.values().forEach { CastleWarsRound.waiting[it]!!.toList().forEach(action) }

    private fun forEachPlaying(action: (Player) -> Unit) = CastleWarsTeam.values().forEach { CastleWarsRound.playing[it]!!.toList().forEach(action) }

    /** Novite `refreshTimeLeft`: var 380 is the minutes left - of the lobby countdown in the waiting room, of the game in play; 0 = no countdown. */
    fun sendTimeLeft(player: Player) {
        val minutes =
            when {
                player.attr[CW_PLAYING] == true -> CastleWarsRound.minutesLeft
                CastleWarsRound.matchRunning -> 0
                CastleWarsRound.active -> CastleWarsRound.minutesLeft
                else -> 0
            }
        player.setVarp(VARP_TIME_LEFT, minutes.coerceAtLeast(0))
    }

    /** Novite `PlayingGame.refresh`: each team's flag state (0 safe, 1 taken, 2 dropped) and score in the game overlay, and the time. */
    fun refreshPlaying(player: Player) {
        // Each overlay shows its own castle door's health (clientscripts 1367 / 1368: 0 = a red "Health: 0%"); the doors here cannot
        // be broken, so they stay whole.
        player.setVarbit(VARBIT_SARA_DOOR_HEALTH, DOOR_FULL_HEALTH)
        player.setVarbit(VARBIT_ZAM_DOOR_HEALTH, DOOR_FULL_HEALTH)
        player.setVarbit(VARBIT_SARA_FLAG, CastleWarsRound.flagStatus[CastleWarsTeam.SARADOMIN]!!.ordinal)
        player.setVarbit(VARBIT_SARA_SCORE, CastleWarsRound.score[CastleWarsTeam.SARADOMIN]!!)
        player.setVarbit(VARBIT_ZAM_FLAG, CastleWarsRound.flagStatus[CastleWarsTeam.ZAMORAK]!!.ordinal)
        player.setVarbit(VARBIT_ZAM_SCORE, CastleWarsRound.score[CastleWarsTeam.ZAMORAK]!!)
        sendTimeLeft(player)
    }

    fun refreshAllPlaying() = forEachPlaying { refreshPlaying(it) }

    /**
     * Overlays: 57 in the waiting room; in the game 58 for a Saradomin player and 59 for a Zamorak player (cache clientscripts 1367
     * and 1368 fill them - each shows its own castle; Novite opened 58 for both teams). Var 380 and the varbits below drive the texts.
     */
    const val WAITING_OVERLAY = 57
    const val SARA_PLAYING_OVERLAY = 58
    const val ZAM_PLAYING_OVERLAY = 59

    fun playingOverlay(team: CastleWarsTeam) = if (team == CastleWarsTeam.SARADOMIN) SARA_PLAYING_OVERLAY else ZAM_PLAYING_OVERLAY

    const val VARBIT_SARA_DOOR_HEALTH = 136
    const val VARBIT_ZAM_DOOR_HEALTH = 146
    const val DOOR_FULL_HEALTH = 100
    const val VARP_TIME_LEFT = 380
    const val VARBIT_SARA_FLAG = 143
    const val VARBIT_SARA_SCORE = 145
    const val VARBIT_ZAM_FLAG = 153
    const val VARBIT_ZAM_SCORE = 155

    /** N441: "You can't leave just like that!" (Novite CastleWarsWaiting/Playing processMagic/Item/ObjectTeleport) for anyone in a team. */
    const val CANNOT_LEAVE = "You can't leave just like that!"

    /**
     * Leaves the waiting room: the team colours come off, the overlay closes, and the countdown re-checks. [move] is false when
     * the player is already elsewhere (N441 sweep: teleported or walked out by any route) - they are not pulled back to the lobby.
     */
    fun removeWaitingPlayer(
        player: Player,
        move: Boolean = true,
    ) {
        val team = player.attr[CW_TEAM] ?: return
        CastleWarsRound.waiting[team]!!.remove(player)
        player.equipment[EquipmentType.CAPE.id] = null
        player.equipment[EquipmentType.HEAD.id] = null
        player.attr.remove(CW_TEAM)
        player.attr.remove(CW_PLAYING)
        player.closeInterface(InterfaceDestination.PVP_OVERLAY)
        player.setVarp(VARP_TIME_LEFT, 0)
        if (move) player.moveTo(CastleWarsRound.LOBBY)
        refreshLobby()
    }

    /** Moves every waiting player of both teams into play. Called when the lobby countdown ends. */
    fun startMatch(world: World) {
        CastleWarsRound.active = true
        CastleWarsRound.matchRunning = true
        CastleWarsRound.minutesLeft = CastleWarsRound.MATCH_MINUTES
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
        chargeBrace(player)
        player.moveTo(team.baseTile)
        player.openInterface(playingOverlay(team), InterfaceDestination.PVP_OVERLAY)
        refreshPlaying(player)
    }

    /**
     * Leaves the game (exit portal, logout, the end of the game, or the N441 sweep with [move] false): a carried flag drops, the
     * colours, bandages and barricades go. A game whose team has nobody left ends at once, without tickets (a lone player would
     * otherwise farm tickets against an alt that walks out).
     */
    fun removePlayingPlayer(
        player: Player,
        move: Boolean = true,
    ) {
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
        player.attr.remove(CW_BRACE)
        player.write(gg.rsmod.game.message.impl.HintArrowMessage(slot = CARRIER_ARROW_SLOT, type = gg.rsmod.game.message.impl.HintArrowMessage.TYPE_CLEAR))
        player.inventory.remove(Items.BANDAGES, amount = Int.MAX_VALUE)
        player.inventory.remove(Items.BARRICADE, amount = Int.MAX_VALUE)
        player.closeInterface(InterfaceDestination.PVP_OVERLAY)
        player.setVarp(VARP_TIME_LEFT, 0)
        if (move) player.moveTo(CastleWarsRound.LOBBY)
        if (CastleWarsRound.matchRunning && !ending && CastleWarsRound.waiting[team]!!.isEmpty() && CastleWarsRound.playing[team]!!.isEmpty()) {
            abortMatch(team)
        }
    }

    /** True while [endMatch] / [abortMatch] empty the game, so the players they remove do not end it a second time. */
    private var ending = false

    /** The [emptied] team has nobody left: the game is over for the other team, without tickets. */
    private fun abortMatch(emptied: CastleWarsTeam) {
        ending = true
        try {
            CastleWarsRound.playing[emptied.other()]!!.toList().forEach {
                it.message("The ${emptied.displayName} team has left - the game is over. No tickets are given.")
                removePlayingPlayer(it)
            }
        } finally {
            ending = false
        }
        CastleWarsRound.reset()
        refreshLobby()
    }

    /** One tick of the round timer = one in-game minute (see [CastleWarsRound.TICKS_PER_MINUTE]). */
    fun tickMinute(world: World) {
        if (CastleWarsRound.matchRunning) {
            CastleWarsRound.minutesLeft--
            if (CastleWarsRound.minutesLeft <= 0) {
                endMatch()
                return
            }
            // Novite PlayingGame.run: whoever waits joins the running game while it has time left.
            if (CastleWarsRound.minutesLeft > CastleWarsRound.JOIN_RUNNING_MIN_MINUTES) {
                CastleWarsTeam.values().forEach { team -> CastleWarsRound.waiting[team]!!.toList().forEach { joinPlayingGame(it, team) } }
            }
            refreshAllPlaying()
            forEachWaiting { sendTimeLeft(it) }
            return
        }
        if (!CastleWarsRound.active) return
        CastleWarsRound.minutesLeft--
        if (CastleWarsRound.minutesLeft <= 0) {
            startMatch(world)
        } else {
            forEachWaiting { sendTimeLeft(it) }
        }
    }

    private fun endMatch() {
        val saraScore = CastleWarsRound.score[CastleWarsTeam.SARADOMIN]!!
        val zamoScore = CastleWarsRound.score[CastleWarsTeam.ZAMORAK]!!
        val winner = if (saraScore == zamoScore) null else if (saraScore > zamoScore) CastleWarsTeam.SARADOMIN else CastleWarsTeam.ZAMORAK
        if (winner != null) CastleWarsRound.seasonWins[winner] = CastleWarsRound.seasonWins[winner]!! + 1
        ending = true
        try {
            for (team in CastleWarsTeam.values()) {
                for (player in CastleWarsRound.playing[team]!!.toList()) {
                    player.message(
                        when (winner) {
                            null -> "You draw."
                            team -> "You won."
                            else -> "You lost."
                        },
                    )
                    val own = CastleWarsRound.score[team]!!
                    val other = CastleWarsRound.score[team.other()]!!
                    player.inventory.add(Items.CASTLE_WARS_TICKET, tickets(own, other))
                    removePlayingPlayer(player)
                }
            }
        } finally {
            ending = false
        }
        CastleWarsRound.reset()
        // Players who joined in the last minute were left waiting: the next countdown starts for them.
        refreshLobby()
    }

    /**
     * K07 N441 (owner 2026-10-30: "na portal in + teleport of uitloggen houdt de speler de teamkleur en de Castle Wars hood/cape"):
     * every game tick, whoever is in a team but no longer in Castle Wars (any teleport route, a logout the hook missed) leaves it
     * properly - colours off, overlay closed, the countdown or game re-checked. Pest Control does the same for its lander deck.
     */
    fun sweep() {
        forEachWaiting { p -> if (!p.isOnline || p.tile.regionId !in CastleWarsRound.REGIONS) removeWaitingPlayer(p, move = false) }
        forEachPlaying { p -> if (!p.isOnline || p.tile.regionId !in CastleWarsRound.REGIONS) removePlayingPlayer(p, move = false) }
    }

    /** K07 N441: team colours worn outside a game (a server restart in the middle of one, an old save) come off at login. */
    val TEAM_GEAR = CastleWarsTeam.values().flatMap { listOf(it.capeId, it.hoodId) }.toSet()

    fun stripTeamGear(player: Player): Boolean {
        if (player.attr[CW_TEAM] != null) return false
        var stripped = false
        for (slot in listOf(EquipmentType.CAPE, EquipmentType.HEAD)) {
            if (player.equipment[slot.id]?.id in TEAM_GEAR) {
                player.equipment[slot.id] = null
                stripped = true
            }
        }
        return stripped
    }

    /**
     * K07 N412: the Castle wars bracelet (OSRS Wiki "Castle wars bracelet"): worn as the game starts it spends one of its 3 charges
     * ((3) -> (2) -> (1) -> gone) and for that game gives 20% more damage against a player carrying a flag and 50% more healing from
     * bandages. CUSTOM message when the last charge is used (the wiki quotes none).
     */
    val BRACES = listOf(Items.CASTLE_WARS_BRACE_3, Items.CASTLE_WARS_BRACE_2, Items.CASTLE_WARS_BRACE_1)

    fun nextBrace(brace: Int): Int? = BRACES.indexOf(brace).takeIf { it >= 0 }?.let { BRACES.getOrNull(it + 1) }

    fun chargeBrace(player: Player) {
        val worn = player.equipment[EquipmentType.GLOVES.id]?.id ?: return
        if (worn !in BRACES) return
        player.attr[CW_BRACE] = true
        val next = nextBrace(worn)
        player.equipment[EquipmentType.GLOVES.id] = next?.let { Item(it) }
        if (next == null) player.message("Your Castle wars bracelet has used its last charge and crumbles to dust.")
    }

    /**
     * K12 N11: the worn "Check" was a dead option (census 2026-10-30). The charges are the bracelet's own (3) / (2) / (1); the OSRS
     * Wiki quotes no Check message, so the wording is CUSTOM.
     */
    fun braceCheckMessage(brace: Int): String {
        val charges = BRACES.size - BRACES.indexOf(brace)
        return "Your Castle wars bracelet has $charges ${if (charges == 1) "charge" else "charges"} left."
    }

    fun carriesFlag(pawn: gg.rsmod.game.model.entity.Pawn): Boolean =
        pawn is Player && pawn.equipment[EquipmentType.WEAPON.id]?.id.let { it == Items.SARADOMIN_FLAG || it == Items.ZAMORAK_FLAG }

    /** The max hit with the bracelet's +20% against a flag carrier. */
    fun braceMaxHit(
        attacker: gg.rsmod.game.model.entity.Pawn,
        target: gg.rsmod.game.model.entity.Pawn,
        maxHit: Double,
    ): Double = if (attacker is Player && attacker.attr[CW_BRACE] == true && carriesFlag(target)) kotlin.math.floor(maxHit * 1.2) else maxHit

    /**
     * K07 N412: the tickets for one game (OSRS Wiki "Castle wars ticket", non-Castle Wars worlds): a win 3 when the other team did
     * not score, else 2; a 0-0 draw 1, any other draw 2; a loss 1. The wiki's "at least 15 minutes in the game" always holds here:
     * players only join when a 20-minute game starts.
     */
    fun tickets(
        ownScore: Int,
        otherScore: Int,
    ): Int =
        when {
            ownScore > otherScore -> if (otherScore == 0) 3 else 2
            ownScore == otherScore -> if (ownScore == 0) 1 else 2
            else -> 1
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
        // A barricade does not come back once destroyed (respawnOverride is read by World.spawn's npc defaults).
        npc.respawnOverride = false
        world.spawn(npc)
        CastleWarsRound.barricades.add(npc)
        CastleWarsRound.barricadeTeam[npc] = team
        // OSRS Wiki "Barricade": "players cannot walk through them" - the tile is flagged like a solid 1x1 object while it stands.
        setBarricadeCollision(world, npc.tile, solid = true)
    }

    private fun setBarricadeCollision(
        world: World,
        tile: Tile,
        solid: Boolean,
    ) {
        val update = gg.rsmod.game.model.collision.CollisionUpdate.Builder()
        update.setType(if (solid) gg.rsmod.game.model.collision.CollisionUpdate.Type.ADD else gg.rsmod.game.model.collision.CollisionUpdate.Type.REMOVE)
        update.putTile(tile, false, *gg.rsmod.game.model.Direction.NESW)
        world.collision.applyUpdate(update.build())
    }

    /**
     * K07: where a barricade may not go (Novite `CastleWarsPlaying` - the tiles in front of the castle doors/ladders) or where one
     * already stands.
     */
    val NO_BARRICADE_TILES =
        setOf(
            gg.rsmod.game.model.Tile(2422, 3076), gg.rsmod.game.model.Tile(2426, 3080), gg.rsmod.game.model.Tile(2423, 3076),
            gg.rsmod.game.model.Tile(2426, 3081), gg.rsmod.game.model.Tile(2373, 3127), gg.rsmod.game.model.Tile(2373, 3126),
            gg.rsmod.game.model.Tile(2376, 3131), gg.rsmod.game.model.Tile(2377, 3131),
        )

    /** OSRS Wiki "Bandages": "Restore 10% of hitpoints, rounded down"; "Castle Wars bracelet: Increases the healing amount by 50%". */
    fun bandageHeal(
        maxHitpoints: Int,
        brace: Boolean,
    ): Int = kotlin.math.floor(maxHitpoints * 0.10 * (if (brace) 1.5 else 1.0)).toInt()

    const val BANDAGE_RUN_ENERGY = 30.0

    fun canPlaceBarricade(tile: gg.rsmod.game.model.Tile): Boolean =
        NO_BARRICADE_TILES.none { it.x == tile.x && it.z == tile.z } &&
            CastleWarsRound.barricades.none { it.tile.sameAs(tile) }

    fun destroyBarricade(
        world: World,
        npc: Npc,
        /** False when the barricade died in combat: the death handling removes the npc itself. */
        removeNpc: Boolean = true,
    ) {
        if (removeNpc) world.remove(npc)
        if (CastleWarsRound.barricades.remove(npc)) setBarricadeCollision(world, npc.tile, solid = false)
        // K07: the team that set it up gets its slot back (Novite `removeBarricade(team, npc)`).
        val team = CastleWarsRound.barricadeTeam.remove(npc) ?: return
        CastleWarsRound.barricadeCount[team] = (CastleWarsRound.barricadeCount[team]!! - 1).coerceAtLeast(0)
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
        markFlagCarrier(flagTeam, player)
        refreshAllPlaying()
    }

    /** K07 (Novite `addHintIcon`): the team whose flag was stolen sees a hint arrow over the player carrying it. */
    const val CARRIER_ARROW_SLOT = 1

    private fun markFlagCarrier(
        flagTeam: CastleWarsTeam,
        carrier: Player,
    ) {
        CastleWarsRound.playing[flagTeam]!!.forEach {
            it.write(gg.rsmod.game.message.impl.HintArrowMessage(slot = CARRIER_ARROW_SLOT, type = gg.rsmod.game.message.impl.HintArrowMessage.TYPE_PLAYER, target = carrier.index))
        }
    }

    /** Novite `removeHintIcon`: the flag is home or on the floor again. */
    private fun clearFlagCarrier(flagTeam: CastleWarsTeam) {
        CastleWarsRound.playing[flagTeam]!!.forEach {
            it.write(gg.rsmod.game.message.impl.HintArrowMessage(slot = CARRIER_ARROW_SLOT, type = gg.rsmod.game.message.impl.HintArrowMessage.TYPE_CLEAR))
        }
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
            refreshAllPlaying()
            return
        }
        CastleWarsRound.flagStatus[flagTeam] = FlagStatus.TAKEN
        player.equipment[EquipmentType.WEAPON.id] = Item(flagTeam.flagWeaponId)
        markFlagCarrier(flagTeam, player)
        refreshAllPlaying()
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
        clearFlagCarrier(flagTeam)
        refreshAllPlaying()
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
        clearFlagCarrier(flagTeam)
        refreshAllPlaying()
    }

    /**
     * Directional energy barrier (real option "Pass", verified). Novite's own `hasFlag()` guard
     * compares the carried weapon id against 4902/4903 - the flag-STAND object ids, not the flag
     * WEAPON ids (4037/4039) - which can never match, so that guard is dead code in the donor
     * itself despite the accompanying "can't cross barrier with flag" message. Implemented here
     * against the real flag weapon ids instead, matching the donor's stated intent rather than
     * its literal (bugged) comparison - disclosed, not a silent behavior change from a working
     * mechanic. Movement is Novite's one-step walk across the barrier (see below), not a teleport.
     */
    fun passBarrier(
        player: Player,
        obj: GameObject,
    ) {
        // Novite CastleWarsPlaying.processObjectClick1: 4469 only for Saradomin (its respawn room, base 2426,3076,1), 4470 only for
        // Zamorak (2373,3131,1); anyone else's click does nothing there - no message in Novite, Void or the OSRS Wiki, so none is
        // invented (traced instead).
        val owner = if (obj.id == Objs.ENERGY_BARRIER) CastleWarsTeam.SARADOMIN else CastleWarsTeam.ZAMORAK
        if (CastleWarsRound.playing[owner]?.contains(player) != true) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                "castle wars barrier ${obj.id}: ${player.username} refused - not playing for $owner"
            }
            return
        }
        val carryingFlag = CastleWarsTeam.forFlagWeapon(player.equipment[EquipmentType.WEAPON.id]?.id ?: -1) != null
        if (carryingFlag) {
            player.message("You can't cross barrier with flag.")
            return
        }
        // Novite CastleWarsPlaying.passBarrier: the barrier is a wall loc on one edge of its own tile (rotation 0 west, 1 north,
        // 2 east, 3 south); the crossing is the one step between the barrier's tile and the tile across that edge, walked with
        // addWalkSteps(.., checks = false) while locked. Novite only starts it when the player already stands on the barrier's
        // row/column and otherwise does nothing; the click's route usually ends beside the barrier (or diagonally), so the player
        // first walks - with collision - to the aligned tile on his own side, then takes the one bounded step across
        // (owner priority #1 2026-10-03, fail-batch-9: "walked to the loc, then no action and no message").
        val rotation = obj.rot
        val own = Tile(obj.tile.x, obj.tile.z, player.tile.height)
        val across =
            when (rotation) {
                0 -> own.transform(-1, 0)
                1 -> own.transform(0, 1)
                2 -> own.transform(1, 0)
                else -> own.transform(0, -1)
            }
        // The player's side: across the barrier's edge when he stands beyond it on the crossing axis.
        val onAcrossSide =
            when (rotation) {
                0 -> player.tile.x < obj.tile.x
                1 -> player.tile.z > obj.tile.z
                2 -> player.tile.x > obj.tile.x
                else -> player.tile.z < obj.tile.z
            }
        val near = if (onAcrossSide) across else own
        val far = if (onAcrossSide) own else across
        player.lockingQueue(lockState = LockState.FULL) {
            try {
                walkToTile(player, near)
                walkOverTile(player, far, Render.NONE)
            } catch (e: ObstacleApproachFailed) {
                player.stopMovement()
                player.message("I can't reach that!")
            }
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
