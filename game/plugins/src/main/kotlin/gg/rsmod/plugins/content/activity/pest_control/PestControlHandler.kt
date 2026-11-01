package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapConfiguration
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.playJingle
import gg.rsmod.plugins.api.ext.playSong
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import java.util.PriorityQueue
import kotlin.random.Random

/**
 * Pest Control (owner 2026-10-02: "finish pest control fully"). A port of the 2009scape activity (see PestControlData.kt
 * for every source and id check):
 *
 * - three landers at the Void Knights' Outpost, boarded by the gangplank and left by the ladder, each with its own
 *   500-tick departure timer, the 5..25 team size, the priority queue for players left behind and the lander overlay (407);
 * - every game runs in its own copy of the island (an [InstancedMap] of region 10536), so all three landers - and several
 *   games of one lander - run at the same time;
 * - the game overlay (408): time left, the knight's and the four portals' health, the shields and the player's zeal;
 * - shielded portals that drop one by one (ticks 25/75/125/175 = OSRS 15 s then every 30 s, random order), monster waves from every portal, the
 *   monsters' own behaviour (PestControlMonsters.kt), barricades and gates (PestControlBarricades.kt);
 * - zeal from every hit on a portal or monster (PawnExt.dealHit -> [onDamage]) and from repairs; at the end a won game pays
 *   the lander's commendation points plus combat level x 10 coins to every player with 50 zeal or more;
 * - dying in the game is safe and puts the player back at the landing; the rewards are spent at the Void Knights (267).
 *
 * PROBLEM lines are logged through [PestControlTrace] for every refused or failed route.
 */
object PestControlHandler {
    private val PC_PRIORITY = AttributeKey<Int>()

    /** Set on a player while they are in a game: the game they belong to. */
    val GAME_ATTR = AttributeKey<PestControlGame>()

    /**
     * Zeal in the current game (2009scape attribute "pc_zeal"): 2009scape counts hitpoints of damage. Hits are 1:1 hitpoints since the
     * HP unit migration, so one point of damage is one zeal (the old x10 lifepoint bookkeeping needed 500 damage for 50 zeal).
     */
    val ZEAL_ATTR = AttributeKey<Int>()

    fun zealOf(player: Player): Int = player.attr[ZEAL_ATTR] ?: 0

    private class Lobby(
        val tier: PestControlTier,
    ) {
        var ticks = 0
        val waiting = PriorityQueue<Player>(20, compareByDescending { it.attr[PC_PRIORITY] ?: 0 })
    }

    private val lobbies = PestControlTier.values().associateWith { Lobby(it) }

    val games = mutableListOf<PestControlGame>()

    fun lobbyOf(player: Player): PestControlTier? = lobbies.values.firstOrNull { player in it.waiting }?.tier

    fun gameOf(player: Player): PestControlGame? = player.attr[GAME_ATTR]?.takeIf { it.active && it.map.area.contains(player.tile) }

    fun gameAt(tile: Tile): PestControlGame? = games.firstOrNull { it.active && it.map.area.contains(tile) }

    fun gameOf(npc: Npc): PestControlGame? = gameAt(npc.tile)

    /** True on a lander deck or anywhere inside a running game: nobody can attack another player there. */
    fun isSafeTile(tile: Tile): Boolean =
        (tile.height == 0 && PestControlTier.values().any { it.lander.contains(tile) }) || gameAt(tile) != null

    // ---- landers ----------------------------------------------------------------------------------------------------

