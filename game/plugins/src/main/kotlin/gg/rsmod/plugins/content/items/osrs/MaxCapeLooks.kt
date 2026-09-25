package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.*

/**
 * Max cape "Customise" look perk (owner 2026-09-19, monetization advice accepted): the max cape variants stay free to make by
 * combining (OSRS rule, [MaxCapes]); what the 78 Store sells is a LOOK-only unlock. With a variant's look unlocked the plain max
 * cape can be drawn as that variant through the cache's own max cape "Customise" option - stats, perks and the equipment stay
 * the plain max cape - and only while the player still owns the variant's real component (inventory, worn or bank).
 */
object MaxCapeLooks {
    /** Unlocked variant capes, comma separated (persistent). */
    val UNLOCKED_ATTR = AttributeKey<String>(persistenceKey = "max_cape_looks_unlocked")

    /** The chosen variant cape id, 0 = the plain max cape (persistent). */
    val SELECTED_ATTR = AttributeKey<Int>(persistenceKey = "max_cape_look")

    fun unlocked(player: Player): Set<Int> =
        player.attr[UNLOCKED_ATTR]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()

    fun unlock(
        player: Player,
        variantCape: Int,
    ): Boolean {
        if (MaxCapes.forCape(variantCape) == null) return false
        val set = unlocked(player)
        if (variantCape in set) return false
        player.attr[UNLOCKED_ATTR] = (set + variantCape).joinToString(",")
        return true
    }

    fun ownsComponent(
        player: Player,
        variant: MaxCapes.Variant,
    ): Boolean =
        player.inventory.contains(variant.component) || player.bank.contains(variant.component) ||
            player.equipment.contains(variant.component)

    /** The cape id the appearance block draws for the worn [itemId] (plain max cape only). */
    fun shownCape(
        player: Player,
        itemId: Int,
    ): Int {
        if (itemId != MaxCapes.MAX_CAPE) return itemId
        val selected = player.attr[SELECTED_ATTR] ?: return itemId
        if (selected !in unlocked(player)) return itemId
        val variant = MaxCapes.forCape(selected) ?: return itemId
        return if (ownsComponent(player, variant)) variant.cape else itemId
    }

    fun select(
        player: Player,
        variantCape: Int,
    ) {
        player.attr[SELECTED_ATTR] = variantCape
        player.addBlock(gg.rsmod.game.sync.block.UpdateBlockType.APPEARANCE)
    }

    fun wearingMaxCape(player: Player): Boolean = player.getEquipment(EquipmentType.CAPE)?.id == MaxCapes.MAX_CAPE

    /** The look chooser, reached from the max cape's "Features" menu. */
    fun customise(player: Player) {
        val choices = unlocked(player).mapNotNull { MaxCapes.forCape(it) }.filter { ownsComponent(player, it) }
        if (choices.isEmpty()) {
            player.message("You have no max cape looks unlocked. Unlock them in the 78 Store; you need to own the matching item.")
            return
        }
        val names =
            listOf("Plain max cape") + choices.map { player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, it.cape).name }
        player.queue {
            // 667 option menus hold five lines: show four looks + "More..." per page until the rest fits.
            var start = 0
            var index = -1
            while (index < 0) {
                val remaining = names.size - start
                if (remaining <= 5) {
                    index = start + options(*names.drop(start).toTypedArray(), title = "Choose your max cape look") - 1
                } else {
                    val picked = options(*(names.subList(start, start + 4) + "More...").toTypedArray(), title = "Choose your max cape look")
                    if (picked == 5) start += 4 else index = start + picked - 1
                }
            }
            if (index == 0) {
                select(player, 0)
                player.message("Your max cape shows its own colours again.")
            } else {
                val variant = choices.getOrNull(index - 1) ?: return@queue
                select(player, variant.cape)
                player.message("Your max cape now looks like the ${names[index].lowercase()}.")
            }
        }
    }
}
