package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.npcs.Constants

/**
 * When the Deadman 7-second countdown ([SevenSecondAction]) applies to logging out and teleporting.
 *
 * Owner instruction 2026-09-17 (supersedes the 2026-09-16 "always a countdown on logout" and the
 * "blocked with a message when hit in the last 7 seconds" teleport rule):
 * - logout: no countdown when you are not skulled and not in combat - you log out at once;
 * - teleport: the countdown "moet alleen gaan komen als je geskulled bent of in combat bent (met
 *   uitzondering van alle bosses)".
 *
 * "In combat" means a player or npc hit you within the last 7 seconds ([TELEPORT_COMBAT_TIMER], armed
 * by [gg.rsmod.plugins.content.combat.Combat.postAttack] on every landed hit) or you are currently
 * attacking someone. A fight with a boss npc never counts ([BossNpcs]).
 */
object DeadmanTimerGate {
    fun needsCountdown(player: Player): Boolean = player.hasSkullIcon(SkullIcon.RED) || inNonBossCombat(player)

    fun inNonBossCombat(player: Player): Boolean {
        if (player.timers.has(TELEPORT_COMBAT_TIMER)) {
            val attacker = player.attr[LAST_HIT_BY_ATTR]?.get()
            if (attacker == null || !BossNpcs.isBoss(attacker)) return true
        }
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