    /** Gangplank: boards the lander (2009scape PCObjectHandler "cross" + PestControlActivityPlugin.start). */
    fun board(
        player: Player,
        tier: PestControlTier,
    ) {
        if (player.combatLevel < tier.combatReq) {
            player.message("You need a combat level of ${tier.combatReq} or higher to board this lander.")
            return
        }
        // N205: a familiar boards with its owner; its follow recovery puts it beside him on the island.
        if (lobbyOf(player) != null || gameOf(player) != null) {
            player.message("You are already waiting for a Pest Control game.")
            return
        }
        player.moveTo(tier.boardTile)
        val lobby = lobbies.getValue(tier)
        lobby.waiting.add(player)
        player.message("You board the lander.")
        player.openInterface(PC_LANDER_INTERFACE, InterfaceDestination.PVP_OVERLAY)
        player.setComponentText(PC_LANDER_INTERFACE, 3, tier.displayName)
        player.setComponentText(PC_LANDER_INTERFACE, 16, "Pest Points: ${player.attr[PEST_POINTS] ?: 0}")
        // N204: the cache line reads "(Need 5 to 25 players)"; a lander departs with PC_MIN_TEAM_SIZE (1) now.
        player.setComponentText(PC_LANDER_INTERFACE, 15, "(Need $PC_MIN_TEAM_SIZE to $PC_MAX_TEAM_SIZE players)")
        sendDeparture(lobby, player)
        sendPlayerCount(lobby)
    }

    /**
     * The `pestcontrol*` commands (bug search 2026-10-02, BS3): a remote route, so only from a safe zone and out of combat;
     * they put the player on the lander like the gangplank does.
     */
    fun boardRemote(
        player: Player,
        tier: PestControlTier,
    ) {
        if (gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.refuse(player, "board a Pest Control lander")) return
        board(player, tier)
    }

    /** Ladder on the lander, logout, or walking/teleporting off the deck. */
    fun leaveLander(
        player: Player,
        move: Boolean,
    ) {
        val lobby = lobbies.values.firstOrNull { player in it.waiting } ?: return
        lobby.waiting.remove(player)
        player.closeInterface(InterfaceDestination.PVP_OVERLAY)
        if (move) player.moveTo(lobby.tier.leaveTile)
        sendPlayerCount(lobby)
    }

    private fun sendDeparture(
        lobby: Lobby,
        player: Player,
    ) {
        val left = PC_LOBBY_DEPARTURE_TICKS - lobby.ticks
        val text =
            when {
                left > 99 -> "Next Departure: ${left / 100} min"
                left > 50 -> "Next Departure: 1 min"
                else -> "Next Departure: 30 seconds"
            }
        player.setComponentText(PC_LANDER_INTERFACE, 13, text)
    }

    private fun sendPlayerCount(lobby: Lobby) {
        for (p in lobby.waiting) p.setComponentText(PC_LANDER_INTERFACE, 14, "Players Ready: ${lobby.waiting.size}")
    }

    /** Every game tick (pest_control.plugin.kts world timer). */
    fun tick(world: World) {
        for (lobby in lobbies.values) tickLobby(world, lobby)
        for (game in games.toList()) {
            if (game.active) game.tick()
        }
        games.removeIf { !it.active }
    }

    private fun tickLobby(
        world: World,
        lobby: Lobby,
    ) {
        // Whoever walked or teleported off the deck, or logged out, is no longer waiting.
        lobby.waiting.removeIf { p ->
            val gone = !p.isOnline || !lobby.tier.lander.contains(p.tile) || p.tile.height != 0
            if (gone && p.isOnline) p.closeInterface(InterfaceDestination.PVP_OVERLAY)
            gone
        }
        lobby.ticks++
        if (lobby.waiting.size >= PC_MAX_TEAM_SIZE && lobby.ticks < 475) lobby.ticks = 495
        if ((lobby.ticks < 450 && lobby.ticks % 100 == 0) || lobby.ticks % 50 == 0 || lobby.ticks == 495) {
            lobby.waiting.forEach { sendDeparture(lobby, it) }
        }
        // N204: 50 ticks (30 seconds) before departure, a red line in the chat of everyone on the lander.
        if (lobby.ticks == PC_LOBBY_DEPARTURE_TICKS - 50) lobby.waiting.forEach { it.message(PC_START_SOON_MESSAGE) }
        if (lobby.ticks >= PC_LOBBY_DEPARTURE_TICKS) {
            if (lobby.waiting.size >= PC_MIN_TEAM_SIZE) {
                start(world, lobby)
            } else {
                lobby.ticks = 400
            }
        }
    }

