package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.refreshBonuses

/**
 * Abyssal tentacle charges (degradation audit 2026-10-04 section 6; OSRS Wiki "Abyssal tentacle"):
 *  - "The abyssal tentacle can be created by using a kraken tentacle on an abyssal whip. Once created, the weapon will degrade after
 *    10,000 attacks, including misses. Afterwards the whip is consumed, leaving only the tentacle."
 *  - "Players can combine the charges of abyssal tentacles, up to a maximum of 20,000 charges. This action will destroy one abyssal
 *    tentacle."
 *  - "If the abyssal tentacle is not protected upon death, it will fully degrade into a kraken tentacle."
 *  - Destroy: "Dissolve - This will dissolve your whip immediately. The whip will be permanently lost."
 * The charges live in [ItemAttribute.CHARGES]; a tentacle without the attribute (made before this rule) holds the full 10,000.
 * ADAPTED: OSRS leaves the kraken tentacle in the weapon slot ("it doesn't appear in the player's hands"); here it goes to the
 * backpack, or to the floor under the player when the backpack is full. The message wording is not sourced.
 */
object AbyssalTentacle {
    const val CREATED_CHARGES = 10_000
    const val MAX_CHARGES = 20_000

    fun isTentacle(itemId: Int?): Boolean = itemId == Items.ABYSSAL_TENTACLE

    fun charges(item: Item): Int = if (isTentacle(item.id)) item.attr[ItemAttribute.CHARGES] ?: CREATED_CHARGES else 0

    fun withCharges(
        item: Item,
        charges: Int,
    ): Item = Item(Items.ABYSSAL_TENTACLE, 1).copyAttr(item).also { it.attr[ItemAttribute.CHARGES] = charges.coerceIn(0, MAX_CHARGES) }

    fun created(): Item = Item(Items.ABYSSAL_TENTACLE, 1).also { it.attr[ItemAttribute.CHARGES] = CREATED_CHARGES }

    /** "Check": the charges left. */
    fun checkMessage(item: Item): String = "Your abyssal tentacle can perform ${"%,d".format(charges(item))} more attacks."

    /**
     * "If the abyssal tentacle is not protected upon death, it will fully degrade into a kraken tentacle": every lost tentacle of
     * [result] becomes a kraken tentacle in its slot before the death is split, so the kraken tentacle then follows the normal
     * route of a tradeable item (the gravestone on a PvM death, the killer on a PvP death). Protected tentacles are untouched.
     */
    fun degradeLost(result: gg.rsmod.plugins.content.mechanics.death.DeathResolutionResult): gg.rsmod.plugins.content.mechanics.death.DeathResolutionResult {
        if (result.itemRisk.lost.none { isTentacle(it.item.id) }) return result
        val victim = result.victim
        val lost =
            result.itemRisk.lost.map { slotItem ->
                if (!isTentacle(slotItem.item.id)) return@map slotItem
                val container =
                    when (slotItem.source) {
                        gg.rsmod.plugins.content.mechanics.death.DeathContainerSource.INVENTORY -> victim.inventory
                        gg.rsmod.plugins.content.mechanics.death.DeathContainerSource.EQUIPMENT -> victim.equipment
                    }
                if (!isTentacle(container[slotItem.slot]?.id)) return@map slotItem
                val kraken = Item(Items.KRAKEN_TENTACLE, 1)
                container[slotItem.slot] = kraken
                slotItem.copy(item = kraken)
            }
        return result.copy(itemRisk = result.itemRisk.copy(lost = lost))
    }

    /** One charge per attack, hit or miss ("10,000 attacks, including misses"); at 0 the whip is consumed. */
    fun afterAttack(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON)?.takeIf { isTentacle(it.id) } ?: return
        val left = charges(weapon) - 1
        if (left > 0) {
            player.equipment[EquipmentType.WEAPON.id] = withCharges(weapon, left)
            return
        }
        player.equipment[EquipmentType.WEAPON.id] = null
        player.refreshBonuses()
        player.message("<col=ff0000>Your abyssal tentacle has degraded; only the kraken tentacle is left.")
        if (!player.inventory.add(Items.KRAKEN_TENTACLE, 1).hasSucceeded()) {
            player.world.spawn(gg.rsmod.game.model.entity.GroundItem(Items.KRAKEN_TENTACLE, 1, player.tile, player))
        }
    }
}
