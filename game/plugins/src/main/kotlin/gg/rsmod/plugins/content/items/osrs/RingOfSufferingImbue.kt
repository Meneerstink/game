package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.format
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.store.StoreUi

/**
 * A11 (owner 2026-10-11 answer A: "Imbue-optie via een NPC (Perdu/Quartermaster), charges blijven"; VRAGEN 12 answered 2026-10-17:
 * "600 Deadman Points, alleen Ring of suffering en (r)"): use a Ring of suffering or Ring of suffering (r) on Perdu and she imbues it,
 * keeping its recoil charges and its recoil setting (OSRS: the imbue is applied to the ring handed in; Wiki "Ring of suffering (i)").
 *
 * Donor: Darkan SoulwarsRewards.kt:126-162 (onItemOnNpc of the Soul Wars imbue npc): "I can imbue that ring for you for <cost> Zeal. Is
 * that alright with you?", options "Yes, please imbue my ring." / "Nevermind.", the cost re-checked and taken at Yes ("You don't have
 * enough Zeal."), then the SAME item gets the imbued id. ADAPTED: the currency is Deadman Points and the price and ring pairs are the
 * 78 Store "Imbue services" entries (one price for both routes); the inventory slot is re-read at Yes, so a ring that left the slot
 * while she asked is never imbued nor paid for; the success line is not sourced (Darkan says nothing).
 */
object RingOfSufferingImbue {
    /** The 78 Store "Imbue services" entry for each ring Perdu imbues (owner: only the Ring of suffering and its (r)). */
    private val RINGS = listOf(Items.RING_OF_SUFFERING, Items.RING_OF_SUFFERING_R)

    val ENTRIES: Map<Int, StoreCatalogue.Entry> by lazy {
        RINGS.associateWith { ring ->
            StoreCatalogue.ENTRIES.single { it.kind == StoreCatalogue.Kind.UPGRADE && it.requiredBaseItems == listOf(ring) }
        }
    }

    val IMBUABLE: Set<Int> get() = RINGS.toSet()

    enum class Result { IMBUED, NOT_ENOUGH_POINTS, RING_GONE }

    /** Imbues the ring [ringId] in inventory [slot]: pays the entry's price once and keeps everything stored on the ring. */
    fun imbue(
        player: Player,
        slot: Int,
        ringId: Int,
    ): Result {
        val entry = ENTRIES[ringId] ?: return Result.RING_GONE
        val ring = player.inventory[slot]?.takeIf { it.id == ringId } ?: return Result.RING_GONE
        val currency = entry.currency
        val balance = player.attr[currency.attr] ?: 0
        if (balance < entry.price) return Result.NOT_ENOUGH_POINTS
        // N106: pay through the one balance helper first (never below 0), then exchange the ring.
        if (!gg.rsmod.game.model.attr.PointBalance.take(player.attr, currency.attr, entry.price)) return Result.NOT_ENOUGH_POINTS
        player.inventory[slot] = RingOfSuffering.exchanged(ring, entry.resultItems.single())
        StoreUi.audit(player, entry, 1, entry.price)
        player.playSound(gg.rsmod.plugins.content.mechanics.shops.ShopSounds.TRANSACTION)
        return Result.IMBUED
    }

    /** Perdu's dialogue after the player used the ring in [slot] on her. */
    suspend fun dialogue(
        task: QueueTask,
        slot: Int,
        ringId: Int,
    ) {
        val player = task.player
        val entry = ENTRIES[ringId] ?: return
        val price = "${entry.price.format()} ${entry.currency.plural}"
        task.chatNpc("I can imbue that ring for you for $price. Is that alright with you?")
        if (task.options("Yes, please imbue my ring.", "Nevermind.") != 1) return
        when (imbue(player, slot, ringId)) {
            Result.IMBUED -> player.message("Perdu imbues your ${name(player, ringId).lowercase()} for $price.")
            Result.NOT_ENOUGH_POINTS -> task.chatNpc("You don't have enough ${entry.currency.plural}.")
            Result.RING_GONE -> {
                Trace.problem(Trace.Cat.ITEM) { "perdu-imbue ${player.username}: ring $ringId left slot $slot before Yes - nothing imbued, nothing paid" }
                player.message("You need a ${name(player, ringId)} in your inventory for this imbue.")
            }
        }
    }

    private fun name(
        player: Player,
        id: Int,
    ) = player.world.definitions.getNullable(ItemDef::class.java, id)?.name ?: "ring"
}
