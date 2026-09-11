package gg.rsmod.plugins.content.mechanics.clan

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.death.SafeDeath

/**
 * Full (customizable-rules) Clan Wars: clan-vs-clan challenge, rule/arena/victory-condition
 * selection, and a real match. Q-052. See [ClanWarsMatch]'s doc for the two disclosed,
 * deliberate simplifications versus Novite's donor source: one shared war at a time at a real
 * static arena location (no instance/zone manager exists in this engine - same class of
 * limitation already recorded for Dungeoneering/Construction), and chat-menu configuration
 * instead of Novite's own (unverified-against-this-cache) interfaces 265/790/791.
 *
 * Entry is via `::clanwarenter` rather than a physical "Challenge Hall" portal object, because no
 * real obj id/placement for that specific room has been verified in this project's cache yet -
 * a disclosed simplification, not a guess at geometry.
 *
 * Rule enforcement implemented for real: ITEMS_LOST (wired into [SafeDeath] - the war is only
 * registered as a safe death zone when this rule is NOT active, exactly inverting Novite's own
 * semantics for the flag). NO_FOOD/NO_POTIONS/NO_PRAYER/NO_MELEE/NO_RANGE/NO_MAGIC/NO_FAMILIARS
 * are configured and tracked in [ClanWarsMatch.rules] (queryable by any future code) but NOT yet
 * enforced - each would require touching the combat-style, prayer-activation, and item-consume
 * subsystems broadly; left as a named remaining gap rather than a partial/fake enforcement, to
 * keep this batch's blast radius to new files (+2 small additive `Clans.kt` helpers).
 */
val CLAN_WAR_TIMER = TimerKey()
val PENDING_RULES = gg.rsmod.game.model.attr.AttributeKey<MutableSet<ClanWarRule>>()
val PENDING_VICTORY = gg.rsmod.game.model.attr.AttributeKey<Int>()
val PENDING_TIME = gg.rsmod.game.model.attr.AttributeKey<Int>()

fun world_random(
    min: Int,
    max: Int,
): Int = min + java.util.concurrent.ThreadLocalRandom.current().nextInt(max - min + 1)

fun clanWarArenaTiles(arena: ClanWarArena): Pair<Tile, Tile> {
    // Real donor bounding boxes are ~60-100 tiles per side; treat entry as "at/near the spawn tile"
    // rather than re-deriving the exact NE corner (not sourced independently of the SW+size in
    // AreaType, and not needed for a single shared, non-instanced arena).
    return arena.southWest to arena.southWest.transform(80, 80, 0)
}

fun inClanWarArena(tile: Tile): Boolean {
    val match = ClanWarsMatch.active ?: return false
    if (!match.started) return false
    val (sw, ne) = clanWarArenaTiles(match.arena)
    return tile.height == 0 && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
}

on_world_init {
    // Only safe when the active war's ITEMS_LOST rule is NOT set - Novite's flag means the
    // opposite of safe (items ARE lost) when active, replicated faithfully here.
    SafeDeath.register { player ->
        val match = ClanWarsMatch.active
        match != null && match.teamOf(player) != 0 && inClanWarArena(player.tile) && ClanWarRule.ITEMS_LOST !in match.rules
    }
}

fun endWar(match: ClanWarsMatch) {
    ClanWarsMatch.active = null
    val firstMsg =
        when {
            match.firstKills == match.secondKills -> "Your clan war ended in a draw."
            match.firstKills > match.secondKills -> "Your clan is victorious!"
            else -> "Your clan has been defeated."
        }
    val secondMsg =
        when {
            match.firstKills == match.secondKills -> "Your clan war ended in a draw."
            match.secondKills > match.firstKills -> "Your clan is victorious!"
            else -> "Your clan has been defeated."
        }
    (match.firstPlayers + match.secondPlayers).forEach { it.attr.remove(CLAN_WAR_ACCEPTED_TERMS) }
    match.firstPlayers.forEach {
        it.filterableMessage(firstMsg)
        it.moveTo(Tile(world_random(3266, 3270), world_random(3679, 3682), 0))
    }
    match.secondPlayers.forEach {
        it.filterableMessage(secondMsg)
        it.moveTo(Tile(world_random(3266, 3270), world_random(3679, 3682), 0))
    }
}