    /** Admin `pcstart`: departs now, whatever the team size (testing on a quiet server). */
    fun forceStart(
        world: World,
        tier: PestControlTier,
    ): Boolean {
        val lobby = lobbies.getValue(tier)
        if (lobby.waiting.isEmpty()) return false
        start(world, lobby)
        return true
    }

    private fun start(
        world: World,
        lobby: Lobby,
    ) {
        lobby.ticks = 0
        val map = allocate(world, lobby.tier)
        if (map == null) {
            PestControlTrace.problem("no free instance space for a ${lobby.tier.displayName} game; ${lobby.waiting.size} players keep waiting")
            lobby.waiting.forEach { it.message("The lander cannot depart right now; please wait for the next departure.") }
            return
        }
        val game = PestControlGame(world, lobby.tier, map)
        games.add(game)
        var count = 0
        val left = mutableListOf<Player>()
        while (lobby.waiting.isNotEmpty()) {
            val p = lobby.waiting.poll()
            if (!p.isOnline) continue
            if (++count > PC_MAX_TEAM_SIZE) {
                left.add(p)
                continue
            }
            // Whoever got skulled, Tele Blocked or into a fight since boarding stays behind (bug search 2026-10-02).
            val refusal = gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.moveRefusal(p, "be taken to Pest Control")
            if (refusal != null) {
                count--
                p.message(refusal)
                p.message("You have lost your place on the Pest Control lander.")
                p.closeInterface(InterfaceDestination.PVP_OVERLAY)
                PestControlTrace.problem("${p.username} lost the lander place at departure: $refusal")
                continue
            }
            p.attr.remove(PC_PRIORITY)
            game.addPlayer(p)
        }
        for (p in left) {
            val priority = (p.attr[PC_PRIORITY] ?: 0) + 1
            p.message("You have been given priority level $priority over other players in joining the next")
            p.message("game.")
            p.attr[PC_PRIORITY] = priority
            lobby.waiting.add(p)
        }
        sendPlayerCount(lobby)
        game.begin()
    }

    private fun allocate(
        world: World,
        tier: PestControlTier,
    ): InstancedMap? {
        val chunks = InstancedChunkSet.Builder()
        for (x in 0 until 8) {
            for (z in 0 until 8) {
                chunks.set(x, z, 0, 0, PC_REGION_BASE.transform(x * 8, z * 8, 0))
            }
        }
        val config =
            InstancedMapConfiguration.Builder()
                .setExitTile(tier.leaveTile)
                .setBypassObjectChunkBounds(true)
                .build()
        return world.instanceAllocator.allocate(world, chunks.build(), config)
    }

    // ---- in the game ------------------------------------------------------------------------------------------------

    /** PawnExt.dealHit, for every landed hit: zeal for a player's damage on a portal or a monster of their own game. */
    fun onDamage(
        target: Pawn,
        source: Pawn,
        dealt: Int,
    ) {
        if (target !is Npc || dealt <= 0) return
        if (source !is Player) {
            gameOf(target)?.let { if (it.knight === target) it.knightHit() }
            return
        }
        val game = source.attr[GAME_ATTR] ?: return
        if (!game.active || !game.owns(target)) return
        if (game.isPortal(target)) game.portalHit(target)
        addZeal(source, dealt)
    }

    /** N232: rounded at random so even one-point hits receive the small team's exact average share. */
    fun knightDamage(
        players: Int,
        damage: Int,
        roll: Double = Random.nextDouble(),
    ): Int {
        if (damage <= 0) return damage
        val share = PestControlBalance.profile(players, PestControlTier.NOVICE).damageShare * damage
        val whole = share.toInt()
        return whole + if (roll < share - whole) 1 else 0
    }

    /** N232: fewer direct knight attackers for small teams; identical on every boat. */
    fun knightShifterCap(players: Int): Int = PestControlBalance.profile(players, PestControlTier.NOVICE).knightShifters

    /** [zeal] whole zeal: damage dealt, or 5 for a repair. */
    fun addZeal(
        player: Player,
        zeal: Int,
    ) {
        if (zeal <= 0) return
        player.attr[ZEAL_ATTR] = zealOf(player) + zeal
        sendZeal(player, zealOf(player))
    }

