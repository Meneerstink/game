package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.godwars.GodWars
import gg.rsmod.plugins.content.npcs.Constants

/**
 * When the Deadman teleport/logout delay ([SevenSecondAction], 6 seconds = 10 ticks) applies, and when an unskulled player
 * may teleport at once.
 *
 * DMM-ARMAGEDDON D1 (owner 20-11-2026, "precies als de foto", OSRS Wiki Deadman: Armageddon "PJ timer"):
 * - "If Player B is skulled and tries to teleport (or log out), they must wait 6 seconds (10 ticks) ... This is the Deadman
 *   teleport/logout delay. If Player B is hit, or takes some other action like eating food or attacking back, this delay is
 *   cancelled and the player must start it again" -> skulled = [Teleport.COUNTDOWN] for every teleport and every logout.
 * - "If Player B is not skulled, they can teleport instantly, so long as they haven't been hit for 5.4 seconds (9 ticks). If
 *   Player B is in combat with a boss/NPC, the boss/NPC must not have hit Player B for at least 2.4 seconds." The wiki table
 *   shows the attack at 0 s and the instant teleport at 6.0 s (and the PJ at 10.2 s for "9.6 seconds (16 ticks)", see
 *   [gg.rsmod.plugins.content.combat.Combat.PJ_TICKS] = 17): counted from the attack cycle the windows are therefore
 *   [PVP_HIT_TELEPORT_BLOCK_CYCLES] = 10 and [NPC_HIT_TELEPORT_BLOCK_CYCLES] = 5 cycles.
 * - Unskulled logout: "Player B can trigger logout delay, clicking Logout here will activate a 6 second (10 tick) timer" while
 *   in combat (owner 20-11: the same 5.4 s player / 2.4 s npc windows decide "in combat"); out of combat the logout is instant.
 * - Owner 20-11: the old boss-area exception (sept: boss hits never delayed a teleport) is GONE - a boss is an npc (2.4 s).
 *   The minigame exception (owner 10-10, Pest Control) stays: there an npc hit never delays a teleport.
 * - The Fortuity trinket keeps its countdown on every teleport (DMM2).
 *
 * Player hits are read from [PvpSkull.cyclesSincePvpHit] (never overwritten by an npc hit, audit D-11) and, as a fallback for
 * routes that skip [PvpSkull.markAggression], from [TELEPORT_COMBAT_TIMER] with a player as the last attacker. Npc hits from
 * [LAST_NPC_HIT_CYCLE_ATTR], recorded by [gg.rsmod.plugins.content.combat.Combat.postAttack] via [markNpcHit].
 */
object DeadmanTimerGate {
    enum class Teleport {
        /** Teleport at once. */
        INSTANT,

        /** Skulled: the 6-second (10-tick) countdown. */
        COUNTDOWN,

        /** Unskulled but hit too recently: refuse with the remaining-seconds message. */
        BLOCKED_IN_COMBAT,
    }

    /** Wiki: not hit by a player "for 5.4 seconds (9 ticks)"; table: instant at 6.0 s -> 10 cycles from the attack. */
    const val PVP_HIT_TELEPORT_BLOCK_CYCLES = 10

    /** Wiki: "the boss/NPC must not have hit Player B for at least 2.4 seconds" (4 ticks) -> 5 cycles from the attack. */
    const val NPC_HIT_TELEPORT_BLOCK_CYCLES = 5

    /** The world cycle an npc (boss or not) last attacked this player. Cleared by death. */
    val LAST_NPC_HIT_CYCLE_ATTR = AttributeKey<Int>(resetOnDeath = true)

    fun markNpcHit(player: Player) {
        player.attr[LAST_NPC_HIT_CYCLE_ATTR] = player.world.currentCycle
    }

    fun teleportDecision(player: Player): Teleport =
        when {
            PvpSkull.isSkulled(player) || gg.rsmod.plugins.content.mechanics.deadman.DeadmanTrinkets.fortuity(player) -> Teleport.COUNTDOWN
            recentlyHitByPlayer(player) -> Teleport.BLOCKED_IN_COMBAT
            inMinigame(player) -> Teleport.INSTANT
            recentlyHitByNpc(player) -> Teleport.BLOCKED_IN_COMBAT
            else -> Teleport.INSTANT
        }

