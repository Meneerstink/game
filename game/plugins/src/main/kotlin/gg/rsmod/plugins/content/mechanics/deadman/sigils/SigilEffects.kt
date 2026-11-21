package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * PROJECT48 SIGILS - what activating a sigil does at once. The windowed sigils (Binding, Specialised Strikes, Porcupine, Finality) only
 * open their window here (Sigils.activate did that); their effect is read by the combat hooks ([SigilCombat]) while the window is open.
 * The instant sigils (Last Recall, Versatility, Serpent) are filled in by their own rows (S45, S48, S55).
 */
object SigilEffects {
    fun activated(
        player: Player,
        sigil: Sigil,
    ) {
        when (sigil) {
            Sigil.BINDING -> player.message("Binding: your next binding spell within ${sigil.windowSeconds} seconds is much more accurate.")
            Sigil.SPECIALISED_STRIKES -> player.message("Specialised Strikes: your next special attack costs 10 less for ${sigil.windowSeconds} seconds.")
            Sigil.THE_PORCUPINE -> player.message("Porcupine: attackers are hurt by recoil for ${sigil.windowSeconds} seconds.")
            Sigil.FINALITY -> player.message("Finality: your next special attack within ${sigil.windowSeconds} seconds cannot miss.")
            else -> instant(player, sigil)
        }
    }

    /** Where the player's most recent teleport started (S45 Last Recall); set from Pawn.teleport. */
    private val LAST_TELEPORT_FROM = gg.rsmod.game.model.attr.AttributeKey<gg.rsmod.game.model.Tile>()
    private val SKIP_RECORD = gg.rsmod.game.model.attr.AttributeKey<Boolean>()

    /** Called by Pawn.teleport when a teleport starts: remembers the tile it started on (not for Last Recall's own teleport). */
    fun recordTeleport(player: Player) {
        if (player.attr[SKIP_RECORD] == true) {
            player.attr.remove(SKIP_RECORD)
            return
        }
        player.attr[LAST_TELEPORT_FROM] = player.tile
    }

    /** Instant sigils: S45 Last Recall, S48 Versatility, S55 Serpent. */
    private fun instant(
        player: Player,
        sigil: Sigil,
    ) {
        when (sigil) {
            Sigil.LAST_RECALL -> recall(player)
            Sigil.VERSATILITY -> versatility(player)
            Sigil.THE_SERPENT -> serpent(player)
            else -> player.message("${sigil.label} is not ready yet.")
        }
    }

    /** S45: "teleport back to the location from which your most recent teleport occurred". */
    private fun recall(player: Player) {
        val from = player.attr[LAST_TELEPORT_FROM]
        if (from == null) {
            player.message("You have not teleported yet, so there is nowhere to recall to.")
            return
        }
        player.canTeleport(gg.rsmod.plugins.content.magic.TeleportType.MODERN) {
            player.attr[SKIP_RECORD] = true
            player.teleport(from, gg.rsmod.plugins.content.magic.TeleportType.MODERN)
        }
    }

    /** S48: "instantly swap your Spellbook to any unlocked Spellbook while outside of combat". */
    private fun versatility(player: Player) {
        if (player.getCombatTarget() != null || player.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) {
            player.message("You cannot swap your spellbook while in combat.")
            return
        }
        val current = player.getSpellbook()
        val books = gg.rsmod.plugins.api.Spellbook.values.filter { it != current && gg.rsmod.plugins.content.magic.Spellbooks.hasAccess(player, it) }
        if (books.isEmpty()) {
            player.message("You have no other spellbook unlocked.")
            return
        }
        player.queue {
            val labels = books.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
            val choice = options(*labels.toTypedArray(), title = "Swap to which spellbook?")
            if (choice in 1..books.size) gg.rsmod.plugins.content.magic.Spellbooks.select(player, books[choice - 1])
        }
    }

    /** S55: "cure venom and poison, as well as gain immunity from both effects for 10 minutes". */
    private fun serpent(player: Player) {
        gg.rsmod.plugins.content.mechanics.poison.Poison.cure(player)
        gg.rsmod.plugins.content.mechanics.poison.Venom.cure(player, immunityTicks = IMMUNITY_TICKS, announce = false)
        player.timers[gg.rsmod.game.model.timer.POISON_IMMUNITY] = IMMUNITY_TICKS
        player.timers[gg.rsmod.game.model.timer.VENOM_IMMUNITY] = IMMUNITY_TICKS
        player.message("The serpent sigil cures you and shields you from poison and venom for 10 minutes.")
    }

    private const val IMMUNITY_TICKS = 1000
}
