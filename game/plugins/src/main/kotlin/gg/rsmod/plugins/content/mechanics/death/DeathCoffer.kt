package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.DEATH_COFFER_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item

/** The outcome of sacrificing items to Death's Coffer. */
sealed class CofferOutcome {
    /** "You cannot trade that item to Death." */
    object Ineligible : CofferOutcome()

    /** "The coffer is too full to accommodate that." */
    object Full : CofferOutcome()

    object NothingSelected : CofferOutcome()

    data class Sacrificed(val item: Item, val coins: Long) : CofferOutcome()
}

/**
 * Death's Coffer (OSRS Wiki "Death's Coffer", loc 39550 "Sacrifice"): items worth 10,000 coins or more each are sacrificed
 * for 105% of their Grand Exchange price. The coins can only pay reclamation fees ([DeathPayment]) and never come back.
 * Ineligible: items worth 9,999 or less, untradeable items and tradeable items that cannot be offered on the Grand
 * Exchange ([gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.exchangeable]). The coffer holds at most
 * 2,147,483,647 coins.
 */
object DeathCoffer {
    const val CANNOT_TRADE = "You cannot trade that item to Death."
    const val TOO_FULL = "The coffer is too full to accommodate that."

    fun balance(player: Player): Int = player.attr[DEATH_COFFER_ATTR] ?: 0

    /** The unnoted definition an item is valued and judged by. */
    private fun baseDef(
        definitions: DefinitionSet,
        itemId: Int,
    ): ItemDef? {
        val def = definitions.getNullable(ItemDef::class.java, itemId) ?: return null
        return if (def.noted) definitions.getNullable(ItemDef::class.java, def.noteLinkId) else def
    }

    fun eligible(
        definitions: DefinitionSet,
        itemId: Int,
        value: ItemRiskValueProvider,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): Boolean {
        val def = baseDef(definitions, itemId) ?: return false
        if (!gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.exchangeable(def)) return false
        return value.getValue(def.id) >= config.cofferMinItemValue
    }

    /** What one unit adds to the coffer: 105% of its Grand Exchange value (the coffer screen's "x coins"). */
    fun unitOffer(
        definitions: DefinitionSet,
        itemId: Int,
        value: ItemRiskValueProvider,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): Long {
        val def = baseDef(definitions, itemId) ?: return 0L
        return value.getValue(def.id) * config.cofferValuePercent / 100L
    }

    /** Sacrifices up to [amount] of the item in inventory [slot]. Nothing changes unless every unit fits the coffer. */
    fun sacrifice(
        player: Player,
        slot: Int,
        amount: Int,
        value: ItemRiskValueProvider,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): CofferOutcome {
        val definitions = player.world.definitions
        val item = player.inventory[slot] ?: return CofferOutcome.NothingSelected
        if (amount <= 0) return CofferOutcome.NothingSelected
        if (!eligible(definitions, item.id, value, config)) return CofferOutcome.Ineligible
        val units = minOf(amount, player.inventory.getItemCount(item.id))
        val coins = unitOffer(definitions, item.id, value, config) * units
        if (balance(player).toLong() + coins > config.cofferMaxTotal) return CofferOutcome.Full
        val removed = player.inventory.remove(item.id, units, assureFullRemoval = true, beginSlot = slot).completed
        if (removed <= 0) return CofferOutcome.NothingSelected
        val paid = unitOffer(definitions, item.id, value, config) * removed
        player.attr[DEATH_COFFER_ATTR] = (balance(player) + paid).toInt()
        return CofferOutcome.Sacrificed(Item(item.id, removed), paid)
    }
}
