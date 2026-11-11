package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.cmd.conformance.Bots
import gg.rsmod.plugins.content.mechanics.moderation.GearPresets
import gg.rsmod.plugins.content.quests.foundation.BeneathCursedSands

/**
 * N462 test tool (owner 11-11 17:0x, max 1 hour): party members for ToA group tests with ONE test client. Test server only.
 * The bots are the conformance bots (Bots.create: a real Client without a socket, never saved); every action goes through the same
 * code a player reaches (ToaLobby.join/start, ToaRaid.enter*, Attack = the client's OpNpc2 packet, logout hooks, login hooks).
 *   ::toabot add [n]        n bots (default 1) next to you in the lobby, BCS done, gear_max, in YOUR party (you lead)
 *   ::toabot follow         every bot enters the room you are in now (same entry call as a player) and stands on your tile
 *   ::toabot attack         every living bot attacks the nearest living npc of the current room (Attack packet)
 *   ::toabot kill <i|all>   lethal hit on bot i (1-based) or all bots - a real death in the room
 *   ::toabot logout <i>     bot i logs out (logout hooks: ToaLobby.logout -> raid.disconnect); nothing is saved
 *   ::toabot login <i>      bot i logs in again on your tile (login hooks: ToaLobby.login -> raid.reconnect)
 *   ::toabot list           name, tile, hp, raid membership of every bot
 *   ::toabot remove         every bot leaves the raid/party and the world
 */
val toaBots = linkedMapOf<String, Client>()

fun botName(i: Int) = "toabot$i"

fun spawnBot(world: gg.rsmod.game.model.World, name: String, tile: Tile): Client =
    Bots.create(world, tile, name) { BeneathCursedSands.setStage(it, BeneathCursedSands.completedValue) }.also { bot ->
        BeneathCursedSands.setStage(bot, BeneathCursedSands.completedValue)
        GearPresets.forCommand("gear_max")?.let { GearPresets.apply(bot, it) }
        toaBots[name] = bot
    }

fun pick(arg: String?): List<Client> =
    if (arg == null || arg == "all") toaBots.values.toList()
    else listOfNotNull(arg.toIntOrNull()?.let { toaBots[botName(it)] })

fun nearestNpc(raid: ToaRaid, from: Player): Npc? {
    val run = raid.progress.current ?: return null
    return raid.scope(run)?.spawned?.filter { it.isSpawned() && !it.isDead() && it.tile.height == from.tile.height }
        ?.minByOrNull { it.tile.getDistance(from.tile) }
}

fun logoutBot(bot: Client) {
    // Player.handleLogout without Client's save (a bot is never written to disk): the same hooks a real logout runs.
    runCatching { bot.interruptQueues() }
    runCatching { bot.unlock() }
    // (InstancedMapAllocator.logout is internal to the engine: skipped; the ToA hook below is what the raid reacts to.)
    runCatching { bot.world.plugins.executeLogout(bot) }
    runCatching { bot.world.unregister(bot) }
}

/** A boss arena / Wardens room has really started (crystal used): its npcs exist only from then on (11-11: 'no npc' before). */
fun roomStarted(raid: ToaRaid): Boolean {
    val run = raid.progress.current ?: return false
    return raid.scope(run)?.spawned?.any { it.isSpawned() && !it.isDead() } == true
}

/** Every bot enters the leader's current room through the player's own entry call and stands on the leader's tile. */
fun followAll(raid: ToaRaid, leader: Player): String = toaBots.values.joinToString(prefix = "toabot follow: ") { bot ->
    val key = raid.progress.current?.key
    val entered = when {
        key == null -> true
        key.path == null -> raid.enterWardens(bot)
        key.boss -> raid.enterBoss(bot, key.path!!)
        else -> raid.enterChallenge(bot, key.path!!)
    }
    // A bot that may not enter (dead in this room = spectator, OSRS) stays where the raid put it.
    if (entered || raid.canFight(bot)) bot.teleportTo(leader.tile)
    "${bot.username}=$entered"
}

