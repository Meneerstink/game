package gg.rsmod.plugins.content.inter.attack

import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.timer.SPECIAL_ATTACK_TIMER
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * First log-in logic (when accounts have just been made).
 */
on_login {
    if (player.attr.getOrDefault(NEW_ACCOUNT_ATTR, false)) {
        AttackTab.setEnergy(player, 100)
    }

    SpecialEnergyRegen.onLogin(player.timers, SpecialEnergyRegen.wearingLightbearer(player))
}

/**
 * Raise spec by 10% every 30 seconds (15 seconds with the Lightbearer), capping at 100 - see [SpecialEnergyRegen].
 */
on_timer(SPECIAL_ATTACK_TIMER) {
    val spec = AttackTab.getEnergy(player)
    val newSpec = SpecialEnergyRegen.onTimer(player.timers, spec, SpecialEnergyRegen.wearingLightbearer(player))
    AttackTab.setEnergy(player, newSpec)
}

on_item_equip(item = Items.LIGHTBEARER) {
    SpecialEnergyRegen.onLightbearerEquipped(player.timers)
}

on_item_unequip(item = Items.LIGHTBEARER) {
    SpecialEnergyRegen.onLightbearerUnequipped(player.timers)
}

/**
 * Attack style buttons
 */
listOf(11, 12, 13, 14).forEachIndexed { style, component ->
    on_button(interfaceId = 884, component = component) {
        player.setVarp(AttackTab.ATTACK_STYLE_VARP, style)
        // OSRS: choosing a melee style turns autocast off; the chosen spell is remembered for the Spell box.
        gg.rsmod.plugins.content.combat.magic.Autocast.deactivate(player, gg.rsmod.plugins.content.combat.magic.Autocast.Reason.MELEE_STYLE)
    }
}

/**
 * Toggle auto-retaliate button.
 */
on_button(interfaceId = 884, component = 15) {
    player.toggleVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP)
}

/**
 * Toggle special attack.
 */
on_button(interfaceId = 884, component = 4) {
    if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
            player, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.SPECIAL_ATTACK,
        )
    ) {
        return@on_button
    }
    // Granite maul: bar clicks drive Quick Smash homing and the deselect window (GraniteMaul).
    if (gg.rsmod.plugins.content.items.osrs.GraniteMaul.isWielding(player)) {
        gg.rsmod.plugins.content.items.osrs.GraniteMaul.onBarClick(player)
        return@on_button
    }
    if (SpecialAttacks.executeInstant(player)) {
        return@on_button
    }
    player.toggleVarp(AttackTab.SPECIAL_ATTACK_VARP)
}

/**
 * Disable special attack when switching weapons.
 */
on_equip_to_slot(EquipmentType.WEAPON.id) {
    player.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 0)
}

/**
 * Toggles on the 3rd attack style when the 4th weapon style is unavailable.
 */
on_unequip_from_slot(EquipmentType.WEAPON.id) {
    if (player.getVarp(AttackTab.ATTACK_STYLE_VARP) == 3) {
        player.setVarp(AttackTab.ATTACK_STYLE_VARP, 2)
    }
}

/**
 * Disable special attack on log-out.
 */
on_logout {
    player.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 0)
}