    /**
     * Owner 2026-10-10 ("minigames such as pest control should be instant teleport", kept 20-11): inside a minigame a recent
     * npc hit never delays a teleport. A skull or a recent player hit still apply (checked first in [teleportDecision]).
     */
    fun inMinigame(player: Player): Boolean =
        gg.rsmod.plugins.content.activity.pest_control.PestControlHandler.gameOf(player) != null ||
            gg.rsmod.plugins.content.activity.pest_control.PestControlHandler.lobbyOf(player) != null

    /**
     * Logout: the 6-second countdown when skulled or in combat (hit by a player in the last [PVP_HIT_TELEPORT_BLOCK_CYCLES] or
     * by any npc, bosses included, in the last [NPC_HIT_TELEPORT_BLOCK_CYCLES]); otherwise at once.
     *
     * `Player.requestLogout(deadmanDelayHandled = true)` clearing the X-log hold needs no engine change for this: the logout
     * button only reaches it when this returned false or after an uninterrupted countdown, and any hit on the player cancels
     * that countdown.
     */
    fun needsCountdown(player: Player): Boolean =
        PvpSkull.isSkulled(player) || recentlyHitByPlayer(player) || recentlyHitByNpc(player)

    /** Hit by another player within [PVP_HIT_TELEPORT_BLOCK_CYCLES]. */
    fun recentlyHitByPlayer(player: Player): Boolean = pvpCyclesLeft(player) > 0

    /** Hit by any npc (boss or not) within [NPC_HIT_TELEPORT_BLOCK_CYCLES]. */
    fun recentlyHitByNpc(player: Player): Boolean = npcCyclesLeft(player) > 0

    private fun pvpCyclesLeft(player: Player): Int {
        val since = PvpSkull.cyclesSincePvpHit(player)
        val fromPvp = if (since != null && since >= 0) PVP_HIT_TELEPORT_BLOCK_CYCLES - since else 0
        // Fallback: a player attack that did not pass markAggression still armed the combat timer with that player as last hitter.
        val fromTimer =
            if (player.timers.has(TELEPORT_COMBAT_TIMER) && player.attr[LAST_HIT_BY_ATTR]?.get() is Player) {
                player.timers[TELEPORT_COMBAT_TIMER].coerceAtMost(PVP_HIT_TELEPORT_BLOCK_CYCLES)
            } else {
                0
            }
        return maxOf(fromPvp, fromTimer, 0)
    }

    private fun npcCyclesLeft(player: Player): Int {
        val last = player.attr[LAST_NPC_HIT_CYCLE_ATTR] ?: return 0
        val since = player.world.currentCycle - last
        return if (since >= 0) (NPC_HIT_TELEPORT_BLOCK_CYCLES - since).coerceAtLeast(0) else 0
    }

    /**
     * One entry point for non-magical escape routes (boats, minecarts, carpets, portals and similar transports). Permanent
     * Deadman applies this countdown regardless of skull or combat status (owner 20-11: 6 seconds like teleport and logout).
     * Callers pass the complete action so it cannot continue outside the gate.
     */
    fun requestRoute(
        player: Player,
        kind: SevenSecondAction.Kind,
        action: () -> Unit,
    ): Boolean {
        SevenSecondAction.start(player, kind, action)
        return false
    }

    /** Whole seconds the player must still stay out of combat, for the refusal message. */
    fun combatSecondsLeft(player: Player): Int {
        val npc = if (inMinigame(player)) 0 else npcCyclesLeft(player)
        val cycles = maxOf(pvpCyclesLeft(player), npc)
        return if (cycles > 0) SevenSecondAction.secondsFor(cycles) else 0
    }

    fun blockedMessage(player: Player): String = "You must be out of combat for another ${combatSecondsLeft(player)} seconds to teleport."
}

/**
 * "Alle bosses" (owner 2026-09-17): every boss npc of this world, by id where a constant exists and
 * by cache name for the multi-id families, so a fight with any form of a boss is exempt from the
 * Deadman countdown. [Constants.BOSS_NPC_IDS] is the existing boss list (slayer/boss tracking);
 * the rest are the bosses with their own encounters in this codebase.
 */
