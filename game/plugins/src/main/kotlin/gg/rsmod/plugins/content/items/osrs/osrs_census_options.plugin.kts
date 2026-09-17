package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.items.armor.DegradeTable
import gg.rsmod.plugins.content.items.armor.MoonArmour

/*
 * Option census 2026-09-17c, second pass: the imported item options that still had no handler after the shared fixes (48 of 204).
 * Every binding is guarded by the option really being on the cache item. Sources per block; wording that the OSRS Wiki does not
 * quote is a plain ADAPTED message, never an invented mechanic.
 */
fun hasInventoryOption(
    itemId: Int,
    option: String,
): Boolean = world.definitions.get(ItemDef::class.java, itemId).inventoryMenu.any { it.equals(option, ignoreCase = true) }

fun itemName(itemId: Int): String = world.definitions.get(ItemDef::class.java, itemId).name

/** Replaces the clicked item after a yes/no confirmation; the slot is re-checked after the dialogue. */
fun confirmReplace(
    player: Player,
    itemId: Int,
    title: String,
    result: Int,
    done: String,
) {
    val slot = player.getInteractingItemSlot()
    if (player.inventory[slot]?.id != itemId) return
    player.queue {
        if (options("Yes.", "No.", title = title) != 1) return@queue
        if (player.inventory[slot]?.id != itemId) return@queue
        player.inventory[slot] = Item(result)
        player.message(done)
    }
}

/*
 * Imbued rings "Uncharge" (OSRS Wiki "Ring of the gods (i)" / "Ring of suffering (i)": options "Wear, Uncharge, ..."; "Manually
 * uncharging the Ring ... (i) will refund 100% of the points/zeal", "When uncharged, the scroll of imbuing is returned"; ring of
 * suffering: "If a player uncharged the ring, the recoil rings stored will not be returned"). ADAPTED: this server has no imbue
 * currency (no Nightmare Zone, Soul Wars or Emir's Arena) and imbued rings only exist as spawned items, so there is nothing to refund;
 * the ring returns to its base item.
 */
listOf(
    Items.RING_OF_THE_GODS_I to Items.RING_OF_THE_GODS,
    Items.TYRANNICAL_RING_I to Items.TYRANNICAL_RING,
    Items.TREASONOUS_RING_I to Items.TREASONOUS_RING,
    Items.RING_OF_SUFFERING_I to Items.RING_OF_SUFFERING,
    Items.RING_OF_SUFFERING_RI to Items.RING_OF_SUFFERING,
).forEach { (imbued, base) ->
    if (hasInventoryOption(imbued, "Uncharge")) {
        on_item_option(item = imbued, option = "Uncharge") {
            val recoils = imbued == Items.RING_OF_SUFFERING_RI
            confirmReplace(
                player,
                imbued,
                if (recoils) "Remove the imbue? The stored recoil charges are lost." else "Remove the imbue from this ring?",
                base,
                "You remove the imbue from the ring.",
            )
        }
    }
}

/*
 * Amulet of blood fury "Revert" (infobox options "Wear, Check, Revert"): back to the amulet of fury it was made from; the blood shard
 * and charges are not returned (the page only returns a fury on a PvP death too: "the blood shard and any remaining charges are lost").
 * Amulet of rancour (s) "Revert": "If the player reverts the recoloured amulet, they must redo all of the above steps".
 */
if (hasInventoryOption(Items.AMULET_OF_BLOOD_FURY, "Revert")) {
    on_item_option(item = Items.AMULET_OF_BLOOD_FURY, option = "Revert") {
        confirmReplace(
            player,
            Items.AMULET_OF_BLOOD_FURY,
            "Revert to an amulet of fury? The blood shard and its charges are lost.",
            Items.AMULET_OF_FURY,
            "You revert the amulet to an amulet of fury.",
        )
    }
}
if (hasInventoryOption(Items.AMULET_OF_RANCOUR_S, "Revert")) {
    on_item_option(item = Items.AMULET_OF_RANCOUR_S, option = "Revert") {
        confirmReplace(
            player,
            Items.AMULET_OF_RANCOUR_S,
            "Revert the amulet to its original colours?",
            Items.AMULET_OF_RANCOUR,
            "You revert the amulet of rancour.",
        )
    }
}

/*
 * Moon armour "Check" (OSRS Wiki "Moon equipment": "The status of each item consists of 3000 degradable points"). The remaining
 * points come from the same per-combat-tick charge the degradation table spends. ADAPTED wording.
 */
MoonArmour.values().forEach { armour ->
    listOf(armour.newId, armour.degradedId, armour.brokenId).distinct().forEach { id ->
        if (!hasInventoryOption(id, "Check")) return@forEach
        on_item_option(item = id, option = "Check") {
            val item = player.inventory[player.getInteractingItemSlot()]?.takeIf { it.id == id } ?: return@on_item_option
            val percent =
                when (id) {
                    armour.brokenId -> 0
                    armour.newId -> 100
                    else -> {
                        val left = item.attr[ItemAttribute.CHARGES] ?: DegradeTable.MOON_DEGRADED_CHARGES
                        ((left.toLong() * 100 + DegradeTable.MOON_DEGRADED_CHARGES - 1) / DegradeTable.MOON_DEGRADED_CHARGES).toInt().coerceIn(1, 100)
                    }
                }
            player.message(
                if (percent == 0) {
                    "Your ${itemName(id)} is fully degraded and must be repaired."
                } else {
                    "Your ${itemName(id)} has $percent% of its durability left."
                },
            )
        }
    }
}

/* Dizana's max cape (broken / mangled) "Empty": a broken cape has already lost its stored ammunition on the death that broke it. */
listOf(Items.DIZANAS_MAX_CAPE_BROKEN, Items.DIZANAS_MAX_CAPE_L_BROKEN, Items.DIZANAS_MAX_CAPE_L_MANGLED).forEach { cape ->
    if (hasInventoryOption(cape, "Empty")) {
        on_item_option(item = cape, option = "Empty") { player.message("The cape holds no ammunition.") }
    }
}

/*
 * "Inspect" on creation materials and "Read" on the Trouver parchment: OSRS answers with a short description of the item. The exact
 * wording is not on the wiki pages (SOURCE_GAP), so the item's own cache examine text is shown - sourced text, no invented message.
 */
on_world_init_late {
    for (id in 22328 until world.definitions.getCount(ItemDef::class.java)) {
        val def = world.definitions.getNullable(ItemDef::class.java, id) ?: continue
        if (def.noted) continue
        listOf("Inspect", "Read").forEach { option ->
            val index = def.inventoryMenu.indexOfFirst { it.equals(option, ignoreCase = true) }
            if (index < 0 || world.plugins.hasItemOption(id, index + 1)) return@forEach
            on_item_option(item = id, option = option) {
                val examine = world.definitions.get(ItemDef::class.java, id).examine
                player.message(if (examine.isNullOrBlank()) "You see nothing unusual about the ${itemName(id).lowercase()}." else examine)
            }
        }
    }
}
