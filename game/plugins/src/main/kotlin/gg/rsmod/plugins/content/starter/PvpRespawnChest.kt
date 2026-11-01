package gg.rsmod.plugins.content.starter

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * N517 / N503 D9 (owner 2026-11-01 02:5x, literally: "na een pvp death deadmanchest met 78k coins en deadmanskull"): after a
 * PvP death the victim gets a Deadman chest - its own item ([ITEM], a clone of the Deadman starter pack 23881), not the
 * new-player pack (that one stays 1m coins + 100 house tablets + the skull, [NewPlayerStart]).
 *
 * Who gets one: a player killed by another player ([grant] is called by death.plugin.kts only for a resolved player killer;
 * an alt or staff kill (D10) and a guard kill (D7) have no killer there, Practice PvP and the safe minigames never reach it).
 * Abuse (dossier N517: two friends killing each other for 78k a death): at most one chest per [COOLDOWN_MS] per victim,
 * persisted, so a relog or a restart does not reset it. CLAUDE'S CHOICE (owner question collected in N517).
 *
 * Open: 78,000 coins and a Deadman's skull - the skull only while the player has none anywhere ([Possessions], also the bank
 * and the gravestone), so dying over and over never piles up skulls.
 */
object PvpRespawnChest {
    /** The Deadman chest (cache clone of 23881, ItemTransactionTool clone, owner 2026-11-01). */
    const val ITEM = Items.DEADMAN_CHEST

    const val COINS = 78_000

    /** One chest per hour per victim. */
    const val COOLDOWN_MS = 60L * 60L * 1000L

    /** When this player last received a chest (epoch ms). */
    val LAST_GRANTED = AttributeKey<Long>(persistenceKey = "pvp_respawn_chest_at")

    fun eligible(
        player: Player,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val last = player.attr[LAST_GRANTED] ?: return true
        return now - last >= COOLDOWN_MS || now < last
    }

    /** Hands the victim of a PvP death a Deadman chest (inventory, else bank). True when one was given. */
    fun grant(
        victim: Player,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!eligible(victim, now)) return false
        val given = victim.inventory.add(ITEM).hasSucceeded() || victim.bank.add(ITEM).hasSucceeded()
        if (!given) return false
        victim.attr[LAST_GRANTED] = now
        victim.message("<col=ef1020>You receive a Deadman chest to start again: open it for 78,000 coins and a Deadman's skull.")
        return true
    }

    /** What opening gives [player] right now: the coins, and the skull only when they have none anywhere. */
    fun contents(player: Player): List<Pair<Int, Int>> {
        val skull = gg.rsmod.plugins.content.mechanics.ownership.Possessions.owns(player, setOf(Items.DEADMANS_SKULL))
        return listOf(Items.COINS_995 to COINS) + if (skull) emptyList() else listOf(Items.DEADMANS_SKULL to 1)
    }

    /** "Open": the chest becomes its contents, all or nothing. */
    fun open(player: Player) {
        if (!player.inventory.contains(ITEM)) return
        val contents = contents(player)
        val slots =
            contents.count { (item, _) ->
                val def = player.world.definitions.get(ItemDef::class.java, item)
                !(def.stackable && player.inventory.contains(item))
            }
        if (player.inventory.freeSlotCount + 1 < slots) {
            player.message("You need $slots free inventory spaces to open this.")
            return
        }
        if (!player.inventory.remove(ITEM, assureFullRemoval = true).hasSucceeded()) return
        contents.forEach { (item, amount) -> player.inventory.add(item, amount) }
        if (contents.none { it.first == Items.DEADMANS_SKULL }) {
            player.message("You already have a Deadman's skull, so the chest only holds coins.")
        }
    }
}
