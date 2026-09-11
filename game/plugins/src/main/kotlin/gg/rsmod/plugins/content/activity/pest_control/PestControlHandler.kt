package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import java.util.PriorityQueue
import kotlin.random.Random

/**
 * Core session logic for Q-050 Pest Control. See PestControlData.kt for the sourcing note and
 * the real-vs-simplified id/tier tables.
 *
 * Real vertical slice ported: 3 combat-level-gated lobbies, real 500-tick departure timer with
 * early-start at team-full (PestControlActivityPlugin's pulse), a single shared game in progress
 * at a time (this engine has no instance manager - see PestControlData.kt), 4 real portals + a
 * squire spawned at the real static region-10536 coordinates, the real per-portal monster-wave
 * cadence formula (tick() in PCPortalNPC.java: a wave fires when `ticks % 35 == playerCount` and
 * region npc count < 100), the real 7-type weighted monster mix (r.nextInt(7) switch, with the
 * Spinner<->Torcher and Brawler<->Defiler concurrent-cap substitutions), the real shield-drop
 * schedule (ticks 50/100/150/200, one random portal each), the real win conditions (all 4 portals
 * destroyed = early win, 2000-tick timer = win) and loss condition (squire killed), and a
 * damage-share pest-points award at game end.
 *
 * Disclosed simplifications, not guesses: (1) monster ids are pooled flat per type rather than
 * split into the donor's 3 difficulty-indexed sub-arrays (all 45 ids are still the real, verified
 * ones - only the difficulty-tier split is dropped); (2) per-portal "difficulty index" (which
 * would make some portals spawn tougher waves than others) is not modelled - all 4 portals use
 * the same flat wave-size formula; (3) squire per-hit "zeal" tracking (used donor-side for a
 * live points-ticker on the game overlay) is approximated at game end via each portal's
 * DamageMap.getDamageFrom(player) summed across all 4 portals, since this engine has no existing
 * per-hit damage-event hook to mirror the donor's live accumulator; (4) barricades/gates already
 * exist physically in the map (real ids, real placements) but are not interactive (no
 * repair/attack wiring) - a named remaining gap, not a guess; (5) joining is via command
 * (::pestcontrolnovice/intermediate/veteran) rather than a physical lander/gangplank object,
 * since no cache id specifically tied to the Pest Control boat boarding option was verified -
 * same disclosed-simplification precedent as Clan Wars (full)'s ::clanwarenter command; (6) the
 * donor spawns each portal in a non-attackable "+4 id" transform and only reTransforms it to the
 * attackable base id once its shield timer drops - not replicated here (portals are attackable
 * from the moment they spawn, the shield-drop is cosmetic-message-only) because the "+4" shielded
 * npc ids were not independently confirmed present for every tier and guessing which numeric
 * offset applies to the veteran tier's ids risked spawning a wrong/nonexistent npc - a real,
 * named remaining gap rather than a guessed id.
 */
object PestControlHandler {
    private val PC_PRIORITY = gg.rsmod.game.model.attr.AttributeKey<Int>()

    private val waiting: Map<PestControlTier, PriorityQueue<Player>> =
        PestControlTier.values().toList().associateWith { PriorityQueue(20, compareByDescending { it.attr[PC_PRIORITY] ?: 0 }) }

    private var lobbyTicks = 0
    private var session: Session? = null

    private class Session(
        val tier: PestControlTier,
        val world: World,
    ) {
        var ticks = 0
        val portals = arrayOfNulls<Npc>(4)
        val portalDown = BooleanArray(4)
        var squire: Npc? = null
        val participants = mutableListOf<Player>()
        val shieldOrder = (0..3).shuffled()
        var spinnersAlive = 0
        var brawlersAlive = 0
    }

    fun join(
        player: Player,
        tier: PestControlTier,
    ) {
        if (player.combatLevel < tier.combatReq) {
            player.message("You need a combat level of ${tier.combatReq} or higher to board this lander.")
            return
        }
        if (session?.participants?.contains(player) == true || waiting.values.any { it.contains(player) }) {
            player.message("You are already waiting for a Pest Control game.")
            return
        }
        waiting.getValue(tier).add(player)
        player.message("You board the ${tier.displayName.lowercase()} lander and wait for the next game.")
    }

    /** Called every game tick from the pest_control.plugin.kts master timer. */
    fun tick(world: World) {
        val active = session
        if (active == null) {
            lobbyTicks++
            for (tier in PestControlTier.values().toList()) {
                val queue = waiting.getValue(tier)
                if (queue.isEmpty()) continue
                val full = queue.size >= PC_MAX_TEAM_SIZE
                if (lobbyTicks >= PC_LOBBY_DEPARTURE_TICKS || full) {
                    if (queue.size >= PC_MIN_TEAM_SIZE) {
                        startGame(world, tier)
                        lobbyTicks = 0
                        return
                    }
                }
            }
            return
        }
        active.ticks++
        val shieldStep = active.ticks / 50
        if (active.ticks % 50 == 0 && shieldStep in 1..4) {
            val index = active.shieldOrder[shieldStep - 1]
            dropShield(active, index)
        }
        val playerCount = active.participants.count { it.isOnline }
        for (portal in active.portals) {
            if (portal == null || portal.isDead()) continue
            if (active.ticks % 35 == playerCount && countRegionNpcs(active) < 100) {
                spawnWave(active, portal)
            }
        }
        if (active.ticks >= PC_GAME_DURATION_TICKS) {
            endGame(active, success = true)
        }
    }

