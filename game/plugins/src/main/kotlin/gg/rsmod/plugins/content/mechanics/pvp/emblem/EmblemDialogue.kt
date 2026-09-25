package gg.rsmod.plugins.content.mechanics.pvp.emblem

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FIRST_OPTION
import gg.rsmod.plugins.api.cfg.SECOND_OPTION
import gg.rsmod.plugins.api.ext.*

/** Emblem Trader cash-out and the emblem's "Inspect" text (the Emblem Trader is the Deadman Store npc, `StoreNpcs`). */
object EmblemDialogue {
    /** Deadman Points or a tier-scaled XP lamp for the emblem; the carried one first, else the banked one. */
    suspend fun QueueTask.cashOut(npc: Int) {
        val player = player
        val tier = DeadmanEmblem.holdings(player).sortedWith(compareBy<DeadmanEmblem.Held> { it.inBank }.thenByDescending { it.tier }).firstOrNull()?.tier ?: 0
        if (tier == 0) {
            chatNpc("You have no emblem to cash in. Take one from a strong monster, then make it grow in the fight.", npc = npc)
            return
        }
        val points = DeadmanEmblem.points(tier)
        val xp = DeadmanEmblem.lampXp(tier)
        chatNpc(
            "A tier $tier emblem. I'll give you ${fmt(points)} Deadman Points for it, or a lamp worth ${fmt(xp)} experience.",
            npc = npc,
        )
        when (options("${fmt(points)} Deadman Points.", "A Deadman lamp (${fmt(xp)} XP).", "I'll keep risking it.", title = "Cash in your tier $tier emblem?")) {
            FIRST_OPTION -> if (DeadmanEmblem.cashOut(player, DeadmanEmblem.CashOut.POINTS)) chatNpc("Spend them well.", npc = npc)
            SECOND_OPTION -> if (DeadmanEmblem.cashOut(player, DeadmanEmblem.CashOut.LAMP)) chatNpc("Rub it when you're ready.", npc = npc)
        }
    }

    fun inspect(player: Player, tier: Int) {
        player.message("<col=ef1020>Deadman emblem - tier $tier of ${DeadmanEmblem.MAX_TIER}.")
        player.message("Worth ${fmt(DeadmanEmblem.points(tier))} Deadman Points or a ${fmt(DeadmanEmblem.lampXp(tier))} XP lamp at the Emblem Trader.")
        if (tier < DeadmanEmblem.MAX_TIER) {
            player.message(
                "Next: a valid kill while you carry it makes it tier ${tier + 1} (${fmt(DeadmanEmblem.points(tier + 1))} points). " +
                    "Your victim must risk at least 1M.",
            )
        } else {
            player.message("It cannot grow any stronger.")
        }
        player.message("It is always lost when you die to another player - Protect Item cannot save it.")
    }
}
