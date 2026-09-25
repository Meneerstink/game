package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.godwars.GodWars
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.npcs.Constants

/**
 * When the Deadman 7-second countdown ([SevenSecondAction]) applies to logging out and teleporting.
 *
 * Owner "deadmanmode vervijning" (2026-09-17, OSRS Deadman Mode wiki wording):
 * - "Skulled players will not be able to teleport instantly even if they are out of combat, as they
 *   will receive the 7 second timer interface when they attempt to teleport."
 * - "Unskulled players will be able to teleport instantly as long as they have not been attacked by a
 *   player or NPC in the last 7 seconds. If a unskulled player has been attacked recently and attempts
 *   to teleport, they will instead receive the following game message: You must be out of combat for
 *   another X seconds to teleport." - no interface, the player keeps acting freely.
 * - "Players who are in a boss area ... can teleport instantly without a timer": inside a boss area
 *   ([BossAreas]) recent hits never delay a teleport; a hit by any boss npc never counts anywhere.
 * - Logout keeps the earlier rule: a countdown only when skulled or in (non-boss) combat.
 *
 * "Attacked in the last 7 seconds" is [TELEPORT_COMBAT_TIMER], armed for [SevenSecondAction.DURATION_CYCLES]
 * by [gg.rsmod.plugins.content.combat.Combat.postAttack] on every landed hit.
 *
 * Audit D-11: a boss hit overwrites LAST_HIT_BY, so "the last attacker" alone let a player hit by a PKer at a boss take
 * one boss hit and then log out or teleport instantly. Both logout and the boss-area teleport exception now also look
 * at the last PLAYER hit ([PvpSkull.cyclesSincePvpHit], recorded by [PvpSkull.markAggression] and never overwritten by
 * an npc): within [PVP_HIT_TELEPORT_BLOCK_CYCLES] of it, logout counts down and teleporting is blocked, boss or not.
 * (Not [gg.rsmod.game.model.timer.DEADMAN_LOGOUT_TIMER]: combat now arms that X-log hold for boss hits too, so it cannot
 * tell a PvP hit from a boss hit.)
 */
object DeadmanTimerGate {
    enum class Teleport {
        /** Teleport at once. */
        INSTANT,

        /** Skulled: the 7-second countdown interface. */
        COUNTDOWN,

        /** Unskulled but hit in the last 7 seconds: refuse with the remaining-seconds message. */
        BLOCKED_IN_COMBAT,
    }

    /** Audit D-11: after a player hit, even a boss area gives no instant teleport for this long (7 seconds, as elsewhere). */
    const val PVP_HIT_TELEPORT_BLOCK_CYCLES = SevenSecondAction.DURATION_CYCLES

    fun teleportDecision(player: Player): Teleport =
        when {
            PvpSkull.isSkulled(player) -> Teleport.COUNTDOWN
            recentlyHitByPlayer(player) -> Teleport.BLOCKED_IN_COMBAT
            BossAreas.isBossArea(player.world, player.tile) -> Teleport.INSTANT
            recentlyHitByNonBoss(player) -> Teleport.BLOCKED_IN_COMBAT
            else -> Teleport.INSTANT
        }

    /**
     * Owner exception: logout counts down only when skulled or in combat with a player / non-boss npc. Audit D-11: a
     * player hit in the last 7 seconds counts too, even when a boss hit came after it.
     *
     * `Player.requestLogout(deadmanDelayHandled = true)` clearing the X-log hold needs no engine change for this: the
     * logout button only reaches it when this returned false or after an uninterrupted 7-second countdown, and any hit
     * on the player cancels that countdown, so no player hit can have landed in the 7 seconds before it completes.
     */
    fun needsCountdown(player: Player): Boolean =
        PvpSkull.isSkulled(player) || recentlyHitByPlayer(player) || inNonBossCombat(player)

    /** Audit D-11: hit by another player within [PVP_HIT_TELEPORT_BLOCK_CYCLES]. */
    fun recentlyHitByPlayer(player: Player): Boolean {
        val since = PvpSkull.cyclesSincePvpHit(player) ?: return false
        return since in 0 until PVP_HIT_TELEPORT_BLOCK_CYCLES
    }

    /**
     * One entry point for non-magical escape routes (boats, minecarts, carpets, portals and
     * similar transports). Permanent Deadman applies this countdown regardless of skull or
     * combat status. The owner's narrower exception applies to logout/teleport only; it does not
     * exempt non-teleport transport. Callers pass the complete action so it cannot continue
     * outside the gate.
     */
    fun requestRoute(
        player: Player,
        kind: SevenSecondAction.Kind,
        action: () -> Unit,
    ): Boolean {
        SevenSecondAction.start(player, kind, action)
        return false
    }

    /** Whole seconds the player must still stay out of combat, for the owner's message. */
    fun combatSecondsLeft(player: Player): Int {
        val combat = if (player.timers.has(TELEPORT_COMBAT_TIMER)) player.timers[TELEPORT_COMBAT_TIMER] else 0
        // Audit D-11: the player-hit block can outlast a TELEPORT_COMBAT_TIMER that was never refreshed.
        val pvp = PvpSkull.cyclesSincePvpHit(player)?.let { PVP_HIT_TELEPORT_BLOCK_CYCLES - it }?.takeIf { it > 0 } ?: 0
        val cycles = maxOf(combat, pvp)
        return if (cycles > 0) SevenSecondAction.secondsFor(cycles) else 0
    }

    fun blockedMessage(player: Player): String = "You must be out of combat for another ${combatSecondsLeft(player)} seconds to teleport."

    /** Hit within the last 7 seconds by something that is not a boss. */
    fun recentlyHitByNonBoss(player: Player): Boolean {
        if (!player.timers.has(TELEPORT_COMBAT_TIMER)) return false
        val attacker = player.attr[LAST_HIT_BY_ATTR]?.get()
        return attacker == null || !BossNpcs.isBoss(attacker)
    }

    fun inNonBossCombat(player: Player): Boolean {
        if (recentlyHitByNonBoss(player)) return true
        val target = player.getCombatTarget()
        if (target != null && !BossNpcs.isBoss(target)) return true
        return false
    }
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

    fun isBossArea(
        world: World,
        tile: Tile,
    ): Boolean {
        val region = tile.regionId
        if (region in bootRegions) return true
        return world.npcs.any { npc -> npc != null && npc.tile.regionId == region && BossNpcs.isBoss(npc) }
    }
}