fun checkVictory(match: ClanWarsMatch) {
    if (match.isKnockout()) {
        if (match.firstPlayers.isEmpty() || match.secondPlayers.isEmpty()) endWar(match)
        return
    }
    if (match.isMostKills()) return // resolved only by the timer running out
    if (match.firstKills >= match.victoryType || match.secondKills >= match.victoryType) endWar(match)
}

on_player_death {
    val match = ClanWarsMatch.active ?: return@on_player_death
    val team = match.teamOf(player)
    if (team == 0 || !inClanWarArena(player.tile)) return@on_player_death
    val killer = player.attr[KILLER_ATTR]?.get() as? Player
    if (killer != null) {
        val killerTeam = match.teamOf(killer)
        if (killerTeam == 1) match.firstKills++ else if (killerTeam == 2) match.secondKills++
    }
    if (team == 1) match.firstPlayers.remove(player) else match.secondPlayers.remove(player)
    checkVictory(match)
}

on_login {
    player.timers[CLAN_WAR_TIMER] = 1
}

on_timer(CLAN_WAR_TIMER) {
    val match = ClanWarsMatch.active
    if (match != null && !match.started) {
        if (--match.preWarTicks <= 0) {
            match.started = true
            match.ticksLeft = match.timeLimit
            (match.firstPlayers + match.secondPlayers).forEach { it.filterableMessage("The clan war has begun!") }
        }
    } else if (match != null && match.started && match.timeLimit != -1) {
        if (--match.ticksLeft <= 0) endWar(match)
    }
    player.timers[CLAN_WAR_TIMER] = 1
}

fun startNegotiation(
    leader: Player,
    opponentName: String,
) {
    val leaderClan = Clans.clanOf(leader)
    if (leaderClan == null) {
        leader.filterableMessage("You're not in a clan.")
        return
    }
    val opponent = leader.findOnlinePlayer(opponentName)
    val opponentClan = opponent?.let { Clans.clanOf(it) }
    if (opponent == null || opponentClan == null) {
        leader.filterableMessage("That player isn't online or isn't in a clan.")
        return
    }
    if (opponentClan == leaderClan) {
        leader.filterableMessage("You can't challenge your own clan.")
        return
    }
    if (ClanWarsMatch.active != null) {
        leader.filterableMessage("A clan war is already in progress.")
        return
    }
    leader.attr[CLAN_WAR_OPPONENT] = opponent.username
    opponent.attr[CLAN_WAR_OPPONENT] = leader.username
    leader.filterableMessage("You challenge ${opponent.username}'s clan to a Clan War. Use ::clanwarconfigure to set the rules.")
    opponent.filterableMessage("${leader.username}'s clan has challenged your clan to a Clan War. Use ::clanwarconfigure to set the rules.")
}

on_command("clanwarchallenge") {
    val args = player.getCommandArgs()
    val opponentName = args.joinToString(" ")
    if (opponentName.isBlank()) {
        player.message("Usage: ::clanwarchallenge <opponent player name>")
        return@on_command
    }
    startNegotiation(player, opponentName)
}

fun opponentLink(player: Player): Player? {
    val name = player.attr[CLAN_WAR_OPPONENT] ?: return null
    val other = player.findOnlinePlayer(name) ?: return null
    return if (other.attr[CLAN_WAR_OPPONENT] == player.username) other else null
}

on_command("clanwarconfigure") {
    val other = opponentLink(player)
    if (other == null) {
        player.message("You have no pending Clan War negotiation. Use ::clanwarchallenge first.")
        return@on_command
    }
    player.queue {
        val ruleNames = ClanWarRule.values().map { it.displayName }.toTypedArray()
        val choice = options(*ruleNames, title = "Toggle a rule (current war rules are shown separately):")
        if (choice < 1 || choice > ruleNames.size) return@queue
        val rule = ClanWarRule.values()[choice - 1]
        val pending = player.attr[PENDING_RULES] ?: mutableSetOf<ClanWarRule>().also { player.attr[PENDING_RULES] = it }
        if (rule in pending) pending.remove(rule) else pending.add(rule)
        player.attr.remove(CLAN_WAR_ACCEPTED_TERMS)
        other.attr.remove(CLAN_WAR_ACCEPTED_TERMS)
        player.filterableMessage("${rule.displayName}: ${if (rule in pending) "ON" else "OFF"}")
    }
}

