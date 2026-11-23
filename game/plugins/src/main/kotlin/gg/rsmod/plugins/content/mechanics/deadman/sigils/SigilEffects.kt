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
            // OSRS DMM video cMUhQb_j3dw 05:34.
            Sigil.BINDING -> player.message("The Sigil of Binding pulses with energy, ready to help you bind an opponent.")
            Sigil.SPECIALISED_STRIKES -> player.message("Specialised Strikes: your next special attack costs 10 less for ${sigil.windowSeconds} seconds.")
            Sigil.THE_PORCUPINE -> player.message("Porcupine: attackers are hurt by recoil for ${sigil.windowSeconds} seconds.")
            // OSRS DMM video HIisFATzF8c 01:54 + 10:02 (fixed chat; same green as the cooldown line in that video, which
            // video cMUhQb_j3dw shows green in the transparent chat).
            Sigil.FINALITY -> player.message("<col=006000>Your Sigil of Finality enables you to find a gap in your opponent's defences.</col>")
            else -> instant(player, sigil)
        }
    }

    /** Where the player's most recent teleport started (S45 Last Recall); set from the teleport gate (canTeleport) and Pawn.teleport. */
    private val LAST_TELEPORT_FROM = gg.rsmod.game.model.attr.AttributeKey<gg.rsmod.game.model.Tile>()

    /** A [type] teleport starts on the player's tile: remember it (Last Recall's own teleport is not a place to recall to). */
    fun recordTeleport(
        player: Player,
        type: gg.rsmod.plugins.content.magic.TeleportType,
    ) {
        if (type == gg.rsmod.plugins.content.magic.TeleportType.SIGIL_LAST_RECALL) return
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

    /**
     * S45: "teleport back to the location from which your most recent teleport occurred" - with the sigil's own OSRS teleport
     * (TeleportType.SIGIL_LAST_RECALL). The sigil is only activated (5-minute cooldown, activation sound) once the teleport really
     * starts: with nowhere to recall to, or a teleport that is refused (Tele Block, deep Wilderness, the Deadman countdown that is
     * interrupted), the cooldown is not spent. The item option calls this instead of the generic activation.
     */
    fun activateLastRecall(player: Player) {
        val sigil = Sigil.LAST_RECALL
        if (!Sigils.effective(player, sigil)) {
            player.message("This sigil is not attuned.")
            return
        }
        val left = Sigils.cooldownLeft(player, sigil)
        if (left > 0) {
            player.message("The ${sigil.label} will be ready in $left seconds.")
            return
        }
        val from = player.attr[LAST_TELEPORT_FROM]
        if (from == null) {
            player.message("You have not teleported yet, so there is nowhere to recall to.")
            return
        }
        val type = gg.rsmod.plugins.content.magic.TeleportType.SIGIL_LAST_RECALL
        player.canTeleport(type) {
            if (!Sigils.activate(player, sigil)) return@canTeleport
            SigilFx.activated(player, sigil)
            // exact: the player stood on that very tile when the earlier teleport started (no Landing correction to a tile nearby).
            player.teleport(from, type, exact = true)
        }
    }

    private fun recall(player: Player) = activateLastRecall(player)

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
            if (choice in 1..books.size) {
                val book = books[choice - 1]
                if (gg.rsmod.plugins.content.magic.Spellbooks.select(player, book)) {
                    // OSRS DMM chat line (video p6P1-FCaNvM 02:36 'standard', 03:04 'Ancient'): "Your Sigil of Versatility refreshes
                    // your memory of the <book> spellbook." - standard in lower case, the named books capitalised.
                    val name = if (book == gg.rsmod.plugins.api.Spellbook.STANDARD) "standard" else labels[choice - 1]
                    player.message("Your Sigil of Versatility refreshes your memory of the $name spellbook.")
                }
            }
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

    /** S63 Sigil of Slaughter: true when this Slayer task kill does NOT lower the task count (50%, OSRS Wiki); the XP is given anyway. */
    fun slaughterKeepsCount(player: Player): Boolean = Sigils.effective(player, Sigil.SLAUGHTER) && player.world.randomDouble() < 0.5
}
