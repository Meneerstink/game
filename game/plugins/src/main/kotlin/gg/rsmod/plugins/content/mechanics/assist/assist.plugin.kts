package gg.rsmod.plugins.content.mechanics.assist

import gg.rsmod.game.model.skill.XpRedirect

/**
 * The Assist System's hooks (rules and sources in [AssistSystem]): the "Req Assist" option (slot 7, runetek5.plugin.kts), the Assist
 * XP display 301, the experience redirect, and every way a session ends - closing 301 (moving, Escape, the close button), logging
 * out, dying, leaving the 20 squares, combat ([AssistSystem.endForCombat], combat.plugin.kts) and the Assist tab filter
 * (chat_tabs.plugin.kts).
 */
XpRedirect.handler = AssistSystem::redirectXp

on_player_option(AssistSystem.OPTION) {
    AssistSystem.option(player, player.getInteractingPlayer())
}

AssistSystem.SKILLS.forEach { s ->
    on_button(interfaceId = AssistSystem.INTERFACE, component = s.component) {
        AssistSystem.toggle(player, s.component)
    }
}

on_interface_close(AssistSystem.INTERFACE) {
    if (player.attr.has(AssistSystem.ASSISTED)) AssistSystem.end(player)
}

on_timer(AssistSystem.RANGE_TIMER) {
    AssistSystem.checkRange(player)
    if (player.attr.has(AssistSystem.ASSISTANT)) player.timers[AssistSystem.RANGE_TIMER] = 1
}

on_logout {
    if (AssistSystem.inSession(player) || player.attr.has(AssistSystem.ASSISTANT) || player.attr.has(AssistSystem.ASSISTED)) {
        AssistSystem.end(player)
    }
}

on_player_pre_death {
    if (player.attr.has(AssistSystem.ASSISTANT) || player.attr.has(AssistSystem.ASSISTED)) AssistSystem.end(player)
}