on_command("toabot", Privilege.ADMIN_POWER) {
    if (System.getProperty("rsps.testServer") != "true") return@on_command
    val args = player.getCommandArgs()
    val lobby = ToaLobby.of(world)
    val raid = lobby.raid(player.uid)
    val reply = when (args.firstOrNull()?.lowercase()) {
        "add" -> {
            if (!ToaMap.inLobby(player.tile)) "toabot add: stand in the ToA lobby first"
            else {
                if (lobby.party(player.uid) == null) lobby.create(player.uid)
                val n = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 7) ?: 1
                val added = (1..7).map { botName(it) }.filter { it !in toaBots }.take(n).map { name ->
                    val bot = spawnBot(world, name, player.tile)
                    "$name joined=${lobby.join(bot.uid, player.uid)}"
                }
                "toabot add: ${added.joinToString()} (party ${lobby.party(player.uid)?.members?.size})"
            }
        }
        "follow" -> {
            if (raid == null) "toabot follow: you are not in a raid"
            else if (raid.progress.current?.key?.let { it.boss || it.wardens } == true && !roomStarted(raid)) {
                // Arena rooms start at the crystal: wait (max 30 ticks) until the leader is really inside, then bring the bots
                // (11-11 test: a follow right after the crystal click left the bot outside the arena wall).
                // world.queue: a player's own queue is cancelled by his next click (the crystal) - 11-11 test.
                // 'player' inside world.queue would read the queue's context (the World): capture the leader first.
                val leader = player
                world.queue {
                    var waited = 0
                    while (!roomStarted(raid) && waited++ < 30) wait(1)
                    leader.message(followAll(raid, leader))
                }
                "toabot follow: waiting until you are inside the arena"
            } else followAll(raid, player)
        }
        "attack" -> {
            if (raid == null) "toabot attack: you are not in a raid"
            else toaBots.values.filter { !it.isDead() }.joinToString(prefix = "toabot attack: ") { bot ->
                val npc = nearestNpc(raid, bot)
                if (npc == null) "${bot.username}=no npc" else "${bot.username}->${npc.id}=${Bots.npcOption(bot, npc, 2)}"
            }
        }
        "kill" -> pick(args.getOrNull(1)).joinToString(prefix = "toabot kill: ") { bot ->
            bot.hit(bot.getCurrentLifepoints() + 10)
            bot.username
        }
        "logout" -> pick(args.getOrNull(1) ?: "none").joinToString(prefix = "toabot logout: ") { bot ->
            logoutBot(bot); toaBots.remove(bot.username); bot.username
        }
        "login" -> {
            val i = args.getOrNull(1)?.toIntOrNull()
            val name = i?.let { botName(it) }
            if (name == null || name in toaBots) "toabot login: give the number of a logged-out bot"
            else { spawnBot(world, name, player.tile); "toabot login: $name raid=${lobby.raid(gg.rsmod.game.model.PlayerUID(name)) != null}" }
        }
        "list" -> if (toaBots.isEmpty()) "toabot: no bots" else toaBots.values.joinToString(prefix = "toabot: ") { bot ->
            "${bot.username} ${bot.tile.x},${bot.tile.z},${bot.tile.height} hp=${bot.getCurrentLifepoints()} dead=${bot.isDead()} " +
                "party=${lobby.party(bot.uid) != null} raid=${lobby.raid(bot.uid) != null}"
        }
        "remove" -> {
            val names = toaBots.values.toList().map { bot ->
                lobby.leaveRaid(bot, returnToLobby = false); lobby.leave(bot.uid); logoutBot(bot); bot.username
            }
            toaBots.clear()
            "toabot remove: ${names.joinToString()}"
        }
        else -> "toabot add [n] | follow | attack | kill <i|all> | logout <i> | login <i> | list | remove"
    }
    player.message(reply)
}