    fun sendZeal(
        player: Player,
        total: Int,
    ) {
        val col = if (total > PC_MIN_ZEAL) "<col=00FF00>" else ""
        player.setComponentText(PC_GAME_INTERFACE, 11, if (total > 350) "${col}LOTS!" else "$col$total")
    }

    /** Squire 3781 "Leave", or anything else that takes a player out of their game. */
    fun leaveGame(
        player: Player,
        move: Boolean,
    ) {
        val game = player.attr[GAME_ATTR] ?: return
        game.removePlayer(player)
        cleanUp(player)
        if (move) player.moveTo(game.tier.leaveTile)
    }

    /** Player death inside a game: items are kept (SafeDeath) and the player is put back at the landing. */
    fun respawnTile(player: Player): Tile? {
        val game = player.attr[GAME_ATTR] ?: return null
        if (!game.active) return null
        return game.landingTile()
    }

    fun cleanUp(player: Player) {
        player.attr.remove(GAME_ATTR)
        player.attr.remove(ZEAL_ATTR)
        player.closeInterface(InterfaceDestination.PVP_OVERLAY)
    }

    /** End of a game for one player (2009scape PestControlActivityPlugin.end). */
    fun pay(
        player: Player,
        game: PestControlGame,
        success: Boolean,
    ) {
        val zeal = zealOf(player)
        cleanUp(player)
        player.moveTo(game.tier.leaveTile)
        restore(player)
        when {
            !success -> {
                player.playJingle(PC_JINGLE_DEFEAT)
                player.message("The Void Knight was killed, another of our Order has fallen and that Island is lost.")
            }
            zeal >= PC_MIN_ZEAL -> {
                player.attr[PEST_WINS] = (player.attr[PEST_WINS] ?: 0) + 1
                player.playJingle(PC_JINGLE_WIN)
                val points = game.tier.pointsPerWin
                val total = gg.rsmod.game.model.attr.PointBalance.add(player.attr, PEST_POINTS, points) // N106: one balance helper
                val coins = player.combatLevel * 10
                val added = player.inventory.add(995, coins)
                if (!added.hasSucceeded()) {
                    player.world.spawn(gg.rsmod.game.model.entity.GroundItem(995, coins, player.tile, player))
                }
                player.message("Congratulations! You managed to destroy all the portals! We've awarded you $points Void Knight")
                player.message("Commendation points. Please also accept these coins as a reward. You now have $total points.")
            }
            else -> {
                player.attr[PEST_WINS] = (player.attr[PEST_WINS] ?: 0) + 1
                player.playJingle(PC_JINGLE_WIN)
                player.message("Congratulations! You managed to destroy all the portals! However, you did not succeed in reaching")
                player.message("the required amount of damage dealt, so we cannot grant you a reward.")
            }
        }
    }

    /** 2009scape: fullRestore, cure poison, restore skills. */
    private fun restore(player: Player) {
        player.skills.restoreAll()
        player.setCurrentLifepoints(player.getMaximumLifepoints())
        player.runEnergy = 100.0
        gg.rsmod.plugins.content.mechanics.poison.Poison.cure(player)
    }
}

