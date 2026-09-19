package gg.rsmod.plugins.content.items.osrs

/**
 * Max cape "Customise" (cache option on the 667 max cape, inventory op 3 and worn op 1): choose which unlocked variant look the
 * plain max cape shows (rules in [MaxCapeLooks]). Look-only; the variants' stats need the real combined cape.
 */

gg.rsmod.game.model.entity.Player.appearanceItemOverride = { player, slot, itemId ->
    if (slot == EquipmentType.CAPE.id) MaxCapeLooks.shownCape(player, itemId) else itemId
}

fun customise(player: Player) {
    val choices =
        MaxCapeLooks.unlocked(player).mapNotNull { MaxCapes.forCape(it) }
            .filter { MaxCapeLooks.ownsComponent(player, it) }
    if (choices.isEmpty()) {
        player.message("You have no max cape looks unlocked. Unlock them in the 78 Store; you need to own the matching item.")
        return
    }
    val names = listOf("Plain max cape") + choices.map { player.world.definitions.get(ItemDef::class.java, it.cape).name }
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
            MaxCapeLooks.select(player, 0)
            player.message("Your max cape shows its own colours again.")
        } else {
            val variant = choices.getOrNull(index - 1) ?: return@queue
            MaxCapeLooks.select(player, variant.cape)
            player.message("Your max cape now looks like the ${names[index].lowercase()}.")
        }
    }
}

on_item_option(item = MaxCapes.MAX_CAPE, option = "Customise") { customise(player) }
on_equipment_option(item = MaxCapes.MAX_CAPE, option = "Customise") { customise(player) }
