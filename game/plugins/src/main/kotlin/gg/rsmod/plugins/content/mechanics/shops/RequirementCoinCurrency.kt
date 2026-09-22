package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * What an item of a [RequirementCoinCurrency] shop needs besides its coin price.
 *
 * @param check returns the refusal message when the player may not buy the item, or null when they may.
 * @param materials items consumed per unit bought (e.g. Ava's accumulator: 75 steel arrows).
 * @param bonusItems items handed over with each unit (e.g. the Max hood that comes with the Max cape).
 * @param maxPerPurchase caps one Buy-X click (a Max cape is bought one at a time).
 */
data class PurchaseRule(
    val check: (Player) -> String? = { null },
    val materials: List<Pair<Int, Int>> = emptyList(),
    val bonusItems: List<Int> = emptyList(),
    val maxPerPurchase: Int = Int.MAX_VALUE,
)

/**
 * A coin shop whose items can carry requirements, extra material costs and bundled items, so a service NPC that
 * really sells through a shop interface (Ava's devices, Max's cape) does not need a chat-dialogue purchase route.
 * Items without a rule behave exactly like [CoinCurrency].
 */
class RequirementCoinCurrency(
    private val rules: Map<Int, PurchaseRule>,
) : ItemCurrency(Items.COINS_995, singularCurrency = "coin", pluralCurrency = "coins") {
    override fun sellToPlayer(
        p: Player,
        shop: Shop,
        slot: Int,
        amt: Int,
    ) {
        val shopItem = shop.items[slot] ?: return
        val rule = rules[shopItem.item] ?: return super.sellToPlayer(p, shop, slot, amt)

        rule.check(p)?.let {
            p.message(it)
            return
        }
        var amount = minOf(amt, rule.maxPerPurchase)
        rule.materials.forEach { (item, perUnit) -> amount = minOf(amount, p.inventory.getItemCount(item) / perUnit) }
        if (amount <= 0) {
            val need = rule.materials.joinToString(" and ") { (item, perUnit) ->
                "$perUnit x ${p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).name.lowercase()}"
            }
            p.message("You also need $need for each one.")
            return
        }
        if (rule.bonusItems.isNotEmpty() && p.inventory.freeSlotCount < amount * (1 + rule.bonusItems.size)) {
            p.message("You don't have enough inventory space.")
            return
        }

        val before = p.inventory.getItemCount(shopItem.item)
        super.sellToPlayer(p, shop, slot, amount)
        val bought = p.inventory.getItemCount(shopItem.item) - before
        if (bought <= 0) return
        rule.materials.forEach { (item, perUnit) -> p.inventory.remove(item, perUnit * bought, assureFullRemoval = true) }
        rule.bonusItems.forEach { bonus -> p.inventory.add(bonus, bought) }
    }
}
