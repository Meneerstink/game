package gg.rsmod.plugins.content.combat.magic

import gg.rsmod.game.tools.importer.AutocastInterfaceLayout

/*
 * OSRS Combat Options autocast (see [Autocast]). The client sends the op it was clicked with as its opcode: 61 = op1, 64 = op2.
 */
private val OP1 = 61
private val OP2 = 64

on_login {
    Autocast.migrateLegacy(player)
    Autocast.sync(player)
}

/*
 * The interface-open hook fires before the IfOpenSub is written, and the client rebuilds 884 from the cache on every (re)open
 * (login, display-mode change, returning from the selection panel), so the Spell box is refreshed one cycle later.
 */
on_interface_open(interfaceId = AutocastInterfaceLayout.COMBAT_TAB) {
    val p = player
    world.queue {
        wait(1)
        if (p.isOnline) Autocast.sync(p)
    }
}

on_button(interfaceId = AutocastInterfaceLayout.COMBAT_TAB, component = AutocastInterfaceLayout.BOX_BUTTON) {
    when (player.getInteractingOpcode()) {
        OP1 -> Autocast.openSelection(player, Autocast.Mode.STANDARD)
        OP2 -> Autocast.openSelection(player, Autocast.Mode.DEFENSIVE)
    }
}

(AutocastInterfaceLayout.SELECT_FIRST_SPELL..AutocastInterfaceLayout.SELECT_CANCEL).forEach { component ->
    on_button(interfaceId = AutocastInterfaceLayout.SELECT_INTERFACE, component = component) {
        Autocast.onSelectionClick(player, component)
    }
}

on_player_death {
    Autocast.onDeath(player)
}

on_equip_to_slot(EquipmentType.WEAPON.id) {
    // The weapon is already in its slot. The selection panel lists what the old weapon allowed, so it closes.
    Autocast.closeSelection(player)
    Autocast.onWeaponChanged(player)
}

on_unequip_from_slot(EquipmentType.WEAPON.id) {
    Autocast.closeSelection(player)
    Autocast.onWeaponChanged(player)
}

/*
 * QA instrumentation (admin): "autocast" prints the authoritative server state next to what the client was sent; "autocast debug"
 * toggles logging of every selection, reset and refusal with its reason.
 */
on_command("autocast", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    if (player.getCommandArgs().firstOrNull() == "debug") {
        AutocastPolicy.debug = !AutocastPolicy.debug
        player.message("Autocast debug logging: ${if (AutocastPolicy.debug) "on" else "off"}.")
        return@on_command
    }
    val spell = Autocast.selected(player)
    player.message("Autocast: spell=${spell?.name ?: "none"} mode=${Autocast.mode(player)} resolved=${Autocast.resolve(player)?.name ?: "none"}")
    player.message("Client mirrors: varp108=${player.getVarp(gg.rsmod.plugins.content.combat.Combat.SELECTED_AUTOCAST_VARP)} style=${player.getVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.ATTACK_STYLE_VARP)} " +
        "book=${player.getSpellbook()} weapon=${player.getEquipment(EquipmentType.WEAPON)?.id ?: -1} lastPlayerAttack=${player.attr[Autocast.LAST_PLAYER_ATTACK_CYCLE] ?: -1}")
}