object BossNpcs {
    val IDS: Set<Int> =
        Constants.BOSS_NPC_IDS +
            setOf(
                Npcs.KING_BLACK_DRAGON_2642,
                Npcs.KALPHITE_QUEEN_1159, Npcs.KALPHITE_QUEEN_1160, Npcs.KALPHITE_QUEEN_3835, Npcs.KALPHITE_QUEEN_3836, Npcs.KALPHITE_QUEEN_4234,
                Npcs.GENERAL_GRAARDOR, Npcs.KREEARRA, Npcs.COMMANDER_ZILYANA, Npcs.KRIL_TSUTSAROTH,
                Npcs.NEX, Npcs.NEX_13448,
                Npcs.CORPOREAL_BEAST,
                Npcs.AHRIM_THE_BLIGHTED, Npcs.DHAROK_THE_WRETCHED, Npcs.GUTHAN_THE_INFESTED,
                Npcs.KARIL_THE_TAINTED, Npcs.TORAG_THE_CORRUPTED, Npcs.VERAC_THE_DEFILED,
                Npcs.BORK, Npcs.BORK_7134,
                Npcs.BARRELCHEST, Npcs.BARRELCHEST_6696,
                Npcs.AVATAR_OF_DESTRUCTION, Npcs.AVATAR_OF_DESTRUCTION_8615,
                Npcs.AVATAR_OF_CREATION, Npcs.AVATAR_OF_CREATION_8614,
                Npcs.DECAYING_AVATAR,
                Npcs.BALANCE_ELEMENTAL, Npcs.BALANCE_ELEMENTAL_8282, Npcs.BALANCE_ELEMENTAL_8283,
                Npcs.BALANCE_ELEMENTAL_8284, Npcs.BALANCE_ELEMENTAL_8285, Npcs.BALANCE_ELEMENTAL_8546,
            ) +
            (Npcs.TORMENTED_DEMON..Npcs.TORMENTED_DEMON_8366).toSet()

    /** Cache names of boss families whose every variant id should count. */
    val NAMES: Set<String> =
        setOf(
            "king black dragon", "kalphite queen", "chaos elemental", "dagannoth rex", "dagannoth prime",
            "dagannoth supreme", "giant mole", "tztok-jad", "general graardor", "kree'arra",
            "commander zilyana", "k'ril tsutsaroth", "nex", "corporeal beast", "tormented demon", "glacor",
            "bork", "ahrim the blighted", "dharok the wretched", "guthan the infested", "karil the tainted",
            "torag the corrupted", "verac the defiled", "barrelchest", "avatar of destruction",
            "avatar of creation", "decaying avatar", "balance elemental", "mimic", "the mimic",
        )

    fun isBoss(pawn: Pawn): Boolean {
        if (pawn !is Npc) return false
        if (pawn.id in IDS) return true
        return pawn.def.name.lowercase() in NAMES
    }
}

/**
 * Boss areas (owner 2026-09-17: "all godwars bosses dungeons ... corporal beast cave king black dragon
 * cave tormented demon and other bosses we have is safe" for teleporting). Derived from the existing
 * boss definitions instead of a hand-typed list: a 64x64 map region is a boss area when
 * - a [BossNpcs] npc stood in it when the world finished loading ([init], so a boss in its respawn
 *   gap still counts), or
 * - a [BossNpcs] npc is standing in it right now (script-spawned bosses such as Nex or Bork), or
 * - it is one of the four God Wars generals' chambers ([GodWars.God]) - the whole GWD dungeon.
 *
 * The live check only walks the npcs of the player's own region and runs once per teleport attempt,
 * never per cycle.
 */
object BossAreas {
    private val bootRegions = HashSet<Int>()

    /** Called once from the world-init hook after every spawn file has been applied. */
    fun init(world: World): String {
        bootRegions.clear()
        world.npcs.forEach { npc -> if (npc != null && BossNpcs.isBoss(npc)) bootRegions += npc.tile.regionId }
        GodWars.God.values().forEach { god ->
            for (x in god.chamberX step 8) for (z in god.chamberZ step 8) bootRegions += Tile(x, z, god.chamberHeight).regionId
            bootRegions += Tile(god.chamberX.last, god.chamberZ.last, god.chamberHeight).regionId
        }
        return "BossAreas: ${bootRegions.size} boss regions derived from the boss npc spawns and the God Wars chambers"
    }

    fun bootRegionCount(): Int = bootRegions.size

    /** Fixed lairs captured at startup, unlike an ordinary field which temporarily holds a breach boss. */
    fun wasBossAreaAtBoot(tile: Tile): Boolean = tile.regionId in bootRegions

    fun isBossArea(
        world: World,
        tile: Tile,
    ): Boolean {
        val region = tile.regionId
        if (region in bootRegions) return true
        return world.npcs.any { npc -> npc != null && npc.tile.regionId == region && BossNpcs.isBoss(npc) }
    }
}