    private fun startGame(
        world: World,
        tier: PestControlTier,
    ) {
        val queue = waiting.getValue(tier)
        val s = Session(tier, world)
        var count = 0
        while (queue.isNotEmpty() && count < PC_MAX_TEAM_SIZE) {
            val p = queue.poll()
            if (!p.isOnline) continue
            count++
            s.participants.add(p)
            p.attr[PC_PRIORITY] = 0
            val (bx, bz) = PC_SPAWN_BASE
            p.moveTo(PC_REGION_BASE.transform(bx + PC_SPAWN_X_RANGE.random(), bz + PC_SPAWN_Z_RANGE.random(), 0))
            p.message("You must defend the Void Knight while the portals are unsummoned. The ritual")
            p.message("takes twenty minutes though, so you can help out by destroying them yourselves!")
        }
        for (leftover in queue) {
            leftover.attr[PC_PRIORITY] = (leftover.attr[PC_PRIORITY] ?: 0) + 1
        }
        for (i in 0..3) {
            val (ox, oz) = PC_PORTAL_OFFSETS[i]
            val portal = Npc(tier.portalBaseId + i, PC_REGION_BASE.transform(ox, oz, 0), world)
            portal.respawns = false
            world.spawn(portal)
            s.portals[i] = portal
        }
        val (sx, sz) = PC_SQUIRE_OFFSET
        val squireId = PC_SQUIRE_IDS[Random.nextInt(PC_SQUIRE_IDS.size)]
        val squire = Npc(squireId, PC_REGION_BASE.transform(sx, sz, 0), world)
        squire.respawns = false
        world.spawn(squire)
        s.squire = squire
        session = s
    }

    private fun dropShield(
        s: Session,
        index: Int,
    ) {
        s.portalDown[index] = true
        val names = arrayOf("purple, western", "blue, eastern", "yellow, south-eastern", "red, south-western")
        val message = "The ${names[index]} portal shield has dropped!"
        for (p in s.participants) {
            if (p.isOnline) p.message(message)
        }
    }

    private fun countRegionNpcs(s: Session): Int = s.portals.count { it != null && !it.isDead() } + s.spinnersAlive + s.brawlersAlive

    private fun spawnWave(
        s: Session,
        portal: Npc,
    ) {
        val amount = 1 + s.participants.count { it.isOnline } / 10
        repeat(amount) {
            val ids =
                when (Random.nextInt(7)) {
                    0 -> PC_SPLATTER_IDS
                    1 -> PC_SHIFTER_IDS
                    2 -> PC_RAVAGER_IDS
                    3 -> if (s.spinnersAlive < 3) { s.spinnersAlive++; PC_SPINNER_IDS } else PC_TORCHER_IDS
                    4 -> PC_TORCHER_IDS
                    5 -> PC_DEFILER_IDS
                    else -> if (s.brawlersAlive < 2) { s.brawlersAlive++; PC_BRAWLER_IDS } else PC_DEFILER_IDS
                }
            val id = ids[Random.nextInt(ids.size)]
            val spawnTile = portal.tile.transform(Random.nextInt(-2, 3), Random.nextInt(-2, 3), 0)
            val monster = Npc(id, spawnTile, s.world)
            monster.respawns = false
            s.world.spawn(monster)
        }
    }

    /** Called from on_npc_death(PORTAL ids...) - checks for the all-4-destroyed early win. */
    fun onPortalDestroyed(npc: Npc) {
        val s = session ?: return
        val index = s.portals.indexOfFirst { it === npc }
        if (index == -1) return
        for (p in s.participants) {
            if (p.isOnline) p.message("A portal has been destroyed!")
        }
        if (s.portals.all { it == null || it.isDead() }) {
            endGame(s, success = true)
        }
    }

    /** Called from on_npc_death(squire ids...) - the loss condition. */
    fun onSquireKilled(npc: Npc) {
        val s = session ?: return
        if (s.squire !== npc) return
        endGame(s, success = false)
    }

    private fun endGame(
        s: Session,
        success: Boolean,
    ) {
        if (session !== s) return
        session = null
        for (p in s.participants) {
            if (!p.isOnline) continue
            p.moveTo(s.tier.leaveTile)
            if (success) {
                val damage = s.portals.sumOf { it?.damageMap?.getDamageFrom(p) ?: 0 }
                if (damage > 0) {
                    p.attr[PEST_POINTS] = (p.attr[PEST_POINTS] ?: 0) + s.tier.pointsPerWin
                    p.message("The Void Knights thank you for your assistance in the battle.")
                    p.message("You have been awarded ${s.tier.pointsPerWin} pest control points.")
                } else {
                    p.message("You did not contribute enough to earn any pest control points this game.")
                }
            } else {
                p.message("The Void Knight has fallen! The battle is lost.")
            }
        }
        s.portals.forEach { it?.let { npc -> if (!npc.isDead()) s.world.remove(npc) } }
        s.squire?.let { if (!it.isDead()) s.world.remove(it) }
    }
}
