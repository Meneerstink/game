package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.*
import gg.rsmod.plugins.content.mechanics.pvp.DangerWarning

/** Wiki Minigame Teleport + owner25-11: free, 0XP, independent persistent20 minute cooldown.
 * The source catalogue is restricted to minigames present in this server. N409 Task List
 * has no approved/implemented catalogue in this baseline, so no Task List hook is invented.
 */
object MinigameTeleport {
    val TIMER = TimerKey(persistenceKey = "minigame_teleport_delay", tickOffline = true, resetOnDeath = false)
    const val COOLDOWN = 2000
    private val CAST = TimerKey()
    val SPELLS = listOf(SpellbookData.MINIGAME_TELEPORT, SpellbookData.ANCIENT_MINIGAME_TELEPORT, SpellbookData.LUNAR_MINIGAME_TELEPORT)
    data class Destination(val name: String, val tile: Tile, val combatLevel: Int = 0)
    // Existing entrances, also named by OSRS Wiki Minigame Teleport. No quest/diary gates.
    val DESTINATIONS = listOf(
        Destination("Castle Wars", Tile(2442, 3093)),
        Destination("Pest Control", Tile(2658, 2663), combatLevel = 40),
        Destination("Sorceress's Garden", Tile(3321, 3138)),
    )

    fun refusal(player: Player, spell: SpellbookData, destination: Destination? = null): String? {
        if (spell !in SPELLS) return MagicSpells.WRONG_SPELLBOOK
        MagicSpells.castProblem(player, spell.level, spell.runes, spell.uniqueId)?.let { return it }
        if (destination != null && player.combatLevel < destination.combatLevel) return "You need a combat level of ${destination.combatLevel} to teleport to ${destination.name}."
        if (player.timers.has(TIMER)) return "You need to wait another ${(player.timers[TIMER] + 99) / 100} minutes to cast this spell."
        // Wiki forbids ANY Wilderness, including levels1..20; shared gate still handles Tele Block/activities/Deadman.
        if (player.tile.getWildernessLevel() > 0) return "You can't use a minigame teleport in the Wilderness."
        if (gg.rsmod.plugins.content.areas.poh.PlayerHouse.roomAt(player.tile) != null) return "You can't use a minigame teleport from a player-owned house."
        return player.teleportRefusal(TeleportType.MODERN)?.firstOrNull()
    }

    fun cast(player: Player, spell: SpellbookData, destination: Destination) {
        if (destination !in DESTINATIONS || player.timers.has(CAST)) return
        val problem = refusal(player, spell, destination)
        if (problem != null) { player.message(problem); return }
        // All state is checked again after warning/dialogue/Deadman countdown and during the cast.
        if (DangerWarning.needsWarning(player, player.tile, destination.tile)) {
            player.queue(TaskPriority.STRONG) {
                if (DangerWarning.confirm?.invoke(this, DangerWarning.BODY_TEXT, destination.tile) == true) {
                    DangerWarning.allowNextCrossing(player)
                    start(player, spell, destination)
                }
            }
        } else start(player, spell, destination)
    }

    private fun start(player: Player, spell: SpellbookData, destination: Destination) {
        if (player.timers.has(CAST) || refusal(player, spell, destination) != null) return
        player.canTeleport(TeleportType.MODERN) {
            if (player.timers.has(CAST) || refusal(player, spell, destination) != null) return@canTeleport
            player.timers[CAST] = 1
            player.stopMovement()
            val start = player.tile
            player.queue(TaskPriority.STANDARD) {
                val complete = with(HomeTeleportSequence) {
                    play(defaultExtraTicks = 5, interruptOnAttack = true) {
                        player.tile == start && refusal(player, spell, destination) == null && player.teleportRefusal(TeleportType.MODERN) == null
                    }
                }
                player.animate(-1); player.graphic(-1)
                if (!complete) return@queue
                // No suspension between the final guards, cooldown and move. Double packets cannot teleport twice.
                if (refusal(player, spell, destination) != null || player.teleportRefusal(TeleportType.MODERN) != null) return@queue
                val landing = gg.rsmod.game.model.collision.Landing.resolve(player.world.collision, destination.tile)
                player.timers[TIMER] = COOLDOWN
                player.moveTo(landing)
                player.world.spawn(gg.rsmod.game.model.entity.AreaSound(landing, 200, 10, 1))
                SpellbookSwap.onSpellCast(player, spell.uniqueId)
            }
        }
    }
}