/** One running game in its own copy of the island. */
class PestControlGame(
    val world: World,
    val tier: PestControlTier,
    val map: InstancedMap,
) {
    var active = true
        private set
    var ticks = 0
        private set
    val base: Tile = map.area.bottomLeft
    val portals = arrayOfNulls<Npc>(4)
    private val shieldUp = BooleanArray(4) { true }
    private val portalDirty = BooleanArray(4) { true }
    private var knightDirty = true
    lateinit var knight: Npc
    val monsters = mutableListOf<Npc>()
    val barricades = mutableListOf<gg.rsmod.game.model.entity.GameObject>()
    val players = mutableListOf<Player>()
    private val shieldOrder = (0..3).shuffled()
    private val knightDamageBudget = PestControlBalance.KnightDamageBudget()
    val balance get() = PestControlBalance.profile(players.size, tier)

    internal fun knightDamage(damage: Int, roll: Double = Random.nextDouble()): Int =
        knightDamageBudget.apply(ticks, PestControlHandler.knightDamage(players.size, damage, roll), balance.knightDamagePerWindow)

    /** Apply at impact: queued, delayed and scripted hits all use the remaining team and one shared budget. */
    internal fun configureKnightDamage() {
        knight.hitModifier = { hit ->
            if (active) hit.hitmarks.forEach { mark -> mark.damage = knightDamage(mark.damage) }
        }
    }

    /** Which portal each spinner belongs to (it heals that portal and explodes when it dies). */
    val spinnerPortal = HashMap<Npc, Int>()

    fun local(
        x: Int,
        z: Int,
    ): Tile = base.transform(x, z, 0)

    fun landingTile(): Tile = local(PC_SPAWN_X + Random.nextInt(4), PC_SPAWN_Z + Random.nextInt(6))

    fun owns(npc: Npc): Boolean = map.area.contains(npc.tile) && (npc in monsters || isPortal(npc) || npc === knight)

    fun isPortal(npc: Npc): Boolean = portals.any { it === npc }

    fun portalIndex(npc: Npc): Int = portals.indexOfFirst { it === npc }

    fun portalHit(npc: Npc) {
        val i = portalIndex(npc)
        if (i >= 0) portalDirty[i] = true
    }

    fun knightHit() {
        knightDirty = true
    }

    fun addPlayer(p: Player) {
        players.add(p)
        p.attr[PestControlHandler.GAME_ATTR] = this
        p.attr[PestControlHandler.ZEAL_ATTR] = 0
        p.closeInterface(InterfaceDestination.PVP_OVERLAY)
        p.moveTo(landingTile())
        p.openInterface(PC_GAME_INTERFACE, InterfaceDestination.PVP_OVERLAY)
        p.setComponentText(PC_GAME_INTERFACE, 0, "20 min")
        p.setComponentText(PC_GAME_INTERFACE, 1, (PC_KNIGHT_LIFEPOINTS).toString())
        for (i in 0..3) {
            p.setComponentText(PC_GAME_INTERFACE, 13 + i, "<col=00FF00>${tier.portalLifepoints}")
            p.setComponentHidden(PC_GAME_INTERFACE, 18 + (i shl 1), false)
        }
        PestControlHandler.sendZeal(p, 0)
        val defs = world.definitions
        val songId = defs.get(gg.rsmod.game.fs.def.EnumDef::class.java, 1351).getInt(PC_MUSIC_INDEX)
        if (songId > 0) p.playSong(songId, defs.get(gg.rsmod.game.fs.def.EnumDef::class.java, 1345).getString(PC_MUSIC_INDEX))
        p.message("You must defend the Void Knight while the portals are unsummoned. The ritual takes twenty")
        p.message("minutes though, so you can help out by destroying them yourselves! Now GO GO GO!")
    }

    fun removePlayer(p: Player) {
        players.remove(p)
        rebalancePopulation()
    }

    /** Spawns the shielded portals, the Void Knight and the leave squire (2009scape PestControlSession.spawnNPCs). */
    fun begin() {
        PestControlBarricades.collect(this)
        for (i in 0..3) {
            val (ox, oz) = PC_PORTAL_OFFSETS[i]
            portals[i] = spawnPortal(tier.shieldedPortalBaseId + i, local(ox, oz), shielded = true)
        }
        val (kx, kz) = PC_SQUIRE_OFFSET
        knight = Npc(PC_KNIGHT_IDS[Random.nextInt(PC_KNIGHT_IDS.size)], local(kx, kz), world)
        knight.respawnOverride = false
        knight.walkRadius = 0
        world.spawn(knight)
        knight.combatDef = knight.combatDef.copy(lifepoints = PC_KNIGHT_LIFEPOINTS)
        knight.setCurrentLifepoints(PC_KNIGHT_LIFEPOINTS)
        configureKnightDamage()
        // The knight neither moves nor fights back; monsters can still hit it.
        knight.lock = gg.rsmod.game.model.LockState.FULL
        // No "Attack" option in the cache (players never hit it), but the pests must: the shared combat gate's npc-only target.
        knight.attr[gg.rsmod.plugins.content.combat.Combat.NPC_ONLY_TARGET_ATTR] = true
        val (sx, sz) = PC_LEAVE_SQUIRE_OFFSET
        val squire = Npc(PC_LEAVE_SQUIRE, local(sx, sz), world)
        squire.respawnOverride = false
        squire.walkRadius = 0
        world.spawn(squire)
        if (players.isEmpty()) {
            PestControlTrace.problem("${tier.displayName} game started with no players (all offline); ended at once")
            end(success = false, reason = "no players at the start")
        }
    }

    private fun spawnPortal(
        id: Int,
        tile: Tile,
        shielded: Boolean,
    ): Npc {
        val portal = Npc(id, tile, world)
        portal.respawnOverride = false
        portal.walkRadius = 0
        world.spawn(portal)
        if (shielded) {
            portal.combatDef = portal.combatDef.copy(lifepoints = tier.portalLifepoints)
            portal.lock = gg.rsmod.game.model.LockState.FULL_WITH_DAMAGE_IMMUNITY
        } else {
            portal.combatDef = portal.combatDef.copy(lifepoints = tier.portalLifepoints)
            portal.lock = gg.rsmod.game.model.LockState.FULL
        }
        portal.setCurrentLifepoints(tier.portalLifepoints)
        return portal
    }

    fun tick() {
        // The instance is released by the allocator once nobody is inside; the game ends with it.
        if (world.instanceAllocator.getMap(base) !== map) {
            end(success = false, silent = true, reason = "instance released")
            return
        }
        if (knight.isDead() || !knight.isSpawned()) {
            end(success = false, reason = "knight dead=${knight.isDead()} spawned=${knight.isSpawned()}")
            return
        }
        // Whoever teleported or walked out of the instance has left the game.
        for (p in players.toList()) {
            if (!p.isOnline || !map.area.contains(p.tile) || p.attr[PestControlHandler.GAME_ATTR] !== this) {
                players.remove(p)
                if (p.isOnline && p.attr[PestControlHandler.GAME_ATTR] === this) PestControlHandler.cleanUp(p)
            }
        }
        if (ticks % 100 == 0) sendAll(0, "${20 - ticks / 100} min")
        ticks++
        val shieldStep = PC_SHIELD_DROP_TICKS.indexOf(ticks)
        if (shieldStep >= 0) dropShield(shieldOrder[shieldStep])
        if (ticks >= PC_GAME_DURATION_TICKS) {
            end(success = true, reason = "time up")
            return
        }
        monsters.removeIf { !it.isSpawned() || it.isDead() }
        spinnerPortal.keys.removeIf { it !in monsters }
        rebalancePopulation()
        for (i in 0..3) {
            val portal = portals[i] ?: continue
            if (portal.isDead() || !portal.isSpawned()) continue
            if (ticks % balance.waveTicks == 0 && monsters.size < balance.populationCap) spawnWave(i, portal)
            if (portalDirty[i] && ticks % 10 == 0) {
                val lp = portal.getCurrentLifepoints()
                sendAll(13 + i, (if (lp > portal.getMaximumLifepoints() / 4) "<col=00FF00>" else "<col=FF0000>") + lp)
                portalDirty[i] = false
            }
        }
        if (knightDirty && ticks % 10 == 0) {
            val lp = knight.getCurrentLifepoints()
            sendAll(1, if (lp > PC_KNIGHT_LIFEPOINTS / 4) lp.toString() else "<col=FF0000>$lp")
            knightDirty = false
        }
        if (ticks % KNIGHT_SPAWN_TICKS == 0 && spawnKnightShifter() && Random.nextInt(5) == 0) spawnKnightShifter()
        PestControlMonsters.tick(this)
    }

    /** Shifters spawned beside the Void Knight (Darkan: the knight is spawn index 4). */
    private val knightShifters = HashSet<Npc>()

    /**
     * Darkan PestPortal.processNPC / PestControl.createPestNPC for index 4: every 15 ticks the knight brings a shifter (and 1 time in 5 a
     * second one) on a free tile within 5 tiles of it, at most 4 alive. Our 2009scape port spawned nothing at the knight.
     */
    internal fun spawnKnightShifter(): Boolean {
        knightShifters.removeIf { !it.isSpawned() || it.isDead() }
        if (knightShifters.size >= PestControlHandler.knightShifterCap(players.size) || monsters.size >= balance.populationCap || !knight.isSpawned() || knight.isDead()) return false
        val ids = PC_SHIFTERS[tier.difficulty]
        val tile =
            (0 until 10).map { knight.tile.transform(Random.nextInt(-5, 6), Random.nextInt(-5, 6), 0) }
                .firstOrNull { map.area.contains(it) && !world.collision.isClipped(it) } ?: knight.tile
        knightShifters += spawnMonster(ids[Random.nextInt(ids.size)], tile, KNIGHT_SPAWN_INDEX)
        return true
    }

    private fun sendAll(
        child: Int,
        text: String,
    ) {
        for (p in players) p.setComponentText(PC_GAME_INTERFACE, child, text)
    }

    /** 2009scape removePortalShield: message, overlay shield hidden, the portal becomes attackable. */
    internal fun dropShield(index: Int) {
        if (!shieldUp[index]) return
        shieldUp[index] = false
        val message = "The ${PC_PORTAL_NAMES[index]} portal shield has dropped!"
        for (p in players) {
            p.setComponentHidden(PC_GAME_INTERFACE, 18 + (index shl 1), true)
            p.message(message)
        }
        knight.forceChat(message)
        val shielded = portals[index] ?: return
        if (shielded.isDead() || !shielded.isSpawned()) return
        val lifepoints = shielded.getCurrentLifepoints()
        world.remove(shielded)
        val portal = spawnPortal(tier.portalBaseId + index, shielded.tile, shielded = false)
        portal.setCurrentLifepoints(lifepoints)
        portals[index] = portal
        // Spinners keep healing the same portal index; nothing else holds the old npc.
    }

    /** 2009scape PCPortalNPC.spawnNPCs: a wave of difficulty-indexed monsters walking out towards the knight. */
    internal fun spawnWave(
        index: Int,
        portal: Npc,
        random: Random = Random.Default,
    ) {
        val d = tier.difficulty
        val amount = minOf(balance.waveSize, (balance.populationCap - monsters.size).coerceAtLeast(0))
        val dx = Integer.signum(knight.tile.x - portal.tile.x)
        val dz = Integer.signum(knight.tile.z - portal.tile.z)
        for (n in 0 until amount) {
            val ids =
                when (random.nextInt(7)) {
                    0 -> PC_SPLATTERS[d]
                    1 -> PC_SHIFTERS[d]
                    2 -> PC_RAVAGERS[d]
                    3 -> if (spinnerPortal.count { it.value == index } < balance.spinnersPerPortal) PC_SPINNERS[d] else PC_TORCHERS[d]
                    4 -> PC_TORCHERS[d]
                    5 -> PC_DEFILERS[d]
                    else -> if (monsters.count { pestMonsterOf(it.id) == PestMonster.BRAWLER } < 2) PC_BRAWLERS[d] else PC_DEFILERS[d]
                }
            var x = dx * 3
            var z = dz * 3
            if (x == 0) x = n else z = n
            val centre = portal.tile.transform(1, 1, 0)
            val id = ids[random.nextInt(ids.size)]
            val tile = if (pestMonsterOf(id) == PestMonster.SPINNER) {
                val visible = spinnerTile(index)
                if (visible == null) {
                    PestControlTrace.problem("no clear spinner tile at portal $index in ${tier.displayName}; spawn skipped")
                    continue
                }
                visible
            } else centre.transform(x, z, 0)
            spawnMonster(id, tile, index)
        }
    }

    /** Keep a clear gap around all portals, and a separate, walkable tile for each spinner. */
    internal fun spinnerTile(index: Int, spinner: Npc? = null): Tile? {
        val portal = portals.getOrNull(index) ?: return null
        val tiles = PestControlBalance.spinnerTiles(portal.tile, portal.getSize())
        return tiles.firstOrNull { tile ->
            map.area.contains(tile) && !world.collision.isClipped(tile) &&
                portals.filterNotNull().none { p ->
                    tile.x in p.tile.x until p.tile.x + p.getSize() && tile.z in p.tile.z until p.tile.z + p.getSize()
                } && spinnerPortal.keys.none { it !== spinner && it.tile == tile }
        }
    }

    /** Apply departures to pests already alive, not only the next wave. No drops/zeal for despawning excess pests. */
    internal fun rebalancePopulation() {
        fun remove(monster: Npc) {
            monster.resetInteractions()
            monster.stopMovement()
            world.remove(monster)
            monsters.remove(monster)
            spinnerPortal.remove(monster)
            knightShifters.remove(monster)
        }
        knightShifters.removeIf { it !in monsters || !it.isSpawned() || it.isDead() }
        knightShifters.toList().drop(balance.knightShifters).forEach { remove(it) }
        for (index in 0..3) {
            spinnerPortal.filterValues { it == index }.keys.toList().drop(balance.spinnersPerPortal).forEach { remove(it) }
        }
        monsters.toList().drop(balance.populationCap).forEach { remove(it) }
    }

    /** One pest monster of this game: never respawns, hunts only this game's players, a spinner belongs to [portalIndex]. */
    fun spawnMonster(
        id: Int,
        tile: Tile,
        portalIndex: Int,
    ): Npc {
        val monster = Npc(id, tile, world)
        monster.respawnOverride = false
        monster.walkRadius = if (pestMonsterOf(id) == PestMonster.SPINNER) 0 else 64
        // Darkan PestControlGameController.canMove / PestControl.isBrawlerAt: a player cannot step onto a brawler's tile (they
        // wall off the knight's path); every other pest is walked through as in OSRS.
        monster.blocksPlayers = pestMonsterOf(id) == PestMonster.BRAWLER
        world.spawn(monster)
        monster.aggroCheck = { _, target -> target.attr[PestControlHandler.GAME_ATTR] === this }
        monsters.add(monster)
        if (pestMonsterOf(id) == PestMonster.SPINNER) spinnerPortal[monster] = portalIndex
        return monster
    }

    /** on_npc_death for every portal id. */
    fun onPortalDeath(npc: Npc) {
        val index = portalIndex(npc)
        if (index < 0) return
        for (p in players) p.message("The ${PC_PORTAL_NAMES[index]} portal has been destroyed!")
        sendAll(13 + index, "<col=FF0000>0")
        if (portals.all { it == null || it.isDead() || !it.isSpawned() }) {
            end(success = true, reason = "all portals down")
            return
        }
        // 2009scape: the portal's spinners explode and the knight regains 50 hitpoints.
        for ((spinner, portalIndex) in spinnerPortal.toList()) {
            if (portalIndex == index) PestControlMonsters.spinnerExplode(this, spinner)
        }
        knight.setCurrentLifepoints(minOf(PC_KNIGHT_LIFEPOINTS, knight.getCurrentLifepoints() + PC_KNIGHT_PORTAL_HEAL))
        knightDirty = true
    }

    fun end(
        success: Boolean,
        silent: Boolean = false,
        reason: String = "",
    ) {
        if (!active) return
        active = false
        gg.rsmod.game.model.World.logger.info { "Pest Control ${tier.displayName} game over (${if (success) "won" else "lost"}) at tick $ticks: $reason" }
        for (p in players.toList()) {
            if (!p.isOnline) continue
            if (silent) PestControlHandler.cleanUp(p) else PestControlHandler.pay(p, this, success)
        }
        players.clear()
        if (world.instanceAllocator.getMap(base) === map) world.instanceAllocator.release(world, map)
    }
}
