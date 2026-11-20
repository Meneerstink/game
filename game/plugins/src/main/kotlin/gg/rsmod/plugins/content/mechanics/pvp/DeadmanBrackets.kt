package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PROTECTION_FORFEITED_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.message

/**
 * The Deadman: Armageddon combat brackets (DMM-ARMAGEDDON D8, owner 20-11-2026: "bouw ook het bracketsysteem als op de foto").
 *
 * OSRS Wiki Deadman: Armageddon "Bracket worlds": "As players gain additional combat levels ... they will progress through five
 * distinct combat brackets, with each combat bracket having access to a separate world. Upon entering a new combat bracket,
 * including the starting one, players will be given one hour of PvP protection ... it is not possible to move back down a combat
 * bracket." Brackets 3-50, 51-70, 71-90, 91-110, 111-126; our combat level goes to 138, so the last one is 111-138 (owner 20-11).
 *
 * We have one world, so a separate world per bracket becomes: a player can only attack (and be attacked by) players of his own
 * bracket ([sameBracket], owner 20-11 - replaces the +/-14 combat levels). Every new bracket reached after this release gives the
 * hour of protection again (owner 20-11), with the rules of the start protection ([BeginnerProtection]); when the player is in
 * combat at the level-up, the hour starts as soon as he has been out of combat for [OUT_OF_COMBAT_CYCLES] (no escape mid-fight).
 *
 * The experience and drop multipliers stay the Annihilation tiers for now (owner 20-11: "de xp en drop multipliers vullen we later
 * in"); the quests of each bracket are [gg.rsmod.plugins.content.quests.foundation.QuestBrackets].
 */
object DeadmanBrackets {
    enum class Bracket(val levels: IntRange) {
        B3_50(3..50),
        B51_70(51..70),
        B71_90(71..90),
        B91_110(91..110),
        B111_PLUS(111..AreaState.MAX_COMBAT_LEVEL),
        ;

        val label: String get() = "${levels.first}-${levels.last}"
    }

    /** The highest bracket the account ever reached (ordinal): a bracket never goes down. */
    val REACHED = AttributeKey<Int>(persistenceKey = "armageddon_bracket_reached")

    /** A new bracket's protection hour that waits until the player is out of combat. */
    val PENDING_PROTECTION = AttributeKey<Boolean>(persistenceKey = "armageddon_bracket_protection_pending")

    /** Re-checks a waiting protection hour ([PENDING_PROTECTION]) every [CHECK_CYCLES]; deadman_brackets.plugin.kts. */
    val PROTECTION_CHECK_TIMER = TimerKey()
    const val CHECK_CYCLES = 5

    /** Out of combat this long (cycles since the last attack either way; = the PJ timer) before a waiting hour starts. */
    const val OUT_OF_COMBAT_CYCLES = 17

    fun forCombatLevel(level: Int): Bracket = Bracket.values().lastOrNull { level >= it.levels.first } ?: Bracket.B3_50

    /** The player's bracket: by combat level, never below the highest bracket the account reached. */
    fun of(player: Player): Bracket {
        val byLevel = forCombatLevel(player.combatLevel)
        val reached = player.attr[REACHED]?.let { Bracket.values().getOrNull(it) } ?: return byLevel
        return if (reached.ordinal > byLevel.ordinal) reached else byLevel
    }

    fun sameBracket(
        a: Player,
        b: Player,
    ): Boolean = of(a) == of(b)

    /** Login: records the bracket without protection or message (an account's current bracket is not "new"). */
    fun recordAtLogin(player: Player) {
        val now = forCombatLevel(player.combatLevel).ordinal
        val reached = player.attr[REACHED]
        if (reached == null || now > reached) player.attr[REACHED] = now
    }

    /**
     * After a combat level-up: a bracket above the highest one reached is recorded, announced and gets its protection hour.
     * Returns the new bracket, or null when the bracket did not rise.
     */
    fun onCombatLevelRaised(player: Player): Bracket? {
        val now = forCombatLevel(player.combatLevel)
        val reached = player.attr[REACHED]
        if (reached != null && now.ordinal <= reached) return null
        player.attr[REACHED] = now.ordinal
        if (reached == null) return null
        player.message("<col=ef1020>You have entered the ${now.label} combat bracket.</col>")
        player.message("You can only fight players of this bracket now.")
        player.attr[PENDING_PROTECTION] = true
        if (!grantPendingProtection(player)) {
            player.message("Your protection hour starts once you are out of combat.")
            player.timers[PROTECTION_CHECK_TIMER] = CHECK_CYCLES
        }
        return now
    }

    fun inCombat(player: Player): Boolean {
        val last = player.attr[BankSecurity.LAST_COMBAT_CYCLE_ATTR] ?: return false
        return player.world.currentCycle - last < OUT_OF_COMBAT_CYCLES
    }

    /** Starts a waiting protection hour once the player is out of combat; true when it started. */
    fun grantPendingProtection(player: Player): Boolean {
        if (player.attr[PENDING_PROTECTION] != true) return false
        if (inCombat(player)) return false
        player.attr.remove(PENDING_PROTECTION)
        player.timers[NEW_PLAYER_PROTECTION_TIMER] = BeginnerProtection.PROTECTION_CYCLES
        player.attr[PROTECTION_FORFEITED_ATTR] = false
        player.message("<col=ef1020>New bracket: you have one hour of PvP protection.</col>")
        player.message("Nobody can attack you and you cannot attack players; attacking a player ends it.")
        return true
    }
}
