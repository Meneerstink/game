package gg.rsmod.plugins.content.activity.stealing_creation

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * Q-053 Stealing Creation - lobby/team-balance vertical slice. See
 * `StealingCreationLobbyData.kt` for the full sourcing note and the disclosed instance-manager
 * limit that keeps this batch scoped to the pre-game lobby only (the real gameplay arena is a
 * per-match procedurally-generated map in the donor, which this engine has no manager for).
 *
 * Ported from Novite's `StealingCreationLobby.java` (team join/leave/balance-check/interface
 * update logic - directly portable, no cache dependency) and the entry half of
 * `StealingCreationLobbyController.java` (the stile climb-over transition).
 */
object StealingCreationLobbyHandler {
    private val redTeam = mutableListOf<Player>()
    private val blueTeam = mutableListOf<Player>()
    private var countdownTicks: Int? = null

    private fun totalLevel(
        ids: IntArray,
        team: List<Player>,
    ): Int = team.sumOf { p -> ids.sumOf { p.skills.getMaxLevel(it) } }

    private fun canEnter(
        player: Player,
        joiningRed: Boolean,
    ): Boolean {
        val skillTotal = totalLevel(StealingCreationLobbyData.TOTAL_SKILL_IDS, if (joiningRed) redTeam else blueTeam)
        val combatTotal = totalLevel(StealingCreationLobbyData.TOTAL_COMBAT_IDS, if (joiningRed) redTeam else blueTeam)
        val otherSkillTotal = totalLevel(StealingCreationLobbyData.TOTAL_SKILL_IDS, if (joiningRed) blueTeam else redTeam)
        val otherCombatTotal = totalLevel(StealingCreationLobbyData.TOTAL_COMBAT_IDS, if (joiningRed) blueTeam else redTeam)
        if ((skillTotal + combatTotal) > (otherSkillTotal + otherCombatTotal)) {
            player.message("This team is too strong for you to join at present.")
            return false
        }
        // Disclosed simplification: Novite also checks wearingArmour()/familiar/pet/betrayal-
        // penalty here. This batch verifies only the two checks with a clean, generic API
        // (empty inventory/equipment) - familiar/pet/penalty-timer checks are a named remaining
        // gap, not guessed at with an unverified API.
        if (player.inventory.filterNotNull().isNotEmpty() || player.equipment.filterNotNull().isNotEmpty()) {
            player.message("You may not take any items into Stealing Creation. Bank everything first.")
            return false
        }
        return true
    }

    fun join(
        player: Player,
        joinRed: Boolean,
    ) {
        if (redTeam.contains(player) || blueTeam.contains(player)) {
            leave(player)
            return
        }
        if (!canEnter(player, joinRed)) {
            return
        }
        if (joinRed) redTeam.add(player) else blueTeam.add(player)
        player.moveTo(if (joinRed) StealingCreationLobbyData.RED_WAITING_TILE else StealingCreationLobbyData.BLUE_WAITING_TILE)
        if (redTeam.size >= StealingCreationLobbyData.MIN_TEAM_SIZE && blueTeam.size >= StealingCreationLobbyData.MIN_TEAM_SIZE) {
            if (countdownTicks == null) countdownTicks = StealingCreationLobbyData.LOBBY_COUNTDOWN_STEPS
        }
        updateInterfaces()
    }

    fun leave(player: Player) {
        val wasQueued = redTeam.remove(player) || blueTeam.remove(player)
        if (!wasQueued) return
        if (redTeam.size < StealingCreationLobbyData.MIN_TEAM_SIZE || blueTeam.size < StealingCreationLobbyData.MIN_TEAM_SIZE) {
            countdownTicks = null
        }
        player.setComponentHidden(StealingCreationLobbyData.LOBBY_INTERFACE, 2, true)
        player.moveTo(StealingCreationLobbyData.EXIT_TILE)
        updateInterfaces()
    }

    /** Called every tick by the world timer. */
    fun tick() {
        val ticks = countdownTicks ?: return
        if (ticks <= 0) {
            // Real architecture limit, not a stub: the actual match requires a per-game
            // procedurally-generated arena (Novite's GameArea.create()), which this engine has
            // no instance/zone manager for - the same documented gap as Dungeoneering/
            // Construction/Clan-Wars-full. Rather than silently doing nothing or inventing a
            // fake arena, tell the queued players plainly and reset the lobby.
            (redTeam + blueTeam).forEach {
                it.message("Stealing Creation's arena isn't available on this server yet - your queue has been reset.")
            }
            redTeam.clear()
            blueTeam.clear()
            countdownTicks = null
            return
        }
        countdownTicks = ticks - 1
        updateInterfaces()
    }

    private fun updateInterfaces() {
        for (player in redTeam) updateTeamInterface(player, inRedTeam = true)
        for (player in blueTeam) updateTeamInterface(player, inRedTeam = false)
    }

    private fun updateTeamInterface(
        player: Player,
        inRedTeam: Boolean,
    ) {
        val ownTeam = if (inRedTeam) redTeam else blueTeam
        val otherTeam = if (inRedTeam) blueTeam else redTeam
        val skillTotal = totalLevel(StealingCreationLobbyData.TOTAL_SKILL_IDS, ownTeam)
        val combatTotal = totalLevel(StealingCreationLobbyData.TOTAL_COMBAT_IDS, ownTeam)
        val otherSkillTotal = totalLevel(StealingCreationLobbyData.TOTAL_SKILL_IDS, otherTeam)
        val otherCombatTotal = totalLevel(StealingCreationLobbyData.TOTAL_COMBAT_IDS, otherTeam)
        val ticks = countdownTicks
        if (ticks != null) {
            player.setComponentHidden(StealingCreationLobbyData.LOBBY_INTERFACE, 2, true)
            player.setComponentText(StealingCreationLobbyData.LOBBY_INTERFACE, 1, "Game Start : $ticks mins")
        } else {
            player.setComponentHidden(StealingCreationLobbyData.LOBBY_INTERFACE, 2, false)
            val ownNeeded = (StealingCreationLobbyData.MIN_TEAM_SIZE - ownTeam.size).coerceAtLeast(0)
            val otherNeeded = (StealingCreationLobbyData.MIN_TEAM_SIZE - otherTeam.size).coerceAtLeast(0)
            player.setComponentText(StealingCreationLobbyData.LOBBY_INTERFACE, 34, ownNeeded.toString())
            player.setComponentText(StealingCreationLobbyData.LOBBY_INTERFACE, 33, otherNeeded.toString())
        }
        player.setComponentText(StealingCreationLobbyData.LOBBY_INTERFACE, 4, skillTotal.toString())
        player.setComponentText(StealingCreationLobbyData.LOBBY_INTERFACE, 5, combatTotal.toString())
        player.setComponentText(StealingCreationLobbyData.LOBBY_INTERFACE, 6, otherCombatTotal.toString())
        player.setComponentText(StealingCreationLobbyData.LOBBY_INTERFACE, 7, otherSkillTotal.toString())
    }
}
