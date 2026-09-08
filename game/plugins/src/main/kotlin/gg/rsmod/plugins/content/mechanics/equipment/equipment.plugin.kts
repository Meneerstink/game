package gg.rsmod.plugins.content.mechanics.equipment

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.attr.INTERACTING_PLAYER_ATTR
import gg.rsmod.plugins.content.mechanics.trading.getTradeSession
import gg.rsmod.plugins.content.mechanics.trading.removeTradeSession
import gg.rsmod.plugins.content.quests.finishedQuest
import gg.rsmod.plugins.content.quests.impl.LostCity
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp
import gg.rsmod.plugins.content.mechanics.death.ItemsKeptOnDeath

val KEPT_ON_DEATH_INTERFACE = ItemsKeptOnDeath.INTERFACE_ID

val questItems = arrayOf(Items.QUEST_POINT_CAPE, Items.QUEST_POINT_HOOD)
questItems.forEach {
    can_equip_item(item = it) {
        if (!player.completedAllQuests()) {
            player.message("You need to complete all of the available Quests before you can wear this.")
            false
        } else {
            true
        }
    }
}

val dragonSqShields = arrayOf(Items.DRAGON_SQ_SHIELD, Items.DRAGON_SQ_SHIELD_SP, Items.DRAGON_SQUARE_SHIELD_OR)
dragonSqShields.forEach {
    // TODO: Once Legends Quest is released, make this require Legends Quest instead
    can_equip_item(item = it) {
        if (!player.completedAllQuests()) {
            player.message("You need to complete all of the available Quests before you can wear this.")
            false
        } else {
            true
        }
    }
}

can_equip_item(item = Items.DRAGON_LONGSWORD) {
    if (!player.finishedQuest(LostCity)) {
        player.message("You need to complete <col=0000ff>Lost City</col> before you can equip the Dragon Longsword.")
        false
    } else {
        true
    }
}

can_equip_item(item = Items.DRAGON_DAGGER) {
    if (!player.finishedQuest(LostCity)) {
        player.message("You need to complete <col=0000ff>Lost City</col> before you can equip the Dragon Dagger.")
        false
    } else {
        true
    }
}

can_equip_item(item = Items.DRAGON_DAGGER_P) {
    if (!player.finishedQuest(LostCity)) {
        player.message("You need to complete <col=0000ff>Lost City</col> before you can equip the Dragon Dagger.")
        false
    } else {
        true
    }
}

can_equip_item(item = Items.DRAGON_DAGGER_P_5680) {
    if (!player.finishedQuest(LostCity)) {
        player.message("You need to complete <col=0000ff>Lost City</col> before you can equip the Dragon Dagger.")
        false
    } else {
        true
    }
}

can_equip_item(item = Items.DRAGON_DAGGER_P_5698) {
    if (!player.finishedQuest(LostCity)) {
        player.message("You need to complete <col=0000ff>Lost City</col> before you can equip the Dragon Dagger.")
        false
    } else {
        true
    }
}

on_button(interfaceId = 387, component = 45) {
    when (player.getInteractingOpcode()) {
        61 -> {
            // Interface 17 builds its own item lists out of inventory 93 / worn 94 / familiar 530,
            // but the wording, the title and the "you may choose N" count all come from varbits the
            // server owns - see ItemsKeptOnDeath. Without them the screen opens on the Wilderness
            // panel with a keep count of zero regardless of the player's real risk.
            ItemsKeptOnDeath.open(player)
            player.openInterface(interfaceId = KEPT_ON_DEATH_INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
        }
    }
}

/**
 * "What if I entered the Wilderness?" / "Back" - the only stateful control on the screen. The
 * component bakes op1 itself (`events=0x000002`), so the click always reaches us.
 */
on_button(interfaceId = KEPT_ON_DEATH_INTERFACE, component = ItemsKeptOnDeath.TOGGLE_COMPONENT) {
    ItemsKeptOnDeath.toggleWildernessPreview(player)
}

on_button(interfaceId = KEPT_ON_DEATH_INTERFACE, component = ItemsKeptOnDeath.CLOSE_COMPONENT) {
    player.closeInterface(interfaceId = KEPT_ON_DEATH_INTERFACE)
}

fun bind_unequip(
    equipment: EquipmentType,
    child: Int,
) {
    on_button(interfaceId = 387, component = child) {
        val opt = player.getInteractingOpcode()
        if (player.getTradeSession() != null) {
            val partner = player.attr[INTERACTING_PLAYER_ATTR]?.get()
            partner!!.removeTradeSession()
            player.removeTradeSession()
        }
        when (opt) {
            61 -> {
                // Finding 9 (audit): EquipAction.unequip moves the item straight into the
                // player's real inventory with no Practice-block check, and PracticePvp.cleanup
                // only clears equipment slots (already empty once unequipped) - so granted temp
                // gear could be kept permanently by simply unequipping it mid-match.
                if (PracticePvp.isHoldingTempGear(player)) {
                    player.filterableMessage("You can't remove free Practice PvP gear - it's cleared automatically when the match ends.")
                    return@on_button
                }
                val result = EquipAction.unequip(player, equipment.id)
                // R04.7: every slot affects combat bonuses, not just weapon/shield - the bonus
                // screen (interface 667) went stale after unequipping e.g. a helmet or boots
                // via the ordinary equipment tab, since only WEAPON refreshed it before this.
                if (result == EquipAction.Result.SUCCESS) {
                    player.refreshBonuses()
                    if (equipment == EquipmentType.WEAPON) {
                        player.sendWeaponComponentInformation()
                    }
                }
            }

            25 -> {
                val item = player.equipment[equipment.id] ?: return@on_button
                world.sendExamine(player, item.id, ExamineEntityType.ITEM)
            }

            else -> {
                val item = player.equipment[equipment.id] ?: return@on_button
                val menuOpt =
                    when (opt) {
                        64 -> 1
                        4 -> 2
                        52 -> 3
                        81 -> 4
                        91 -> 5
                        else -> 0
                    }
                if (!world.plugins.executeEquipmentOption(
                        player,
                        item.id,
                        menuOpt,
                    ) &&
                    world.devContext.debugItemActions
                ) {
                    val action = item.getDef(world.definitions).equipmentMenu[menuOpt]
                    player.message(
                        "Unhandled equipment action: [item=${item.id}, option=$menuOpt, action=$action]",
                        ChatMessageType.CONSOLE,
                    )
                }
            }
        }
    }
}

for (equipment in EquipmentType.values) {
    on_equip_to_slot(equipment.id) {
        player.playSound(Sfx.EQUIP_FUN)
        // R04.7: same fix as the unequip side above - refresh on every slot, not just
        // weapon/shield, so the bonus screen never shows stale numbers after any equip change.
        player.refreshBonuses()
        if (equipment == EquipmentType.WEAPON || equipment == EquipmentType.SHIELD) {
            player.sendWeaponComponentInformation()
        }
    }
}

bind_unequip(EquipmentType.HEAD, child = 8)
bind_unequip(EquipmentType.CAPE, child = 11)
bind_unequip(EquipmentType.AMULET, child = 14)
bind_unequip(EquipmentType.AMMO, child = 38)
bind_unequip(EquipmentType.WEAPON, child = 17)
bind_unequip(EquipmentType.CHEST, child = 20)
bind_unequip(EquipmentType.SHIELD, child = 23)
bind_unequip(EquipmentType.LEGS, child = 26)
bind_unequip(EquipmentType.GLOVES, child = 29)
bind_unequip(EquipmentType.BOOTS, child = 32)
bind_unequip(EquipmentType.RING, child = 35)
