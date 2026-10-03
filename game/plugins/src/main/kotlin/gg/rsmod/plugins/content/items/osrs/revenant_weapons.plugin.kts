package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item

/**
 * Revenant chainmaces and sceptres: revenant ether charging, Check, Uncharge, Swap and Dismantle (rules and sources in
 * [RevenantWeapons]). ADAPTED: the messages (no sourced wording). Options are bound only when the imported definition carries them.
 */

fun revHasOption(
    itemId: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

fun revUsedSlot(
    player: Player,
    itemId: Int,
): Int? {
    val first = player.attr[INTERACTING_ITEM_SLOT] ?: return null
    val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return null
    return when (itemId) {
        player.inventory[first]?.id -> first
        player.inventory[second]?.id -> second
        else -> null
    }
}

fun etherStatus(item: Item): String = "Revenant ether: ${RevenantWeapons.ether(item)}"

// Ether on the weapon: 1,000 activates an uncharged one, then up to 16,000 more.
RevenantWeapons.ALL.forEach { weapon ->
    on_item_on_item(item1 = Items.REVENANT_ETHER, item2 = weapon) {
        val slot = revUsedSlot(player, weapon) ?: return@on_item_on_item
        val item = player.inventory[slot] ?: return@on_item_on_item
        val taken = RevenantWeapons.etherToTake(item, player.inventory.getItemCount(Items.REVENANT_ETHER))
        if (taken <= 0) {
            player.message(
                if (weapon in RevenantWeapons.CHARGED_FOR) {
                    "You need ${RevenantWeapons.ACTIVATION_ETHER} revenant ether to activate this weapon."
                } else {
                    "This weapon cannot hold any more revenant ether."
                },
            )
            return@on_item_on_item
        }
        player.inventory.remove(Items.REVENANT_ETHER, taken)
        val charged = RevenantWeapons.charge(item, taken)
        player.inventory[slot] = charged
        player.message(etherStatus(charged))
    }
}

RevenantWeapons.UNCHARGED_FOR.keys.forEach { weapon ->
    if (revHasOption(weapon, "Check", worn = false)) {
        on_item_option(item = weapon, option = "Check") {
            val item = player.inventory[player.getInteractingItemSlot()]?.takeIf { it.id == weapon } ?: return@on_item_option
            player.message(etherStatus(item))
        }
    }
    if (revHasOption(weapon, "Check", worn = true)) {
        on_equipment_option(item = weapon, option = "Check") {
            val item = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == weapon } ?: return@on_equipment_option
            player.message(etherStatus(item))
        }
    }
    if (revHasOption(weapon, "Uncharge", worn = false)) {
        on_item_option(item = weapon, option = "Uncharge") {
            val slot = player.getInteractingItemSlot()
            val item = player.inventory[slot]?.takeIf { it.id == weapon } ?: return@on_item_option
            val (uncharged, ether) = RevenantWeapons.uncharge(item)
            if (!player.inventory.add(Items.REVENANT_ETHER, ether, assureFullInsertion = true).hasSucceeded()) {
                player.message("You don't have enough inventory space to do that.")
                return@on_item_option
            }
            player.inventory[slot] = uncharged
        }
    }
}

// "Swap": the sceptre's built-in spell variant <-> the autocasting "(a)" variant, ether kept.
RevenantWeapons.SWAP.keys.forEach { sceptre ->
    if (revHasOption(sceptre, "Swap", worn = false)) {
        on_item_option(item = sceptre, option = "Swap") {
            val slot = player.getInteractingItemSlot()
            val item = player.inventory[slot]?.takeIf { it.id == sceptre } ?: return@on_item_option
            player.inventory[slot] = RevenantWeapons.swapped(item) ?: return@on_item_option
        }
    }
}