on_command("clanwaraccept") {
    val other = opponentLink(player)
    if (other == null) {
        player.message("You have no pending Clan War negotiation. Use ::clanwarchallenge first.")
        return@on_command
    }
    if (other.attr[CLAN_WAR_ACCEPTED_TERMS] == true) {
        val leaderClan = Clans.clanOf(player)!!
        val opponentClan = Clans.clanOf(other)!!
        val arena = ClanWarArena.values()[world_random(0, ClanWarArena.values().size - 1)]
        val match = ClanWarsMatch(leaderClan, opponentClan, arena)
        match.rules.addAll(player.attr[PENDING_RULES].orEmpty())
        match.rules.addAll(other.attr[PENDING_RULES].orEmpty())
        match.victoryType = player.attr[PENDING_VICTORY] ?: -1
        match.timeLimit = player.attr[PENDING_TIME] ?: -1
        ClanWarsMatch.active = match
        player.attr.remove(CLAN_WAR_OPPONENT)
        other.attr.remove(CLAN_WAR_OPPONENT)
        val firstRoster = Clans.onlineMembers(player, leaderClan)
        val secondRoster = Clans.onlineMembers(player, opponentClan)
        firstRoster.forEach { it.filterableMessage("Your clan war has been arranged. Use ::clanwarenter to join.") }
        secondRoster.forEach { it.filterableMessage("Your clan war has been arranged. Use ::clanwarenter to join.") }
    } else {
        player.attr[CLAN_WAR_ACCEPTED_TERMS] = true
        player.filterableMessage("You accept the current terms. Waiting for the other clan to accept.")
    }
}

on_command("clanwarenter") {
    val match = ClanWarsMatch.active
    if (match == null) {
        player.message("There is no arranged Clan War to join.")
        return@on_command
    }
    val clan = Clans.clanOf(player)
    val team =
        when (clan) {
            match.firstClan -> 1
            match.secondClan -> 2
            else -> 0
        }
    if (team == 0) {
        player.message("Your clan isn't part of the arranged Clan War.")
        return@on_command
    }
    val spawn = if (team == 1) match.arena.firstSpawn() else match.arena.secondSpawn()
    if (team == 1) match.firstPlayers.add(player) else match.secondPlayers.add(player)
    player.moveTo(spawn)
    player.filterableMessage("You enter the Clan War arena.")
}

on_command("clanwarleave") {
    val match = ClanWarsMatch.active ?: return@on_command
    val team = match.teamOf(player)
    if (team == 0) return@on_command
    if (team == 1) match.firstPlayers.remove(player) else match.secondPlayers.remove(player)
    player.moveTo(Tile(world_random(3266, 3270), world_random(3679, 3682), 0))
    player.filterableMessage("You leave the Clan War arena.")
    checkVictory(match)
}

on_command("clanwarvictory") {
    player.queue {
        val labels = CLAN_WAR_VICTORY_TYPES.map { if (it == -1) "Knockout" else if (it == -2) "Most kills" else "$it kills" }.toTypedArray()
        val choice = options(*labels, title = "Select the victory condition:")
        if (choice < 1 || choice > labels.size) return@queue
        player.attr[PENDING_VICTORY] = CLAN_WAR_VICTORY_TYPES[choice - 1]
        player.attr.remove(CLAN_WAR_ACCEPTED_TERMS)
        opponentLink(player)?.attr?.remove(CLAN_WAR_ACCEPTED_TERMS)
        player.filterableMessage("Victory condition set to: ${labels[choice - 1]}")
    }
}

on_command("clanwartime") {
    player.queue {
        val labels = CLAN_WAR_TIME_LIMITS.map { if (it == -1) "Unlimited" else "${it / 100} minutes" }.toTypedArray()
        val choice = options(*labels, title = "Select the time limit:")
        if (choice < 1 || choice > labels.size) return@queue
        player.attr[PENDING_TIME] = CLAN_WAR_TIME_LIMITS[choice - 1]
        player.attr.remove(CLAN_WAR_ACCEPTED_TERMS)
        opponentLink(player)?.attr?.remove(CLAN_WAR_ACCEPTED_TERMS)
        player.filterableMessage("Time limit set to: ${labels[choice - 1]}")
    }
}
