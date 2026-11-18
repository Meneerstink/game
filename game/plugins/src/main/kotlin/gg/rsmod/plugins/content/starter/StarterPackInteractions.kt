package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.items.food.Food
import gg.rsmod.plugins.content.items.food.Foods
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanInventory
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids
import gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions
import gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction

/** Starter/Chronicle wiki revisions and Transcript:Chronicle 14828423; presentation is supplied by Claude. */
object StarterPackInteractions {
    val DESTINATION = Tile(3201, 3355)
    /**
     * Claude 18-11 (lead, advice chosen - GPT-A 7.2 left it null): four OSRS recordings (PHASE7-SOURCES 7.2) show the ordinary standard
     * teleport pose (arms out, purple orb, shrink) for the Chronicle; the montages hide the exact ticks. So the Chronicle uses OUR standard
     * spellbook teleport (MODERN: same look, delay, Tele Block and Deadman gates as every standard teleport here).
     */
    @Volatile var chronicleAppearance: TeleportType? = TeleportType.MODERN
    enum class Result { OK, NOT_READY, ITEM_CHANGED, NO_CHARGES, WILDERNESS, FULL, NO_CARDS, BLOCKED }

    fun charges(player: Player): Int = (player.attr[DeadmanStarterPack.CHRONICLE_CHARGES] ?: 0).coerceIn(0, 1000)
    fun checkMessage(player: Player): String = when (val amount = charges(player)) {
        0 -> "Your book has run out of charges."
        1 -> "You have one charge left in your book."
        else -> "Your book has $amount charges left."
    }

    /** Same food, heal and delays as ordinary tuna; only the physical starter item is consumed. */
    fun eatTuna(player: Player, slot: Int, expectedId: Int): Result {
        val id = DeadmanStarterPack.ids?.tuna ?: return Result.NOT_READY
        val held = player.inventory.rawItems.getOrNull(slot) ?: return Result.ITEM_CHANGED
        if (held.id != id || expectedId != id || held.amount != 1 || held.getDef(player.world.definitions).noted) return Result.ITEM_CHANGED
        if (player.isDead() || !Foods.canEat(player, Food.TUNA) || ActivityRestrictions.refuse(player, RestrictedAction.EAT)) return Result.BLOCKED
        player.inventory[slot] = null
        Foods.eat(player, Food.TUNA)
        return Result.OK
    }

    /** The book's charge account survives loss/replacement. Cards never create a separate per-item balance. */
    fun charge(player: Player, bookSlot: Int, expectedId: Int): Result {
        val id = DeadmanStarterPack.ids?.chronicle ?: return Result.NOT_READY
        val cards = Dmm2Ids.ItemId.TELEPORT_CARD.local ?: return Result.NOT_READY
        val held = player.inventory.rawItems.getOrNull(bookSlot) ?: return Result.ITEM_CHANGED
        if (expectedId != id || held.id != id || held.amount != 1) return Result.ITEM_CHANGED
        val amount = minOf(1000 - charges(player), player.inventory.getItemCount(cards))
        if (charges(player) == 1000) return Result.FULL
        if (amount <= 0) return Result.NO_CARDS
        val staged = ItemContainer(player.inventory)
        if (!staged.remove(cards, amount).hasSucceeded()) return Result.NO_CARDS
        DeadmanInventory.publish(staged, player.inventory)
        player.attr[DeadmanStarterPack.CHRONICLE_CHARGES] = charges(player) + amount
        player.persistNow()
        player.message(if (amount == 1) "You add a single charge to your book. It now has ${if (charges(player) == 1) "one charge" else "${charges(player)} charges"}."
            else "You add $amount charges to your book. It now has ${charges(player)} charges.")
        return Result.OK
    }

    /** Rechecked after the shared Deadman countdown; a moved/replaced book cannot buy a teleport. */
    fun spend(player: Player, used: Item, slot: Int, equipped: Boolean): Result {
        if (player.isDead()) return Result.BLOCKED
        val id = DeadmanStarterPack.ids?.chronicle ?: return Result.NOT_READY
        val container = if (equipped) player.equipment else player.inventory
        if (used.id != id || used.amount != 1 || container.rawItems.getOrNull(slot) !== used) return Result.ITEM_CHANGED
        if (player.tile.getWildernessLevel() > 0) return Result.WILDERNESS
        if (charges(player) == 0) return Result.NO_CHARGES
        player.attr[DeadmanStarterPack.CHRONICLE_CHARGES] = charges(player) - 1
        player.persistNow()
        return Result.OK
    }

    fun travel(player: Player, used: Item, slot: Int, equipped: Boolean): Result {
        if (player.tile.getWildernessLevel() > 0) return Result.WILDERNESS
        if (charges(player) == 0) {
            player.message("Your book does not have any charges. Purchase some Teleport Cards from Diango.")
            return Result.NO_CHARGES
        }
        val appearance = chronicleAppearance ?: return Result.NOT_READY
        player.canTeleport(appearance) {
            if (spend(player, used, slot, equipped) == Result.OK) {
                player.teleport(DESTINATION, appearance)
                player.message("<col=ff0000>${when (val amount = charges(player)) {
                    0 -> "Your book has run out of charges."
                    1 -> "You have one charge left in your book."
                    else -> "You have $amount charges left in your book."
                }}</col>")
            }
        }
        return Result.OK
    }
}