// "Players can dismantle an uncharged ... to receive 7,500 revenant ether" (the Craw's bow rule, same for Viggora's chainmace and
// Thammaron's sceptre).
listOf(Items.VIGGORAS_CHAINMACE_U, Items.THAMMARONS_SCEPTRE_U, Items.THAMMARONS_SCEPTRE_AU).forEach { weapon ->
    if (revHasOption(weapon, "Dismantle", worn = false)) {
        on_item_option(item = weapon, option = "Dismantle") {
            val slot = player.getInteractingItemSlot()
            if (player.inventory[slot]?.id != weapon) return@on_item_option
            player.inventory[slot] = null
            if (!player.inventory.add(Items.REVENANT_ETHER, RevenantBows.CRAWS_DISMANTLE_ETHER, assureFullInsertion = true).hasSucceeded()) {
                player.inventory[slot] = Item(weapon)
                player.message("You don't have enough inventory space to do that.")
            }
        }
    }
}

/*
 * Upgrades (OSRS Wiki): "Viggora's chainmace (u) and Claws of Callisto" with 85 Smithing (boostable) -> Ursine chainmace (u);
 * "Thammaron's sceptre (u) with the Skull of Vet'ion using level 85 Crafting" -> Accursed sceptre (u). Dismantle reverses it
 * (the base weapon and the component back). ADAPTED: the messages; the NPC routes (Andros Mai / Phabelle Bile, 500,000 coins)
 * are not built.
 */
data class RevUpgrade(val base: Int, val part: Int, val result: Int, val skill: Int, val skillName: String)

val REV_UPGRADES =
    listOf(
        RevUpgrade(Items.VIGGORAS_CHAINMACE_U, Items.CLAWS_OF_CALLISTO, Items.URSINE_CHAINMACE_U, Skills.SMITHING, "Smithing"),
        RevUpgrade(Items.THAMMARONS_SCEPTRE_U, Items.SKULL_OF_VETION, Items.ACCURSED_SCEPTRE_U, Skills.CRAFTING, "Crafting"),
        RevUpgrade(Items.THAMMARONS_SCEPTRE_AU, Items.SKULL_OF_VETION, Items.ACCURSED_SCEPTRE_AU, Skills.CRAFTING, "Crafting"),
    )
val REV_UPGRADE_LEVEL = 85

REV_UPGRADES.forEach { upgrade ->
    on_item_on_item(item1 = upgrade.part, item2 = upgrade.base) {
        if (player.skills.getCurrentLevel(upgrade.skill) < REV_UPGRADE_LEVEL) {
            player.message("You need a ${upgrade.skillName} level of $REV_UPGRADE_LEVEL to do that.")
            return@on_item_on_item
        }
        val slot = revUsedSlot(player, upgrade.base) ?: return@on_item_on_item
        if (!player.inventory.remove(upgrade.part, 1).hasSucceeded()) return@on_item_on_item
        player.inventory[slot] = Item(upgrade.result)
        gg.rsmod.plugins.content.items.combine.ItemAttachSounds.attached(player, upgrade.part, upgrade.base)
        player.message("You attach the ${world.definitions.get(ItemDef::class.java, upgrade.part).name} to the weapon.")
    }
    if (revHasOption(upgrade.result, "Dismantle", worn = false)) {
        on_item_option(item = upgrade.result, option = "Dismantle") {
            val slot = player.getInteractingItemSlot()
            if (player.inventory[slot]?.id != upgrade.result) return@on_item_option
            if (player.inventory.freeSlotCount < 1) {
                player.message("You don't have enough inventory space to do that.")
                return@on_item_option
            }
            player.inventory[slot] = Item(upgrade.base)
            player.inventory.add(upgrade.part, 1)
        }
    }
}

// Worn "Swap" (the sceptres carry it on the worn menu too).
RevenantWeapons.SWAP.keys.forEach { sceptre ->
    if (revHasOption(sceptre, "Swap", worn = true)) {
        on_equipment_option(item = sceptre, option = "Swap") {
            val item = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == sceptre } ?: return@on_equipment_option
            player.equipment[EquipmentType.WEAPON.id] = RevenantWeapons.swapped(item) ?: return@on_equipment_option
            player.refreshBonuses()
        }
    }
}